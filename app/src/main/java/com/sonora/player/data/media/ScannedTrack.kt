package com.sonora.player.data.media

import com.sonora.player.domain.model.TextNormalizer
import com.sonora.player.domain.model.TrackSource

/** Raw result of discovering one audio file, before it is merged into the index. */
data class ScannedTrack(
    val uri: String,
    val source: TrackSource,
    val mediaStoreId: Long? = null,
    val folderUri: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val dateAddedSec: Long = 0,
    val dateModifiedSec: Long = 0,
    val sizeBytes: Long = 0,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val displayName: String? = null,
    val relativePath: String? = null,
) {
    val dedupKey: String get() = dedupKeyOf(sizeBytes, durationMs, title, artist)

    companion object {
        /**
         * Same song reachable through two URIs (MediaStore + chosen folder) or after a move/rename
         * keeps size and tags. Duration is only used when the size is unknown because
         * different extractors round it differently.
         */
        fun dedupKeyOf(sizeBytes: Long, durationMs: Long, title: String?, artist: String?): String {
            val sizePart = if (sizeBytes > 0) "s$sizeBytes" else "d${durationMs / 1000}"
            return "$sizePart|${TextNormalizer.normalize(title)}|${TextNormalizer.normalize(artist)}"
        }

        /** Strips the extension from a file name to use it as a fallback title. */
        fun titleFromFileName(name: String?): String {
            if (name.isNullOrBlank()) return "Sem título"
            val dot = name.lastIndexOf('.')
            return (if (dot > 0) name.substring(0, dot) else name).trim().ifEmpty { name }
        }

        /** Cleans tag values: blank and placeholder strings become null. */
        fun cleanTag(value: String?): String? {
            val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return if (v.equals("<unknown>", ignoreCase = true)) null else v
        }

        /** "3/12" or "3" -> 3 ; MediaStore encodes disc*1000+track. */
        fun parseTrackNumber(value: String?): Int? {
            val first = value?.substringBefore('/')?.trim()?.toIntOrNull() ?: return null
            return (if (first >= 1000) first % 1000 else first).takeIf { it > 0 }
        }

        fun parseYear(value: String?): Int? =
            value?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1000..9999 }
    }
}
