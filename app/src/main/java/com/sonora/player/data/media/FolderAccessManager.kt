package com.sonora.player.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.sonora.player.domain.model.TrackSource

/** A file found inside a user-selected folder tree. */
data class FolderFile(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val lastModifiedMs: Long,
    val relativePath: String,
)

/**
 * Storage Access Framework access to folders the user picked with ACTION_OPEN_DOCUMENT_TREE.
 * Only read access is requested and kept; the app never writes to these folders.
 */
class FolderAccessManager(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Persists read access so the folder stays usable after reboot (when the provider allows it). */
    fun persistAccess(treeUri: Uri): Boolean = try {
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Provider did not grant a persistable permission")
        false
    }

    fun releaseAccess(treeUri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun hasAccess(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    fun displayName(treeUri: Uri): String {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return runCatching {
            resolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':').ifBlank { "Pasta" }
    }

    /**
     * Walks the tree (sub-folders included) and returns audio files plus `.lrc` lyric sidecars.
     * @throws SecurityException if the permission was revoked.
     */
    fun listFiles(treeUri: Uri, maxFiles: Int = 50_000, maxDepth: Int = 24): List<FolderFile> {
        val out = ArrayList<FolderFile>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Triple<String, String, Int>>() // docId, relative path, depth
        queue.add(Triple(rootId, "", 0))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (queue.isNotEmpty() && out.size < maxFiles) {
            val (docId, path, depth) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = resolver.query(childrenUri, projection, null, null, null) ?: continue
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (name.startsWith(".")) continue // hidden files and folders (.thumbnails, .trash)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < maxDepth) queue.add(Triple(id, "$path$name/", depth + 1))
                        continue
                    }
                    if (isAudio(name, mime) || name.endsWith(".lrc", ignoreCase = true)) {
                        out += FolderFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            name = name,
                            mimeType = mime,
                            size = if (c.isNull(3)) 0 else c.getLong(3),
                            lastModifiedMs = if (c.isNull(4)) 0 else c.getLong(4),
                            relativePath = path,
                        )
                    }
                }
            }
        }
        return out
    }

    /** Reads metadata for every audio file of a folder, reusing cached rows whose file did not change. */
    fun scanFolder(
        treeUri: Uri,
        metadataReader: MetadataReader,
        cached: Map<String, ScannedTrack>,
        onProgress: (Int) -> Unit,
    ): List<ScannedTrack> {
        val files = listFiles(treeUri).filter { isAudio(it.name, it.mimeType) }
        return files.mapIndexed { index, file ->
            onProgress(index + 1)
            val previous = cached[file.uri.toString()]
            if (previous != null && previous.sizeBytes == file.size && previous.dateModifiedSec == file.lastModifiedMs / 1000) {
                previous
            } else {
                metadataReader.read(file.uri, file.name).let { meta ->
                    ScannedTrack(
                        uri = file.uri.toString(),
                        source = TrackSource.FOLDER,
                        folderUri = treeUri.toString(),
                        title = meta.title ?: ScannedTrack.titleFromFileName(file.name),
                        artist = meta.artist,
                        album = meta.album,
                        albumArtist = meta.albumArtist,
                        genre = meta.genre,
                        durationMs = meta.durationMs,
                        trackNumber = meta.trackNumber,
                        year = meta.year,
                        dateAddedSec = System.currentTimeMillis() / 1000,
                        dateModifiedSec = file.lastModifiedMs / 1000,
                        sizeBytes = file.size,
                        mimeType = meta.mimeType ?: file.mimeType,
                        bitrate = meta.bitrate,
                        displayName = file.name,
                        relativePath = file.relativePath,
                    )
                }
            }
        }
    }

    /**
     * Finds "<same name>.lrc" next to an audio document. Path-style document ids (used by the
     * device storage provider, e.g. "primary:Music/a.mp3") are resolved directly; other providers
     * fall back to listing only the parent folder. The whole tree is never walked here.
     */
    fun findLyricsSidecar(treeUri: Uri, audioUri: Uri): Uri? = runCatching {
        val docId = DocumentsContract.getDocumentId(audioUri)
        val dot = docId.lastIndexOf('.')
        if (dot > docId.lastIndexOf('/') && docId.contains(':')) {
            for (ext in listOf(".lrc", ".LRC")) {
                val candidate = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId.substring(0, dot) + ext)
                val exists = resolver.query(candidate, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                    ?.use { it.moveToFirst() } == true
                if (exists) return@runCatching candidate
            }
        }
        null
    }.getOrNull()

    companion object {
        private const val TAG = "FolderAccess"
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "amr", "3gp", "mka", "mp4", "m4b", "awb", "mid", "midi", "xmf", "ts",
        )

        fun isAudio(name: String, mime: String?): Boolean {
            if (mime != null && mime.startsWith("audio/")) return true
            return name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
        }
    }
}
