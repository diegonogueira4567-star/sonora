package com.sonora.player

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * End-to-end UI flows on the real app. Requires the sample audio files pushed to the device
 * (titles "Amanhecer", "Caminhos", album "Sonora Demo", artist "Banda Teste").
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val permission = GrantPermissionRule.grant(com.sonora.player.ui.permissions.AUDIO_PERMISSION)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permission).around(compose)

    @Before
    fun skipOnboarding() {
        runBlocking { app.container.settingsStore.setOnboardingDone(true) }
    }

    private fun waitForText(text: String, timeout: Long = 15_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    @Test
    fun bottomNavigation_switchesBetweenMainScreens() {
        waitForText("Tocar tudo aleatório")
        compose.onNodeWithTag("nav_search").performClick()
        compose.onNodeWithTag("search_field").assertExists()
        compose.onNodeWithTag("nav_library").performClick()
        waitForText("Sua biblioteca")
        compose.onNodeWithTag("nav_home").performClick()
        waitForText("Tocar tudo aleatório")
    }

    @Test
    fun search_findsLocalSongs_andShowsEmptyState() {
        compose.onNodeWithTag("nav_search").performClick()
        compose.onNodeWithTag("search_field").performTextInput("  AMANHECER ")
        compose.waitUntilAtLeastOneExists(hasText("Amanhecer"), 10_000)
        compose.onNodeWithTag("search_field").performTextReplacement("zzqqxx")
        waitForText("Nada encontrado")
    }

    @Test
    fun library_tabsShowAlbumsAndArtists() {
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("tab_ALBUMS").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("albums_grid"), 10_000)
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("albums_grid").performScrollToNode(hasText("Sonora Demo")) }.isSuccess }
        compose.onNodeWithTag("tab_ARTISTS").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("artists_list").performScrollToNode(hasText("Banda Teste")) }.isSuccess }
        compose.onNodeWithText("Banda Teste").performClick()
        waitForText("Caminhos")
    }

    @Test
    fun playingSong_showsMiniPlayer_thenFullPlayer_andMinimizes() {
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("tab_SONGS").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("songs_list").performScrollToNode(hasText("Caminhos")) }.isSuccess }
        compose.onNodeWithText("Caminhos").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("mini_player"), 10_000)
        compose.onNodeWithTag("mini_player").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("full_player"), 5_000)
        compose.onNodeWithTag("player_play_pause").assertExists()
        compose.onNode(hasTestTag("player_play_pause").and(hasContentDescription("Pausar")), useUnmergedTree = false).assertExists()
        compose.onNodeWithTag("player_play_pause").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Reproduzir"), 5_000)
        compose.onNodeWithContentDescription("Minimizar reprodutor").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("full_player"), 5_000)
        compose.onNodeWithTag("mini_player").assertExists()
    }

    @Test
    fun createPlaylist_fromLibrary() {
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithText("Criar playlist").performClick()
        compose.onNodeWithText("Nome").performTextInput("Playlist UI")
        compose.onNodeWithText("Criar").performClick()
        waitForText("Playlist vazia")
        compose.onNodeWithText("Playlist UI").assertExists()
    }

    @Test
    fun settings_openAndShowSections() {
        waitForText("Tocar tudo aleatório")
        compose.onNodeWithContentDescription("Configurações").performClick()
        waitForText("Tema")
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Equalizador"))
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Fazer backup das playlists"))
    }
}
