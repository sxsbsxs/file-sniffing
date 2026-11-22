package com.sxsbsxs.filesniffing

import java.net.URI

/**
 * Strict Niconico watch-link parser. Only canonical watch pages and the official nico.ms short host
 * are accepted; lookalike hosts and live-stream URLs are not treated as uploaded videos.
 */
object NiconicoUrlParser {
    private val videoIdPattern = Regex("^[a-z]{2}[0-9]{1,12}$", RegexOption.IGNORE_CASE)
    private val allowedHosts = setOf(
        "nicovideo.jp",
        "www.nicovideo.jp",
        "m.nicovideo.jp",
        "nico.ms"
    )

    fun extractVideoId(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in allowedHosts) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val candidate = if (host == "nico.ms") {
            segments.firstOrNull()
        } else {
            val watchIndex = segments.indexOfFirst { it.equals("watch", ignoreCase = true) }
            segments.getOrNull(watchIndex + 1).takeIf { watchIndex >= 0 }
        } ?: return null

        return candidate.takeIf { videoIdPattern.matches(it) }?.lowercase()
    }

    fun canonicalWatchUrl(videoId: String): String? =
        videoId.takeIf { videoIdPattern.matches(it) }
            ?.lowercase()
            ?.let { "https://www.nicovideo.jp/watch/$it" }
}
