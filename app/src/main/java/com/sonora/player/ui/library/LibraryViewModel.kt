package com.sonora.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sonora.player.data.local.preferences.AppSettings
import com.sonora.player.data.local.preferences.SettingsStore
import com.sonora.player.domain.model.Album
import com.sonora.player.domain.model.Artist
import com.sonora.player.domain.model.FolderGroup
import com.sonora.player.domain.model.Genre
import com.sonora.player.domain.model.HistoryItem
import com.sonora.player.domain.model.LibraryFolder
import com.sonora.player.domain.model.Playlist
import com.sonora.player.domain.model.ScanState
import com.sonora.player.domain.model.SortOrder
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.model.TrackStat
import com.sonora.player.domain.repository.LibraryRepository
import com.sonora.player.domain.repository.PlaylistRepository
import com.sonora.player.domain.usecase.LibraryGrouping
import com.sonora.player.domain.usecase.TrackSorter
import com.sonora.player.SonoraApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-scoped state of the local library, shared by Home, Library and detail screens. */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settings: StateFlow<AppSettings?> = settingsStore.settings.state(null)
    val scanState: StateFlow<ScanState> = library.scanState

    /** Null while the first database read is pending (shows a loading state instead of "empty"). */
    val tracks: StateFlow<List<Track>?> = library.visibleTracks.map<List<Track>, List<Track>?> { it }.state(null)

    val sortOrder: StateFlow<SortOrder> = settingsStore.settings.map { it.defaultSort }.state(SortOrder())

    val sortedTracks: StateFlow<List<Track>> =
        combine(library.visibleTracks, sortOrder) { t, order -> TrackSorter.sort(t, order) }.flowOn(Dispatchers.Default).state(emptyList())

    val albums: StateFlow<List<Album>> = library.visibleTracks.map { LibraryGrouping.albums(it) }.flowOn(Dispatchers.Default).state(emptyList())
    val artists: StateFlow<List<Artist>> = library.visibleTracks.map { LibraryGrouping.artists(it) }.flowOn(Dispatchers.Default).state(emptyList())
    val genres: StateFlow<List<Genre>> = library.visibleTracks.map { LibraryGrouping.genres(it) }.flowOn(Dispatchers.Default).state(emptyList())
    val folderGroups: StateFlow<List<FolderGroup>> = library.visibleTracks.map { LibraryGrouping.folders(it) }.flowOn(Dispatchers.Default).state(emptyList())
    val recentlyAdded: StateFlow<List<Track>> =
        library.visibleTracks.map { list -> list.sortedByDescending { it.dateAddedSec }.take(60) }.flowOn(Dispatchers.Default).state(emptyList())

    val playlistsList: StateFlow<List<Playlist>> = playlists.playlists.state(emptyList())
    val favoriteIds: StateFlow<Set<Long>> = library.favoriteIds.state(emptySet())
    val favoriteTracks: StateFlow<List<Track>> = library.favoriteTracks.state(emptyList())
    val history: StateFlow<List<HistoryItem>> = library.history.state(emptyList())
    val topTracks: StateFlow<List<TrackStat>> = library.topTracks.state(emptyList())
    val totalPlays: StateFlow<Int> = library.totalPlays.state(0)
    val totalPlayedMs: StateFlow<Long> = library.totalPlayedMs.state(0L)
    val folders: StateFlow<List<LibraryFolder>> = library.folders.state(emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun hasAudioPermission(): Boolean = library.hasAudioPermission()

    private var startupScanDone = false

    /** Runs once per ViewModel (survives rotation/theme changes), and is cheap when nothing changed. */
    fun ensureStartupScan() {
        if (startupScanDone) return
        startupScanDone = true
        rescan()
    }

    fun rescan(userInitiated: Boolean = false, force: Boolean = userInitiated) {
        viewModelScope.launch {
            val result = library.rescan(force)
            if (userInitiated) {
                when (result) {
                    is ScanState.Done -> _messages.tryEmit(
                        buildString {
                            append("Biblioteca atualizada: ${result.total} músicas")
                            if (result.added > 0) append(", ${result.added} novas")
                            if (result.removed > 0) append(", ${result.removed} indisponíveis")
                        },
                    )
                    is ScanState.Failed -> _messages.tryEmit(result.message)
                    else -> Unit
                }
            }
            (result as? ScanState.Done)?.warnings?.firstOrNull()?.let { _messages.tryEmit(it) }
        }
    }

    fun setSort(order: SortOrder) = viewModelScope.launch { settingsStore.setDefaultSort(order) }

    fun toggleFavorite(trackId: Long) = viewModelScope.launch {
        val now = trackId !in favoriteIds.value
        library.setFavorite(trackId, now)
        _messages.tryEmit(if (now) "Adicionada às favoritas" else "Removida das favoritas")
    }

    fun createPlaylist(name: String, trackIds: List<Long> = emptyList(), onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        runCatching { playlists.create(name, trackIds) }
            .onSuccess {
                _messages.tryEmit(if (trackIds.isEmpty()) "Playlist \"$name\" criada" else "Playlist \"$name\" criada com ${trackIds.size} faixa(s)")
                onCreated(it)
            }
            .onFailure { _messages.tryEmit(it.message ?: "Não foi possível criar a playlist.") }
    }

    fun addToPlaylist(playlist: Playlist, trackIds: List<Long>) = viewModelScope.launch {
        runCatching { playlists.addTracks(playlist.id, trackIds) }
            .onSuccess { added ->
                _messages.tryEmit(
                    when {
                        added == 0 -> "Já está em \"${playlist.name}\""
                        added == 1 -> "Adicionada a \"${playlist.name}\""
                        else -> "$added faixas adicionadas a \"${playlist.name}\""
                    },
                )
            }
            .onFailure { _messages.tryEmit("Não foi possível salvar na playlist.") }
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch {
        runCatching { playlists.rename(id, name) }.onFailure { _messages.tryEmit(it.message ?: "Nome inválido.") }
    }

    fun deletePlaylist(id: Long, name: String) = viewModelScope.launch {
        playlists.delete(id)
        _messages.tryEmit("Playlist \"$name\" excluída")
    }

    fun removeFromPlaylist(playlistId: Long, entryId: Long) = viewModelScope.launch {
        playlists.removeEntry(playlistId, entryId)
        _messages.tryEmit("Removida da playlist")
    }

    fun movePlaylistEntry(playlistId: Long, from: Int, to: Int) = viewModelScope.launch { playlists.moveEntry(playlistId, from, to) }

    fun observePlaylist(id: Long) = playlists.observePlaylist(id)
    fun observePlaylistEntries(id: Long) = playlists.observeEntries(id)

    fun addFolder(treeUri: String) = viewModelScope.launch {
        library.addFolder(treeUri)
            .onSuccess { _messages.tryEmit("Pasta \"$it\" adicionada. Lendo músicas…") }
            .onFailure { _messages.tryEmit(it.message ?: "Não foi possível adicionar a pasta.") }
    }

    fun removeFolder(treeUri: String) = viewModelScope.launch {
        library.removeFolder(treeUri)
        _messages.tryEmit("Pasta removida da biblioteca. Os arquivos não foram alterados.")
    }

    fun clearHistory() = viewModelScope.launch {
        library.clearHistory()
        _messages.tryEmit("Histórico limpo")
    }

    fun clearStats() = viewModelScope.launch {
        library.clearStats()
        _messages.tryEmit("Estatísticas zeradas")
    }

    suspend fun exportBackup(): String = playlists.exportBackup()

    fun importBackup(json: String) = viewModelScope.launch {
        runCatching { playlists.importBackup(json) }
            .onSuccess {
                _messages.tryEmit(
                    "Restaurado: ${it.playlists} playlist(s), ${it.tracksMatched} faixa(s) encontradas" +
                        if (it.tracksMissing > 0) ", ${it.tracksMissing} não encontradas neste aparelho" else "",
                )
            }
            .onFailure { _messages.tryEmit(it.message ?: "Arquivo de backup inválido.") }
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SonoraApp
                LibraryViewModel(app.container.libraryRepository, app.container.playlistRepository, app.container.settingsStore)
            }
        }
    }
}
