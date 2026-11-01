package com.sonora.player

import com.sonora.player.data.local.database.TrackEntity
import com.sonora.player.data.media.ScannedTrack
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.model.TrackSource

fun scanned(
    uri: String,
    title: String = "Song $uri",
    artist: String? = "Artist",
    album: String? = "Album",
    size: Long = 1000,
    duration: Long = 180_000,
    source: TrackSource = TrackSource.MEDIA_STORE,
    folderUri: String? = null,
) = ScannedTrack(
    uri = uri, source = source, title = title, artist = artist, album = album, sizeBytes = size, durationMs = duration,
    folderUri = folderUri, dateAddedSec = 100,
)

fun ScannedTrack.asEntity(id: Long, available: Boolean = true, duplicate: Boolean = false) = TrackEntity(
    id = id, uri = uri, source = source.name, mediaStoreId = mediaStoreId, folderUri = folderUri, title = title, artist = artist,
    album = album, albumArtist = albumArtist, genre = genre, durationMs = durationMs, trackNumber = trackNumber, year = year,
    dateAddedSec = dateAddedSec, dateModifiedSec = dateModifiedSec, sizeBytes = sizeBytes, mimeType = mimeType, bitrate = bitrate,
    displayName = displayName, relativePath = relativePath, dedupKey = dedupKey, available = available, duplicate = duplicate,
)

fun track(
    id: Long,
    title: String,
    artist: String? = null,
    album: String? = null,
    duration: Long = 200_000,
    added: Long = 0,
    genre: String? = null,
    trackNumber: Int? = null,
    albumArtist: String? = null,
) = Track(
    id = id, uri = "content://media/external/audio/media/$id", source = TrackSource.MEDIA_STORE, title = title, artist = artist,
    album = album, albumArtist = albumArtist, genre = genre, durationMs = duration, trackNumber = trackNumber, year = null,
    dateAddedSec = added, sizeBytes = 1000 + id, mimeType = "audio/mpeg", bitrate = null, displayName = "$title.mp3",
    relativePath = "Music/", folderUri = null, available = true, duplicate = false,
)
