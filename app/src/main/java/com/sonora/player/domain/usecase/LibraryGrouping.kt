package com.sonora.player.domain.usecase

import com.sonora.player.domain.model.Album
import com.sonora.player.domain.model.Artist
import com.sonora.player.domain.model.FolderGroup
import com.sonora.player.domain.model.Genre
import com.sonora.player.domain.model.TextNormalizer
import com.sonora.player.domain.model.Track

/** Derives albums, artists, genres and folders from the flat track list. */
object LibraryGrouping {

    fun albums(tracks: List<Track>): List<Album> =
        tracks.groupBy { it.albumKey }.map { (key, group) ->
            val ordered = TrackSorter.albumOrder(group)
            Album(
                key = key,
                title = group.first().displayAlbum,
                artist = group.firstNotNullOfOrNull { it.albumArtist } ?: mostCommon(group.map { it.displayArtist }),
                trackCount = group.size,
                durationMs = group.sumOf { it.durationMs },
                year = group.mapNotNull { it.year }.maxOrNull(),
                coverTrackUri = ordered.first().uri,
            )
        }.sortedWith { a, b -> TrackSorter.compareText(a.title, b.title) }

    fun artists(tracks: List<Track>): List<Artist> =
        tracks.groupBy { it.artistKey }.map { (key, group) ->
            Artist(
                key = key,
                name = group.first().displayArtist,
                trackCount = group.size,
                albumCount = group.map { it.albumKey }.distinct().size,
                coverTrackUri = group.first().uri,
            )
        }.sortedWith(compareBy<Artist> { it.key.isEmpty() }.thenComparator { a, b -> TrackSorter.compareText(a.name, b.name) })

    fun genres(tracks: List<Track>): List<Genre> =
        tracks.groupBy { TextNormalizer.key(it.genre) }.map { (key, group) ->
            Genre(
                key = key,
                name = group.first().genre?.takeIf { it.isNotBlank() } ?: Track.UNKNOWN_GENRE,
                trackCount = group.size,
                coverTrackUri = group.first().uri,
            )
        }.sortedWith(compareBy<Genre> { it.key.isEmpty() }.thenComparator { a, b -> TrackSorter.compareText(a.name, b.name) })

    /** Folder of a track: MediaStore relative path, or the path inside a picked folder. */
    fun folderKey(track: Track): String =
        track.relativePath?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?.let { if (track.folderUri != null) "${track.folderUri}#$it" else it }
            ?: track.folderUri ?: "/"

    fun folderName(track: Track): String =
        track.relativePath?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Raiz"

    fun folders(tracks: List<Track>): List<FolderGroup> =
        tracks.groupBy { folderKey(it) }.map { (key, group) ->
            FolderGroup(key = key, name = folderName(group.first()), trackCount = group.size, coverTrackUri = group.first().uri)
        }.sortedWith { a, b -> TrackSorter.compareText(a.name, b.name) }

    private fun mostCommon(values: List<String>): String =
        values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: Track.UNKNOWN_ARTIST
}
