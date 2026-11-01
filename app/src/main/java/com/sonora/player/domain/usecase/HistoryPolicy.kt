package com.sonora.player.domain.usecase

/**
 * Documented rule for the play history:
 *  1. A track counts as "played" after it has actually been heard for 30 seconds, or for half of
 *     its duration when it is shorter than one minute (minimum 3 s). Paused time does not count.
 *  2. Each qualifying playthrough increments the local statistics (play count).
 *  3. The history list does not repeat the same track back to back: if the most recent history
 *     entry is the same track and is less than [MERGE_WINDOW_MS] old, its timestamp is refreshed
 *     instead of adding a new row (e.g. "repeat one" does not flood the history).
 */
object HistoryPolicy {
    const val THRESHOLD_MS = 30_000L
    const val MIN_THRESHOLD_MS = 3_000L
    const val MERGE_WINDOW_MS = 30 * 60_000L

    fun thresholdFor(durationMs: Long): Long =
        if (durationMs <= 0) THRESHOLD_MS else minOf(THRESHOLD_MS, maxOf(MIN_THRESHOLD_MS, durationMs / 2))

    fun qualifies(listenedMs: Long, durationMs: Long): Boolean = listenedMs >= thresholdFor(durationMs)

    fun shouldMergeWithLatest(latestTrackId: Long?, latestPlayedAt: Long?, trackId: Long, now: Long): Boolean =
        latestTrackId == trackId && latestPlayedAt != null && now - latestPlayedAt in 0..MERGE_WINDOW_MS
}
