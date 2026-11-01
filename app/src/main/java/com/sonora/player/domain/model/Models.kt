package com.sonora.player.domain.model

/** Where a track was discovered. */
enum class TrackSource { MEDIA_STORE, FOLDER }

/**
 * A playable audio file indexed in the local library. [uri] is the stable access handle
 * (MediaStore content URI or a SAF document URI); absolute paths are never relied upon.
 */
data class Track(
    val id: Long,
    val uri: String,
    val source: TrackSource,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val bitrate: Int?,
    val displayName: String?,
    val relativePath: String?,
    val folderUri: String?,
    val available: Boolean,
    val duplicate: Boolean,
) {
    val displayArtist: String get() = artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST
    val displayAlbum: String get() = album?.takeIf { it.isNotBlank() } ?: UNKNOWN_ALBUM
    val albumKey: String get() = albumKeyOf(album, albumArtist ?: artist)
    val artistKey: String get() = artistKeyOf(artist)

    companion object {
        const val UNKNOWN_ARTIST = "Artista desconhecido"
        const val UNKNOWN_ALBUM = "Álbum desconhecido"
        const val UNKNOWN_GENRE = "Gênero desconhecido"

        fun albumKeyOf(album: String?, artist: String?): String =
            TextNormalizer.key(album) + "\u0001" + TextNormalizer.key(artist)

        fun artistKeyOf(artist: String?): String = TextNormalizer.key(artist)
    }
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int?,
    val coverTrackUri: String,
)

data class Artist(
    val key: String,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val coverTrackUri: String,
)

data class Genre(
    val key: String,
    val name: String,
    val trackCount: Int,
    val coverTrackUri: String,
)

data class FolderGroup(
    val key: String,
    val name: String,
    val trackCount: Int,
    val coverTrackUri: String,
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
    val durationMs: Long,
    val coverTrackUri: String?,
)

/** One row of a playlist; [track] is null when the file is no longer indexed (kept, never deleted). */
data class PlaylistEntry(
    val entryId: Long,
    val position: Int,
    val trackId: Long,
    val track: Track?,
)

data class HistoryItem(
    val id: Long,
    val track: Track,
    val playedAt: Long,
)

data class TrackStat(
    val track: Track,
    val playCount: Int,
    val totalPlayedMs: Long,
    val lastPlayedAt: Long,
)

/** A user-chosen folder (Storage Access Framework tree). */
data class LibraryFolder(
    val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val accessible: Boolean,
    val trackCount: Int,
)

enum class SortField { TITLE, ARTIST, ALBUM, DURATION, DATE_ADDED }

data class SortOrder(val field: SortField = SortField.TITLE, val ascending: Boolean = true)

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val message: String, val found: Int = 0) : ScanState
    data class Done(val total: Int, val added: Int, val removed: Int, val warnings: List<String>) : ScanState
    data class Failed(val message: String) : ScanState
}

enum class RepeatMode { OFF, ONE, ALL }

enum class ThemeMode { SYSTEM, DARK, LIGHT }
