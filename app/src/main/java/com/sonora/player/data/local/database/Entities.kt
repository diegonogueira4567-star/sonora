package com.sonora.player.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["uri"], unique = true), Index("dedupKey"), Index("available")],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    /** "MEDIA_STORE" or "FOLDER". */
    val source: String,
    val mediaStoreId: Long?,
    /** Tree URI of the user-selected folder that contains this file (SAF tracks only). */
    val folderUri: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val bitrate: Int?,
    val displayName: String?,
    val relativePath: String?,
    /** Content fingerprint used to detect the same song reachable through several URIs. */
    val dedupKey: String,
    val available: Boolean = true,
    val duplicate: Boolean = false,
    val missingSince: Long? = null,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Playlist rows reference tracks by id without a foreign key on purpose: a track that becomes
 * temporarily unavailable must never remove it from the playlist.
 */
@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId", "position"]), Index("trackId")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val addedAt: Long,
)

@Entity(tableName = "history", indices = [Index("playedAt"), Index("trackId")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
)

@Entity(tableName = "track_stats")
data class TrackStatsEntity(
    @PrimaryKey val trackId: Long,
    val playCount: Int,
    val totalPlayedMs: Long,
    val lastPlayedAt: Long,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val accessible: Boolean = true,
    val lastScanAt: Long? = null,
)

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(name = "trackCount") val trackCount: Int,
    @ColumnInfo(name = "durationMs") val durationMs: Long,
    @ColumnInfo(name = "coverUri") val coverUri: String?,
)
