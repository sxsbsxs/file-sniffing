package com.sxsbsxs.filesniffing.data.extractor

import java.net.URI
import java.util.Locale

/** Conservative URL-shape hints for generic yt-dlp playlists. */
internal object YtDlpPlaylistDetector {
    private val playlistQueryKeys = setOf(
        "list", "playlist", "playlist_id", "album", "series", "collection", "mylist"
    )
    private val playlistPathMarkers = setOf(
        "playlist", "playlists", "album", "albums", "series", "mylist",
        "medialist", "collection", "collections", "sets"
    )

    fun isLikelyPlaylistUrl(rawUrl: String): Boolean {
        if (YouTubeUrlParser.isPlaylistUrl(rawUrl)) return true
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return false

        val queryKeys = uri.rawQuery.orEmpty()
            .split('&')
            .mapNotNull { part ->
                part.substringBefore('=', "").takeIf { it.isNotBlank() }
            }
        if (queryKeys.any { key ->
                runCatching { java.net.URLDecoder.decode(key, Charsets.UTF_8.name()) }
                    .getOrNull()
                    ?.lowercase(Locale.ROOT) in playlistQueryKeys
            }
        ) return true

        return uri.path.orEmpty()
            .split('/')
            .filter(String::isNotBlank)
            .any { it.lowercase(Locale.ROOT) in playlistPathMarkers }
    }
}
