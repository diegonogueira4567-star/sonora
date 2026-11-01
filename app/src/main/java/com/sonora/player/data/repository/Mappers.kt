package com.sonora.player.data.repository

import com.sonora.player.data.local.database.TrackEntity
import com.sonora.player.data.media.ScannedTrack
import com.sonora.player.domain.model.Track
import com.sonora.player.domain.model.TrackSource

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    uri = uri,
    source = runCatching { TrackSource.valueOf(source) }.getOrDefault(TrackSource.MEDIA_STORE),
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    genre = genre,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    dateAddedSec = dateAddedSec,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    bitrate = bitrate,
    displayName = displayName,
    relativePath = relativePath,
    folderUri = folderUri,
    available = available,
    duplicate = duplicate,
)

fun TrackEntity.toScanned(): ScannedTrack = ScannedTrack(
    uri = uri,
    source = runCatching { TrackSource.valueOf(source) }.getOrDefault(TrackSource.MEDIA_STORE),
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
)
