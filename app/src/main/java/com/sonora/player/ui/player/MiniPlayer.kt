package com.sonora.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sonora.player.playback.PlayerUiState
import com.sonora.player.ui.components.Artwork
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    /** Read only by the progress line, so position ticks do not recompose the rest of the UI. */
    position: StateFlow<Long>,
    isFavorite: Boolean,
    swipeEnabled: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("mini_player"),
    ) {
        Column {
            Row(
                Modifier
                    .clickable(onClickLabel = "Abrir reprodutor", onClick = onExpand)
                    .pointerInput(swipeEnabled) {
                        detectVerticalDragGestures { change, amount ->
                            if (amount < -12) {
                                change.consume()
                                onExpand()
                            }
                        }
                    }
                    .pointerInput(swipeEnabled) {
                        if (!swipeEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragX < -threshold) onNext() else if (dragX > threshold) onPrevious()
                                dragX = 0f
                            },
                            onDragCancel = { dragX = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragX += amount
                        }
                    }
                    .heightIn(min = 60.dp)
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).offset { IntOffset((dragX * 0.4f).roundToInt(), 0) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(state.artworkUri, Modifier.size(44.dp), seed = state.album ?: state.title)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.artist ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (state.trackId != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remover das favoritas" else "Adicionar às favoritas",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.testTag("mini_play_pause")) {
                    Icon(
                        if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.playWhenReady) "Pausar" else "Reproduzir",
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima faixa")
                }
            }
            MiniProgress(position, state.durationMs)
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun MiniProgress(position: StateFlow<Long>, durationMs: Long) {
    val positionMs by position.collectAsState()
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val bar = MaterialTheme.colorScheme.onSurface
    // Drawn in the draw phase: a position change only redraws this line.
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(2.dp)
            .semantics { contentDescription = "Progresso ${(progress * 100).roundToInt()}%" },
    ) {
        drawRect(track)
        drawRect(bar, size = size.copy(width = size.width * progress))
    }
}
