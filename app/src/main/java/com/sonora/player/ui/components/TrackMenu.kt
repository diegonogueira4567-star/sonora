package com.sonora.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.sonora.player.data.media.AudioMetadata
import com.sonora.player.domain.model.Track
import com.sonora.player.navigation.Routes
import com.sonora.player.playback.PlaybackController
import com.sonora.player.ui.LocalAppContainer
import com.sonora.player.ui.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a track menu was opened from, enabling context-specific actions. */
data class PlaylistContext(val playlistId: Long, val entryId: Long, val position: Int, val size: Int)

/** Root-level state for the per-track options sheet and its dialogs. */
class TrackMenuState {
    var track by mutableStateOf<Track?>(null)
        private set
    var playlistContext by mutableStateOf<PlaylistContext?>(null)
        private set
    var addToPlaylist by mutableStateOf<List<Track>?>(null)
    var details by mutableStateOf<Track?>(null)

    fun open(track: Track, context: PlaylistContext? = null) {
        this.track = track
        playlistContext = context
    }

    fun close() {
        track = null
        playlistContext = null
    }

    fun openAddToPlaylist(tracks: List<Track>) {
        addToPlaylist = tracks.filter { it.id > 0 }
    }
}

val LocalTrackMenu = staticCompositionLocalOf<TrackMenuState> { error("TrackMenuState not provided") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuHost(
    state: TrackMenuState,
    library: LibraryViewModel,
    playback: PlaybackController,
    navigate: (String) -> Unit,
) {
    val favorites by library.favoriteIds.collectAsState()
    state.track?.let { track ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { state.close() }, sheetState = sheet) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.uri, Modifier.size(56.dp), seed = track.album ?: track.title)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.displayArtist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                fun act(block: () -> Unit) {
                    block()
                    state.close()
                }
                val isFav = track.id in favorites
                if (track.available) {
                    MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Tocar a seguir") { act { playback.playNext(listOf(track)) } }
                    MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Adicionar ao final da fila") { act { playback.addToQueue(listOf(track)) } }
                }
                MenuItem(if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isFav) "Remover das favoritas" else "Adicionar às favoritas") {
                    act { library.toggleFavorite(track.id) }
                }
                MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar a uma playlist") { act { state.openAddToPlaylist(listOf(track)) } }
                MenuItem(Icons.Rounded.Album, "Ir para o álbum") { act { navigate(Routes.album(track.albumKey)) } }
                MenuItem(Icons.Rounded.Person, "Ir para o artista") { act { navigate(Routes.artist(track.artistKey)) } }
                state.playlistContext?.let { ctx ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    if (ctx.position > 0) MenuItem(Icons.Rounded.ArrowUpward, "Mover para cima") {
                        act { library.movePlaylistEntry(ctx.playlistId, ctx.position, ctx.position - 1) }
                    }
                    if (ctx.position < ctx.size - 1) MenuItem(Icons.Rounded.ArrowDownward, "Mover para baixo") {
                        act { library.movePlaylistEntry(ctx.playlistId, ctx.position, ctx.position + 1) }
                    }
                    MenuItem(Icons.Rounded.RemoveCircleOutline, "Remover desta playlist") { act { library.removeFromPlaylist(ctx.playlistId, ctx.entryId) } }
                }
                MenuItem(Icons.Rounded.Info, "Detalhes do arquivo") { act { state.details = track } }
            }
        }
    }

    state.addToPlaylist?.let { tracks -> AddToPlaylistDialog(tracks, library, onDismiss = { state.addToPlaylist = null }) }
    state.details?.let { TrackDetailsDialog(it, onDismiss = { state.details = null }) }
}

@Composable
fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, library: LibraryViewModel, onDismiss: () -> Unit) {
    val playlists by library.playlistsList.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog(
            title = "Nova playlist",
            initial = "",
            confirmLabel = "Criar",
            onConfirm = { name ->
                library.createPlaylist(name, tracks.map { it.id })
                onDismiss()
            },
            onDismiss = { creating = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "Adicionar à playlist" else "Adicionar ${tracks.size} faixas") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Nova playlist", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            library.addToPlaylist(p, tracks.map { it.id })
                            onDismiss()
                        }.heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(p.coverTrackUri, Modifier.size(40.dp), seed = p.name)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(songsLabel(p.trackCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun TrackDetailsDialog(track: Track, onDismiss: () -> Unit) {
    val reader = LocalAppContainer.current.metadataReader
    var meta by remember { mutableStateOf<AudioMetadata?>(null) }
    LaunchedEffect(track.uri) {
        meta = withContext(Dispatchers.IO) { if (track.available) reader.read(Uri.parse(track.uri)) else null }
    }
    val m = meta
    val rows = buildList {
        add("Título" to track.title)
        add("Artista" to track.displayArtist)
        add("Álbum" to track.displayAlbum)
        track.genre?.let { add("Gênero" to it) }
        track.year?.let { add("Ano" to it.toString()) }
        add("Duração" to formatDuration(track.durationMs))
        add("Formato" to (m?.mimeType ?: track.mimeType ?: "—"))
        (m?.bitrate ?: track.bitrate)?.let { add("Taxa de bits" to "${it / 1000} kbps") }
        m?.sampleRate?.let { add("Taxa de amostragem" to "$it Hz") }
        m?.bitsPerSample?.let { add("Profundidade" to "$it bits") }
        add("Tamanho" to formatSize(track.sizeBytes))
        add("Arquivo" to listOfNotNull(track.relativePath, track.displayName).joinToString("").ifEmpty { "—" })
        add("Origem" to if (track.folderUri != null) "Pasta selecionada" else "Biblioteca de mídia do Android")
        if (!track.available) add("Estado" to "Indisponível (arquivo removido, movido ou sem permissão)")
        if (m?.readable == false) add("Aviso" to "Não foi possível ler os metadados; o arquivo pode estar corrompido.")
    }
    InfoDialog(title = "Detalhes", rows = rows, onDismiss = onDismiss)
}
