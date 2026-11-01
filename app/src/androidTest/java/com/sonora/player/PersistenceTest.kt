package com.sonora.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonora.player.data.local.database.FolderEntity
import com.sonora.player.data.local.database.SonoraDatabase
import com.sonora.player.data.local.database.TrackEntity
import com.sonora.player.data.local.preferences.EqualizerSettings
import com.sonora.player.data.local.preferences.SettingsStore
import com.sonora.player.data.media.ArtworkLoader
import com.sonora.player.data.media.FolderAccessManager
import com.sonora.player.data.media.MediaStoreScanner
import com.sonora.player.data.media.MetadataReader
import com.sonora.player.data.repository.LibraryRepositoryImpl
import com.sonora.player.data.repository.PlaylistRepositoryImpl
import com.sonora.player.domain.model.ScanState
import com.sonora.player.domain.model.SortField
import com.sonora.player.domain.model.SortOrder
import com.sonora.player.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var db: SonoraDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private lateinit var library: LibraryRepositoryImpl
    private lateinit var playlists: PlaylistRepositoryImpl
    private var now = 1_000_000L

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(targetContext, SonoraDatabase::class.java).build()
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(targetContext.cacheDir, "t-${UUID.randomUUID()}.preferences_pb") })
        val reader = MetadataReader(targetContext)
        library = LibraryRepositoryImpl(
            targetContext, db, settings, FolderAccessManager(targetContext), MediaStoreScanner(targetContext), reader,
            ArtworkLoader(targetContext, reader), scope, clock = { now }, scanStateName = "test_scan_${UUID.randomUUID()}",
        )
        playlists = PlaylistRepositoryImpl(db, library, clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun insertTracks(n: Int): List<Long> = runBlocking {
        db.trackDao().insertAll((1..n).map {
            TrackEntity(uri = "content://test/$it", source = "MEDIA_STORE", mediaStoreId = it.toLong(), folderUri = null, title = "Faixa $it",
                artist = "Artista", album = "Álbum", albumArtist = null, genre = null, durationMs = 60_000, trackNumber = it, year = null,
                dateAddedSec = it.toLong(), dateModifiedSec = 0, sizeBytes = 1000L + it, mimeType = "audio/mpeg", bitrate = null,
                displayName = "f$it.mp3", relativePath = "Music/", dedupKey = "k$it")
        })
    }

    @Test
    fun playlist_createRenameAddReorderRemoveDelete() = runBlocking {
        val ids = insertTracks(4)
        val id = playlists.create("  Viagem  ")
        assertEquals("Viagem", playlists.playlists.first().single().name)
        assertEquals(3, playlists.addTracks(id, ids.take(3)))
        assertEquals("duplicated tracks are not added twice", 1, playlists.addTracks(id, ids))
        playlists.moveEntry(id, 0, 3)
        var entries = playlists.observeEntries(id).first()
        assertEquals(listOf(ids[1], ids[2], ids[3], ids[0]), entries.map { it.trackId })
        assertEquals(listOf(0, 1, 2, 3), entries.map { it.position })
        playlists.removeEntry(id, entries[1].entryId)
        entries = playlists.observeEntries(id).first()
        assertEquals(listOf(ids[1], ids[3], ids[0]), entries.map { it.trackId })
        assertEquals(listOf(0, 1, 2), entries.map { it.position })
        playlists.rename(id, "Estrada")
        val summary = playlists.playlists.first().single()
        assertEquals("Estrada", summary.name)
        assertEquals(3, summary.trackCount)
        assertEquals(180_000, summary.durationMs)
        playlists.delete(id)
        assertTrue(playlists.playlists.first().isEmpty())
        assertTrue(db.playlistDao().getEntries(id).isEmpty())
    }

    @Test
    fun playlist_blankNameRejected() = runBlocking {
        try {
            playlists.create("   ")
            fail("blank name accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun playlist_keepsTracksThatBecameUnavailable() = runBlocking {
        val ids = insertTracks(2)
        val id = playlists.create("P", ids)
        db.trackDao().markUnavailable(listOf(ids[0]), now)
        val entries = playlists.observeEntries(id).first()
        assertEquals(2, entries.size)
        assertFalse(entries[0].track!!.available)
        assertEquals(60_000, playlists.playlists.first().single().durationMs)
    }

    @Test
    fun favorites_addAndRemove() = runBlocking {
        val ids = insertTracks(2)
        library.setFavorite(ids[0], true)
        library.setFavorite(ids[0], true)
        // Shared flows replay their latest value; wait for the emission that reflects the write.
        assertEquals(setOf(ids[0]), withTimeout(5_000) { library.favoriteIds.first { it.isNotEmpty() } })
        assertEquals("Faixa 1", library.favoriteTracks.first { it.isNotEmpty() }.single().title)
        library.setFavorite(ids[0], false)
        assertTrue(withTimeout(5_000) { library.favoriteIds.first { it.isEmpty() } }.isEmpty())
    }

    @Test
    fun history_followsDocumentedRule() = runBlocking {
        val ids = insertTracks(2)
        library.recordListen(ids[0], 30_000, countAsPlay = true)
        now += 60_000
        library.recordListen(ids[0], 30_000, countAsPlay = true) // repeat within 30 min -> merged
        library.recordListen(ids[0], 5_000, countAsPlay = false) // extra time only
        assertEquals(1, db.historyDao().count())
        val stats = db.statsDao().get(ids[0])!!
        assertEquals(2, stats.playCount)
        assertEquals(65_000, stats.totalPlayedMs)
        now += 60_000
        library.recordListen(ids[1], 30_000, true)
        val history = withTimeout(5_000) { library.history.first { it.size == 2 } }
        assertEquals(listOf(ids[1], ids[0]), history.map { it.track.id })
        settings.setHistoryEnabled(false)
        library.recordListen(ids[0], 30_000, true)
        assertEquals(2, db.historyDao().count())
        library.clearHistory()
        assertEquals(0, db.historyDao().count())
    }

    @Test
    fun backup_exportImportRoundTrip() = runBlocking {
        val ids = insertTracks(3)
        playlists.create("Backup", listOf(ids[2], ids[0]))
        library.setFavorite(ids[1], true)
        val json = playlists.exportBackup()
        playlists.delete(playlists.playlists.first().single().id)
        library.setFavorite(ids[1], false)
        val report = playlists.importBackup(json)
        assertEquals(1, report.playlists)
        assertEquals(2, report.tracksMatched)
        val restored = playlists.playlists.first().single()
        assertEquals("Backup", restored.name)
        assertEquals(listOf(ids[2], ids[0]), playlists.observeEntries(restored.id).first().map { it.trackId })
        assertEquals(setOf(ids[1]), withTimeout(5_000) { library.favoriteIds.first { it == setOf(ids[1]) } })
    }

    @Test
    fun database_survivesReopen() {
        val name = "restart-${UUID.randomUUID()}.db"
        val first = Room.databaseBuilder(targetContext, SonoraDatabase::class.java, name).build()
        runBlocking {
            PlaylistRepositoryImpl(first, library).create("Persistente")
        }
        first.close()
        val second = Room.databaseBuilder(targetContext, SonoraDatabase::class.java, name).build()
        val names = runBlocking { second.playlistDao().getAll().map { it.name } }
        second.close()
        targetContext.deleteDatabase(name)
        assertEquals(listOf("Persistente"), names)
    }

    @Test
    fun settings_persistAcrossStoreInstances() {
        val file = File(targetContext.cacheDir, "s-${UUID.randomUUID()}.preferences_pb")
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val a = SettingsStore(PreferenceDataStoreFactory.create(scope = scopeA) { file })
        runBlocking {
            a.setThemeMode(ThemeMode.LIGHT)
            a.setAccentColor(0xFF4F8CFF)
            a.setDefaultSort(SortOrder(SortField.ARTIST, false))
            a.setFadeDurationMs(4000)
            a.setEqualizer(EqualizerSettings(true, -1, listOf(300, -200, 0), 500))
        }
        scopeA.cancel()
        Thread.sleep(200)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val b = SettingsStore(PreferenceDataStoreFactory.create(scope = scopeB) { file })
        val s = runBlocking { b.settings.first() }
        scopeB.cancel()
        assertEquals(ThemeMode.LIGHT, s.themeMode)
        assertEquals(0xFF4F8CFF, s.accentColor)
        assertEquals(SortOrder(SortField.ARTIST, false), s.defaultSort)
        assertEquals(4000, s.fadeDurationMs)
        assertEquals(listOf(300, -200, 0), s.equalizer.bandLevels)
        assertTrue(s.equalizer.enabled)
    }

    @Test
    fun folderWithoutPermission_isReportedAndMarkedInaccessible() = runBlocking {
        val fake = "content://com.android.externalstorage.documents/tree/primary%3ANaoAutorizada"
        db.folderDao().upsert(FolderEntity(fake, "NaoAutorizada", now))
        val result = library.rescan()
        assertTrue(result is ScanState.Done)
        assertTrue((result as ScanState.Done).warnings.any { "revogado" in it })
        assertFalse(db.folderDao().getAll().single().accessible)
    }

    @Test
    fun rescanWithoutChanges_isSkipped_butForcedRescanRuns() = runBlocking {
        grantAudioPermission()
        library.rescan(force = false) // first scan indexes everything
        val states = java.util.Collections.synchronizedList(mutableListOf<ScanState>())
        val job = scope.launch { library.scanState.collect { states += it } }
        kotlinx.coroutines.delay(100)
        states.clear()
        val skipped = library.rescan(force = false) as ScanState.Done
        assertEquals(0, skipped.added)
        assertTrue("unchanged library must not be re-read: $states", states.none { it is ScanState.Scanning })
        library.rescan(force = true)
        kotlinx.coroutines.delay(100)
        job.cancel()
        assertTrue("forced rescan must read the library", states.any { it is ScanState.Scanning })
    }

    /** Requires audio files on the device (see docs/RELATORIO.md) and the audio permission. */
    @Test
    fun mediaStoreScan_findsRealFiles_andRescanCreatesNoDuplicates() = runBlocking {
        grantAudioPermission()
        val first = library.rescan() as ScanState.Done
        assertTrue("expected audio files on the device, found ${first.total}", first.total >= 1)
        val tracks = library.visibleTracks.first()
        tracks.firstOrNull { it.title == "Amanhecer" }?.let {
            assertEquals("Sonora Demo", it.album)
            assertEquals("Banda Teste", it.artist)
            assertTrue(it.durationMs in 30_000..36_000)
        }
        assertNotNull(tracks.firstOrNull())
        val second = library.rescan() as ScanState.Done
        assertEquals(0, second.added)
        assertEquals(first.total, second.total)
        assertEquals(db.trackDao().getAll().size, db.trackDao().getAll().map { it.uri }.distinct().size)
    }
}
