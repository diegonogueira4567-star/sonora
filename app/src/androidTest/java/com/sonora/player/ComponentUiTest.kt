package com.sonora.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonora.player.domain.model.RepeatMode
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.model.TrackSource
import com.sonora.player.playback.PlayerUiState
import com.sonora.player.ui.LocalAppContainer
import com.sonora.player.ui.components.EmptyState
import com.sonora.player.ui.components.LocalTrackMenu
import com.sonora.player.ui.components.TrackMenuState
import com.sonora.player.ui.components.TrackRow
import com.sonora.player.ui.permissions.PermissionScreen
import com.sonora.player.ui.player.MiniPlayer
import com.sonora.player.ui.player.PlayerScreen
import com.sonora.player.ui.theme.SonoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val playing = PlayerUiState(
        connected = true, mediaId = "ext:x", title = "Uma música com um título bastante longo para testar quebra", artist = "Artista",
        album = "Álbum", isPlaying = true, playWhenReady = true, durationMs = 200_000, hasNext = true, repeat = RepeatMode.OFF,
    )

    @Composable
    private fun Host(fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalAppContainer provides app.container,
            LocalTrackMenu provides TrackMenuState(),
            LocalDensity provides Density(density.density, fontScale),
        ) { SonoraTheme { content() } }
    }

    @Test
    fun permissionScreen_explainsAndOffersAlternatives() {
        var granted = 0
        var skipped = 0
        compose.setContent { Host { PermissionScreen(false, { granted++ }, {}, {}, { skipped++ }) } }
        compose.onNodeWithText("Somente leitura de áudio").assertExists()
        compose.onNodeWithText("Escolher uma pasta").assertExists()
        compose.onNodeWithTag("grant_permission").performClick()
        compose.onNodeWithTag("skip_permission").performScrollTo().performClick()
        assertEquals(1, granted)
        assertEquals(1, skipped)
    }

    @Test
    fun permissionScreen_permanentlyDenied_pointsToSettings() {
        compose.setContent { Host { PermissionScreen(true, {}, {}, {}, {}) } }
        compose.onNodeWithText("Abrir configurações do app").assertExists()
    }

    @Test
    fun emptyState_showsMessageAndAction() {
        var clicked = false
        compose.setContent { Host { EmptyState(Icons.Rounded.MusicOff, "Nenhuma música", "Mensagem", primaryAction = "Atualizar" to { clicked = true }) } }
        compose.onNodeWithText("Nenhuma música").assertIsDisplayed()
        compose.onNodeWithText("Atualizar").performClick()
        assertTrue(clicked)
    }

    @Test
    fun miniPlayer_largeFontOnNarrowScreen_keepsButtonsUsable() {
        var toggles = 0
        compose.setContent {
            Host(fontScale = 2f) {
                Box(Modifier.width(320.dp)) {
                    MiniPlayer(playing, kotlinx.coroutines.flow.MutableStateFlow(50_000L), false, true, {}, { toggles++ }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Próxima faixa").assertIsDisplayed()
        assertEquals(1, toggles)
    }

    @Test
    fun trackRow_unavailableFileIsMarked_andMenuReachable() {
        var menu = 0
        val t = Track(1, "content://x", TrackSource.MEDIA_STORE, "Faixa sumida", null, null, null, null, 0, null, null, 0, 0, null, null, null, null, null,
            available = false, duplicate = false)
        compose.setContent { Host(fontScale = 1.5f) { TrackRow(t, onClick = {}, onMore = { menu++ }) } }
        compose.onNodeWithText("Arquivo indisponível").assertExists()
        compose.onNodeWithContentDescription("Opções de Faixa sumida").performClick()
        assertEquals(1, menu)
    }

    @Test
    fun fullPlayer_compactPortrait_controlsVisibleWithoutScrolling() {
        compose.setContent {
            Host(fontScale = 1.3f) {
                Box(Modifier.size(360.dp, 640.dp)) {
                    PlayerScreen(playing, kotlinx.coroutines.flow.MutableStateFlow(30_000L), false, true, true, app.container.playbackController, {}, {}, {})
                }
            }
        }
        // Primary controls fit on screen without scrolling; secondary actions are reachable by scrolling.
        compose.onNodeWithTag("player_play_pause").assertIsDisplayed()
        compose.onNodeWithTag("seek_bar").assertIsDisplayed()
        listOf("Faixa anterior", "Próxima faixa").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onNodeWithContentDescription("Fila de reprodução").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fullPlayer_smallScreenLargeFont_allControlsReachable() {
        compose.setContent {
            Host(fontScale = 1.6f) {
                Box(Modifier.size(320.dp, 560.dp)) {
                    PlayerScreen(playing, kotlinx.coroutines.flow.MutableStateFlow(30_000L), false, true, true, app.container.playbackController, {}, {}, {})
                }
            }
        }
        listOf("Aleatório", "Faixa anterior", "Próxima faixa", "Repetição", "Fila de reprodução", "Letra").forEach {
            compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("player_play_pause").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Minimizar reprodutor").assertExists()
    }
}
