package com.sonora.player.data

import com.sonora.player.asEntity
import com.sonora.player.data.media.LibraryMerger
import com.sonora.player.data.media.ScanScope
import com.sonora.player.data.repository.toDomain
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.model.TrackSource
import com.sonora.player.scanned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMergerTest {
    private val ms = setOf<ScanScope>(ScanScope.MediaStore)

    @Test
    fun emptyLibraryAndEmptyScan_producesNothing() {
        val r = LibraryMerger.merge(emptyList(), emptyList(), ms, 0)
        assertTrue(r.inserts.isEmpty() && r.updates.isEmpty() && r.unavailableIds.isEmpty())
    }

    @Test
    fun validFiles_areInserted() {
        val r = LibraryMerger.merge(emptyList(), listOf(scanned("a"), scanned("b")), ms, 0)
        assertEquals(listOf("a", "b"), r.inserts.map { it.uri })
        assertTrue(r.inserts.all { it.available && !it.duplicate })
    }

    @Test
    fun missingMetadata_isIndexedWithFallbacks() {
        val r = LibraryMerger.merge(emptyList(), listOf(scanned("x", title = "Gravação 01", artist = null, album = null)), ms, 0)
        val track = r.inserts.single().copy(id = 1).toDomain()
        assertEquals("Gravação 01", track.title)
        assertEquals(Track.UNKNOWN_ARTIST, track.displayArtist)
        assertEquals(Track.UNKNOWN_ALBUM, track.displayAlbum)
    }

    @Test
    fun sameUriTwiceInScan_isIndexedOnce() {
        val r = LibraryMerger.merge(emptyList(), listOf(scanned("a"), scanned("a")), ms, 0)
        assertEquals(1, r.inserts.size)
    }

    @Test
    fun unchangedFile_producesNoUpdate() {
        val s = scanned("a")
        val r = LibraryMerger.merge(listOf(s.asEntity(1)), listOf(s), ms, 0)
        assertTrue(r.inserts.isEmpty())
        assertTrue(r.updates.isEmpty())
    }

    @Test
    fun changedTags_updateExistingRowKeepingId() {
        val old = scanned("a", title = "Old")
        val r = LibraryMerger.merge(listOf(old.asEntity(7)), listOf(scanned("a", title = "New")), ms, 0)
        assertEquals(7, r.updates.single().id)
        assertEquals("New", r.updates.single().title)
    }

    @Test
    fun removedFile_isMarkedUnavailable_notDeleted() {
        val r = LibraryMerger.merge(listOf(scanned("a").asEntity(1), scanned("b").asEntity(2)), listOf(scanned("a")), ms, 0)
        assertEquals(listOf(2L), r.unavailableIds)
        assertTrue(r.inserts.isEmpty())
    }

    @Test
    fun movedOrRenamedFile_isRelinkedKeepingId() {
        val before = scanned("content://media/1", title = "Song", size = 4242)
        val after = scanned("content://media/99", title = "Song", size = 4242)
        val r = LibraryMerger.merge(listOf(before.asEntity(5)), listOf(after), ms, 0)
        assertTrue(r.inserts.isEmpty())
        assertTrue(r.unavailableIds.isEmpty())
        assertEquals(1, r.relinked)
        assertEquals(5L, r.updates.single().id)
        assertEquals("content://media/99", r.updates.single().uri)
    }

    @Test
    fun unavailableFileThatReappears_isAvailableAgain() {
        val s = scanned("a")
        val r = LibraryMerger.merge(listOf(s.asEntity(1, available = false)), listOf(s), ms, 0)
        assertTrue(r.updates.single().available)
    }

    @Test
    fun scopeThatFailed_doesNotMarkItsFilesUnavailable() {
        val existing = listOf(scanned("a").asEntity(1))
        val r = LibraryMerger.merge(existing, emptyList(), completedScopes = emptySet(), now = 0)
        assertTrue(r.unavailableIds.isEmpty())
    }

    @Test
    fun revokedFolder_marksOnlyItsFilesUnavailable() {
        val folderTrack = scanned("doc://f/1", source = TrackSource.FOLDER, folderUri = "tree://f").asEntity(1)
        val msTrack = scanned("content://media/2").asEntity(2)
        val r = LibraryMerger.merge(listOf(folderTrack, msTrack), listOf(scanned("content://media/2")),
            setOf(ScanScope.MediaStore, ScanScope.Folder("tree://f")), 0)
        assertEquals(listOf(1L), r.unavailableIds)
    }

    @Test
    fun duplicates_preferMediaStoreCopy() {
        val msCopy = scanned("content://media/1", title = "Same", size = 777).asEntity(10)
        val folderCopy = scanned("doc://f/1", title = "Same", size = 777, source = TrackSource.FOLDER, folderUri = "tree://f").asEntity(3)
        val changes = LibraryMerger.computeDuplicateFlags(listOf(msCopy, folderCopy))
        assertEquals(1, changes.size)
        assertEquals(3L, changes.single().id)
        assertTrue(changes.single().duplicate)
    }

    @Test
    fun duplicateBecomesVisible_whenCanonicalCopyDisappears() {
        val msCopy = scanned("content://media/1", title = "Same", size = 777).asEntity(10, available = false)
        val folderCopy = scanned("doc://f/1", title = "Same", size = 777, source = TrackSource.FOLDER, folderUri = "tree://f").asEntity(3, duplicate = true)
        val changes = LibraryMerger.computeDuplicateFlags(listOf(msCopy, folderCopy))
        assertFalse(changes.single().duplicate)
    }

    @Test
    fun differentSongsWithSameTitle_areNotDuplicates() {
        val a = scanned("a", title = "Intro", size = 100).asEntity(1)
        val b = scanned("b", title = "Intro", size = 200).asEntity(2)
        assertTrue(LibraryMerger.computeDuplicateFlags(listOf(a, b)).isEmpty())
    }
}
