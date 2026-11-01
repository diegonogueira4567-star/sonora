package com.sonora.player.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sonora.player.SonoraApp
import com.sonora.player.domain.repository.LibraryRepository
import com.sonora.player.domain.repository.PlaylistRepository
import com.sonora.player.domain.usecase.LibraryGrouping
import com.sonora.player.domain.usecase.SearchEngine
import com.sonora.player.domain.usecase.SearchResults
import com.sonora.player.navigation.Routes
import com.sonora.player.playback.PlaybackController
import com.sonora.player.ui.components.Artwork
import com.sonora.player.ui.components.EmptyState
import com.sonora.player.ui.components.LocalTrackMenu
import com.sonora.player.ui.components.SectionHeader
import com.sonora.player.ui.components.TrackRow
import com.sonora.player.ui.components.songsLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(library: LibraryRepository, playlists: PlaylistRepository) : ViewModel() {
    val query = MutableStateFlow("")

    /** Rebuilt only when the library or the playlists change, not per keystroke. */
    private val index = combine(library.visibleTracks, playlists.playlists) { tracks, pls ->
        SearchEngine.index(tracks, LibraryGrouping.artists(tracks), LibraryGrouping.albums(tracks), pls)
    }.flowOn(Dispatchers.Default)

    val results: StateFlow<SearchResults> = combine(query.debounce { if (it.isBlank()) 0 else 150 }, index) { q, idx -> q to idx }
        // A newer query cancels the computation of the previous one.
        .mapLatest { (q, idx) ->
            if (q.isBlank()) SearchResults(q)
            else com.sonora.player.PerfLog.measure("search", { r: SearchResults -> "library=${idx.size} hits=${r.tracks.size}" }) { idx.search(q) }
        }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults(""))

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SonoraApp
                SearchViewModel(app.container.libraryRepository, app.container.playlistRepository)
            }
        }
    }
}

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    favorites: Set<Long>,
    playback: PlaybackController,
    currentMediaId: String?,
    navigate: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val focus = LocalFocusManager.current
    val menu = LocalTrackMenu.current
    Column(Modifier.fillMaxSize()) {
        Text("Buscar", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        TextField(
            value = query,
            onValueChange = { vm.query.value = it },
            singleLine = true,
            placeholder = { Text("Músicas, artistas, álbuns ou playlists") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Clear, contentDescription = "Limpar busca") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search_field"),
        )
        LazyColumn(Modifier.fillMaxSize().testTag("search_results"), contentPadding = contentPadding) {
            when {
                query.isBlank() -> item {
                    EmptyState(Icons.Rounded.Search, "Encontre o que quer ouvir", "A busca funciona sem internet, apenas nas músicas deste aparelho.")
                }
                results.isEmpty && results.query == query -> item {
                    EmptyState(Icons.Rounded.SearchOff, "Nada encontrado para \"${query.trim()}\"", "Verifique a ortografia ou tente outras palavras.")
                }
                else -> {
                    if (results.tracks.isNotEmpty()) {
                        item { SectionHeader("Músicas") }
                        items(results.tracks.take(30), key = { "t${it.id}" }) { t ->
                            TrackRow(t, onClick = { focus.clearFocus(); playback.playTracks(results.tracks, results.tracks.indexOf(t)) }, onMore = { menu.open(t) },
                                isCurrent = currentMediaId == t.id.toString(), isFavorite = t.id in favorites)
                        }
                    }
                    if (results.artists.isNotEmpty()) {
                        item { SectionHeader("Artistas") }
                        items(results.artists.take(10), key = { "a${it.key}" }) { a ->
                            ResultRow(a.coverTrackUri, a.name, "Artista • ${songsLabel(a.trackCount)}", circle = true) { navigate(Routes.artist(a.key)) }
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item { SectionHeader("Álbuns") }
                        items(results.albums.take(10), key = { "b${it.key}" }) { a ->
                            ResultRow(a.coverTrackUri, a.title, "Álbum • ${a.artist}") { navigate(Routes.album(a.key)) }
                        }
                    }
                    if (results.playlists.isNotEmpty()) {
                        item { SectionHeader("Playlists") }
                        items(results.playlists, key = { "p${it.id}" }) { p ->
                            ResultRow(p.coverTrackUri, p.name, "Playlist • ${songsLabel(p.trackCount)}") { navigate(Routes.playlist(p.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(artworkUri: String?, title: String, subtitle: String, circle: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artworkUri, Modifier.size(48.dp), shape = if (circle) CircleShape else RoundedCornerShape(6.dp), seed = title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
