package com.sonora.player.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonora.player.ui.LocalAppContainer

/** Deterministic two-tone gradient used when a file has no embedded cover. */
fun placeholderBrush(seed: String?): Brush {
    val hash = (seed ?: "").hashCode()
    val hue = ((hash ushr 1) % 360).toFloat()
    val a = Color.hsv(hue, 0.55f, 0.55f)
    val b = Color.hsv((hue + 40f) % 360f, 0.65f, 0.28f)
    return Brush.linearGradient(listOf(a, b))
}

@Composable
fun rememberArtwork(uri: String?, sizePx: Int): ImageBitmap? {
    val loader = LocalAppContainer.current.artworkLoader
    var bitmap by remember(uri, sizePx) { mutableStateOf(uri?.let { loader.cached(it, sizePx)?.asImageBitmap() }) }
    LaunchedEffect(uri, sizePx) {
        if (uri != null && bitmap == null && !loader.isKnownMissing(uri)) bitmap = loader.load(uri, sizePx)?.asImageBitmap()
    }
    return bitmap
}

@Composable
fun rememberArtworkBitmap(uri: String?, sizePx: Int): Bitmap? {
    val loader = LocalAppContainer.current.artworkLoader
    var bitmap by remember(uri, sizePx) { mutableStateOf(uri?.let { loader.cached(it, sizePx) }) }
    LaunchedEffect(uri, sizePx) {
        if (uri != null && bitmap == null && !loader.isKnownMissing(uri)) bitmap = loader.load(uri, sizePx)
    }
    return bitmap
}

@Composable
fun Artwork(
    uri: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = 192,
    shape: Shape = RoundedCornerShape(6.dp),
    seed: String? = uri,
    iconSize: Dp = 22.dp,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
) {
    val bitmap = rememberArtwork(uri, sizePx)
    Box(modifier.clip(shape).background(placeholderBrush(seed)), contentAlignment = Alignment.Center) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "artwork") { bmp ->
            if (bmp != null) {
                Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(fallbackIcon, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}

/** Average color of a bitmap, used to tint the full-screen player background. */
fun dominantColor(bitmap: Bitmap?): Color? {
    if (bitmap == null) return null
    return runCatching {
        val small = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
        Color(small.getPixel(0, 0))
    }.getOrNull()
}

