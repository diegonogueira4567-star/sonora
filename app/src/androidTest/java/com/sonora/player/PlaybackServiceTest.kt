package com.sonora.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.sonora.player.playback.PlaybackCommands
import com.sonora.player.playback.PlaybackService
import com.sonora.player.playback.ShuffleOrders
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Drives the real PlaybackService (ExoPlayer + MediaSession) with real audio files. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private lateinit var controller: MediaController
    private val dir by lazy { File(targetContext.filesDir, "test-audio").apply { mkdirs() } }
    private val short1 by lazy { writeToneWav(File(dir, "short1.wav"), 1.5, 440.0) }
    private val short2 by lazy { writeToneWav(File(dir, "short2.wav"), 1.5, 660.0) }
    private val long1 by lazy { writeToneWav(File(dir, "long1.wav"), 8.0, 520.0) }
    private val corrupt by lazy { writeCorruptFile(File(dir, "broken.mp3")) }

    private fun item(file: File, id: String = file.name): MediaItem =
        MediaItem.Builder().setMediaId("ext:${Uri.fromFile(file)}").setUri(Uri.fromFile(file))
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(file)).build())
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(id).build()).build()

    private var activity: androidx.test.core.app.ActivityScenario<MainActivity>? = null

    @Before
    fun connect() {
        // Android 15 only grants audio focus to apps in the foreground (or with a media FGS), so
        // playback is started the way a user does it: with the app on screen.
        grantAudioPermission()
        kotlinx.coroutines.runBlocking { app.container.settingsStore.setOnboardingDone(true) }
        activity = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val token = SessionToken(targetContext, ComponentName(targetContext, PlaybackService::class.java))
        val future = onMain { MediaController.Builder(targetContext, token).buildAsync() }
        controller = future.get(10, TimeUnit.SECONDS)
        onMain {
            controller.stop()
            controller.clearMediaItems()
            controller.shuffleModeEnabled = false
            controller.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    @After
    fun release() {
        if (!::controller.isInitialized) {
            activity?.close()
            return
        }
        onMain {
            controller.stop()
            controller.clearMediaItems()
            controller.release()
        }
        activity?.close()
    }

    private fun load(vararg files: File, start: Int = 0, play: Boolean = true) = onMain {
        controller.setMediaItems(files.map { item(it) }, start, 0)
        controller.prepare()
        if (play) controller.play()
    }

    @Test
    fun playPauseSeekNextPrevious() {
        load(long1, short1, start = 0)
        waitUntil(message = "tocando") { controller.isPlaying }
        onMain { controller.pause() }
        waitUntil(message = "pausado") { !controller.isPlaying }
        onMain { controller.seekTo(5_000) }
        waitUntil(message = "posição 5s") { controller.currentPosition in 4_900..5_600 }
        onMain { controller.play() }
        waitUntil(message = "retomado") { controller.isPlaying && controller.currentPosition >= 5_000 }
        onMain { controller.seekToNextMediaItem() }
        waitUntil(message = "próxima") { controller.currentMediaItemIndex == 1 }
        onMain { controller.seekToPreviousMediaItem() }
        waitUntil(message = "anterior") { controller.currentMediaItemIndex == 0 }
        assertTrue(onMain { controller.duration } in 7_500..8_500)
    }

    @Test
    fun endOfTrack_advancesAutomatically_thenEnds() {
        load(short1, short2)
        waitUntil(message = "avançou para a segunda faixa") { controller.currentMediaItemIndex == 1 }
        waitUntil(message = "fim da fila") { controller.playbackState == Player.STATE_ENDED }
        assertFalse(onMain { controller.isPlaying })
    }

    @Test
    fun repeatOne_replaysSameTrack() {
        load(short1, short2, play = false)
        onMain { controller.repeatMode = Player.REPEAT_MODE_ONE; controller.play() }
        var maxSeen = 0L
        waitUntil(message = "repetição da faixa") {
            val p = controller.currentPosition
            if (p > maxSeen) maxSeen = p
            // Position wrapped around after reaching the end, while still on the first item.
            maxSeen > 1_000 && p < maxSeen - 500 && controller.currentMediaItemIndex == 0
        }
        assertEquals(Player.REPEAT_MODE_ONE, onMain { controller.repeatMode })
        assertTrue(onMain { controller.playbackState != Player.STATE_ENDED })
    }

    @Test
    fun repeatAll_wrapsToFirstTrack() {
        onMain { controller.repeatMode = Player.REPEAT_MODE_ALL }
        load(short1, short2, start = 1)
        waitUntil(message = "voltou ao início") { controller.currentMediaItemIndex == 0 && controller.isPlaying }
    }

    @Test
    fun shuffle_startsWithChosenItemAndKeepsOriginalOrder() {
        val files = (0 until 10).map { writeToneWav(File(dir, "s$it.wav"), 1.0, 300.0 + it * 20) }
        onMain { controller.shuffleModeEnabled = true }
        load(*files.toTypedArray(), start = 4, play = false)
        waitUntil(message = "fila carregada") { controller.mediaItemCount == 10 }
        val order = onMain { ShuffleOrders.playOrder(controller.currentTimeline, true) }
        assertEquals(4, order.first())
        assertTrue(ShuffleOrders.isValidPermutation(order, 10))
        // Original order is untouched: timeline index i is still file i.
        assertEquals("s7.wav", onMain { controller.getMediaItemAt(7).mediaMetadata.title.toString() })
        onMain { controller.shuffleModeEnabled = false }
        waitUntil(message = "aleatório desligado") { !controller.shuffleModeEnabled }
        assertEquals((0 until 10).toList(), onMain { ShuffleOrders.playOrder(controller.currentTimeline, false).toList() })
    }

    @Test
    fun queue_moveRemoveAndClearUpcoming() {
        load(long1, short1, short2, play = false)
        waitUntil { controller.mediaItemCount == 3 }
        onMain { controller.sendCustomCommand(SessionCommand(PlaybackCommands.MOVE_IN_PLAY_ORDER, Bundle.EMPTY), Bundle().apply { putInt("from", 2); putInt("to", 1) }) }
        waitUntil(message = "reordenada") { controller.getMediaItemAt(1).mediaMetadata.title.toString() == "short2.wav" }
        onMain { controller.removeMediaItem(2) }
        waitUntil(message = "removida") { controller.mediaItemCount == 2 }
        onMain { controller.sendCustomCommand(SessionCommand(PlaybackCommands.CLEAR_UPCOMING, Bundle.EMPTY), Bundle.EMPTY) }
        waitUntil(message = "fila limpa") { controller.mediaItemCount == 1 }
        assertEquals("long1.wav", onMain { controller.currentMediaItem?.mediaMetadata?.title.toString() })
    }

    @Test
    fun corruptFile_isSkippedAndPlaybackContinues() {
        load(corrupt, long1)
        waitUntil(15_000, "pulou o arquivo corrompido") { controller.currentMediaItemIndex == 1 && controller.isPlaying }
    }

    @Test
    fun onlyUnplayableFile_reportsError() {
        load(File(dir, "nao-existe.mp3"))
        waitUntil(15_000, "erro reportado") { controller.playerError != null }
        assertNotNull(onMain { controller.playerError })
    }

    @Test
    fun audioFocusLoss_pausesPlayback() {
        load(long1)
        waitUntil(message = "tocando") { controller.isPlaying }
        val am = targetContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build()
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, am.requestAudioFocus(request))
        try {
            waitUntil(message = "pausou ao perder o foco") { !controller.isPlaying }
        } finally {
            am.abandonAudioFocusRequest(request)
        }
    }

    @Test
    fun systemMediaButtons_controlThePlayer() {
        load(long1)
        waitUntil(message = "tocando") { controller.isPlaying }
        val am = targetContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun press(code: Int) {
            val t = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        }
        press(KeyEvent.KEYCODE_MEDIA_PAUSE)
        waitUntil(message = "pausado pela tecla de mídia") { !controller.playWhenReady }
        press(KeyEvent.KEYCODE_MEDIA_PLAY)
        waitUntil(message = "retomado pela tecla de mídia") { controller.isPlaying }
    }

    @Test
    fun sleepTimerAtEndOfTrack_pausesAtBoundary() {
        load(short1, long1)
        waitUntil { controller.isPlaying }
        onMain { controller.sendCustomCommand(SessionCommand(PlaybackCommands.SET_SLEEP_TIMER, Bundle.EMPTY), Bundle().apply { putBoolean("endOfTrack", true) }) }
        waitUntil(message = "parou ao fim da faixa") { !controller.playWhenReady }
        Thread.sleep(500)
        assertFalse(onMain { controller.isPlaying })
    }

    @Test
    fun largeQueue_isHandled() {
        val items = List(3000) { item(short1, "q$it") }
        onMain {
            controller.setMediaItems(items, 2500, 0)
            controller.prepare()
        }
        waitUntil(15_000, "fila grande") { controller.mediaItemCount == 3000 && controller.currentMediaItemIndex == 2500 }
        onMain { controller.seekToDefaultPosition(10); controller.play() }
        waitUntil(message = "tocando item 10") { controller.currentMediaItemIndex == 10 && controller.isPlaying }
    }
}
