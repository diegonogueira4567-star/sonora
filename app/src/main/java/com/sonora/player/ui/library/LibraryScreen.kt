package com.sonora.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.domain.model.ScanState
import com.sonora.player.domain.model.SortField
import com.sonora.player.domain.model.SortOrder
import com.sonora.player.navigation.Routes
import com.sonora.player.playback.PlaybackController
import com.sonora.player.ui.components.Artwork
import com.sonora.player.ui.components.EmptyState
import com.sonora.player.ui.components.LocalTrackMenu
import com.sonora.player.ui.components.MediaCard
import com.sonora.player.ui.components.PlayShuffleButtons
import com.sonora.player.ui.components.TextInputDialog
import com.sonora.player.ui.components.TrackRow
import com.sonora.player.ui.components.formatLongDuration
import com.sonora.player.ui.components.songsLabel
import com.sonora.player.ui.home.ScanBanner

enum class LibraryTab(val label: String) { PLAYLISTS("Playlists"), SONGS("Músicas"), ALBUMS("Álbuns"), ARTISTS("Artistas"), GENRES("Gêneros"), FOLDERS("Pastas") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    playback: PlaybackController,
    currentMediaId: String?,
    navigate: (String) -> Unit,
    onPickFolder: () -> Unit,
    contentPadding: PaddingValues,
) {
    var tab by rememberSaveable { mutableStateOf(LibraryTab.PLAYLISTS) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val scan by vm.scanState.collectAsState()
    val sort by vm.sortOrder.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sua biblioteca", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (tab == LibraryTab.SONGS) SortMenu(sort, vm::setSort)
            IconButton(onClick = { vm.rescan(userInitiated = true) }) { Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar biblioteca") }
            IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, contentDescription = "Criar playlist") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(LibraryTab.entries.toList()) { t ->
                FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }, modifier = Modifier.testTag("tab_${t.name}"))
            }
        }
        if (scan is ScanState.Scanning) ScanBanner((scan as ScanState.Scanning).message, (scan as ScanState.Scanning).found)
        PullToRefreshBox(
            isRefreshing = scan is ScanState.Scanning,
            onRefresh = { vm.rescan(userInitiated = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (tab) {
                LibraryTab.PLAYLISTS -> PlaylistsTab(vm, navigate, onCreate = { creating = true }, contentPadding)
                LibraryTab.SONGS -> SongsTab(vm, playback, currentMediaId, onPickFolder, contentPadding)
                LibraryTab.ALBUMS -> AlbumsTab(vm, navigate, contentPadding)
                LibraryTab.ARTISTS -> ArtistsTab(vm, navigate, contentPadding)
                LibraryTab.GENRES -> GenresTab(vm, navigate, contentPadding)
                LibraryTab.FOLDERS -> FoldersTab(vm, navigate, onPickFolder, contentPadding)
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Nova playlist",
            initial = "",
            confirmLabel = "Criar",
            onConfirm = { name -> vm.createPlaylist(name) { id -> navigate(Routes.playlist(id)) }; creating = false },
            onDismiss = { creating = false },
        )
    }
}

@Composable
fun SortMenu(current: SortOrder, onChange: (SortOrder) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val labels = mapOf(
                SortField.TITLE to "Nome", SortField.ARTIST to "Artista", SortField.ALBUM to "Álbum",
                SortField.DURATION to "Duração", SortField.DATE_ADDED to "Data de adição",
            )
            labels.forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(label + if (current.field == field) (if (current.ascending) "  ↑" else "  ↓") else "") },
                    onClick = {
                        onChange(if (current.field == field) current.copy(ascending = !current.ascending) else SortOrder(field, field != SortField.DATE_ADDED))
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RowEntry(icon: ImageVector?, artworkUri: String?, title: String, subtitle: String, onClick: () -> Unit, circle: Boolean = false, seed: String = title) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(56.dp).then(Modifier),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = if (circle) CircleShape else RoundedCornerShape(6.dp),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        } else {
            Artwork(artworkUri, Modifier.size(56.dp), shape = if (circle) CircleShape else RoundedCornerShape(6.dp), seed = seed)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaylistsTab(vm: LibraryViewModel, navigate: (String) -> Unit, onCreate: () -> Unit, contentPadding: PaddingValues) {
    val playlists by vm.playlistsList.collectAsState()
    val favorites by vm.favoriteTracks.collectAsState()
    val history by vm.history.collectAsState()
    LazyColumn(Modifier.fillMaxSize().testTag("playlists_list"), contentPadding = contentPadding) {
        item { RowEntry(Icons.Rounded.Add, null, "Criar playlist", "Monte sua seleção", onCreate) }
        item { RowEntry(Icons.Rounded.Favorite, null, "Músicas favoritas", songsLabel(favorites.size), { navigate(Routes.FAVORITES) }) }
        item { RowEntry(Icons.Rounded.History, null, "Histórico", "${history.size} reproduções recentes", { navigate(Routes.HISTORY) }) }
        item { RowEntry(Icons.Rounded.BarChart, null, "Estatísticas", "Suas músicas mais ouvidas", { navigate(Routes.STATS) }) }
        items(playlists, key = { it.id }) { p ->
            RowEntry(null, p.coverTrackUri, p.name, "Playlist • ${songsLabel(p.trackCount)}" + if (p.durationMs > 0) " • ${formatLongDuration(p.durationMs)}" else "",
                { navigate(Routes.playlist(p.id)) }, seed = p.name)
        }
    }
}

@Composable
private fun SongsTab(vm: LibraryViewModel, playback: PlaybackController, currentMediaId: String?, onPickFolder: () -> Unit, contentPadding: PaddingValues) {
    val tracks by vm.sortedTracks.collectAsState()
    val all by vm.tracks.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val menu = LocalTrackMenu.current
    if (all != null && tracks.isEmpty()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                EmptyState(Icons.Rounded.LibraryMusic, "Nenhuma música", "Sua biblioteca está vazia. Atualize ou escolha uma pasta com músicas.",
                    primaryAction = "Escolher uma pasta" to onPickFolder, secondaryAction = "Atualizar" to { vm.rescan(true) })
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("songs_list"), contentPadding = contentPadding) {
        item {
            Text(songsLabel(tracks.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        item { PlayShuffleButtons(onPlay = { playback.playTracks(tracks, 0, shuffle = false) }, onShuffle = { playback.playTracks(tracks, shuffle = true) }) }
        items(tracks.size, key = { tracks[it].id }) { i ->
            val t = tracks[i]
            TrackRow(t, onClick = { playback.playTracks(tracks, i) }, onMore = { menu.open(t) },
                isCurrent = currentMediaId == t.id.toString(), isFavorite = t.id in favorites)
        }
    }
}

@Composable
private fun AlbumsTab(vm: LibraryViewModel, navigate: (String) -> Unit, contentPadding: PaddingValues) {
    val albums by vm.albums.collectAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = contentPadding.calculateBottomPadding()),
        modifier = Modifier.fillMaxSize().testTag("albums_grid"),
    ) {
        items(albums, key = { it.key }) { a ->
            MediaCard(a.title, "${a.artist} • ${songsLabel(a.trackCount)}", a.coverTrackUri, onClick = { navigate(Routes.album(a.key)) }, seed = a.title)
        }
    }
}

@Composable
private fun ArtistsTab(vm: LibraryViewModel, navigate: (String) -> Unit, contentPadding: PaddingValues) {
    val artists by vm.artists.collectAsState()
    LazyColumn(Modifier.fillMaxSize().testTag("artists_list"), contentPadding = contentPadding) {
        items(artists, key = { it.key }) { a ->
            RowEntry(null, a.coverTrackUri, a.name, "${songsLabel(a.trackCount)} • ${a.albumCount} álbum(ns)", { navigate(Routes.artist(a.key)) }, circle = true, seed = a.name)
        }
    }
}

@Composable
private fun GenresTab(vm: LibraryViewModel, navigate: (String) -> Unit, contentPadding: PaddingValues) {
    val genres by vm.genres.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (genres.size == 1 && genres.first().key.isEmpty()) {
            item {
                Text(
                    "Seus arquivos não informam gênero nos metadados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(genres, key = { it.key }) { g ->
            RowEntry(Icons.Rounded.Category, null, g.name, songsLabel(g.trackCount), { navigate(Routes.genre(g.key)) })
        }
    }
}

@Composable
private fun FoldersTab(vm: LibraryViewModel, navigate: (String) -> Unit, onPickFolder: () -> Unit, contentPadding: PaddingValues) {
    val groups by vm.folderGroups.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { RowEntry(Icons.Rounded.CreateNewFolder, null, "Gerenciar pastas", "Adicionar ou remover pastas monitoradas", { navigate(Routes.FOLDERS) }) }
        items(groups, key = { it.key }) { f ->
            RowEntry(Icons.Rounded.Folder, null, f.name, songsLabel(f.trackCount), { navigate(Routes.folder(f.key)) })
        }
    }
}
