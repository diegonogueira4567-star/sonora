package com.sonora.player.domain.usecase

import com.sonora.player.domain.model.Album
import com.sonora.player.domain.model.Artist
import com.sonora.player.domain.model.Playlist
import com.sonora.player.domain.model.TextNormalizer
import com.sonora.player.domain.model.Track

data class SearchResults(
    val query: String,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

/**
 * Offline search: case- and accent-insensitive, tolerant to extra spaces; every word of the query
 * must appear in the item. Prefix matches rank above matches in the middle of a word.
 */
object SearchEngine {

    fun search(
        rawQuery: String,
        tracks: List<Track>,
        artists: List<Artist>,
        albums: List<Album>,
        playlists: List<Playlist>,
        limit: Int = 50,
    ): SearchResults = index(tracks, artists, albums, playlists).search(rawQuery, limit)

    /**
     * Pre-normalizes every searchable field once. Building costs one pass over the library; each
     * query afterwards only compares precomputed strings (no Unicode normalization per keystroke).
     */
    fun index(tracks: List<Track>, artists: List<Artist>, albums: List<Album>, playlists: List<Playlist>): SearchIndex =
        SearchIndex(
            tracks = tracks.map { Indexed(it, normalizedFields(it.title, it.artist, it.album, it.albumArtist)) },
            artists = artists.map { Indexed(it, normalizedFields(it.name)) },
            albums = albums.map { Indexed(it, normalizedFields(it.title, it.artist)) },
            playlists = playlists.map { Indexed(it, normalizedFields(it.name)) },
        )

    private fun normalizedFields(vararg values: String?): List<String> = values.map { TextNormalizer.normalize(it) }

    class Indexed<T>(val item: T, val fields: List<String>)

    class SearchIndex internal constructor(
        private val tracks: List<Indexed<Track>>,
        private val artists: List<Indexed<Artist>>,
        private val albums: List<Indexed<Album>>,
        private val playlists: List<Indexed<Playlist>>,
    ) {
        val size: Int get() = tracks.size

        fun search(rawQuery: String, limit: Int = 50): SearchResults {
            val query = TextNormalizer.normalize(rawQuery)
            if (query.isEmpty()) return SearchResults(rawQuery)
            val terms = query.split(' ')
            return SearchResults(
                query = rawQuery,
                tracks = rank(tracks, terms, limit),
                artists = rank(artists, terms, limit),
                albums = rank(albums, terms, limit),
                playlists = rank(playlists, terms, limit),
            )
        }
    }

    private fun <T> rank(items: List<Indexed<T>>, terms: List<String>, limit: Int): List<T> =
        items.asSequence()
            .mapNotNull { indexed -> score(indexed.fields, terms)?.let { it to indexed.item } }
            .sortedByDescending { it.first }
            .take(limit)
            .map { it.second }
            .toList()

    /** Returns null when some term is missing; otherwise a relevance score. */
    internal fun score(fields: List<String>, terms: List<String>): Int? {
        var total = 0
        for (term in terms) {
            var best = 0
            fields.forEachIndexed { index, field ->
                val weight = (fields.size - index) // earlier fields (title) weigh more
                val s = when {
                    field == term -> 100
                    field.startsWith(term) -> 60
                    field.contains(" $term") -> 40
                    field.contains(term) -> 15
                    else -> 0
                }
                if (s > 0) best = maxOf(best, s * 10 + weight)
            }
            if (best == 0) return null
            total += best
        }
        return total
    }
}
