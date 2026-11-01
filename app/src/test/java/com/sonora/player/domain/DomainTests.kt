package com.sonora.player.domain

import com.sonora.player.domain.model.Playlist
import com.sonora.player.domain.model.SortField
import com.sonora.player.domain.model.SortOrder
import com.sonora.player.domain.model.TextNormalizer
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.usecase.HistoryPolicy
import com.sonora.player.domain.usecase.LibraryGrouping
import com.sonora.player.domain.usecase.SearchEngine
import com.sonora.player.domain.usecase.TrackSorter
import com.sonora.player.domain.usecase.moved
import com.sonora.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test fun normalize_caseAccentsAndSpaces() = assertEquals("acao e cafe", TextNormalizer.normalize("  AÇÃO   é  Café "))
    @Test fun normalize_nullAndBlank() {
        assertEquals("", TextNormalizer.normalize(null))
        assertEquals("", TextNormalizer.normalize("   "))
    }
}

class TrackSorterTest {
    private val tracks = listOf(
        track(1, "beta", artist = "Zeca", album = "B", duration = 300, added = 3),
        track(2, "Álvaro", artist = null, album = null, duration = 100, added = 1),
        track(3, "Carlos", artist = "ana", album = "A", duration = 200, added = 2),
    )

    @Test fun byTitle_ignoresCaseAndAccents() =
        assertEquals(listOf(2L, 1L, 3L), TrackSorter.sort(tracks, SortOrder(SortField.TITLE)).map { it.id })

    @Test fun byTitle_descending() =
        assertEquals(listOf(3L, 1L, 2L), TrackSorter.sort(tracks, SortOrder(SortField.TITLE, ascending = false)).map { it.id })

    @Test fun byArtist_unknownAlwaysLast() {
        assertEquals(listOf(3L, 1L, 2L), TrackSorter.sort(tracks, SortOrder(SortField.ARTIST)).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), TrackSorter.sort(tracks, SortOrder(SortField.ARTIST, false)).map { it.id })
    }

    @Test fun byDuration_andDate() {
        assertEquals(listOf(2L, 3L, 1L), TrackSorter.sort(tracks, SortOrder(SortField.DURATION)).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), TrackSorter.sort(tracks, SortOrder(SortField.DATE_ADDED, false)).map { it.id })
    }

    @Test fun albumOrder_usesTrackNumbers() {
        val album = listOf(track(1, "Z", trackNumber = 2), track(2, "A", trackNumber = 1), track(3, "M"))
        assertEquals(listOf(2L, 1L, 3L), TrackSorter.albumOrder(album).map { it.id })
    }
}

class SearchEngineTest {
    private val tracks = listOf(
        track(1, "Garota de Ipanema", artist = "Tom Jobim", album = "Getz/Gilberto"),
        track(2, "Águas de Março", artist = "Elis Regina", album = "Elis & Tom"),
        track(3, "Ipanema Beach", artist = "Other"),
    )
    private val playlists = listOf(Playlist(1, "Bossa Nova", 0, 0, 2, 0, null))

    private fun search(q: String) = SearchEngine.search(q, tracks, LibraryGrouping.artists(tracks), LibraryGrouping.albums(tracks), playlists)

    @Test fun caseAndAccentInsensitive() = assertEquals(listOf(2L), search("aguas de MARCO").tracks.map { it.id })

    @Test fun extraSpacesIgnored() = assertEquals(listOf(2L), search("   águas    março  ").tracks.map { it.id })

    @Test fun termsMayMatchDifferentFields() = assertEquals(listOf(1L), search("ipanema jobim").tracks.map { it.id })

    @Test fun titlePrefixRanksFirst() = assertEquals(listOf(3L, 1L), search("ipanema").tracks.map { it.id })

    @Test fun findsArtistsAlbumsAndPlaylists() {
        assertTrue(search("elis").artists.any { it.name == "Elis Regina" })
        assertTrue(search("getz").albums.any { it.title == "Getz/Gilberto" })
        assertEquals("Bossa Nova", search("bossa").playlists.single().name)
    }

    @Test fun emptyAndNoMatch() {
        assertTrue(search("   ").isEmpty)
        assertTrue(search("xyzxyz").isEmpty)
    }
}

class LibraryGroupingTest {
    @Test fun albums_groupByAlbumAndArtist_unknownHandled() {
        val t = listOf(
            track(1, "a", artist = "X", album = "Same"), track(2, "b", artist = "X", album = "Same"),
            track(3, "c", artist = "Y", album = "Same"), track(4, "d"),
        )
        val albums = LibraryGrouping.albums(t)
        assertEquals(3, albums.size)
        assertTrue(albums.any { it.title == Track.UNKNOWN_ALBUM && it.trackCount == 1 })
        assertEquals(2, albums.first { it.title == "Same" && it.artist == "X" }.trackCount)
    }

    @Test fun albums_useAlbumArtistForCompilations() {
        val t = listOf(
            track(1, "a", artist = "A", album = "Hits", albumArtist = "Vários"),
            track(2, "b", artist = "B", album = "Hits", albumArtist = "Vários"),
        )
        assertEquals(1, LibraryGrouping.albums(t).size)
    }

    @Test fun artists_andGenres() {
        val t = listOf(track(1, "a", artist = "Ana", genre = "Rock"), track(2, "b", artist = "ANA", genre = "rock"), track(3, "c"))
        val artists = LibraryGrouping.artists(t)
        assertEquals(2, artists.size)
        assertEquals(Track.UNKNOWN_ARTIST, artists.last().name)
        val genres = LibraryGrouping.genres(t)
        assertEquals(2, genres.first { it.key == "rock" }.trackCount)
        assertEquals(Track.UNKNOWN_GENRE, genres.last().name)
    }
}

class HistoryPolicyTest {
    @Test fun threshold_rules() {
        assertEquals(30_000, HistoryPolicy.thresholdFor(240_000))
        assertEquals(20_000, HistoryPolicy.thresholdFor(40_000))
        assertEquals(3_000, HistoryPolicy.thresholdFor(4_000))
        assertEquals(30_000, HistoryPolicy.thresholdFor(0))
        assertTrue(HistoryPolicy.qualifies(30_000, 240_000))
        assertFalse(HistoryPolicy.qualifies(29_999, 240_000))
    }

    @Test fun merge_onlySameTrackWithinWindow() {
        assertTrue(HistoryPolicy.shouldMergeWithLatest(5, 1_000, 5, 1_000 + 60_000))
        assertFalse(HistoryPolicy.shouldMergeWithLatest(5, 1_000, 6, 2_000))
        assertFalse(HistoryPolicy.shouldMergeWithLatest(5, 0, 5, HistoryPolicy.MERGE_WINDOW_MS + 1))
        assertFalse(HistoryPolicy.shouldMergeWithLatest(null, null, 5, 0))
    }
}

class ListReorderTest {
    @Test fun moved_forwardBackwardAndInvalid() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(0, 5))
    }
}
