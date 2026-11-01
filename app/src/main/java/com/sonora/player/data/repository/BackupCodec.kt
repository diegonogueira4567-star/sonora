package com.sonora.player.data.repository

import com.sonora.player.data.media.ScannedTrack
import com.sonora.player.domain.model.TextNormalizer
import org.json.JSONArray
import org.json.JSONObject

/** Portable reference to a track: enough to find it again on this or another device. */
data class TrackRef(
    val uri: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
)

data class PlaylistBackup(val name: String, val tracks: List<TrackRef>)

data class LibraryBackup(
    val exportedAt: Long,
    val playlists: List<PlaylistBackup>,
    val favorites: List<TrackRef>,
)

/** JSON format for playlist/favourite backups. Contains no audio data. */
object BackupCodec {
    const val FORMAT = "sonora-backup"
    const val VERSION = 1

    fun encode(backup: LibraryBackup): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", backup.exportedAt)
        put("playlists", JSONArray().apply {
            backup.playlists.forEach { p ->
                put(JSONObject().apply {
                    put("name", p.name)
                    put("tracks", JSONArray().apply { p.tracks.forEach { put(it.toJson()) } })
                })
            }
        })
        put("favorites", JSONArray().apply { backup.favorites.forEach { put(it.toJson()) } })
    }.toString(2)

    /** @throws IllegalArgumentException for files that are not Sonora backups. */
    fun decode(json: String): LibraryBackup {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo de backup inválido.")
        }
        require(root.optString("format") == FORMAT) { "Este arquivo não é um backup do Sonora." }
        require(root.optInt("version", 0) in 1..VERSION) { "Versão de backup não suportada." }
        val playlists = root.optJSONArray("playlists").objects().map { p ->
            PlaylistBackup(
                name = p.optString("name").ifBlank { "Playlist importada" },
                tracks = p.optJSONArray("tracks").objects().map { it.toTrackRef() },
            )
        }
        val favorites = root.optJSONArray("favorites").objects().map { it.toTrackRef() }
        return LibraryBackup(root.optLong("exportedAt"), playlists, favorites)
    }

    private fun TrackRef.toJson() = JSONObject().apply {
        put("uri", uri ?: JSONObject.NULL)
        put("title", title)
        put("artist", artist ?: JSONObject.NULL)
        put("album", album ?: JSONObject.NULL)
        put("durationMs", durationMs)
        put("sizeBytes", sizeBytes)
    }

    private fun JSONObject.toTrackRef() = TrackRef(
        uri = optStringOrNull("uri"),
        title = optString("title"),
        artist = optStringOrNull("artist"),
        album = optStringOrNull("album"),
        durationMs = optLong("durationMs"),
        sizeBytes = optLong("sizeBytes"),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Finds the local track that corresponds to a [TrackRef]: same URI, same file fingerprint, or same tags. */
class TrackMatcher(candidates: List<MatchCandidate>) {
    data class MatchCandidate(val id: Long, val uri: String, val dedupKey: String, val title: String, val artist: String?, val durationMs: Long)

    private val byUri = candidates.associateBy { it.uri }
    private val byKey = candidates.groupBy { it.dedupKey }
    private val byTags = candidates.groupBy { tagKey(it.title, it.artist) }

    fun match(ref: TrackRef): Long? {
        ref.uri?.let { byUri[it] }?.let { return it.id }
        byKey[ScannedTrack.dedupKeyOf(ref.sizeBytes, ref.durationMs, ref.title, ref.artist)]?.firstOrNull()?.let { return it.id }
        val sameTags = byTags[tagKey(ref.title, ref.artist)] ?: return null
        return sameTags.minByOrNull { kotlin.math.abs(it.durationMs - ref.durationMs) }
            ?.takeIf { ref.durationMs <= 0 || kotlin.math.abs(it.durationMs - ref.durationMs) < 3_000 }?.id
    }

    private fun tagKey(title: String, artist: String?) = TextNormalizer.normalize(title) + "|" + TextNormalizer.normalize(artist)
}
