package com.sonora.player.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.domain.model.PlaylistEntry
import com.sonora.player.domain.model.TextNormalizer
import com.sonora.player.domain.usecase.moved
import com.sonora.player.playback.PlaybackController
import com.sonora.player.ui.components.Artwork
import com.sonora.player.ui.components.ConfirmDialog
import com.sonora.player.ui.components.DragHandleIcon
import com.sonora.player.ui.components.EmptyState
import com.sonora.player.ui.components.LocalTrackMenu
import com.sonora.player.ui.components.PlayShuffleButtons
import com.sonora.player.ui.components.PlaylistContext
import com.sonora.player.ui.components.TextInputDialog
import com.sonora.player.ui.components.TrackRow
import com.sonora.player.ui.components.formatLongDuration
import com.sonora.player.ui.components.rememberReorderableListState
import com.sonora.player.ui.components.reorderHandle
import com.sonora.player.ui.components.reorderableItem
import com.sonora.player.ui.components.songsLabel
import com.sonora.player.ui.library.CollectionHeader
import com.sonora.player.ui.library.LibraryViewModel

private const val HEADER_ITEMS = 2

@Composable
fun PlaylistScreen(
    playlistId: Long,
    vm: LibraryViewModel,
    playback: PlaybackController,
    currentMediaId: String?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val playlist by remember(playlistId) { vm.observePlaylist(playlistId) }.collectAsState(initial = null)
    val entriesFlow = remember(playlistId) { vm.observePlaylistEntries(playlistId) }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val favorites by vm.favoriteIds.collectAsState()
    val menu = LocalTrackMenu.current
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf<List<PlaylistEntry>>(emptyList()) }
    LaunchedEffect(entries) { local = entries }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableListState(
        listState,
        canMoveTo = { it >= HEADER_ITEMS },
        onMove = { from, to -> local = local.moved(from - HEADER_ITEMS, to - HEADER_ITEMS) },
        onDragEnd = { from, to -> vm.movePlaylistEntry(playlistId, from - HEADER_ITEMS, to - HEADER_ITEMS) },
    )
    val p = playlist
    val playable = local.mapNotNull { it.track }.filter { it.available }
    val unavailable = local.count { it.track?.available != true }

    LazyColumn(Modifier.fillMaxSize().testTag("playlist_list"), state = listState, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item(key = "header") {
            CollectionHeader(
                title = p?.name ?: "Playlist",
                subtitle = buildString {
                    append(songsLabel(local.size))
                    if ((p?.durationMs ?: 0) > 0) append(" • ${formatLongDuration(p!!.durationMs)}")
                    if (unavailable > 0) append(" • $unavailable indisponível(is)")
                },
                artworkUri = p?.coverTrackUri,
                onBack = onBack,
                icon = if (p?.coverTrackUri == null) Icons.AutoMirrored.Rounded.QueueMusic else null,
            ) {
                IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, contentDescription = "Adicionar músicas") }
                IconButton(onClick = { reorderMode = !reorderMode }) {
                    Icon(if (reorderMode) Icons.Rounded.Check else Icons.Rounded.SwapVert, contentDescription = if (reorderMode) "Concluir reordenação" else "Reordenar")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Opções da playlist") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Renomear") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { showMenu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Adicionar à fila") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                            onClick = { showMenu = false; playback.addToQueue(playable) })
                        DropdownMenuItem(text = { Text("Excluir playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { showMenu = false; deleting = true })
                    }
                }
            }
        }
        item(key = "buttons") {
            if (local.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Playlist vazia", "Adicione músicas pelo botão + ou pelo menu de qualquer faixa.",
                    primaryAction = "Adicionar músicas" to { adding = true })
            } else {
                PlayShuffleButtons(onPlay = { playback.playTracks(playable, 0, shuffle = false) }, onShuffle = { playback.playTracks(playable, shuffle = true) },
                    enabled = playable.isNotEmpty())
            }
        }
        itemsIndexed(local, key = { _, e -> e.entryId }) { i, entry ->
            val absolute = i + HEADER_ITEMS
            val track = entry.track
            if (track == null) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Faixa removida da biblioteca", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { vm.removeFromPlaylist(playlistId, entry.entryId) }) { Icon(Icons.Rounded.Delete, contentDescription = "Remover da playlist") }
                }
            } else {
                TrackRow(
                    track,
                    onClick = {
                        val index = playable.indexOfFirst { it.id == track.id }
                        if (index >= 0) playback.playTracks(playable, index)
                    },
                    onMore = { menu.open(track, PlaylistContext(playlistId, entry.entryId, i, local.size)) },
                    isCurrent = currentMediaId == track.id.toString(),
                    isFavorite = track.id in favorites,
                    modifier = Modifier.reorderableItem(reorder, absolute),
                    dragHandle = if (reorderMode) ({ DragHandleIcon(Modifier.reorderHandle(reorder, absolute, entry.entryId)) }) else null,
                )
            }
        }
    }

    if (renaming && p != null) {
        TextInputDialog("Renomear playlist", p.name, "Salvar", onConfirm = { vm.renamePlaylist(playlistId, it); renaming = false }, onDismiss = { renaming = false })
    }
    if (deleting && p != null) {
        ConfirmDialog(
            title = "Excluir \"${p.name}\"?",
            message = "A playlist será excluída. As músicas continuam no aparelho.",
            confirmLabel = "Excluir",
            onConfirm = { deleting = false; vm.deletePlaylist(playlistId, p.name); onBack() },
            onDismiss = { deleting = false },
        )
    }
    if (adding && p != null) {
        AddSongsSheet(vm, alreadyIn = local.map { it.trackId }.toSet(), onAdd = { vm.addToPlaylist(p, listOf(it)) }, onDismiss = { adding = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsSheet(vm: LibraryViewModel, alreadyIn: Set<Long>, onAdd: (Long) -> Unit, onDismiss: () -> Unit) {
    val tracks by vm.sortedTracks.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filtered = remember(tracks, query) {
        val q = TextNormalizer.normalize(query)
        if (q.isEmpty()) tracks else tracks.filter { t ->
            q.split(' ').all { term -> listOf(t.title, t.artist, t.album).any { TextNormalizer.normalize(it).contains(term) } }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.9f).navigationBarsPadding()) {
            Text("Adicionar músicas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, contentDescription = "Limpar busca") } },
                placeholder = { Text("Buscar na biblioteca") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(filtered, key = { it.id }) { t ->
                    val added = t.id in alreadyIn
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !added) { onAdd(t.id) }.heightIn(min = 60.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t.uri, Modifier.size(44.dp), seed = t.album ?: t.title)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(t.displayArtist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (added) Icon(Icons.Rounded.Check, contentDescription = "Já na playlist", tint = MaterialTheme.colorScheme.primary)
                        else OutlinedButton(onClick = { onAdd(t.id) }) { Text("Adicionar") }
                    }
                }
            }
        }
    }
}
