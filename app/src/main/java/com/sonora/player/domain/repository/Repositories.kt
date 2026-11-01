package com.sonora.player.domain.repository

import com.sonora.player.domain.model.HistoryItem
import com.sonora.player.domain.model.LibraryFolder
import com.sonora.player.domain.model.Playlist
import com.sonora.player.domain.model.PlaylistEntry
import com.sonora.player.domain.model.ScanState
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.model.TrackStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository {
    val scanState: StateFlow<ScanState>
    /** Available, non-hidden tracks (unsorted). */
    val visibleTracks: Flow<List<Track>>
    /** Every indexed row, including unavailable ones (used to resolve playlist/history references). */
    val tracksById: Flow<Map<Long, Track>>
    val favoriteIds: Flow<Set<Long>>
    val favoriteTracks: Flow<List<Track>>
    val history: Flow<List<HistoryItem>>
    val topTracks: Flow<List<TrackStat>>
    val totalPlays: Flow<Int>
    val totalPlayedMs: Flow<Long>
    val folders: Flow<List<LibraryFolder>>

    fun hasAudioPermission(): Boolean
    /**
     * Updates the index. With [force] = false the MediaStore part is skipped when Android reports no
     * change since the last scan, and picked folders are only walked when stale (see implementation).
     */
    suspend fun rescan(force: Boolean = false): ScanState
    fun requestRescan(force: Boolean = false)
    suspend fun addFolder(treeUri: String): Result<String>
    suspend fun removeFolder(treeUri: String)
    suspend fun setFavorite(trackId: Long, favorite: Boolean)
    suspend fun isFavorite(trackId: Long): Boolean
    /** Applies [com.sonora.player.domain.usecase.HistoryPolicy] for one finished/qualified listen. */
    suspend fun recordListen(trackId: Long, listenedMs: Long, countAsPlay: Boolean)
    suspend fun clearHistory()
    suspend fun clearStats()
    suspend fun getTracks(ids: List<Long>): List<Track>
    suspend fun getTrack(id: Long): Track?
    suspend fun findByUri(uri: String): Track?
}

data class ImportReport(val playlists: Int, val tracksMatched: Int, val tracksMissing: Int, val favorites: Int)

interface PlaylistRepository {
    val playlists: Flow<List<Playlist>>
    fun observePlaylist(id: Long): Flow<Playlist?>
    fun observeEntries(id: Long): Flow<List<PlaylistEntry>>
    suspend fun create(name: String, trackIds: List<Long> = emptyList()): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    /** Adds tracks not already in the playlist; returns how many were added. */
    suspend fun addTracks(id: Long, trackIds: List<Long>): Int
    suspend fun removeEntry(playlistId: Long, entryId: Long)
    suspend fun moveEntry(playlistId: Long, from: Int, to: Int)
    suspend fun exportBackup(): String
    suspend fun importBackup(json: String): ImportReport
}
