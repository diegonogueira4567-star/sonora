package com.sonora.player.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sonora.player.domain.model.SortField
import com.sonora.player.domain.model.SortOrder
import com.sonora.player.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class EqualizerSettings(
    val enabled: Boolean = false,
    /** Index of a device preset, or -1 for custom [bandLevels]. */
    val preset: Int = -1,
    /** Custom band levels in millibels, one per device band. */
    val bandLevels: List<Int> = emptyList(),
    /** 0..1000 */
    val bassBoost: Int = 0,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: Long = DEFAULT_ACCENT,
    val dynamicColor: Boolean = false,
    val defaultSort: SortOrder = SortOrder(),
    val hideDuplicates: Boolean = true,
    val includeNonMusicAudio: Boolean = false,
    val minDurationSec: Int = 0,
    val swipeMiniPlayer: Boolean = true,
    val swipeArtwork: Boolean = true,
    val swipeDownToClose: Boolean = true,
    val fadeDurationMs: Int = 0,
    val restoreQueue: Boolean = true,
    val skipOnError: Boolean = true,
    val historyEnabled: Boolean = true,
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF1ED2A4
        val ACCENTS = listOf(0xFF1ED2A4, 0xFF4F8CFF, 0xFFFF6B6B, 0xFFFFB547, 0xFFB57BFF, 0xFFFF5FA2, 0xFF2EC4F1)
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** DataStore-backed preferences. Reading failures fall back to defaults instead of crashing. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = longPreferencesKey("accent")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val sortField = stringPreferencesKey("sort_field")
        val sortAsc = booleanPreferencesKey("sort_asc")
        val hideDuplicates = booleanPreferencesKey("hide_duplicates")
        val includeNonMusic = booleanPreferencesKey("include_non_music")
        val minDuration = intPreferencesKey("min_duration_sec")
        val swipeMini = booleanPreferencesKey("swipe_mini")
        val swipeArt = booleanPreferencesKey("swipe_art")
        val swipeDown = booleanPreferencesKey("swipe_down")
        val fade = intPreferencesKey("fade_ms")
        val restoreQueue = booleanPreferencesKey("restore_queue")
        val skipOnError = booleanPreferencesKey("skip_on_error")
        val history = booleanPreferencesKey("history_enabled")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreset = intPreferencesKey("eq_preset")
        val eqBands = stringPreferencesKey("eq_bands")
        val bassBoost = intPreferencesKey("bass_boost")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            accentColor = this[Keys.accent] ?: d.accentColor,
            dynamicColor = this[Keys.dynamic] ?: d.dynamicColor,
            defaultSort = SortOrder(
                field = this[Keys.sortField]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.TITLE,
                ascending = this[Keys.sortAsc] ?: true,
            ),
            hideDuplicates = this[Keys.hideDuplicates] ?: d.hideDuplicates,
            includeNonMusicAudio = this[Keys.includeNonMusic] ?: d.includeNonMusicAudio,
            minDurationSec = this[Keys.minDuration] ?: d.minDurationSec,
            swipeMiniPlayer = this[Keys.swipeMini] ?: d.swipeMiniPlayer,
            swipeArtwork = this[Keys.swipeArt] ?: d.swipeArtwork,
            swipeDownToClose = this[Keys.swipeDown] ?: d.swipeDownToClose,
            fadeDurationMs = this[Keys.fade] ?: d.fadeDurationMs,
            restoreQueue = this[Keys.restoreQueue] ?: d.restoreQueue,
            skipOnError = this[Keys.skipOnError] ?: d.skipOnError,
            historyEnabled = this[Keys.history] ?: d.historyEnabled,
            equalizer = EqualizerSettings(
                enabled = this[Keys.eqEnabled] ?: false,
                preset = this[Keys.eqPreset] ?: -1,
                bandLevels = this[Keys.eqBands]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                bassBoost = this[Keys.bassBoost] ?: 0,
            ),
            onboardingDone = this[Keys.onboarding] ?: false,
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { block(it) }
        } catch (e: IOException) {
            android.util.Log.w("SettingsStore", "Falha ao salvar preferência", e)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setAccentColor(color: Long) = edit { it[Keys.accent] = color }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamic] = enabled }
    suspend fun setDefaultSort(order: SortOrder) = edit {
        it[Keys.sortField] = order.field.name
        it[Keys.sortAsc] = order.ascending
    }
    suspend fun setHideDuplicates(value: Boolean) = edit { it[Keys.hideDuplicates] = value }
    suspend fun setIncludeNonMusicAudio(value: Boolean) = edit { it[Keys.includeNonMusic] = value }
    suspend fun setMinDurationSec(value: Int) = edit { it[Keys.minDuration] = value.coerceIn(0, 600) }
    suspend fun setSwipeMiniPlayer(value: Boolean) = edit { it[Keys.swipeMini] = value }
    suspend fun setSwipeArtwork(value: Boolean) = edit { it[Keys.swipeArt] = value }
    suspend fun setSwipeDownToClose(value: Boolean) = edit { it[Keys.swipeDown] = value }
    suspend fun setFadeDurationMs(value: Int) = edit { it[Keys.fade] = value.coerceIn(0, 12_000) }
    suspend fun setRestoreQueue(value: Boolean) = edit { it[Keys.restoreQueue] = value }
    suspend fun setSkipOnError(value: Boolean) = edit { it[Keys.skipOnError] = value }
    suspend fun setHistoryEnabled(value: Boolean) = edit { it[Keys.history] = value }
    suspend fun setOnboardingDone(value: Boolean) = edit { it[Keys.onboarding] = value }
    suspend fun setEqualizer(eq: EqualizerSettings) = edit {
        it[Keys.eqEnabled] = eq.enabled
        it[Keys.eqPreset] = eq.preset
        it[Keys.eqBands] = eq.bandLevels.joinToString(",")
        it[Keys.bassBoost] = eq.bassBoost.coerceIn(0, 1000)
    }
}
