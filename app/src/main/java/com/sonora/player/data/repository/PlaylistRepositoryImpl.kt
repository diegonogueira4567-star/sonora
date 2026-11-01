package com.sonora.player.data.repository

import androidx.room.withTransaction
import com.sonora.player.data.local.database.PlaylistEntity
import com.sonora.player.data.local.database.PlaylistEntryEntity
import com.sonora.player.data.local.database.SonoraDatabase
import com.sonora.player.data.local.database.TrackEntity
import com.sonora.player.domain.model.Playlist
import com.sonora.player.domain.model.PlaylistEntry
import com.sonora.player.domain.repository.ImportReport
import com.sonora.player.domain.repository.LibraryRepository
import com.sonora.player.domain.repository.PlaylistRepository
import com.sonora.player.domain.usecase.moved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlaylistRepositoryImpl(
    private val db: SonoraDatabase,
    private val library: LibraryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {

    private val dao = db.playlistDao()
    private val trackDao = db.trackDao()
    private val favoriteDao = db.favoriteDao()

    override val playlists: Flow<List<Playlist>> = dao.observeSummaries().map { rows ->
        rows.map { Playlist(it.id, it.name, it.createdAt, it.updatedAt, it.trackCount, it.durationMs, it.coverUri) }
    }

    override fun observePlaylist(id: Long): Flow<Playlist?> = playlists.map { list -> list.firstOrNull { it.id == id } }

    override fun observeEntries(id: Long): Flow<List<PlaylistEntry>> =
        combine(dao.observeEntries(id), library.tracksById) { entries, byId ->
            entries.map { PlaylistEntry(it.id, it.position, it.trackId, byId[it.trackId]) }
        }

    override suspend fun create(name: String, trackIds: List<Long>): Long {
        val clean = name.trim().ifEmpty { throw IllegalArgumentException("O nome da playlist não pode ficar vazio.") }
        val now = clock()
        return db.withTransaction {
            val id = dao.insertPlaylist(PlaylistEntity(name = clean.take(MAX_NAME), createdAt = now, updatedAt = now))
            if (trackIds.isNotEmpty()) {
                dao.insertEntries(trackIds.distinct().mapIndexed { i, t -> PlaylistEntryEntity(playlistId = id, trackId = t, position = i) })
            }
            id
        }
    }

    override suspend fun rename(id: Long, name: String) {
        val clean = name.trim().ifEmpty { throw IllegalArgumentException("O nome da playlist não pode ficar vazio.") }
        dao.rename(id, clean.take(MAX_NAME), clock())
    }

    override suspend fun delete(id: Long) = dao.deletePlaylist(id)

    override suspend fun addTracks(id: Long, trackIds: List<Long>): Int = db.withTransaction {
        val present = dao.getEntries(id).mapTo(HashSet()) { it.trackId }
        val toAdd = trackIds.distinct().filter { it !in present }
        if (toAdd.isNotEmpty()) {
            val start = dao.maxPosition(id) + 1
            dao.insertEntries(toAdd.mapIndexed { i, t -> PlaylistEntryEntity(playlistId = id, trackId = t, position = start + i) })
            dao.touch(id, clock())
        }
        toAdd.size
    }

    override suspend fun removeEntry(playlistId: Long, entryId: Long) = db.withTransaction {
        dao.deleteEntry(entryId)
        renumber(dao.getEntries(playlistId))
        dao.touch(playlistId, clock())
    }

    override suspend fun moveEntry(playlistId: Long, from: Int, to: Int) = db.withTransaction {
        val entries = dao.getEntries(playlistId)
        renumber(entries.moved(from, to))
        dao.touch(playlistId, clock())
    }

    private suspend fun renumber(entries: List<PlaylistEntryEntity>) {
        val changed = entries.mapIndexedNotNull { i, e -> if (e.position != i) e.copy(position = i) else null }
        if (changed.isNotEmpty()) dao.updateEntries(changed)
    }

    override suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val tracks = trackDao.getAll().associateBy { it.id }
        val playlists = dao.getAll().map { p ->
            PlaylistBackup(p.name, dao.getEntries(p.id).mapNotNull { tracks[it.trackId]?.toRef() })
        }
        val favorites = favoriteDao.observeAll().first().mapNotNull { tracks[it.trackId]?.toRef() }
        BackupCodec.encode(LibraryBackup(clock(), playlists, favorites))
    }

    override suspend fun importBackup(json: String): ImportReport = withContext(Dispatchers.IO) {
        val backup = BackupCodec.decode(json)
        val matcher = TrackMatcher(trackDao.getAll().map {
            TrackMatcher.MatchCandidate(it.id, it.uri, it.dedupKey, it.title, it.artist, it.durationMs)
        })
        val existingNames = dao.getAll().mapTo(HashSet()) { it.name.lowercase() }
        var matched = 0
        var missing = 0
        db.withTransaction {
            for (p in backup.playlists) {
                val ids = p.tracks.mapNotNull { ref -> matcher.match(ref).also { if (it == null) missing++ else matched++ } }
                var name = p.name
                var n = 2
                while (name.lowercase() in existingNames) name = "${p.name} ($n)".also { n++ }
                existingNames += name.lowercase()
                create(name, ids)
            }
            backup.favorites.mapNotNull { matcher.match(it) }.forEach { library.setFavorite(it, true) }
        }
        ImportReport(backup.playlists.size, matched, missing, backup.favorites.size)
    }

    private fun TrackEntity.toRef() = TrackRef(uri, title, artist, album, durationMs, sizeBytes)

    private companion object {
        const val MAX_NAME = 120
    }
}
