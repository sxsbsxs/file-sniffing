package com.sxsbsxs.filesniffing.data.extractor

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Validates real YouTube video links without trusting lookalike host names. */
internal object YouTubeUrlParser {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractVideoId(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)

        if (host == "youtu.be" || host.endsWith(".youtu.be")) {
            return segments.firstOrNull()?.let(::validId)
        }
        val youtubeHost = host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        if (!youtubeHost) return null

        if (uri.path.equals("/watch", true)) {
            val rawId = uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.substringBefore('=') == "v" }
                ?.substringAfter('=', "")
                ?: return null
            return runCatching {
                URLDecoder.decode(rawId, StandardCharsets.UTF_8.name())
            }.getOrNull()?.let(::validId)
        }
        if (segments.size >= 2 && segments[0].lowercase(Locale.ROOT) in setOf("shorts", "live", "embed", "v")) {
            return validId(segments[1])
        }
        return null
    }

    private fun validId(candidate: String): String? =
        candidate.takeIf { videoIdPattern.matches(it) }
}
