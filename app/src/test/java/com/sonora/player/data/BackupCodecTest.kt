package com.sonora.player.data

import com.sonora.player.data.media.ScannedTrack
import com.sonora.player.data.repository.BackupCodec
import com.sonora.player.data.repository.LibraryBackup
import com.sonora.player.data.repository.PlaylistBackup
import com.sonora.player.data.repository.TrackMatcher
import com.sonora.player.data.repository.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val ref = TrackRef("content://media/external/audio/media/5", "Música", "Ana", null, 180_000, 4096)

    @Test fun roundTrip() {
        val backup = LibraryBackup(123, listOf(PlaylistBackup("Viagem", listOf(ref))), listOf(ref.copy(uri = null)))
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test fun rejectsForeignFiles() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("""{"format":"other"}""") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("""{"format":"sonora-backup","version":99}""") }
    }

    @Test fun matcher_byUriThenFingerprintThenTags() {
        val candidates = listOf(
            TrackMatcher.MatchCandidate(1, "content://media/external/audio/media/5", "x", "Outra", null, 0),
            TrackMatcher.MatchCandidate(2, "content://media/9", ScannedTrack.dedupKeyOf(4096, 0, "Música", "Ana"), "Música", "Ana", 180_000),
            TrackMatcher.MatchCandidate(3, "doc://3", "y", "Canção", "Zé", 200_500),
        )
        val m = TrackMatcher(candidates)
        assertEquals(1L, m.match(ref))
        assertEquals(2L, m.match(ref.copy(uri = "content://gone")))
        assertEquals(3L, m.match(TrackRef(null, "cancao", "ZE", null, 200_000, 1)))
        assertNull(m.match(TrackRef(null, "Inexistente", null, null, 1000, 1)))
    }
}
