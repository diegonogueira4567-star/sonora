package com.sonora.player.ui.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sonora.player.domain.model.RepeatMode
import com.sonora.player.domain.model.Track
import com.sonora.player.navigation.Routes
import com.sonora.player.playback.PlaybackController
import com.sonora.player.playback.PlayerUiState
import com.sonora.player.ui.LocalAppContainer
import com.sonora.player.ui.components.Artwork
import com.sonora.player.ui.components.SonoraSlider
import com.sonora.player.ui.components.LocalTrackMenu
import com.sonora.player.ui.components.dominantColor
import com.sonora.player.ui.components.formatDuration
import com.sonora.player.ui.components.rememberArtworkBitmap
import com.sonora.player.ui.theme.tabular
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    position: StateFlow<Long>,
    isFavorite: Boolean,
    swipeArtwork: Boolean,
    swipeDownToClose: Boolean,
    playback: PlaybackController,
    onToggleFavorite: () -> Unit,
    onCollapse: () -> Unit,
    navigate: (String) -> Unit,
) {
    BackHandler(onBack = onCollapse)
    DisposableEffect(playback) {
        val release = playback.requestFinePosition()
        onDispose { release() }
    }
    val container = LocalAppContainer.current
    val menu = LocalTrackMenu.current
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(state.trackId) {
        currentTrack = state.trackId?.let { container.libraryRepository.getTrack(it) }
    }

    val bitmap = rememberArtworkBitmap(state.artworkUri, 512)
    val base = MaterialTheme.colorScheme.background
    val tint by animateColorAsState(
        targetValue = remember(bitmap) { dominantColor(bitmap) }?.let { lerp(it, Color.Black, 0.35f) }
            ?: lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.6f),
        animationSpec = tween(600),
        label = "playerTint",
    )
    val density = LocalDensity.current
    var dragY by remember { mutableFloatStateOf(0f) }
    val closeThreshold = with(density) { 140.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(base)
            .background(Brush.verticalGradient(0f to tint, 0.75f to base))
            .testTag("full_player")
            .offset { IntOffset(0, dragY.coerceAtLeast(0f).roundToInt()) }
            .pointerInput(swipeDownToClose) {
                if (!swipeDownToClose) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY > closeThreshold) onCollapse()
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                ) { change, amount ->
                    change.consume()
                    dragY = (dragY + amount).coerceAtLeast(0f)
                }
            },
    ) {
        val topBar: @Composable () -> Unit = {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimizar reprodutor", modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOCANDO AGORA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.album ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        val track = currentTrack
                        if (track != null) {
                            DropdownMenuItem(text = { Text("Adicionar a uma playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                                onClick = { showMenu = false; menu.openAddToPlaylist(listOf(track)) })
                            DropdownMenuItem(text = { Text("Ir para o álbum") }, leadingIcon = { Icon(Icons.Rounded.Album, null) },
                                onClick = { showMenu = false; onCollapse(); navigate(Routes.album(track.albumKey)) })
                            DropdownMenuItem(text = { Text("Ir para o artista") }, leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                onClick = { showMenu = false; onCollapse(); navigate(Routes.artist(track.artistKey)) })
                            DropdownMenuItem(text = { Text("Detalhes do arquivo") }, leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                onClick = { showMenu = false; menu.details = track })
                        }
                        DropdownMenuItem(text = { Text("Temporizador") }, leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                            onClick = { showMenu = false; showSleep = true })
                        DropdownMenuItem(text = { Text("Equalizador") }, leadingIcon = { Icon(Icons.Rounded.Equalizer, null) },
                            onClick = { showMenu = false; onCollapse(); navigate(Routes.EQUALIZER) })
                        DropdownMenuItem(text = { Text("Parar e limpar fila") }, leadingIcon = { Icon(Icons.Rounded.Stop, null) },
                            onClick = { showMenu = false; playback.stopAndClear(); onCollapse() })
                    }
                }
        }
        }
        val controls: @Composable () -> Unit = {
            // Title + favorite
            Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(targetState = state.title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") { title ->
                        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        state.artist ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.trackId != null) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(52.dp)) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remover das favoritas" else "Adicionar às favoritas",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
        }
            
            Spacer(Modifier.height(12.dp))
            SeekBar(state.durationMs, position, onSeek = playback::seekTo)
            
            // Transport controls
            Row(
                Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = playback::toggleShuffle, modifier = Modifier.semantics { stateDescription = if (state.shuffle) "ativado" else "desativado" }) {
                    Icon(
                        Icons.Rounded.Shuffle, contentDescription = "Aleatório",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = playback::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Faixa anterior", modifier = Modifier.size(40.dp))
                }
                FilledIconButton(
                    onClick = playback::togglePlayPause,
                    modifier = Modifier.size(76.dp).testTag("player_play_pause"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Icon(
                        if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.playWhenReady) "Pausar" else "Reproduzir",
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = playback::next, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima faixa", modifier = Modifier.size(40.dp))
                }
                IconButton(
                    onClick = playback::cycleRepeat,
                    modifier = Modifier.semantics {
                        stateDescription = when (state.repeat) {
                            RepeatMode.OFF -> "desativada"
                            RepeatMode.ALL -> "repetir fila"
                            RepeatMode.ONE -> "repetir faixa"
                        }
                    },
                ) {
                    Icon(
                        if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repetição",
                        tint = if (state.repeat != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
        }
            
            VolumeBar()
            
            Row(
                Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showLyrics = true }) { Icon(Icons.Rounded.Lyrics, contentDescription = "Letra") }
                SleepIndicator(state, onClick = { showSleep = true })
                IconButton(onClick = { showQueue = true }, modifier = Modifier.testTag("open_queue")) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Fila de reprodução")
                }
        }
        }
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Landscape phones and wide windows: cover on the left, controls on the right, so every
            // control stays visible without scrolling past an oversized cover.
            val twoPane = maxWidth > maxHeight && maxWidth >= 560.dp
            if (twoPane) {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    topBar()
                    Row(Modifier.fillMaxSize().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            ArtworkPager(state, swipeArtwork, playback)
                        }
                        Spacer(Modifier.width(24.dp))
                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) { controls() }
                    }
                }
            } else {
                val viewportPx = constraints.maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    PortraitPlayerBody(viewportPx, topBar, controls) { size -> ArtworkPager(state, swipeArtwork, playback, fixedSize = size) }
                }
            }
        }
    }

    if (showQueue) QueueSheet(state, playback, onDismiss = { showQueue = false })
    if (showLyrics) LyricsSheet(state, onDismiss = { showLyrics = false })
    if (showSleep) SleepTimerDialog(state, playback, onDismiss = { showSleep = false })
}

/**
 * Portrait layout: the controls are measured first and the cover gets the remaining height, so
 * play/pause and the seek bar stay on screen on compact phones. Only when even a minimum-size cover
 * does not fit (very large fonts) does the parent scroll.
 */
@Composable
private fun PortraitPlayerBody(
    viewportPx: Int,
    topBar: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    artwork: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
) {
    val density = LocalDensity.current
    androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val top = subcompose("top") { topBar() }.map { it.measure(loose) }
        val ctrl = subcompose("controls") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { controls() }
        }.map { it.measure(loose) }
        val topH = top.sumOf { it.height }
        val ctrlH = ctrl.sumOf { it.height }
        val gaps = with(density) { (20.dp + 24.dp).roundToPx() }
        val maxArt = minOf(constraints.maxWidth, with(density) { 440.dp.roundToPx() })
        val minArt = with(density) { 140.dp.roundToPx() }
        val artPx = (viewportPx - topH - ctrlH - gaps).coerceIn(minOf(minArt, maxArt), maxArt)
        val art = subcompose("art") { artwork(with(density) { artPx.toDp() }) }.map { it.measure(loose) }
        val artH = art.sumOf { it.height }
        val total = topH + artH + ctrlH + gaps
        layout(constraints.maxWidth, maxOf(total, minOf(viewportPx, total))) {
            var y = 0
            top.forEach { it.placeRelative((constraints.maxWidth - it.width) / 2, y); y += it.height }
            y += with(density) { 20.dp.roundToPx() }
            art.forEach { it.placeRelative((constraints.maxWidth - it.width) / 2, y); y += it.height }
            y += with(density) { 24.dp.roundToPx() }
            ctrl.forEach { it.placeRelative((constraints.maxWidth - it.width) / 2, y); y += it.height }
        }
    }
}

@Composable
private fun ArtworkPager(state: PlayerUiState, swipeEnabled: Boolean, playback: PlaybackController, fixedSize: androidx.compose.ui.unit.Dp? = null) {
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 520.dp), contentAlignment = Alignment.Center) {
        // Square that fits both dimensions when the height is bounded (two-pane layout).
        val size = fixedSize ?: if (constraints.hasBoundedHeight) minOf(maxWidth, maxHeight, 440.dp) else minOf(maxWidth, 440.dp)
        AnimatedContent(targetState = state.artworkUri, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "art") { artUri ->
            Artwork(
                uri = artUri,
                modifier = Modifier
                    .size(size)
                    .aspectRatio(1f)
                    .offset { IntOffset((dragX * 0.5f).roundToInt(), 0) }
                    .pointerInput(swipeEnabled) {
                        if (!swipeEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragX < -threshold) playback.next() else if (dragX > threshold) playback.previous()
                                dragX = 0f
                            },
                            onDragCancel = { dragX = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragX += amount
                        }
                    }
                    .semantics { contentDescription = "Capa de ${state.title}" },
                sizePx = 768,
                shape = RoundedCornerShape(12.dp),
                seed = state.album ?: state.title,
                iconSize = 96.dp,
            )
        }
    }
}

@Composable
private fun SeekBar(durationMs: Long, position: StateFlow<Long>, onSeek: (Long) -> Unit) {
    val positionMs by position.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = durationMs.coerceAtLeast(1)
    val shown = if (dragging) (dragValue * duration).toLong() else positionMs.coerceIn(0, duration)
    Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
        SonoraSlider(
            value = if (dragging) dragValue else (positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * duration).toLong())
                dragging = false
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            ),
            modifier = Modifier.testTag("seek_bar").semantics {
                contentDescription = "Posição na faixa"
                stateDescription = "${formatDuration(shown)} de ${formatDuration(durationMs)}"
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(shown), style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatDuration((durationMs - shown).coerceAtLeast(0)),
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** System media volume (STREAM_MUSIC), kept in sync with hardware volume keys. */
@Composable
private fun VolumeBar() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SonoraSlider(
            value = volume.toFloat(),
            onValueChange = {
                volume = it.roundToInt()
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
            },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            ),
        )
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
