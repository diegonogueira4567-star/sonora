package com.sonora.player.playback

import com.sonora.player.domain.usecase.HistoryPolicy

/**
 * Accumulates time actually heard for the current item (paused/buffering time excluded) and
 * reports when a playthrough qualifies as a "play" under [HistoryPolicy].
 */
class ListenTracker {
    data class Report(val mediaId: String, val listenedMs: Long, val countAsPlay: Boolean)

    private var mediaId: String? = null
    private var durationMs: Long = 0
    private var unreported: Long = 0
    private var total: Long = 0
    private var counted = false

    /** Starts a new playthrough; returns the leftover listening time of the previous one. */
    fun start(newMediaId: String?, newDurationMs: Long): Report? {
        val leftover = flush()
        mediaId = newMediaId
        durationMs = newDurationMs
        unreported = 0
        total = 0
        counted = false
        return leftover
    }

    fun updateDuration(newDurationMs: Long) {
        if (newDurationMs > 0) durationMs = newDurationMs
    }

    /** Adds heard time; returns a report the first time the playthrough qualifies. */
    fun onListened(deltaMs: Long): Report? {
        val id = mediaId ?: return null
        if (deltaMs <= 0) return null
        unreported += deltaMs
        total += deltaMs
        if (!counted && HistoryPolicy.qualifies(total, durationMs)) {
            counted = true
            return Report(id, unreported, countAsPlay = true).also { unreported = 0 }
        }
        return null
    }

    /** Reports pending listening time (without counting a play). */
    fun flush(): Report? {
        val id = mediaId ?: return null
        if (unreported <= 0) return null
        return Report(id, unreported, countAsPlay = false).also { unreported = 0 }
    }
}

/** Volume envelope for the optional fade transition between tracks. */
object FadeCalculator {
    fun volume(positionMs: Long, durationMs: Long, fadeMs: Int, fadeIn: Boolean, fadeOut: Boolean): Float {
        if (fadeMs <= 0) return 1f
        // Never fade for more than a third of a short track.
        val effective = if (durationMs > 0) minOf(fadeMs.toLong(), durationMs / 3) else fadeMs.toLong()
        if (effective <= 0) return 1f
        var v = 1f
        if (fadeIn && positionMs < effective) v = minOf(v, positionMs.toFloat() / effective)
        if (fadeOut && durationMs > 0) {
            val remaining = durationMs - positionMs
            if (remaining < effective) v = minOf(v, remaining.toFloat() / effective)
        }
        return v.coerceIn(0f, 1f)
    }
}
