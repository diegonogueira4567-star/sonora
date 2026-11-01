package com.sonora.player.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val FOLDERS = "settings/folders"
    const val EQUALIZER = "settings/equalizer"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val RECENT = "recent"
    const val STATS = "stats"
    const val ALL_TRACKS = "tracks"

    const val ALBUM = "album/{key}"
    const val ARTIST = "artist/{key}"
    const val GENRE = "genre/{key}"
    const val FOLDER = "folder/{key}"
    const val PLAYLIST = "playlist/{id}"

    fun album(key: String) = "album/${Uri.encode(key)}"
    fun artist(key: String) = "artist/${Uri.encode(key.ifEmpty { EMPTY_KEY })}"
    fun genre(key: String) = "genre/${Uri.encode(key.ifEmpty { EMPTY_KEY })}"
    fun folder(key: String) = "folder/${Uri.encode(key)}"
    fun playlist(id: Long) = "playlist/$id"

    /** Navigation does not accept empty path segments; "unknown" groups use this marker. */
    const val EMPTY_KEY = "_"
    fun decodeKey(raw: String?): String = (raw ?: "").let { if (it == EMPTY_KEY) "" else it }
}
