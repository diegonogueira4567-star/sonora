package com.sonora.player.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleOrdersTest {
    @Test fun startingWith_isPermutationBeginningWithChosenItem() {
        repeat(50) { seed ->
            val order = ShuffleOrders.startingWith(20, 7, Random(seed))
            assertEquals(7, order.first())
            assertTrue(ShuffleOrders.isValidPermutation(order, 20))
        }
        assertEquals(0, ShuffleOrders.startingWith(0, 0).size)
    }

    @Test fun withInserted_playNextGoesRightAfterCurrent() {
        // Timeline A0 B1 C2 D3, shuffled play order: C2 A0 D3 B1, current = A (index 0)
        val old = intArrayOf(2, 0, 3, 1)
        // Insert 2 items at timeline index 1 (right after current in the original order)
        val new = ShuffleOrders.withInserted(old, insertIndex = 1, count = 2, afterIndex = 0)
        assertArrayEquals(intArrayOf(4, 0, 1, 2, 5, 3), new)
        assertTrue(ShuffleOrders.isValidPermutation(new, 6))
    }

    @Test fun withInserted_addToEndOfQueue() {
        val new = ShuffleOrders.withInserted(intArrayOf(2, 0, 1), insertIndex = 3, count = 1, afterIndex = null)
        assertArrayEquals(intArrayOf(2, 0, 1, 3), new)
    }

    @Test fun moved_reordersPlayOrder() {
        assertArrayEquals(intArrayOf(1, 2, 0), ShuffleOrders.moved(intArrayOf(0, 1, 2), 0, 2))
        assertArrayEquals(intArrayOf(0, 1, 2), ShuffleOrders.moved(intArrayOf(0, 1, 2), 0, 9))
    }

    @Test fun permutationValidation() {
        assertTrue(ShuffleOrders.isValidPermutation(intArrayOf(1, 0), 2))
        assertTrue(!ShuffleOrders.isValidPermutation(intArrayOf(1, 1), 2))
        assertTrue(!ShuffleOrders.isValidPermutation(intArrayOf(0), 2))
    }
}

class QueueSnapshotTest {
    @Test fun jsonRoundTrip() {
        val s = QueueSnapshot(listOf("1", "2", "ext:content://x/y"), 1, 12_345, true, 2, listOf(2, 0, 1))
        assertEquals(s, QueueSnapshot.fromJson(s.toJson()))
    }

    @Test fun invalidJson_returnsNull() = assertNull(QueueSnapshot.fromJson("{broken"))
}

class ListenTrackerTest {
    @Test fun countsOnceAfterThreshold_thenFlushesRemainder() {
        val t = ListenTracker()
        assertNull(t.start("1", 200_000))
        assertNull(t.onListened(29_000))
        val report = t.onListened(1_000)
        assertNotNull(report)
        assertTrue(report!!.countAsPlay)
        assertEquals(30_000, report.listenedMs)
        assertNull(t.onListened(10_000))
        val leftover = t.start("2", 100_000)
        assertEquals(ListenTracker.Report("1", 10_000, false), leftover)
    }

    @Test fun repeatOfSameTrack_countsAgain() {
        val t = ListenTracker()
        t.start("1", 10_000)
        assertTrue(t.onListened(5_000)!!.countAsPlay)
        t.start("1", 10_000)
        assertTrue(t.onListened(5_000)!!.countAsPlay)
    }

    @Test fun nothingPlaying_reportsNothing() {
        val t = ListenTracker()
        assertNull(t.onListened(1_000))
        assertNull(t.flush())
    }
}

class FadeCalculatorTest {
    @Test fun disabledFade_isFullVolume() = assertEquals(1f, FadeCalculator.volume(0, 100_000, 0, true, true))

    @Test fun fadesInAndOut() {
        assertEquals(0.5f, FadeCalculator.volume(2_000, 100_000, 4_000, fadeIn = true, fadeOut = true), 0.001f)
        assertEquals(1f, FadeCalculator.volume(50_000, 100_000, 4_000, true, true), 0.001f)
        assertEquals(0.25f, FadeCalculator.volume(99_000, 100_000, 4_000, true, true), 0.001f)
        assertEquals(1f, FadeCalculator.volume(99_000, 100_000, 4_000, true, fadeOut = false), 0.001f)
        assertEquals(1f, FadeCalculator.volume(500, 100_000, 4_000, fadeIn = false, fadeOut = true), 0.001f)
    }

    @Test fun shortTracksFadeAtMostAThird() {
        // 6 s track, 12 s fade requested -> 2 s effective
        assertEquals(0.5f, FadeCalculator.volume(1_000, 6_000, 12_000, true, true), 0.001f)
    }
}

class LyricsTest {
    @Test fun parsesSyncedLrc_withMultipleTimestampsAndOffset() {
        val lrc = """
            [ar:Artista]
            [offset:+500]
            [00:01.00][00:10.50]Refrão
            [00:05.25]Verso
        """.trimIndent()
        val l = LrcParser.parse(lrc, "t")!!
        assertTrue(l.synced)
        assertEquals(listOf(500L, 4750L, 10000L), l.lines.map { it.timeMs })
        assertEquals(listOf("Refrão", "Verso", "Refrão"), l.lines.map { it.text })
        assertEquals(-1, l.activeIndex(100))
        assertEquals(1, l.activeIndex(6_000))
        assertEquals(2, l.activeIndex(60_000))
    }

    @Test fun plainTextIsUnsynced() {
        val l = LrcParser.parse("Linha 1\r\nLinha 2", "t")!!
        assertTrue(!l.synced)
        assertEquals(2, l.lines.size)
        assertEquals(-1, l.activeIndex(1000))
    }

    @Test fun blankIsNull() = assertNull(LrcParser.parse("  \n ", "t"))

    @Test fun decodesUsltLatin1AndUtf8AndUtf16() {
        val latin = byteArrayOf(0) + "por".toByteArray() + byteArrayOf(0) + "Olá".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Olá", Id3Lyrics.decodeUslt(latin))
        val utf8 = byteArrayOf(3) + "eng".toByteArray() + "desc".toByteArray() + byteArrayOf(0) + "Canção".toByteArray(Charsets.UTF_8)
        assertEquals("Canção", Id3Lyrics.decodeUslt(utf8))
        val utf16 = byteArrayOf(1) + "eng".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) + "Letra".toByteArray(Charsets.UTF_16LE).let { byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it }
        assertEquals("Letra", Id3Lyrics.decodeUslt(utf16))
        assertNull(Id3Lyrics.decodeUslt(byteArrayOf(0, 1)))
    }
}
