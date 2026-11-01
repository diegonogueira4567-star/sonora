package com.sonora.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String) {
    /** Index of the line being sung at [positionMs] (synced lyrics only). */
    fun activeIndex(positionMs: Long): Int {
        if (!synced) return -1
        var result = -1
        for (i in lines.indices) {
            val t = lines[i].timeMs ?: continue
            if (t <= positionMs) result = i else break
        }
        return result
    }
}

/** Parses LRC text. Lines without timestamps make the result unsynced. */
object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("^\\[(ar|ti|al|au|by|re|ve|length|offset|la|#):.*]$", RegexOption.IGNORE_CASE)

    fun parse(text: String, source: String): Lyrics? {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (clean.isEmpty()) return null
        val offset = offsetTag.find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()
        for (raw in clean.lines()) {
            val line = raw.trim()
            if (metaTag.matches(line)) continue
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) {
                plain += LyricLine(null, line)
                continue
            }
            val lyric = line.substring(tags.last().range.last + 1).trim()
            for (tag in tags) {
                val (m, s, frac) = tag.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                val ms = m.toLong() * 60_000 + s.toLong() * 1000 + fracMs - offset
                timed += LyricLine(ms.coerceAtLeast(0), lyric)
            }
        }
        return if (timed.isNotEmpty()) {
            Lyrics(timed.sortedBy { it.timeMs }, synced = true, source = source)
        } else {
            Lyrics(plain.dropWhile { it.text.isEmpty() }, synced = false, source = source).takeIf { it.lines.isNotEmpty() }
        }
    }
}

/** Decodes ID3v2 USLT (unsynchronised lyrics) frame payloads. */
object Id3Lyrics {
    fun decodeUslt(data: ByteArray): String? {
        if (data.size < 4) return null
        val encoding = data[0].toInt()
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val wide = encoding == 1 || encoding == 2
        // Skip 3-byte language code and the null-terminated content descriptor.
        var i = 4
        if (wide) {
            while (i + 1 < data.size && !(data[i].toInt() == 0 && data[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < data.size && data[i].toInt() != 0) i++
            i += 1
        }
        if (i >= data.size) return null
        var text = String(data, i, data.size - i, charset).trimEnd('\u0000').trim()
        if (encoding == 1 && text.startsWith("\uFEFF")) text = text.substring(1)
        return text.ifEmpty { null }
    }
}

/** Extracts embedded lyrics from the container metadata ExoPlayer already parsed. */
@OptIn(UnstableApi::class)
object EmbeddedLyricsExtractor {
    fun fromTracks(tracks: Tracks): String? {
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                group.getTrackFormat(i).metadata?.let { fromMetadata(it) }?.let { return it }
            }
        }
        return null
    }

    fun fromMetadata(metadata: Metadata): String? {
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is BinaryFrame -> if (entry.id == "USLT") Id3Lyrics.decodeUslt(entry.data)?.let { return it }
                is TextInformationFrame -> if (entry.description?.uppercase() in LYRIC_KEYS) entry.values.firstOrNull()?.let { return it }
                is VorbisComment -> if (entry.key.uppercase() in LYRIC_KEYS) return entry.value
            }
        }
        return null
    }

    private val LYRIC_KEYS = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "LYRIC")
}

/** Lyrics of the current item, published by the playback service (same process). */
class LyricsRepository {
    sealed interface State {
        data object Unknown : State
        data class Loaded(val mediaId: String, val lyrics: Lyrics?) : State
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    fun publish(mediaId: String, lyrics: Lyrics?) {
        _state.value = State.Loaded(mediaId, lyrics)
    }
}
