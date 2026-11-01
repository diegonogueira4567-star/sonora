package com.sonora.player.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.sonora.player.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
