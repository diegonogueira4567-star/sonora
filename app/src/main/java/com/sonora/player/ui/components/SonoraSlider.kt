package com.sonora.player.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Slim slider (4 dp track, round 14 dp thumb) used by the player's seek and volume bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonoraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    colors: SliderColors,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        colors = colors,
        interactionSource = interaction,
        modifier = modifier,
        thumb = { SliderDefaults.Thumb(interactionSource = interaction, colors = colors, enabled = enabled, thumbSize = DpSize(14.dp, 14.dp)) },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = colors,
                enabled = enabled,
                modifier = Modifier.height(4.dp),
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
            )
        },
    )
}
