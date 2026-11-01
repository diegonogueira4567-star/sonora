package com.sonora.player.data.media

import com.sonora.player.data.local.database.TrackEntity
import com.sonora.player.domain.model.TrackSource

/** Identifies a discovery source that finished scanning successfully. */
sealed interface ScanScope {
    data object MediaStore : ScanScope
    data class Folder(val treeUri: String) : ScanScope
}

data class MergeResult(
    val inserts: List<TrackEntity>,
    val updates: List<TrackEntity>,
    val unavailableIds: List<Long>,
    /** Rows whose URI changed because the same file was found elsewhere (moved/renamed). */
    val relinked: Int,
)

/**
 * Pure merge of a fresh scan into the existing index. Rows are never deleted: missing files are
 * flagged unavailable so playlists, favourites and history keep their references.
 */
object LibraryMerger {

    fun merge(
        existing: List<TrackEntity>,
        scanned: List<ScannedTrack>,
        completedScopes: Set<ScanScope>,
        now: Long,
    ): MergeResult {
        val byUri = existing.associateBy { it.uri }
        // De-duplicate the scan itself: the same URI can only be indexed once.
        val uniqueScanned = scanned.distinctBy { it.uri }
        val scannedUris = uniqueScanned.mapTo(HashSet()) { it.uri }

        fun inCompletedScope(e: TrackEntity): Boolean = when (e.source) {
            TrackSource.MEDIA_STORE.name -> ScanScope.MediaStore in completedScopes
            else -> e.folderUri != null && ScanScope.Folder(e.folderUri) in completedScopes
        }

        // Rows that were not seen in a scope that completed: candidates for "moved/renamed".
        val missing = existing.filter { it.uri !in scannedUris && inCompletedScope(it) }
        val relinkPool: MutableMap<String, ArrayDeque<TrackEntity>> = HashMap()
        (missing + existing.filter { !it.available && it.uri !in scannedUris && !inCompletedScope(it) })
            .distinctBy { it.id }
            .sortedBy { it.id }
            .forEach { relinkPool.getOrPut(it.dedupKey) { ArrayDeque() }.addLast(it) }

        val inserts = mutableListOf<TrackEntity>()
        val updates = mutableListOf<TrackEntity>()
        val claimed = HashSet<Long>()
        var relinked = 0

        for (s in uniqueScanned) {
            val current = byUri[s.uri]
            if (current != null) {
                val refreshed = current.withScan(s)
                if (refreshed != current) updates += refreshed
                continue
            }
            val previous = relinkPool[s.dedupKey]?.removeFirstOrNull()
            if (previous != null) {
                claimed += previous.id
                relinked++
                updates += previous.withScan(s).copy(uri = s.uri)
            } else {
                inserts += s.toEntity()
            }
        }

        val unavailable = missing.filter { it.available && it.id !in claimed }.map { it.id }
        return MergeResult(inserts, updates, unavailable, relinked)
    }

    /**
     * Among available rows sharing a [TrackEntity.dedupKey], keeps one canonical row visible
     * (MediaStore first, then the oldest) and flags the others as duplicates.
     * Returns only rows whose flag changed.
     */
    fun computeDuplicateFlags(all: List<TrackEntity>): List<TrackEntity> {
        val changes = mutableListOf<TrackEntity>()
        val canonicalIds = all.asSequence()
            .filter { it.available }
            .groupBy { it.dedupKey }
            .values
            .map { group ->
                group.minWith(compareBy<TrackEntity>({ it.source != TrackSource.MEDIA_STORE.name }, { it.id })).id
            }
            .toHashSet()
        val duplicateKeys = all.filter { it.available }.groupingBy { it.dedupKey }.eachCount()
        for (t in all) {
            val shouldBeDuplicate = t.available && (duplicateKeys[t.dedupKey] ?: 0) > 1 && t.id !in canonicalIds
            if (t.duplicate != shouldBeDuplicate) changes += t.copy(duplicate = shouldBeDuplicate)
        }
        return changes
    }

    private fun TrackEntity.withScan(s: ScannedTrack): TrackEntity = copy(
        source = s.source.name,
        mediaStoreId = s.mediaStoreId,
        folderUri = s.folderUri,
        title = s.title,
        artist = s.artist,
        album = s.album,
        albumArtist = s.albumArtist,
        genre = s.genre,
        durationMs = s.durationMs,
        trackNumber = s.trackNumber,
        year = s.year,
        dateAddedSec = if (dateAddedSec > 0) dateAddedSec else s.dateAddedSec,
        dateModifiedSec = s.dateModifiedSec,
        sizeBytes = s.sizeBytes,
        mimeType = s.mimeType,
        bitrate = s.bitrate,
        displayName = s.displayName,
        relativePath = s.relativePath,
        dedupKey = s.dedupKey,
        available = true,
        missingSince = null,
    )

    fun ScannedTrack.toEntity(): TrackEntity = TrackEntity(
        uri = uri,
        source = source.name,
        mediaStoreId = mediaStoreId,
        folderUri = folderUri,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        genre = genre,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        dateAddedSec = dateAddedSec,
        dateModifiedSec = dateModifiedSec,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        bitrate = bitrate,
        displayName = displayName,
        relativePath = relativePath,
        dedupKey = dedupKey,
    )
}
