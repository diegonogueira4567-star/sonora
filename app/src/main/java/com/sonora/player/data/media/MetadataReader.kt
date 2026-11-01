package com.sonora.player.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build

data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val bitrate: Int? = null,
    val mimeType: String? = null,
    val sampleRate: Int? = null,
    val bitsPerSample: Int? = null,
    val readable: Boolean = true,
)

/** Extracts tags with the platform MediaMetadataRetriever. Never throws. */
class MetadataReader(private val context: Context) {

    fun read(uri: Uri, fileName: String? = null): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            fun key(k: Int) = ScannedTrack.cleanTag(retriever.extractMetadata(k))
            AudioMetadata(
                title = key(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = key(MediaMetadataRetriever.METADATA_KEY_GENRE),
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                trackNumber = ScannedTrack.parseTrackNumber(key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                year = ScannedTrack.parseYear(key(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: key(MediaMetadataRetriever.METADATA_KEY_DATE)),
                bitrate = key(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                mimeType = key(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) key(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() else null,
                bitsPerSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) key(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() else null,
            )
        } catch (e: Exception) {
            // Corrupt or unsupported file: index it with what we know so the user can still see it.
            AudioMetadata(title = fileName?.let { ScannedTrack.titleFromFileName(it) }, readable = false)
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun embeddedPicture(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
