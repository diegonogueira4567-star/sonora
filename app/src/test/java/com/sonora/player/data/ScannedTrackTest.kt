package com.sonora.player.data

import com.sonora.player.data.media.FolderAccessManager
import com.sonora.player.data.media.ScannedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedTrackTest {
    @Test fun titleFromFileName_stripsExtension() = assertEquals("01 - Faixa", ScannedTrack.titleFromFileName("01 - Faixa.mp3"))
    @Test fun titleFromFileName_handlesNoExtensionAndNull() {
        assertEquals("README", ScannedTrack.titleFromFileName("README"))
        assertEquals("Sem título", ScannedTrack.titleFromFileName(null))
    }
    @Test fun parseTrackNumber_variants() {
        assertEquals(3, ScannedTrack.parseTrackNumber("3/12"))
        assertEquals(5, ScannedTrack.parseTrackNumber("2005")) // disc 2, track 5 (MediaStore encoding)
        assertNull(ScannedTrack.parseTrackNumber("abc"))
        assertNull(ScannedTrack.parseTrackNumber("0"))
    }
    @Test fun cleanTag_removesPlaceholders() {
        assertNull(ScannedTrack.cleanTag("<unknown>"))
        assertNull(ScannedTrack.cleanTag("   "))
        assertEquals("Ana", ScannedTrack.cleanTag(" Ana "))
    }
    @Test fun parseYear_acceptsDates() {
        assertEquals(1999, ScannedTrack.parseYear("1999-05-01"))
        assertNull(ScannedTrack.parseYear("99"))
    }
    @Test fun dedupKey_ignoresCaseAccentsAndSpaces() {
        assertEquals(ScannedTrack.dedupKeyOf(10, 0, "Canção  Nova", "José"), ScannedTrack.dedupKeyOf(10, 5, "cancao nova", "JOSE"))
    }
    @Test fun audioDetection_byMimeOrExtension() {
        assertTrue(FolderAccessManager.isAudio("a.FLAC", null))
        assertTrue(FolderAccessManager.isAudio("noext", "audio/ogg"))
        assertFalse(FolderAccessManager.isAudio("cover.jpg", "image/jpeg"))
        assertFalse(FolderAccessManager.isAudio("letra.lrc", "application/octet-stream"))
    }
}
