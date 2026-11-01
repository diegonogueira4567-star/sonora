package com.sonora.player.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.sonora.player.domain.model.TrackSource

/** Reads the audio files Android has already indexed in shared storage. */
class MediaStoreScanner(private val context: Context) {

    /**
     * @throws SecurityException when the audio permission is not granted.
     */
    fun scan(includeNonMusic: Boolean): List<ScannedTrack> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(COL_ALBUM_ARTIST)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.BITRATE)
            }
        }.toTypedArray()

        val selection = buildString {
            if (!includeNonMusic) append("${MediaStore.Audio.Media.IS_MUSIC} != 0") else append("1 = 1")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) append(" AND ${MediaStore.Audio.Media.IS_PENDING} = 0")
        }

        val result = ArrayList<ScannedTrack>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                runCatching { result += c.toScannedTrack() }
            }
        }
        return result
    }

    /**
     * Cheap signature of the audio index: on Android 11+ the MediaStore generation numbers; on older
     * versions a single query of ids and modification dates. Used to skip full rescans when nothing
     * changed since the last scan.
     */
    fun fingerprint(includeNonMusic: Boolean): String {
        val prefix = "v1|$includeNonMusic|"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val volumes = MediaStore.getExternalVolumeNames(context).sorted()
            return prefix + MediaStore.getVersion(context) + "|" +
                volumes.joinToString(",") { "$it:${MediaStore.getGeneration(context, it)}" }
        }
        var count = 0L
        var idSum = 0L
        var maxModified = 0L
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATE_MODIFIED),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                count++
                idSum += c.getLong(0)
                maxModified = maxOf(maxModified, c.getLong(1))
            }
        }
        return prefix + "$count|$idSum|$maxModified"
    }

    private fun Cursor.toScannedTrack(): ScannedTrack {
        val id = long(MediaStore.Audio.Media._ID) ?: error("sem id")
        val displayName = string(MediaStore.Audio.Media.DISPLAY_NAME)
        val size = long(MediaStore.Audio.Media.SIZE) ?: 0L
        val title = ScannedTrack.cleanTag(string(MediaStore.Audio.Media.TITLE))
            ?: ScannedTrack.titleFromFileName(displayName)
        val artist = ScannedTrack.cleanTag(string(MediaStore.Audio.Media.ARTIST))
        return ScannedTrack(
            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
            source = TrackSource.MEDIA_STORE,
            mediaStoreId = id,
            title = title,
            artist = artist,
            album = ScannedTrack.cleanTag(string(MediaStore.Audio.Media.ALBUM)),
            albumArtist = ScannedTrack.cleanTag(string(COL_ALBUM_ARTIST)),
            genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ScannedTrack.cleanTag(string(MediaStore.Audio.Media.GENRE)) else null,
            durationMs = long(MediaStore.Audio.Media.DURATION) ?: 0L,
            trackNumber = ScannedTrack.parseTrackNumber(string(MediaStore.Audio.Media.TRACK)),
            year = (long(MediaStore.Audio.Media.YEAR)?.toInt())?.takeIf { it in 1000..9999 },
            dateAddedSec = long(MediaStore.Audio.Media.DATE_ADDED) ?: 0L,
            dateModifiedSec = long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0L,
            sizeBytes = size,
            mimeType = string(MediaStore.Audio.Media.MIME_TYPE),
            bitrate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) long(MediaStore.Audio.Media.BITRATE)?.toInt() else null,
            displayName = displayName,
            relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) string(MediaStore.Audio.Media.RELATIVE_PATH) else null,
        )
    }

    private fun Cursor.string(column: String): String? {
        val i = getColumnIndex(column)
        return if (i >= 0 && !isNull(i)) getString(i) else null
    }

    private fun Cursor.long(column: String): Long? {
        val i = getColumnIndex(column)
        return if (i >= 0 && !isNull(i)) getLong(i) else null
    }

    private companion object {
        /** MediaStore.Audio.AudioColumns.ALBUM_ARTIST exists on all supported versions as "album_artist". */
        const val COL_ALBUM_ARTIST = "album_artist"
    }
}
