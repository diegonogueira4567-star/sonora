package com.sonora.player.ui.components

import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** "1 h 12 min" style, for collection totals. */
fun formatLongDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        totalMin > 0 -> "$m min"
        ms > 0 -> "${ms / 1000} s"
        else -> "0 min"
    }
}

fun songsLabel(count: Int): String = if (count == 1) "1 música" else "$count músicas"

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
