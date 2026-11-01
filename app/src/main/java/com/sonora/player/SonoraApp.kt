package com.sonora.player

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.sonora.player.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SonoraApp : Application() {
    lateinit var container: AppContainer
        private set

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val rescanRunnable = Runnable { container.libraryRepository.requestRescan() }

    override fun onCreate() {
        super.onCreate()
        PerfLog.mark("app.onCreate")
        container = AppContainer(this)
        // Warm up storage off the main thread while the activity and Compose are initialising, so
        // the first screen does not wait for DataStore and the database to open one after another.
        container.appScope.launch(Dispatchers.IO) { runCatching { container.settingsStore.settings.first() } }
        container.appScope.launch(Dispatchers.IO) { runCatching { container.database.openHelper.writableDatabase } }
        // Re-index automatically when Android's media index changes (debounced).
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    if (!container.libraryRepository.hasAudioPermission()) return
                    handler.removeCallbacks(rescanRunnable)
                    handler.postDelayed(rescanRunnable, 3_000)
                }
            },
        )
    }
}
