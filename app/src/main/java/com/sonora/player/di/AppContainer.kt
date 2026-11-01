package com.sonora.player.di

import android.content.Context
import com.sonora.player.data.local.database.SonoraDatabase
import com.sonora.player.data.local.preferences.SettingsStore
import com.sonora.player.data.media.ArtworkLoader
import com.sonora.player.data.media.FolderAccessManager
import com.sonora.player.data.media.MediaStoreScanner
import com.sonora.player.data.media.MetadataReader
import com.sonora.player.data.repository.LibraryRepositoryImpl
import com.sonora.player.data.repository.PlaylistRepositoryImpl
import com.sonora.player.domain.repository.LibraryRepository
import com.sonora.player.domain.repository.PlaylistRepository
import com.sonora.player.playback.AudioEffectsManager
import com.sonora.player.playback.LyricsRepository
import com.sonora.player.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (one instance per process, owned by [com.sonora.player.SonoraApp]). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: SonoraDatabase by lazy { SonoraDatabase.build(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val metadataReader by lazy { MetadataReader(appContext) }
    val folderAccess by lazy { FolderAccessManager(appContext) }
    val artworkLoader by lazy { ArtworkLoader(appContext, metadataReader) }
    private val mediaStoreScanner by lazy { MediaStoreScanner(appContext) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepositoryImpl(appContext, database, settingsStore, folderAccess, mediaStoreScanner, metadataReader, artworkLoader, appScope)
    }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepositoryImpl(database, libraryRepository) }
    val lyricsRepository by lazy { LyricsRepository() }
    val audioEffects by lazy { AudioEffectsManager(settingsStore, appScope) }
    val playbackController by lazy { PlaybackController(appContext, appScope) }
}
