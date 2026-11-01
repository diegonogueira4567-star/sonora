package com.sonora.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.playback.LyricsRepository
import com.sonora.player.playback.PlaybackController
import com.sonora.player.playback.PlayerUiState
import com.sonora.player.playback.QueueItem
import com.sonora.player.ui.LocalAppContainer
import com.sonora.player.ui.components.Artwork
import com.sonora.player.ui.components.DragHandleIcon
import com.sonora.player.ui.components.formatDuration
import com.sonora.player.ui.components.formatLongDuration
import com.sonora.player.ui.components.reorderHandle
import com.sonora.player.ui.components.reorderableItem
import com.sonora.player.ui.components.rememberReorderableListState
import com.sonora.player.ui.components.songsLabel
import com.sonora.player.domain.usecase.moved
import kotlinx.coroutines.delay

private const val QUEUE_HEADER_ITEMS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: PlayerUiState, playback: PlaybackController, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = state.queue.getOrNull(state.currentQueuePosition)
    var upcoming by remember(state.queue, state.currentQueuePosition) {
        mutableStateOf(if (state.currentQueuePosition >= 0) state.queue.drop(state.currentQueuePosition + 1) else state.queue)
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableListState(
        listState,
        canMoveTo = { it >= QUEUE_HEADER_ITEMS },
        onMove = { from, to -> upcoming = upcoming.moved(from - QUEUE_HEADER_ITEMS, to - QUEUE_HEADER_ITEMS) },
        onDragEnd = { from, to ->
            val base = state.currentQueuePosition + 1
            playback.moveInQueue(base + from - QUEUE_HEADER_ITEMS, base + to - QUEUE_HEADER_ITEMS)
        },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fila de reprodução", style = MaterialTheme.typography.titleLarge)
                    val remaining = upcoming.sumOf { it.durationMs }
                    Text(
                        "${songsLabel(upcoming.size)} a seguir • ${formatLongDuration(remaining)}" + if (state.shuffle) " • aleatório" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (upcoming.isNotEmpty()) TextButton(onClick = playback::clearUpcoming) { Text("Limpar") }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().testTag("queue_list")) {
                item(key = "h_now") { QueueHeader("Tocando agora") }
                item(key = "now") {
                    if (current != null) QueueRow(current, isCurrent = true, onClick = {}, onRemove = null, handle = null)
                    else Text("Nada tocando", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item(key = "h_next") { QueueHeader(if (upcoming.isEmpty()) "Nada a seguir" else "A seguir") }
                itemsIndexed(upcoming, key = { _, item -> "q_${item.timelineIndex}_${item.mediaId}" }) { i, item ->
                    val absolute = i + QUEUE_HEADER_ITEMS
                    QueueRow(
                        item,
                        isCurrent = false,
                        onClick = { playback.skipToQueueItem(item.timelineIndex) },
                        onRemove = { playback.removeFromQueue(item.timelineIndex) },
                        handle = { DragHandleIcon(Modifier.reorderHandle(reorder, absolute, item.timelineIndex)) },
                        modifier = Modifier.reorderableItem(reorder, absolute),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    handle: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.artworkUri, Modifier.size(44.dp), seed = item.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(item.artist, item.durationMs.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remover ${item.title} da fila") }
        }
        handle?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(state: PlayerUiState, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val lyricsState by container.lyricsRepository.state.collectAsState()
    val position by container.playbackController.position.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val loaded = (lyricsState as? LyricsRepository.State.Loaded)?.takeIf { it.mediaId == state.mediaId }
    val lyrics = loaded?.lyrics
    val listState = rememberLazyListState()
    val active = lyrics?.activeIndex(position) ?: -1
    LaunchedEffect(active) { if (active > 2) listState.animateScrollToItem(active - 2) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text("Letra", style = MaterialTheme.typography.titleLarge)
            Text(state.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            when {
                loaded == null -> Text("Procurando letra no arquivo…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                lyrics == null -> Text(
                    "Este arquivo não tem letra incorporada. O Sonora lê letras gravadas nas tags do arquivo (ID3 USLT, Vorbis LYRICS) " +
                        "e arquivos .lrc ao lado da música em pastas selecionadas. Nenhuma letra é baixada da internet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        if (lyrics.synced) "Sincronizada • ${lyrics.source}" else lyrics.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        itemsIndexed(lyrics.lines) { i, line ->
                            Text(
                                line.text.ifEmpty { "♪" },
                                style = if (lyrics.synced) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (i == active) FontWeight.Bold else null,
                                color = when {
                                    !lyrics.synced -> MaterialTheme.colorScheme.onSurface
                                    i == active -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepIndicator(state: PlayerUiState, onClick: () -> Unit) {
    val remaining = rememberSleepRemaining(state.sleepEndsAtMs)
    TextButton(onClick = onClick) {
        Icon(Icons.Rounded.Bedtime, contentDescription = "Temporizador", modifier = Modifier.size(20.dp),
            tint = if (remaining > 0 || state.sleepAtEndOfTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.sleepAtEndOfTrack -> Text("  Fim da faixa", style = MaterialTheme.typography.labelMedium)
            remaining > 0 -> Text("  " + formatDuration(remaining), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun rememberSleepRemaining(endsAt: Long): Long {
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(endsAt) {
        while (endsAt > 0) {
            remaining = (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
            if (remaining == 0L) break
            delay(1000)
        }
        if (endsAt <= 0) remaining = 0
    }
    return remaining
}

@Composable
fun SleepTimerDialog(state: PlayerUiState, playback: PlaybackController, onDismiss: () -> Unit) {
    val remaining = rememberSleepRemaining(state.sleepEndsAtMs)
    val active = remaining > 0 || state.sleepAtEndOfTrack
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador") },
        text = {
            Column {
                if (active) {
                    Text(
                        if (state.sleepAtEndOfTrack) "A reprodução vai parar ao fim da faixa atual."
                        else "A reprodução vai parar em ${formatDuration(remaining)}.",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                listOf(5, 10, 15, 30, 45, 60, 90).forEach { min ->
                    Text(
                        "$min minutos",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { playback.setSleepTimer(min); onDismiss() }.padding(vertical = 12.dp),
                    )
                }
                Text(
                    "Ao fim da faixa atual",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { playback.setSleepAtEndOfTrack(); onDismiss() }.padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {
            if (active) TextButton(onClick = { playback.cancelSleepTimer(); onDismiss() }) { Text("Cancelar temporizador") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar", textAlign = TextAlign.End) } },
    )
}
