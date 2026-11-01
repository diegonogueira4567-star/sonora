package com.sonora.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.domain.model.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isFavorite: Boolean = false,
    leadingIndex: Int? = null,
    showArtwork: Boolean = true,
    subtitle: String? = null,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    val enabled = track.available
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(enabled = true, onClick = { if (enabled) onClick() else onMore() }, onLongClick = onMore)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIndex != null) {
            Text(
                text = leadingIndex.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (showArtwork) {
            Box {
                Artwork(track.uri, Modifier.size(48.dp), seed = track.album ?: track.title)
                if (isCurrent) {
                    Box(
                        Modifier.size(48.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.GraphicEq, contentDescription = "Tocando agora", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFavorite) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Favorita", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = when {
                        !enabled -> "Arquivo indisponível"
                        subtitle != null -> subtitle
                        else -> listOfNotNull(track.displayArtist, track.album).joinToString(" • ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (dragHandle != null) dragHandle()
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Opções de ${track.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DragHandleIcon(modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).semantics { contentDescription = "Arrastar para reordenar" }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MediaCard(
    title: String,
    subtitle: String?,
    artworkUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    seed: String? = title,
) {
    Column(
        modifier
            .clip12()
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Artwork(
            uri = artworkUri,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            sizePx = 320,
            shape = if (circle) CircleShape else RoundedCornerShape(8.dp),
            seed = seed,
            iconSize = 40.dp,
            fallbackIcon = if (circle) Icons.Rounded.Person else Icons.Rounded.MusicNote,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circle) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circle) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Modifier.clip12() = this.then(Modifier.clip(RoundedCornerShape(12.dp)))

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (primaryAction != null) {
            Button(onClick = primaryAction.second, modifier = Modifier.padding(top = 8.dp)) { Text(primaryAction.first) }
        }
        if (secondaryAction != null) {
            OutlinedButton(onClick = secondaryAction.second) { Text(secondaryAction.first) }
        }
    }
}

@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPlay,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Tocar")
        }
        FilledTonalButton(
            onClick = onShuffle,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            colors = ButtonDefaults.filledTonalButtonColors(),
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Aleatório")
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}
