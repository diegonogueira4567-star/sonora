package com.sonora.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.sonora.player.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
/** Converts library tracks into Media3 items. mediaId = track id, or "ext:<uri>" for files opened from other apps. */
object MediaItems {
    private const val EXTERNAL_PREFIX = "ext:"

    fun fromTrack(track: Track): MediaItem {
        val uri = Uri.parse(track.uri)
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.displayArtist)
                    .setAlbumTitle(track.album)
                    .setAlbumArtist(track.albumArtist)
                    .setGenre(track.genre)
                    .setTrackNumber(track.trackNumber)
                    .setReleaseYear(track.year)
                    .setDurationMs(track.durationMs.takeIf { it > 0 })
                    .setArtworkUri(uri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }

    fun fromExternalUri(uri: Uri, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(EXTERNAL_PREFIX + uri)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtworkUri(uri).setIsPlayable(true).setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build(),
        )
        .build()

    fun trackIdOf(mediaId: String?): Long? = mediaId?.toLongOrNull()

    fun externalUriOf(mediaId: String?): Uri? =
        mediaId?.takeIf { it.startsWith(EXTERNAL_PREFIX) }?.removePrefix(EXTERNAL_PREFIX)?.let(Uri::parse)
}

/**
 * Shuffle-order arithmetic. The timeline keeps the ORIGINAL order; the shuffle order is a
 * permutation of timeline indexes that defines the EFFECTIVE play order.
 */
object ShuffleOrders {

    /** Random permutation whose first element is [first] (the item that starts playing). */
    fun startingWith(count: Int, first: Int, random: Random = Random.Default): IntArray {
        if (count <= 0) return IntArray(0)
        val rest = (0 until count).filter { it != first }.shuffled(random)
        return if (first in 0 until count) (listOf(first) + rest).toIntArray() else rest.toIntArray()
    }

    /**
     * After inserting [count] items at timeline [insertIndex], returns the shuffle order with the
     * new items placed right after [afterIndex] (old timeline index) in play order, or at the end.
     */
    fun withInserted(oldOrder: IntArray, insertIndex: Int, count: Int, afterIndex: Int?): IntArray {
        val shifted = oldOrder.map { if (it >= insertIndex) it + count else it }.toMutableList()
        val newItems = (insertIndex until insertIndex + count).toList()
        val anchor = afterIndex?.let { if (it >= insertIndex) it + count else it }
        val position = anchor?.let { shifted.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: shifted.size
        shifted.addAll(position, newItems)
        return shifted.toIntArray()
    }

    /** Moves an element of the play order. */
    fun moved(order: IntArray, from: Int, to: Int): IntArray {
        if (from !in order.indices || to !in order.indices) return order
        val list = order.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list.toIntArray()
    }

    /** Current play order of a timeline (identity when shuffle is off). */
    fun playOrder(timeline: Timeline, shuffle: Boolean): IntArray {
        if (timeline.isEmpty) return IntArray(0)
        val out = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            out += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        return out.toIntArray()
    }

    fun isValidPermutation(order: IntArray, count: Int): Boolean =
        order.size == count && order.sorted() == (0 until count).toList()
}

/** Snapshot of the queue persisted between app sessions. */
data class QueueSnapshot(
    val mediaIds: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val shuffleOrder: List<Int>,
) {
    fun toJson(): String = JSONObject().apply {
        put("mediaIds", JSONArray(mediaIds))
        put("index", index)
        put("positionMs", positionMs)
        put("shuffle", shuffle)
        put("repeatMode", repeatMode)
        put("shuffleOrder", JSONArray(shuffleOrder))
    }.toString()

    companion object {
        fun fromJson(json: String): QueueSnapshot? = runCatching {
            val o = JSONObject(json)
            val ids = o.getJSONArray("mediaIds").let { a -> List(a.length()) { a.getString(it) } }
            val order = o.optJSONArray("shuffleOrder")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList()
            QueueSnapshot(ids, o.optInt("index"), o.optLong("positionMs"), o.optBoolean("shuffle"), o.optInt("repeatMode"), order)
        }.getOrNull()
    }
}

/** Stores the queue in the app's private files directory (no audio is copied). */
class QueueStateStore(private val file: File) {
    fun save(snapshot: QueueSnapshot) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(snapshot.toJson())
            tmp.renameTo(file)
        }
    }

    fun load(): QueueSnapshot? = runCatching { if (file.exists()) QueueSnapshot.fromJson(file.readText()) else null }.getOrNull()

    fun clear() {
        file.delete()
    }
}
