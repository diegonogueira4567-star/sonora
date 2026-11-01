package com.sonora.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.sonora.player.domain.model.RepeatMode
import com.sonora.player.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueItem(
    /** Index in the player timeline (original order). */
    val timelineIndex: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val durationMs: Long,
)

data class PlayerUiState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    /** Items in EFFECTIVE play order (shuffle applied). */
    val queue: List<QueueItem> = emptyList(),
    /** Position of the current item inside [queue]. */
    val currentQueuePosition: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val sleepEndsAtMs: Long = 0,
    val sleepAtEndOfTrack: Boolean = false,
    val error: String? = null,
) {
    val hasMedia: Boolean get() = mediaId != null
    val trackId: Long? get() = MediaItems.trackIdOf(mediaId)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
/**
 * UI-side client of [PlaybackService] through a MediaController. The UI only renders what the
 * real player reports (no simulated timers); position is sampled from the controller.
 */
class PlaybackController(private val context: Context, private val scope: CoroutineScope) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var users = 0
    private var positionJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Reference-counted: the activity connects on start and releases on stop. */
    fun connect() {
        users++
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).setListener(SessionListener()).buildAsync()
        controllerFuture = future
        scope.launch {
            try {
                val c = future.await()
                controller = c
                c.addListener(PlayerListener())
                refresh()
                startPositionUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Could not connect to playback service", e)
                _messages.tryEmit("Não foi possível iniciar o serviço de reprodução.")
                controllerFuture = null
            }
        }
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        positionJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c == null) {
            _messages.tryEmit("O reprodutor ainda está iniciando. Tente novamente.")
            return
        }
        block(c)
    }

    // ------------------------------------------------------------ commands

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean? = null) {
        val playable = tracks.filter { it.available }
        if (playable.isEmpty()) {
            _messages.tryEmit("Nenhuma faixa disponível para tocar.")
            return
        }
        val start = tracks.getOrNull(startIndex)?.let { s -> playable.indexOfFirst { it.id == s.id } }?.takeIf { it >= 0 } ?: 0
        withController { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            val first = if (shuffle == true && startIndex == 0) playable.indices.random() else start
            c.setMediaItems(playable.map(MediaItems::fromTrack), first, 0)
            c.prepare()
            c.play()
        }
    }

    fun playExternal(uri: Uri, title: String) = withController { c ->
        c.setMediaItem(MediaItems.fromExternalUri(uri, title))
        c.prepare()
        c.play()
    }

    fun togglePlayPause() = withController { c ->
        if (c.playbackState == Player.STATE_ENDED) {
            c.seekToDefaultPosition(0)
            c.play()
        } else if (c.playWhenReady) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs.coerceAtLeast(0))
        _position.value = positionMs
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(tracks: List<Track>) = sendTracks(PlaybackCommands.PLAY_NEXT, tracks, "Vai tocar a seguir")
    fun addToQueue(tracks: List<Track>) = sendTracks(PlaybackCommands.ADD_TO_QUEUE, tracks, "Adicionada à fila")

    private fun sendTracks(action: String, tracks: List<Track>, confirmation: String) {
        val ids = tracks.filter { it.available }.map { it.id }.toLongArray()
        if (ids.isEmpty()) return
        send(action, Bundle().apply { putLongArray(PlaybackCommands.ARG_TRACK_IDS, ids) }) {
            _messages.tryEmit(if (ids.size == 1) confirmation else "$confirmation: ${ids.size} faixas")
        }
    }

    /** Positions refer to the EFFECTIVE play order shown in the queue screen. */
    fun moveInQueue(from: Int, to: Int) =
        send(PlaybackCommands.MOVE_IN_PLAY_ORDER, Bundle().apply { putInt(PlaybackCommands.ARG_FROM, from); putInt(PlaybackCommands.ARG_TO, to) })

    fun removeFromQueue(timelineIndex: Int) = withController {
        if (timelineIndex in 0 until it.mediaItemCount) it.removeMediaItem(timelineIndex)
    }

    fun skipToQueueItem(timelineIndex: Int) = withController {
        if (timelineIndex in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(timelineIndex)
            it.play()
        }
    }

    fun clearUpcoming() = send(PlaybackCommands.CLEAR_UPCOMING, Bundle.EMPTY)

    fun stopAndClear() = withController {
        it.stop()
        it.clearMediaItems()
    }

    fun setSleepTimer(minutes: Int) =
        send(PlaybackCommands.SET_SLEEP_TIMER, Bundle().apply { putInt(PlaybackCommands.ARG_MINUTES, minutes) })

    fun setSleepAtEndOfTrack() =
        send(PlaybackCommands.SET_SLEEP_TIMER, Bundle().apply { putBoolean(PlaybackCommands.ARG_END_OF_TRACK, true) })

    fun cancelSleepTimer() = send(PlaybackCommands.CANCEL_SLEEP_TIMER, Bundle.EMPTY)

    private fun send(action: String, args: Bundle, onSuccess: () -> Unit = {}) = withController { c ->
        val future = c.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        scope.launch {
            val result = runCatching { future.await() }.getOrNull()
            if (result?.resultCode == SessionResult.RESULT_SUCCESS) onSuccess()
            else _messages.tryEmit("Não foi possível concluir a ação.")
        }
    }

    // ------------------------------------------------------------ state

    private var finePositionUsers = 0

    /**
     * Screens that show the exact position (seek bar, synced lyrics) ask for 4 updates per second
     * while visible; otherwise (mini player only) one per second is enough and every update costs a
     * frame. Returns the function that releases the request.
     */
    fun requestFinePosition(): () -> Unit {
        finePositionUsers++
        var released = false
        return {
            if (!released) {
                released = true
                finePositionUsers--
            }
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition.coerceAtLeast(0) }
                delay(if (_state.value.isPlaying && finePositionUsers > 0) 250 else 1000)
            }
        }
    }

    private var cachedOrder = IntArray(0)
    private var cachedQueue: List<QueueItem> = emptyList()

    /**
     * Rebuilding the queue reads every MediaItem (thousands in big queues), so it is only done when
     * the timeline or the shuffle order changed, not on every player event.
     */
    private fun refresh(rebuildQueue: Boolean = true) {
        val c = controller ?: return
        val item: MediaItem? = c.currentMediaItem
        val metadata = item?.mediaMetadata
        if (rebuildQueue || cachedOrder.size != c.mediaItemCount) {
            cachedOrder = ShuffleOrders.playOrder(c.currentTimeline, c.shuffleModeEnabled)
            cachedQueue = buildQueue(c, cachedOrder)
        }
        val order = cachedOrder
        val queue = cachedQueue
        val extras = c.sessionExtras
        _state.value = PlayerUiState(
            connected = true,
            mediaId = item?.mediaId,
            title = metadata?.title?.toString() ?: item?.localConfiguration?.uri?.lastPathSegment ?: "",
            artist = metadata?.artist?.toString(),
            album = metadata?.albumTitle?.toString(),
            artworkUri = metadata?.artworkUri?.toString(),
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady && c.playbackState != Player.STATE_ENDED,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.takeIf { it > 0 } ?: (metadata?.durationMs ?: 0),
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            queue = queue,
            currentQueuePosition = order.indexOf(c.currentMediaItemIndex),
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            sleepEndsAtMs = extras.getLong(PlaybackCommands.EXTRA_SLEEP_ENDS_AT, 0L),
            sleepAtEndOfTrack = extras.getBoolean(PlaybackCommands.EXTRA_SLEEP_END_OF_TRACK, false),
            error = c.playerError?.let { "Erro de reprodução (${it.errorCodeName})" },
        )
        _position.value = c.currentPosition.coerceAtLeast(0)
    }

    private fun buildQueue(c: MediaController, order: IntArray): List<QueueItem> =
        order.map { index ->
            val mi = c.getMediaItemAt(index)
            QueueItem(
                timelineIndex = index,
                mediaId = mi.mediaId,
                title = mi.mediaMetadata.title?.toString() ?: "Sem título",
                artist = mi.mediaMetadata.artist?.toString(),
                artworkUri = mi.mediaMetadata.artworkUri?.toString(),
                durationMs = mi.mediaMetadata.durationMs ?: 0,
            )
        }

    private inner class PlayerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(
            rebuildQueue = events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED),
        )

        override fun onPlayerError(error: PlaybackException) {
            refresh()
        }
    }

    private inner class SessionListener : MediaController.Listener {
        override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (command.customAction == PlaybackCommands.EVENT_MESSAGE) {
                args.getString(PlaybackCommands.ARG_MESSAGE)?.let { _messages.tryEmit(it) }
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh(rebuildQueue = false)

        override fun onDisconnected(controller: MediaController) {
            this@PlaybackController.controller = null
            controllerFuture = null
            _state.value = _state.value.copy(connected = false)
            // Reconnect if the UI still needs the player (e.g. the service was killed by the system).
            if (users > 0) {
                users--
                scope.launch { delay(500); connect() }
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackController"
    }
}
