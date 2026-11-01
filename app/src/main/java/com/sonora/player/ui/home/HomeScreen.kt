package com.sonora.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.domain.model.ScanState
import com.sonora.player.domain.model.Track
import com.sonora.player.navigation.Routes
import com.sonora.player.playback.PlaybackController
import com.sonora.player.ui.components.Artwork
import com.sonora.player.ui.components.EmptyState
import com.sonora.player.ui.components.LoadingBox
import com.sonora.player.ui.components.MediaCard
import com.sonora.player.ui.components.SectionHeader
import com.sonora.player.ui.components.songsLabel
import com.sonora.player.ui.library.LibraryViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    playback: PlaybackController,
    navigate: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onPickFolder: () -> Unit,
    contentPadding: PaddingValues,
) {
    val tracks by vm.tracks.collectAsState()
    val albums by vm.albums.collectAsState()
    val artists by vm.artists.collectAsState()
    val playlists by vm.playlistsList.collectAsState()
    val history by vm.history.collectAsState()
    val recent by vm.recentlyAdded.collectAsState()
    val top by vm.topTracks.collectAsState()
    val scan by vm.scanState.collectAsState()
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("home_list"), contentPadding = contentPadding) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, contentDescription = "Configurações") }
            }
        }
        if (scan is ScanState.Scanning) {
            item { ScanBanner((scan as ScanState.Scanning).message, (scan as ScanState.Scanning).found) }
        }
        val list = tracks
        when {
            list == null -> item { LoadingBox(Modifier.height(320.dp)) }
            list.isEmpty() -> item {
                if (!vm.hasAudioPermission()) {
                    EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "Vamos encontrar suas músicas",
                        message = "Permita o acesso aos arquivos de áudio do aparelho ou escolha uma pasta. Nada sai do seu celular.",
                        primaryAction = "Permitir acesso às músicas" to onRequestPermission,
                        secondaryAction = "Escolher uma pasta" to onPickFolder,
                    )
                } else if (scan !is ScanState.Scanning) {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = "Nenhuma música encontrada",
                        message = "Não encontramos arquivos de áudio no armazenamento. Copie músicas para o aparelho ou escolha uma pasta específica.",
                        primaryAction = "Escolher uma pasta" to onPickFolder,
                        secondaryAction = "Procurar novamente" to { vm.rescan(userInitiated = true) },
                    )
                }
            }
            else -> {
                item {
                    QuickGrid(
                        listOf(
                            Quick("Tocar tudo aleatório", Icons.Rounded.Shuffle, null) { playback.playTracks(list, shuffle = true) },
                            Quick("Favoritas", Icons.Rounded.Favorite, null) { navigate(Routes.FAVORITES) },
                            Quick("Adicionadas recentemente", Icons.Rounded.Schedule, null) { navigate(Routes.RECENT) },
                            Quick("Histórico", Icons.Rounded.History, null) { navigate(Routes.HISTORY) },
                        ) + playlists.take(4).map { p -> Quick(p.name, null, p.coverTrackUri ?: "playlist:${p.name}") { navigate(Routes.playlist(p.id)) } },
                    )
                }
                val recentPlayed = history.map { it.track }.distinctBy { it.id }.take(15)
                if (recentPlayed.isNotEmpty()) {
                    item { SectionHeader("Tocadas recentemente", action = { TextButton(onClick = { navigate(Routes.HISTORY) }) { Text("Ver tudo") } }) }
                    item { TrackCarousel(recentPlayed, onPlay = { i -> playback.playTracks(recentPlayed, i) }) }
                }
                item { SectionHeader("Adicionadas recentemente", action = { TextButton(onClick = { navigate(Routes.RECENT) }) { Text("Ver tudo") } }) }
                item { TrackCarousel(recent.take(15), onPlay = { i -> playback.playTracks(recent, i) }) }
                if (albums.isNotEmpty()) {
                    item { SectionHeader("Seus álbuns") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                            items(albums.take(20), key = { it.key }) { a ->
                                MediaCard(a.title, a.artist, a.coverTrackUri, onClick = { navigate(Routes.album(a.key)) }, modifier = Modifier.width(150.dp), seed = a.title)
                            }
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { SectionHeader("Artistas") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                            items(artists.take(20), key = { it.key }) { a ->
                                MediaCard(a.name, songsLabel(a.trackCount), a.coverTrackUri, onClick = { navigate(Routes.artist(a.key)) },
                                    modifier = Modifier.width(130.dp), circle = true, seed = a.name)
                            }
                        }
                    }
                }
                if (top.isNotEmpty()) {
                    item { SectionHeader("Mais tocadas", action = { TextButton(onClick = { navigate(Routes.STATS) }) { Text("Estatísticas") } }) }
                    item { TrackCarousel(top.take(15).map { it.track }, onPlay = { i -> playback.playTracks(top.map { it.track }, i) }) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun ScanBanner(message: String, found: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(if (found > 0) "$message ($found)" else message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

private data class Quick(val title: String, val icon: ImageVector?, val artworkUri: String?, val onClick: () -> Unit)

@Composable
private fun QuickGrid(items: List<Quick>) {
    // Column count follows the real width and the user's font scale: one column on compact phones
    // with large text (no words split mid-way), two on regular phones, up to four on tablets.
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minTile = 160.dp * maxOf(1f, fontScale)
        val columns = ((maxWidth - 24.dp) / minTile).toInt().coerceIn(1, 4)
        QuickGridRows(items, columns)
    }
}

@Composable
private fun QuickGridRows(items: List<Quick>, columns: Int) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { q ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = q.onClick),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (q.icon != null) {
                                Box(
                                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(q.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                            } else {
                                Artwork(q.artworkUri, Modifier.size(56.dp), shape = RoundedCornerShape(0.dp), seed = q.title)
                            }
                            Text(
                                q.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun TrackCarousel(tracks: List<Track>, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(tracks.size, key = { tracks[it].id }) { i ->
            val t = tracks[i]
            MediaCard(t.title, t.displayArtist, t.uri, onClick = { onPlay(i) }, modifier = Modifier.width(150.dp), seed = t.album ?: t.title)
        }
    }
}
