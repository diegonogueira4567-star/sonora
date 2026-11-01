package com.sonora.player.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.sonora.player.data.local.database.FavoriteEntity
import com.sonora.player.data.local.database.FolderEntity
import com.sonora.player.data.local.database.HistoryEntity
import com.sonora.player.data.local.database.SonoraDatabase
import com.sonora.player.data.local.database.TrackStatsEntity
import com.sonora.player.data.local.preferences.SettingsStore
import com.sonora.player.data.media.ArtworkLoader
import com.sonora.player.data.media.FolderAccessManager
import com.sonora.player.data.media.LibraryMerger
import com.sonora.player.data.media.MediaStoreScanner
import com.sonora.player.data.media.MetadataReader
import com.sonora.player.data.media.ScanScope
import com.sonora.player.data.media.ScannedTrack
import com.sonora.player.domain.model.HistoryItem
import com.sonora.player.domain.model.LibraryFolder
import com.sonora.player.domain.model.ScanState
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.model.TrackSource
import com.sonora.player.domain.model.TrackStat
import com.sonora.player.domain.repository.LibraryRepository
import com.sonora.player.domain.usecase.HistoryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LibraryRepositoryImpl(
    private val context: Context,
    private val db: SonoraDatabase,
    private val settingsStore: SettingsStore,
    private val folderAccess: FolderAccessManager,
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataReader: MetadataReader,
    private val artworkLoader: ArtworkLoader,
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scanStateName: String = "library_scan",
) : LibraryRepository {

    private val trackDao = db.trackDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()
    private val statsDao = db.statsDao()
    private val folderDao = db.folderDao()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private val scanMutex = Mutex()
    private var pendingScan: Job? = null

    private val allTracks: Flow<List<Track>> = trackDao.observeAll()
        .map { rows -> com.sonora.player.PerfLog.measure("tracks.map", { _: List<Track> -> "rows=${rows.size}" }) { rows.map { it.toDomain() } } }
        .flowOn(Dispatchers.Default)
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val tracksById: Flow<Map<Long, Track>> = allTracks
        .map { list -> list.associateBy { it.id } }
        .flowOn(Dispatchers.Default)
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val visibleTracks: Flow<List<Track>> =
        combine(allTracks, settingsStore.settings.map { it.hideDuplicates to it.minDurationSec }.distinctUntilChanged()) { tracks, (hideDup, minSec) ->
            tracks.filter { t ->
                t.available && (!hideDup || !t.duplicate) && (t.durationMs <= 0 || t.durationMs >= minSec * 1000L)
            }
        }.flowOn(Dispatchers.Default).shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val favoriteIds: Flow<Set<Long>> = favoriteDao.observeAll()
        .map { list -> list.mapTo(LinkedHashSet()) { it.trackId } }
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val favoriteTracks: Flow<List<Track>> =
        combine(favoriteDao.observeAll(), tracksById) { favs, byId ->
            favs.mapNotNull { byId[it.trackId] }.filter { it.available }
        }

    override val history: Flow<List<HistoryItem>> =
        combine(historyDao.observeRecent(200), tracksById) { rows, byId ->
            rows.mapNotNull { row -> byId[row.trackId]?.let { HistoryItem(row.id, it, row.playedAt) } }
        }

    override val topTracks: Flow<List<TrackStat>> =
        combine(statsDao.observeTop(50), tracksById) { rows, byId ->
            rows.mapNotNull { r -> byId[r.trackId]?.let { TrackStat(it, r.playCount, r.totalPlayedMs, r.lastPlayedAt) } }
        }

    override val totalPlays: Flow<Int> = statsDao.observeTotalPlays()
    override val totalPlayedMs: Flow<Long> = statsDao.observeTotalPlayedMs()

    override val folders: Flow<List<LibraryFolder>> =
        combine(folderDao.observeAll(), allTracks) { folders, tracks ->
            val counts = tracks.filter { it.available && it.folderUri != null }.groupingBy { it.folderUri!! }.eachCount()
            folders.map { LibraryFolder(it.treeUri, it.displayName, it.addedAt, it.accessible, counts[it.treeUri] ?: 0) }
        }

    override fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private val scanPrefs by lazy { context.getSharedPreferences(scanStateName, Context.MODE_PRIVATE) }

    override fun requestRescan(force: Boolean) {
        if (pendingScan?.isActive == true) return
        pendingScan = appScope.launch { rescan(force) }
    }

    override suspend fun rescan(force: Boolean): ScanState = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                com.sonora.player.PerfLog.measure("scan", { r: ScanState -> (r as? ScanState.Done)?.let { "total=${it.total} added=${it.added} force=$force" } ?: "" }) { doRescan(force) }
            } catch (e: Exception) {
                Log.e(TAG, "Library scan failed", e)
                ScanState.Failed("Não foi possível atualizar a biblioteca (${e.javaClass.simpleName}).")
            }.also { _scanState.value = it }
        }
    }

    private suspend fun doRescan(force: Boolean): ScanState {
        val settings = settingsStore.settings.first()
        val permission = hasAudioPermission()
        val fingerprint = if (permission) {
            runCatching { mediaStoreScanner.fingerprint(settings.includeNonMusicAudio) }.getOrNull()
        } else {
            "denied"
        }
        // The fingerprint lives outside the database: never trust it if the index has no MediaStore
        // rows (database wiped or recreated), otherwise the library would stay empty.
        val mediaStoreUnchanged = !force && fingerprint != null &&
            fingerprint == scanPrefs.getString(KEY_FINGERPRINT, null) && trackDao.countBySource(TrackSource.MEDIA_STORE.name) > 0
        val folders = folderDao.getAll()
        val now = clock()
        val foldersToWalk = folders.filter { force || it.lastScanAt == null || now - it.lastScanAt > FOLDER_RESCAN_INTERVAL_MS }
        val revokedFolders = folders.filter { it !in foldersToWalk && !folderAccess.hasAccess(Uri.parse(it.treeUri)) }
        if (mediaStoreUnchanged && foldersToWalk.isEmpty() && revokedFolders.isEmpty()) {
            // Nothing changed on the device: avoid re-reading the whole index.
            return ScanState.Done(total = trackDao.countVisible(), added = 0, removed = 0, warnings = emptyList())
        }

        _scanState.value = ScanState.Scanning("Procurando músicas no aparelho…")
        val existing = trackDao.getAll()
        val scanned = ArrayList<ScannedTrack>()
        val completed = HashSet<ScanScope>()
        val warnings = ArrayList<String>()

        if (mediaStoreUnchanged) {
            // MediaStore part skipped: its rows are left untouched (scope not "completed").
        } else if (permission) {
            try {
                scanned += mediaStoreScanner.scan(settings.includeNonMusicAudio)
                completed += ScanScope.MediaStore
            } catch (e: SecurityException) {
                warnings += "Sem permissão para ler as músicas do aparelho."
                completed += ScanScope.MediaStore
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore query failed", e)
                warnings += "Falha ao consultar o índice de mídia do Android."
            }
        } else {
            // Without permission, previously indexed MediaStore files are not readable anymore.
            completed += ScanScope.MediaStore
        }
        _scanState.value = ScanState.Scanning("Lendo pastas selecionadas…", scanned.size)

        val cachedByUri = existing.associate { it.uri to it.toScanned() }
        for (folder in foldersToWalk + revokedFolders) {
            val treeUri = Uri.parse(folder.treeUri)
            if (!folderAccess.hasAccess(treeUri)) {
                folderDao.setScanResult(folder.treeUri, accessible = false, now = clock())
                warnings += "Acesso à pasta \"${folder.displayName}\" foi revogado. Selecione-a novamente."
                completed += ScanScope.Folder(folder.treeUri)
                continue
            }
            try {
                val base = scanned.size
                scanned += folderAccess.scanFolder(treeUri, metadataReader, cachedByUri) { n ->
                    if (n % 25 == 0) _scanState.value = ScanState.Scanning("Lendo \"${folder.displayName}\"…", base + n)
                }
                completed += ScanScope.Folder(folder.treeUri)
                folderDao.setScanResult(folder.treeUri, accessible = true, now = clock())
            } catch (e: SecurityException) {
                folderDao.setScanResult(folder.treeUri, accessible = false, now = clock())
                warnings += "Sem acesso à pasta \"${folder.displayName}\"."
                completed += ScanScope.Folder(folder.treeUri)
            } catch (e: Exception) {
                Log.w(TAG, "Folder scan failed", e)
                warnings += "Não foi possível ler a pasta \"${folder.displayName}\"."
            }
        }

        val merge = LibraryMerger.merge(existing, scanned, completed, clock())
        db.withTransaction {
            if (merge.inserts.isNotEmpty()) trackDao.insertAll(merge.inserts)
            if (merge.updates.isNotEmpty()) trackDao.updateAll(merge.updates)
            if (merge.unavailableIds.isNotEmpty()) {
                merge.unavailableIds.chunked(500).forEach { trackDao.markUnavailable(it, clock()) }
            }
            val flags = LibraryMerger.computeDuplicateFlags(trackDao.getAll())
            if (flags.isNotEmpty()) trackDao.updateAll(flags)
        }
        if (ScanScope.MediaStore in completed && fingerprint != null) {
            scanPrefs.edit().putString(KEY_FINGERPRINT, fingerprint).apply()
        }
        if (merge.inserts.isNotEmpty() || merge.updates.isNotEmpty()) artworkLoader.invalidate()
        return ScanState.Done(
            total = trackDao.countVisible(),
            added = merge.inserts.size,
            removed = merge.unavailableIds.size,
            warnings = warnings,
        )
    }

    override suspend fun addFolder(treeUri: String): Result<String> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(treeUri)
        if (!folderAccess.persistAccess(uri)) {
            return@withContext Result.failure(IllegalStateException("O Android não permitiu manter o acesso a esta pasta."))
        }
        val name = folderAccess.displayName(uri)
        folderDao.upsert(FolderEntity(treeUri = treeUri, displayName = name, addedAt = clock()))
        requestRescan()
        Result.success(name)
    }

    override suspend fun removeFolder(treeUri: String) = withContext(Dispatchers.IO) {
        folderDao.delete(treeUri)
        trackDao.markFolderUnavailable(treeUri, clock())
        folderAccess.releaseAccess(Uri.parse(treeUri))
        // Recompute duplicates so MediaStore copies of these files become visible again.
        db.withTransaction {
            val flags = LibraryMerger.computeDuplicateFlags(trackDao.getAll())
            if (flags.isNotEmpty()) trackDao.updateAll(flags)
        }
    }

    override suspend fun setFavorite(trackId: Long, favorite: Boolean) {
        if (favorite) favoriteDao.insert(FavoriteEntity(trackId, clock())) else favoriteDao.delete(trackId)
    }

    override suspend fun isFavorite(trackId: Long): Boolean = favoriteDao.isFavorite(trackId)

    override suspend fun recordListen(trackId: Long, listenedMs: Long, countAsPlay: Boolean) {
        if (trackId <= 0) return
        val now = clock()
        val settings = settingsStore.settings.first()
        db.withTransaction {
            statsDao.insertIgnore(TrackStatsEntity(trackId, 0, 0, now))
            statsDao.increment(trackId, if (countAsPlay) 1 else 0, listenedMs.coerceAtLeast(0), now)
            if (countAsPlay && settings.historyEnabled) {
                val latest = historyDao.latest()
                if (HistoryPolicy.shouldMergeWithLatest(latest?.trackId, latest?.playedAt, trackId, now)) {
                    historyDao.updatePlayedAt(latest!!.id, now)
                } else {
                    historyDao.insert(HistoryEntity(trackId = trackId, playedAt = now))
                }
            }
        }
    }

    override suspend fun clearHistory() = historyDao.clear()
    override suspend fun clearStats() = statsDao.clear()

    override suspend fun getTracks(ids: List<Long>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val byId = ids.distinct().chunked(900).flatMap { trackDao.getByIds(it) }.associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    override suspend fun getTrack(id: Long): Track? = trackDao.getById(id)?.toDomain()

    override suspend fun findByUri(uri: String): Track? = trackDao.getByUri(uri)?.toDomain()

    private companion object {
        const val TAG = "LibraryRepository"
        const val KEY_FINGERPRINT = "mediastore_fingerprint"
        /** Picked folders have no change notification; they are re-walked at most this often unless forced. */
        const val FOLDER_RESCAN_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
