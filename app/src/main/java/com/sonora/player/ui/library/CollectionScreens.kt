package com.sonora.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.domain.model.TextNormalizer
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.usecase.LibraryGrouping
import com.sonora.player.domain.usecase.TrackSorter
import com.sonora.player.navigation.Routes
import com.sonora.player.playback.PlaybackController
import com.sonora.player.ui.components.Artwork
import com.sonora.player.ui.components.ConfirmDialog
import com.sonora.player.ui.components.EmptyState
import com.sonora.player.ui.components.LocalTrackMenu
import com.sonora.player.ui.components.MediaCard
import com.sonora.player.ui.components.PlayShuffleButtons
import com.sonora.player.ui.components.SectionHeader
import com.sonora.player.ui.components.TrackRow
import com.sonora.player.ui.components.formatDuration
import com.sonora.player.ui.components.formatLongDuration
import com.sonora.player.ui.components.songsLabel
import java.text.DateFormat
import java.util.Date

enum class CollectionKind { ALBUM, ARTIST, GENRE, FOLDER, FAVORITES, HISTORY, RECENT }

@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    artworkUri: String?,
    onBack: () -> Unit,
    icon: ImageVector? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.colorScheme.background)),
        ),
    ) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Box(
                    Modifier.size(180.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(72.dp)) }
            } else {
                Artwork(artworkUri, Modifier.size(200.dp), sizePx = 512, shape = RoundedCornerShape(8.dp), seed = title, iconSize = 64.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

@Composable
fun CollectionScreen(
    kind: CollectionKind,
    key: String,
    vm: LibraryViewModel,
    playback: PlaybackController,
    currentMediaId: String?,
    navigate: (String) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val all by vm.tracks.collectAsState()
    val favoritesList by vm.favoriteTracks.collectAsState()
    val history by vm.history.collectAsState()
    val recent by vm.recentlyAdded.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val albums by vm.albums.collectAsState()
    val menu = LocalTrackMenu.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val source = all.orEmpty()

    val tracks: List<Track> = remember(kind, key, source, favoritesList, history, recent) {
        when (kind) {
            CollectionKind.ALBUM -> TrackSorter.albumOrder(source.filter { it.albumKey == key })
            CollectionKind.ARTIST -> source.filter { it.artistKey == key }
                .sortedWith(compareBy<Track>({ it.album?.lowercase() }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() }))
            CollectionKind.GENRE -> source.filter { TextNormalizer.key(it.genre) == key }.sortedBy { it.title.lowercase() }
            CollectionKind.FOLDER -> source.filter { LibraryGrouping.folderKey(it) == key }.sortedBy { (it.displayName ?: it.title).lowercase() }
            CollectionKind.FAVORITES -> favoritesList
            CollectionKind.HISTORY -> history.map { it.track }
            CollectionKind.RECENT -> recent
        }
    }
    val first = tracks.firstOrNull()
    val title = when (kind) {
        CollectionKind.ALBUM -> first?.displayAlbum ?: "Álbum"
        CollectionKind.ARTIST -> first?.displayArtist ?: "Artista"
        CollectionKind.GENRE -> first?.genre ?: Track.UNKNOWN_GENRE
        CollectionKind.FOLDER -> first?.let { LibraryGrouping.folderName(it) } ?: "Pasta"
        CollectionKind.FAVORITES -> "Músicas favoritas"
        CollectionKind.HISTORY -> "Histórico"
        CollectionKind.RECENT -> "Adicionadas recentemente"
    }
    val subtitle = buildString {
        if (kind == CollectionKind.ALBUM && first != null) append("${first.albumArtist ?: first.displayArtist} • ")
        if (kind == CollectionKind.ALBUM) first?.year?.let { append("$it • ") }
        append(songsLabel(tracks.size))
        val total = tracks.sumOf { it.durationMs }
        if (total > 0 && kind != CollectionKind.HISTORY) append(" • ${formatLongDuration(total)}")
    }
    val icon = when (kind) {
        CollectionKind.FAVORITES -> Icons.Rounded.Favorite
        CollectionKind.HISTORY -> Icons.Rounded.History
        else -> null
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LazyColumn(Modifier.fillMaxSize().testTag("collection_list"), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item {
            CollectionHeader(title, subtitle, first?.uri, onBack, icon) {
                if (tracks.isNotEmpty() && kind != CollectionKind.HISTORY) {
                    IconButton(onClick = { menu.openAddToPlaylist(tracks) }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Adicionar todas a uma playlist") }
                }
                if (kind == CollectionKind.HISTORY && tracks.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpar histórico") }
                }
            }
        }
        if (tracks.isEmpty()) {
            item {
                EmptyState(
                    icon = icon ?: Icons.Rounded.MusicOff,
                    title = when (kind) {
                        CollectionKind.FAVORITES -> "Nenhuma favorita ainda"
                        CollectionKind.HISTORY -> "Histórico vazio"
                        else -> "Nada por aqui"
                    },
                    message = when (kind) {
                        CollectionKind.FAVORITES -> "Toque no coração de uma música para salvá-la aqui."
                        CollectionKind.HISTORY -> "Uma música entra no histórico depois de 30 segundos tocando (ou metade dela, se for curta)."
                        else -> "Estas músicas não estão disponíveis no momento."
                    },
                )
            }
        } else {
            item {
                val playable = if (kind == CollectionKind.HISTORY) tracks.distinctBy { it.id } else tracks
                PlayShuffleButtons(onPlay = { playback.playTracks(playable, 0, shuffle = false) }, onShuffle = { playback.playTracks(playable, shuffle = true) })
            }
            items(tracks.size, key = { i -> if (kind == CollectionKind.HISTORY) "h${history[i].id}" else "t${tracks[i].id}" }) { i ->
                val t = tracks[i]
                TrackRow(
                    t,
                    onClick = { playback.playTracks(if (kind == CollectionKind.HISTORY) listOf(t) + tracks.drop(i + 1).distinctBy { it.id } else tracks, if (kind == CollectionKind.HISTORY) 0 else i) },
                    onMore = { menu.open(t) },
                    isCurrent = currentMediaId == t.id.toString(),
                    isFavorite = t.id in favorites,
                    leadingIndex = if (kind == CollectionKind.ALBUM) (t.trackNumber ?: (i + 1)) else null,
                    showArtwork = kind != CollectionKind.ALBUM,
                    subtitle = when (kind) {
                        CollectionKind.HISTORY -> "${t.displayArtist} • ${dateFormat.format(Date(history[i].playedAt))}"
                        CollectionKind.ALBUM -> "${t.displayArtist} • ${formatDuration(t.durationMs)}"
                        else -> null
                    },
                )
            }
            if (kind == CollectionKind.ARTIST) {
                val artistAlbums = albums.filter { a -> tracks.any { it.albumKey == a.key } }
                if (artistAlbums.isNotEmpty()) {
                    item { SectionHeader("Álbuns") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                            items(artistAlbums, key = { it.key }) { a ->
                                MediaCard(a.title, a.year?.toString() ?: songsLabel(a.trackCount), a.coverTrackUri,
                                    onClick = { navigate(Routes.album(a.key)) }, modifier = Modifier.width(150.dp), seed = a.title)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Limpar histórico?",
            message = "O histórico de reprodução será apagado deste aparelho. Suas músicas e playlists não são afetadas.",
            confirmLabel = "Limpar",
            onConfirm = { vm.clearHistory(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun StatsScreen(vm: LibraryViewModel, playback: PlaybackController, onBack: () -> Unit, contentPadding: PaddingValues) {
    val top by vm.topTracks.collectAsState()
    val plays by vm.totalPlays.collectAsState()
    val time by vm.totalPlayedMs.collectAsState()
    val menu = LocalTrackMenu.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
                Text("Estatísticas", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                StatTile("Reproduções", plays.toString(), Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                StatTile("Tempo ouvido", formatLongDuration(time), Modifier.weight(1f))
            }
        }
        item {
            Text(
                "Calculado apenas neste aparelho. Uma reprodução conta após 30 s de escuta (ou metade de faixas curtas).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item { SectionHeader("Mais tocadas") }
        if (top.isEmpty()) item { Text("Ainda não há dados.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(top.size, key = { top[it].track.id }) { i ->
            val s = top[i]
            TrackRow(s.track, onClick = { playback.playTracks(top.map { it.track }, i) }, onMore = { menu.open(s.track) }, leadingIndex = i + 1,
                subtitle = "${s.playCount} reproduções • ${formatLongDuration(s.totalPlayedMs)}")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
