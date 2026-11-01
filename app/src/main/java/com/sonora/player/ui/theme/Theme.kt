package com.sonora.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sonora.player.domain.model.ThemeMode

private fun onColorFor(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF0B0C0F) else Color.White

fun sonoraDarkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = onColorFor(accent),
    primaryContainer = lerp(Color(0xFF16181D), accent, 0.28f),
    onPrimaryContainer = Color.White,
    secondary = lerp(accent, Color.White, 0.35f),
    onSecondary = Color(0xFF0B0C0F),
    secondaryContainer = Color(0xFF2A2D35),
    onSecondaryContainer = Color(0xFFE6E7EA),
    tertiary = lerp(accent, Color(0xFF8A7CFF), 0.5f),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF0E0F12),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF22252C),
    onSurfaceVariant = Color(0xFFA9ADB6),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF14161A),
    surfaceContainer = Color(0xFF181A1F),
    surfaceContainerHigh = Color(0xFF202329),
    surfaceContainerHighest = Color(0xFF2A2D35),
    outline = Color(0xFF4A4E58),
    outlineVariant = Color(0xFF2E3139),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0000),
)

fun sonoraLightScheme(accent: Color): ColorScheme {
    // Darken very light accents so they stay readable on white.
    val primary = if (accent.luminance() > 0.5f) lerp(accent, Color.Black, 0.35f) else accent
    return lightColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = lerp(Color.White, primary, 0.18f),
        onPrimaryContainer = Color(0xFF0B0C0F),
        secondary = lerp(primary, Color.Black, 0.2f),
        secondaryContainer = Color(0xFFE9EBEF),
        onSecondaryContainer = Color(0xFF16181D),
        background = Color(0xFFF8F9FA),
        onBackground = Color(0xFF121316),
        surface = Color(0xFFF8F9FA),
        onSurface = Color(0xFF121316),
        surfaceVariant = Color(0xFFE9EBEF),
        onSurfaceVariant = Color(0xFF555A64),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF2F3F5),
        surfaceContainer = Color(0xFFEDEFF2),
        surfaceContainerHigh = Color(0xFFE6E8EC),
        surfaceContainerHighest = Color(0xFFDFE2E7),
        outline = Color(0xFF9DA2AC),
        outlineVariant = Color(0xFFD5D8DE),
        error = Color(0xFFC62828),
    )
}

private val SonoraTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val SonoraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun SonoraTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: Color = Color(0xFF1ED2A4),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> sonoraDarkScheme(accent)
        else -> sonoraLightScheme(accent)
    }
    MaterialTheme(colorScheme = scheme, typography = SonoraTypography, shapes = SonoraShapes, content = content)
}

val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
