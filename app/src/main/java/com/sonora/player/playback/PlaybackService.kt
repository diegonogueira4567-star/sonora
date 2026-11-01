package com.sonora.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sonora.player.MainActivity
import com.sonora.player.R
import com.sonora.player.SonoraApp
import com.sonora.player.data.local.preferences.AppSettings
import com.sonora.player.domain.model.TrackSource
import com.sonora.player.widget.PlayerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background playback: one ExoPlayer + one MediaSession for the whole app. Media3 manages the
 * foreground service, the media notification, lock-screen/system controls and Bluetooth/headset
 * media buttons through the session. It is a library service only so that the system media
 * controls can offer "resume" for the last played track (no remote browsing is exposed).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private val container by lazy { (application as SonoraApp).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: SonoraPlayer
    private var session: MediaLibrarySession? = null
    private lateinit var bitmapLoader: ArtworkBitmapLoader
    private lateinit var queueStore: QueueStateStore

    @Volatile
    private var settings = AppSettings()
    private val listenTracker = ListenTracker()
    private var lastTickAt = 0L
    private var lastSaveAt = 0L
    private var consecutiveErrors = 0
    private var fadeInCurrent = false
    private var sleepEndsAtWallMs: Long? = null
    private var sleepEndOfTrack = false
    private var tickerJob: Job? = null

    override fun onCreate() {
        com.sonora.player.PerfLog.mark("service.onCreate.start")
        super.onCreate()
        queueStore = QueueStateStore(File(filesDir, "queue_state.json"))

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val audioSessionId = Util.generateAudioSessionIdV21(this)
        exoPlayer.audioSessionId = audioSessionId
        player = SonoraPlayer(exoPlayer)
        container.audioEffects.attach(audioSessionId)

        bitmapLoader = ArtworkBitmapLoader(container.artworkLoader)
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setId("sonora_session")
            .setSessionActivity(openApp)
            .setBitmapLoader(CacheBitmapLoader(bitmapLoader))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_sonora) },
        )

        exoPlayer.addListener(PlayerEvents())
        com.sonora.player.PerfLog.mark("service.onCreate.end")
        scope.launch {
            settings = container.settingsStore.settings.first()
            if (settings.restoreQueue) restoreQueue(playWhenReady = false)
            container.settingsStore.settings.collect {
                settings = it
                if (it.fadeDurationMs == 0) exoPlayer.volume = 1f
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in background if music is playing; otherwise free everything.
        if (!exoPlayer.playWhenReady || exoPlayer.mediaItemCount == 0 || exoPlayer.playbackState == Player.STATE_ENDED) {
            saveQueue()
            stopSelf()
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        saveJob?.cancel()
        // Persisted on the app scope: this service's scope is cancelled below.
        listenTracker.flush()?.let(::persistListen)
        snapshot()?.let(queueStore::save) ?: queueStore.clear()
        session?.run {
            release()
            session = null
        }
        exoPlayer.release()
        container.audioEffects.release()
        bitmapLoader.release()
        PlayerWidgetProvider.update(this, null, null, false, null)
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- queue operations

    private suspend fun resolveItems(trackIds: LongArray): List<MediaItem> =
        container.libraryRepository.getTracks(trackIds.toList()).filter { it.available }.map(MediaItems::fromTrack)

    private fun insertItems(items: List<MediaItem>, playNext: Boolean) {
        if (items.isEmpty()) return
        if (exoPlayer.mediaItemCount == 0) {
            player.setMediaItems(items.toMutableList(), 0, 0)
            exoPlayer.prepare()
            return
        }
        val current = exoPlayer.currentMediaItemIndex
        val insertAt = if (playNext) current + 1 else exoPlayer.mediaItemCount
        val oldOrder = player.shuffledOrder()
        exoPlayer.addMediaItems(insertAt, items)
        val anchor = if (playNext) current else null
        player.applyShuffleOrder(ShuffleOrders.withInserted(oldOrder, insertAt, items.size, anchor))
        if (exoPlayer.playbackState == Player.STATE_ENDED || exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
    }

    /** Moves an item in the EFFECTIVE play order (shuffle-aware). */
    private fun moveInPlayOrder(from: Int, to: Int) {
        if (exoPlayer.shuffleModeEnabled) {
            player.applyShuffleOrder(ShuffleOrders.moved(player.currentPlayOrder(), from, to))
        } else if (from in 0 until exoPlayer.mediaItemCount && to in 0 until exoPlayer.mediaItemCount) {
            exoPlayer.moveMediaItem(from, to)
        }
    }

    /** Removes every item except the current one. */
    private fun clearUpcoming() {
        val index = exoPlayer.currentMediaItemIndex
        val count = exoPlayer.mediaItemCount
        if (count == 0) return
        if (index + 1 < count) exoPlayer.removeMediaItems(index + 1, count)
        if (index > 0) exoPlayer.removeMediaItems(0, index)
    }

    // ---------------------------------------------------------------- sleep timer

    private fun setSleepTimer(minutes: Int, endOfTrack: Boolean) {
        sleepEndOfTrack = endOfTrack
        sleepEndsAtWallMs = if (endOfTrack) null else System.currentTimeMillis() + minutes * 60_000L
        exoPlayer.pauseAtEndOfMediaItems = endOfTrack
        publishSessionExtras()
        updateTicker()
    }

    private fun cancelSleepTimer() {
        sleepEndOfTrack = false
        sleepEndsAtWallMs = null
        exoPlayer.pauseAtEndOfMediaItems = false
        publishSessionExtras()
        updateTicker()
    }

    private fun publishSessionExtras() {
        session?.sessionExtras = Bundle().apply {
            putLong(PlaybackCommands.EXTRA_SLEEP_ENDS_AT, sleepEndsAtWallMs ?: 0L)
            putBoolean(PlaybackCommands.EXTRA_SLEEP_END_OF_TRACK, sleepEndOfTrack)
        }
    }

    // ---------------------------------------------------------------- periodic work

    /**
     * Periodic work (listen time, fade, sleep timer) only runs while it is needed: during playback or
     * while a sleep timer is armed. When paused the service does no polling at all.
     */
    private fun updateTicker() {
        val needed = exoPlayer.isPlaying || sleepEndsAtWallMs != null
        if (needed && tickerJob?.isActive != true) {
            tickerJob = scope.launch {
                lastTickAt = SystemClock.elapsedRealtime()
                while (isActive) {
                    val fading = settings.fadeDurationMs > 0 && exoPlayer.isPlaying
                    delay(if (fading) 100 else 1000)
                    tick()
                }
            }
        } else if (!needed) {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastTickAt
        lastTickAt = now
        if (exoPlayer.isPlaying) {
            listenTracker.onListened(delta)?.let(::persistListen)
            applyFade()
            if (now - lastSaveAt > 15_000) saveQueue(immediate = true)
        }
        sleepEndsAtWallMs?.let { endsAt ->
            if (System.currentTimeMillis() >= endsAt) {
                exoPlayer.pause()
                cancelSleepTimer()
                broadcast("Temporizador encerrou a reprodução.")
            }
        }
    }

    private fun applyFade() {
        val fadeMs = settings.fadeDurationMs
        if (fadeMs <= 0) {
            if (exoPlayer.volume != 1f) exoPlayer.volume = 1f
            return
        }
        val fadeOut = exoPlayer.hasNextMediaItem() && !sleepEndOfTrack && exoPlayer.repeatMode != Player.REPEAT_MODE_ONE
        val v = FadeCalculator.volume(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0), fadeMs, fadeInCurrent, fadeOut)
        exoPlayer.volume = v
    }

    private fun persistListen(report: ListenTracker.Report) {
        val trackId = MediaItems.trackIdOf(report.mediaId) ?: return
        container.appScope.launch(Dispatchers.IO) {
            runCatching { container.libraryRepository.recordListen(trackId, report.listenedMs, report.countAsPlay) }
                .onFailure { Log.w(TAG, "Could not record listen", it) }
        }
    }

    // ---------------------------------------------------------------- persistence

    private var saveJob: Job? = null

    /**
     * Bursts of player events (e.g. loading a 3,000-track queue) are coalesced into one write.
     */
    private fun saveQueue(immediate: Boolean = false) {
        if (!::exoPlayer.isInitialized) return
        saveJob?.cancel()
        saveJob = scope.launch {
            if (!immediate) delay(SAVE_DEBOUNCE_MS)
            lastSaveAt = SystemClock.elapsedRealtime()
            val snapshot = snapshot()
            withContext(Dispatchers.IO) { if (snapshot == null) queueStore.clear() else queueStore.save(snapshot) }
        }
    }

    private fun snapshot(): QueueSnapshot? {
        val count = exoPlayer.mediaItemCount
        if (count == 0) return null
        return QueueSnapshot(
            mediaIds = (0 until count).map { exoPlayer.getMediaItemAt(it).mediaId },
            index = exoPlayer.currentMediaItemIndex,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
            shuffle = exoPlayer.shuffleModeEnabled,
            repeatMode = exoPlayer.repeatMode,
            shuffleOrder = player.shuffledOrder().toList(),
        )
    }

    private suspend fun loadSnapshotItems(): Pair<QueueSnapshot, List<MediaItem>>? = withContext(Dispatchers.IO) {
        val snapshot = queueStore.load() ?: return@withContext null
        val trackIds = snapshot.mediaIds.mapNotNull { MediaItems.trackIdOf(it) }
        val tracks = container.libraryRepository.getTracks(trackIds).associateBy { it.id.toString() }
        val items = snapshot.mediaIds.map { id ->
            tracks[id]?.takeIf { it.available }?.let(MediaItems::fromTrack)
                ?: MediaItems.externalUriOf(id)?.let { MediaItems.fromExternalUri(it, it.lastPathSegment ?: "Arquivo") }
        }
        if (items.all { it == null }) return@withContext null
        snapshot to items.filterNotNull()
    }

    private fun restoreQueue(playWhenReady: Boolean) {
        scope.launch {
            val (snapshot, items) = loadSnapshotItems() ?: return@launch
            if (exoPlayer.mediaItemCount > 0) return@launch // user already started something
            val index = snapshot.mediaIds.getOrNull(snapshot.index)?.let { id -> items.indexOfFirst { it.mediaId == id } }
                ?.takeIf { it >= 0 } ?: 0
            val sameSize = items.size == snapshot.mediaIds.size
            exoPlayer.repeatMode = snapshot.repeatMode
            exoPlayer.setMediaItems(items, index, if (sameSize || index > 0) snapshot.positionMs else 0)
            exoPlayer.shuffleModeEnabled = snapshot.shuffle
            if (sameSize) player.applyShuffleOrder(snapshot.shuffleOrder.toIntArray())
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.prepare()
        }
    }

    private fun broadcast(message: String) {
        session?.broadcastCustomCommand(
            SessionCommand(PlaybackCommands.EVENT_MESSAGE, Bundle.EMPTY),
            Bundle().apply { putString(PlaybackCommands.ARG_MESSAGE, message) },
        )
    }

    private fun updateWidget() {
        val item = exoPlayer.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString()
        val artist = item?.mediaMetadata?.artist?.toString()
        val playing = exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED
        val artUri = item?.mediaMetadata?.artworkUri?.toString()
        scope.launch(Dispatchers.IO) {
            val art = artUri?.let { container.artworkLoader.loadBlocking(it, 256) }
            PlayerWidgetProvider.update(this@PlaybackService, title, artist, playing, art)
        }
    }

    private fun loadLyrics(item: MediaItem?, tracks: Tracks) {
        val mediaId = item?.mediaId ?: return
        val embedded = EmbeddedLyricsExtractor.fromTracks(tracks)
        if (embedded != null) {
            container.lyricsRepository.publish(mediaId, LrcParser.parse(embedded, "Letra incorporada no arquivo"))
            return
        }
        val trackId = MediaItems.trackIdOf(mediaId)
        scope.launch(Dispatchers.IO) {
            val track = trackId?.let { container.libraryRepository.getTrack(it) }
            val sidecar = if (track?.source == TrackSource.FOLDER && track.folderUri != null) {
                container.folderAccess.findLyricsSidecar(Uri.parse(track.folderUri), Uri.parse(track.uri))
            } else {
                null
            }
            val lyrics = sidecar?.let { uri ->
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                    ?.let { LrcParser.parse(it, "Arquivo .lrc") }
            }
            container.lyricsRepository.publish(mediaId, lyrics)
        }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Arquivo não encontrado. Ele pode ter sido movido ou excluído."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Sem permissão para abrir este arquivo."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Arquivo corrompido ou incompleto."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Formato de arquivo não suportado."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "O aparelho não consegue decodificar este formato."
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "Falha ao decodificar o áudio."
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "Falha na saída de áudio."
        else -> "Não foi possível reproduzir esta faixa."
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            listenTracker.start(mediaItem?.mediaId, exoPlayer.duration.takeIf { it > 0 } ?: 0)?.let(::persistListen)
            fadeInCurrent = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && settings.fadeDurationMs > 0
            if (!fadeInCurrent && exoPlayer.volume != 1f) exoPlayer.volume = 1f
            saveQueue()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && sleepEndOfTrack && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                cancelSleepTimer()
                broadcast("Temporizador encerrou a reprodução ao fim da faixa.")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                listenTracker.updateDuration(exoPlayer.duration)
            }
            if (playbackState == Player.STATE_ENDED) {
                listenTracker.flush()?.let(::persistListen)
                exoPlayer.volume = 1f
                if (sleepEndOfTrack) cancelSleepTimer()
                saveQueue()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            lastTickAt = SystemClock.elapsedRealtime()
            if (isPlaying) consecutiveErrors = 0 else saveQueue()
            updateTicker()
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (!tracks.isEmpty) loadLyrics(exoPlayer.currentMediaItem, tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error ${error.errorCodeName}")
            val message = describe(error)
            consecutiveErrors++
            val canSkip = settings.skipOnError && exoPlayer.hasNextMediaItem() && consecutiveErrors < MAX_CONSECUTIVE_ERRORS
            broadcast(if (canSkip) "$message Pulando para a próxima." else message)
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) container.libraryRepository.requestRescan(force = true)
            if (canSkip) {
                val wasPlaying = exoPlayer.playWhenReady
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
                exoPlayer.playWhenReady = wasPlaying
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            ) {
                updateWidget()
            }
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                saveQueue()
            }
        }
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            // A library session must also grant the library commands (get root/children), otherwise
            // System UI's "resume playback" browser is refused.
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            if (controller.packageName == packageName) {
                PlaybackCommands.ALL.forEach { commands.add(PlaybackCommands.command(it)) }
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands.build())
                .setSessionExtras(Bundle().apply {
                    putLong(PlaybackCommands.EXTRA_SLEEP_ENDS_AT, sleepEndsAtWallMs ?: 0L)
                    putBoolean(PlaybackCommands.EXTRA_SLEEP_END_OF_TRACK, sleepEndOfTrack)
                })
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.PLAY_NEXT, PlaybackCommands.ADD_TO_QUEUE -> {
                    val ids = args.getLongArray(PlaybackCommands.ARG_TRACK_IDS) ?: LongArray(0)
                    val playNext = customCommand.customAction == PlaybackCommands.PLAY_NEXT
                    return scope.future {
                        insertItems(resolveItems(ids), playNext)
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }
                }
                PlaybackCommands.MOVE_IN_PLAY_ORDER ->
                    moveInPlayOrder(args.getInt(PlaybackCommands.ARG_FROM), args.getInt(PlaybackCommands.ARG_TO))
                PlaybackCommands.CLEAR_UPCOMING -> clearUpcoming()
                PlaybackCommands.SET_SLEEP_TIMER ->
                    setSleepTimer(args.getInt(PlaybackCommands.ARG_MINUTES), args.getBoolean(PlaybackCommands.ARG_END_OF_TRACK))
                PlaybackCommands.CANCEL_SLEEP_TIMER -> cancelSleepTimer()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Controllers do not always send the URI; resolve it from the request metadata or the library. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.mapNotNull { item ->
                when {
                    item.localConfiguration != null -> item
                    item.requestMetadata.mediaUri != null -> item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                    else -> MediaItems.trackIdOf(item.mediaId)
                        ?.let { container.libraryRepository.getTrack(it) }
                        ?.takeIf { it.available }
                        ?.let(MediaItems::fromTrack)
                }
            }.toMutableList()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(getString(R.string.app_name)).setIsBrowsable(true).setIsPlayable(false).build())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        /** Only exposes the last played track (used by the system "resume" media card). */
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val current = exoPlayer.currentMediaItem ?: loadSnapshotItems()?.let { (snapshot, items) ->
                items.firstOrNull { it.mediaId == snapshot.mediaIds.getOrNull(snapshot.index) } ?: items.firstOrNull()
            }
            LibraryResult.ofItemList(if (parentId == ROOT_ID && current != null) ImmutableList.of(current) else ImmutableList.of(), params)
        }

        /** Called for "resume" from system UI / Bluetooth play button when nothing is loaded. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val (snapshot, items) = loadSnapshotItems() ?: throw UnsupportedOperationException("Nada para retomar")
            val index = snapshot.mediaIds.getOrNull(snapshot.index)?.let { id -> items.indexOfFirst { it.mediaId == id } }
                ?.takeIf { it >= 0 } ?: 0
            MediaSession.MediaItemsWithStartPosition(items, index, snapshot.positionMs)
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val MAX_CONSECUTIVE_ERRORS = 8
        private const val SAVE_DEBOUNCE_MS = 1_000L
        private const val ROOT_ID = "sonora_root"
    }
}
