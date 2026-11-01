package com.sonora.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import com.sonora.player.data.local.preferences.EqualizerSettings
import com.sonora.player.data.local.preferences.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class EqualizerBand(val index: Int, val centerHz: Int)

data class EffectsCapabilities(
    val equalizerAvailable: Boolean,
    val bands: List<EqualizerBand>,
    val minLevel: Int,
    val maxLevel: Int,
    val presets: List<String>,
    val bassBoostAvailable: Boolean,
    val error: String? = null,
)

/**
 * Real audio effects attached to the player's audio session. Controls are only offered for the
 * effects the device actually provides; preferences are persisted in DataStore.
 */
class AudioEffectsManager(private val settingsStore: SettingsStore, private val scope: CoroutineScope) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var job: Job? = null

    private val _capabilities = MutableStateFlow<EffectsCapabilities?>(null)
    /** Null until the playback service has attached to an audio session. */
    val capabilities: StateFlow<EffectsCapabilities?> = _capabilities.asStateFlow()

    fun attach(audioSessionId: Int) {
        release()
        val eq = runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Equalizer unavailable: ${it.javaClass.simpleName}") }.getOrNull()
        val bb = runCatching { BassBoost(0, audioSessionId) }
            .onFailure { Log.w(TAG, "BassBoost unavailable: ${it.javaClass.simpleName}") }.getOrNull()
        equalizer = eq
        bassBoost = bb
        _capabilities.value = if (eq == null) {
            EffectsCapabilities(false, emptyList(), 0, 0, emptyList(), bb?.strengthSupported == true, "Este aparelho não oferece equalizador para o reprodutor.")
        } else {
            runCatching {
                val range = eq.bandLevelRange
                EffectsCapabilities(
                    equalizerAvailable = true,
                    bands = (0 until eq.numberOfBands).map { EqualizerBand(it, eq.getCenterFreq(it.toShort()) / 1000) },
                    minLevel = range[0].toInt(),
                    maxLevel = range[1].toInt(),
                    presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                    bassBoostAvailable = bb?.strengthSupported == true,
                )
            }.getOrElse { EffectsCapabilities(false, emptyList(), 0, 0, emptyList(), false, "Falha ao ler o equalizador.") }
        }
        job = scope.launch {
            settingsStore.settings.map { it.equalizer }.distinctUntilChanged().collect { apply(it) }
        }
    }

    private fun apply(settings: EqualizerSettings) {
        val caps = _capabilities.value
        equalizer?.let { eq ->
            runCatching {
                if (settings.enabled) {
                    if (settings.preset >= 0 && caps != null && settings.preset < caps.presets.size) {
                        eq.usePreset(settings.preset.toShort())
                    } else {
                        settings.bandLevels.forEachIndexed { band, level ->
                            if (caps != null && band < caps.bands.size) {
                                eq.setBandLevel(band.toShort(), level.coerceIn(caps.minLevel, caps.maxLevel).toShort())
                            }
                        }
                    }
                }
                eq.enabled = settings.enabled
            }.onFailure { Log.w(TAG, "Equalizer apply failed", it) }
        }
        bassBoost?.let { bb ->
            runCatching {
                if (bb.strengthSupported) bb.setStrength(settings.bassBoost.toShort())
                bb.enabled = settings.enabled && settings.bassBoost > 0
            }.onFailure { Log.w(TAG, "BassBoost apply failed", it) }
        }
    }

    /** Current band levels (after a preset is applied), used by the UI to show slider positions. */
    fun currentBandLevels(): List<Int>? = equalizer?.let { eq ->
        runCatching { (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() } }.getOrNull()
    }

    fun release() {
        job?.cancel()
        job = null
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    private companion object {
        const val TAG = "AudioEffects"
    }
}
