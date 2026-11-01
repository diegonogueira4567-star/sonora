package com.sonora.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.sonora.player.data.local.database.FavoriteEntity
import com.sonora.player.data.local.database.FolderEntity
import com.sonora.player.data.local.database.HistoryEntity
import com.sonora.player.data.local.database.PlaylistEntity
import com.sonora.player.data.local.database.PlaylistEntryEntity
import com.sonora.player.data.local.database.PlaylistSummaryRow
import com.sonora.player.data.local.database.TrackEntity
import com.sonora.player.data.local.database.TrackStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    suspend fun getByUri(uri: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun updateAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET available = 0, missingSince = :since WHERE id IN (:ids)")
    suspend fun markUnavailable(ids: List<Long>, since: Long)

    @Query("UPDATE tracks SET available = 0, missingSince = :since WHERE folderUri = :folderUri")
    suspend fun markFolderUnavailable(folderUri: String, since: Long)

    @Query("SELECT COUNT(*) FROM tracks WHERE available = 1 AND duplicate = 0")
    suspend fun countVisible(): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE source = :source")
    suspend fun countBySource(source: String): Int
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt,
            COUNT(e.id) AS trackCount,
            COALESCE(SUM(CASE WHEN t.available = 1 THEN t.durationMs ELSE 0 END), 0) AS durationMs,
            (SELECT t2.uri FROM playlist_entries e2 JOIN tracks t2 ON t2.id = e2.trackId
                WHERE e2.playlistId = p.id AND t2.available = 1 ORDER BY e2.position LIMIT 1) AS coverUri
        FROM playlists p
        LEFT JOIN playlist_entries e ON e.playlistId = p.id
        LEFT JOIN tracks t ON t.id = e.trackId
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getEntries(playlistId: Long): List<PlaylistEntryEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Update
    suspend fun updateEntries(entries: List<PlaylistEntryEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 1")
    suspend fun latest(): HistoryEntity?

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("UPDATE history SET playedAt = :playedAt WHERE id = :id")
    suspend fun updatePlayedAt(id: Long, playedAt: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(stats: TrackStatsEntity)

    @Query(
        """UPDATE track_stats SET playCount = playCount + :plays,
            totalPlayedMs = totalPlayedMs + :playedMs, lastPlayedAt = :now WHERE trackId = :trackId""",
    )
    suspend fun increment(trackId: Long, plays: Int, playedMs: Long, now: Long)

    @Query("SELECT * FROM track_stats ORDER BY playCount DESC, totalPlayedMs DESC LIMIT :limit")
    fun observeTop(limit: Int): Flow<List<TrackStatsEntity>>

    @Query("SELECT COALESCE(SUM(totalPlayedMs), 0) FROM track_stats")
    fun observeTotalPlayedMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM track_stats")
    fun observeTotalPlays(): Flow<Int>

    @Query("SELECT * FROM track_stats WHERE trackId = :trackId")
    suspend fun get(trackId: Long): TrackStatsEntity?

    @Query("DELETE FROM track_stats")
    suspend fun clear()
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)

    @Query("UPDATE folders SET accessible = :accessible, lastScanAt = :now WHERE treeUri = :treeUri")
    suspend fun setScanResult(treeUri: String, accessible: Boolean, now: Long)
}
