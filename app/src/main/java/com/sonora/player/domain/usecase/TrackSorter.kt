package com.sonora.player.domain.usecase

import com.sonora.player.domain.model.SortField
import com.sonora.player.domain.model.SortOrder
import com.sonora.player.domain.model.Track
import java.text.Collator
import java.util.Locale

/** Locale-aware sorting of tracks. Unknown values always go last, whatever the direction. */
object TrackSorter {
    private val collator: Collator = Collator.getInstance(Locale("pt", "BR")).apply { strength = Collator.PRIMARY }

    fun sort(tracks: List<Track>, order: SortOrder): List<Track> {
        val byTitle = Comparator<Track> { a, b -> collator.compare(a.title, b.title) }
        val primary: Comparator<Track> = when (order.field) {
            SortField.TITLE -> byTitle
            SortField.ARTIST -> textComparator { it.artist }
            SortField.ALBUM -> textComparator { it.album }
            SortField.DURATION -> compareBy { it.durationMs }
            SortField.DATE_ADDED -> compareBy { it.dateAddedSec }
        }
        val directed = if (order.ascending) primary else primary.reversed()
        val withUnknownLast = when (order.field) {
            SortField.ARTIST -> unknownLast<Track> { it.artist }.then(directed)
            SortField.ALBUM -> unknownLast<Track> { it.album }.then(directed)
            else -> directed
        }
        // Stable, predictable tie-breakers: album order, then title.
        val tie = compareBy<Track>({ it.album?.lowercase() }, { it.trackNumber ?: Int.MAX_VALUE }).then(byTitle).thenBy { it.id }
        return tracks.sortedWith(withUnknownLast.then(tie))
    }

    private fun textComparator(selector: (Track) -> String?): Comparator<Track> =
        Comparator { a, b -> collator.compare(selector(a).orEmpty(), selector(b).orEmpty()) }

    private fun <T> unknownLast(selector: (T) -> String?): Comparator<T> =
        compareBy { selector(it).isNullOrBlank() }

    /** Album track order: disc/track number, then title. */
    fun albumOrder(tracks: List<Track>): List<Track> =
        tracks.sortedWith(compareBy<Track> { it.trackNumber ?: Int.MAX_VALUE }.thenComparator { a, b -> collator.compare(a.title, b.title) })

    fun compareText(a: String, b: String): Int = collator.compare(a, b)
}
