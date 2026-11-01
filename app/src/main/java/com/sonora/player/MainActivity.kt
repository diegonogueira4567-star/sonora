package com.sonora.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.sonora.player.navigation.SonoraAppUi
import com.sonora.player.ui.LocalAppContainer
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as SonoraApp).container }
    private val openPlayerRequests = MutableStateFlow(0)
    private val externalUris = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        PerfLog.mark("activity.onCreate")
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                SonoraAppUi(openPlayerRequests, externalUris, onExternalHandled = { externalUris.value = null })
            }
        }
    }

    private var connectPending = false
    private var controllerConnected = false

    /**
     * Binding the controller creates the playback service (ExoPlayer + session) on the main thread,
     * so it is deferred until after the next frame: the UI appears first.
     */
    override fun onStart() {
        super.onStart()
        connectPending = true
        Choreographer.getInstance().postFrameCallback {
            window.decorView.post {
                if (connectPending) {
                    connectPending = false
                    controllerConnected = true
                    container.playbackController.connect()
                }
            }
        }
    }

    override fun onStop() {
        connectPending = false
        if (controllerConnected) {
            controllerConnected = false
            container.playbackController.release()
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerRequests.value++
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let { externalUris.value = it }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.sonora.player.OPEN_PLAYER"
    }
}
