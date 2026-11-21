package com.sxsbsxs.filesniffing

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Converts shared links and supported app deep links into browser-loadable URLs.
 *
 * Bilibili share links may wrap a normal mobile-web URL in the "open_app_url"
 * query parameter. The wrapper is URL-encoded, so it must be decoded before a
 * WebView or the HTTP downloader receives it.
 */
internal object SharedLinkNormalizer {
    private val candidatePattern =
        Regex("""(?i)(?:https?://|bilibili://)[^\s<>"']+""")

    fun extractCandidate(raw: String): String? =
        candidatePattern.find(raw)?.value?.trimEnd(
            ')', ']', '}', ',', '.', '!', '?', ';', '，', '。', '；', '！', '？'
        )

    fun normalize(raw: String): String? {
        val candidate = extractCandidate(raw) ?: raw.trim()
            .trimEnd(')', ']', '}', ',', '.', '!', '?', ';')
        if (candidate.isBlank()) return null

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> candidate.takeIf { !uri.host.isNullOrBlank() }
            "bilibili" -> normalizeBilibili(uri)
            else -> null
        }
    }

    private fun normalizeBilibili(uri: URI): String? {
        val embedded = queryParameter(uri, "open_app_url")
        if (!embedded.isNullOrBlank()) {
            var decoded = embedded.trim()
            repeat(2) {
                val possibleUrl = extractCandidate(decoded) ?: decoded.trim()
                if (isHttpUrl(possibleUrl)) return possibleUrl
                decoded = runCatching {
                    URLDecoder.decode(decoded, StandardCharsets.UTF_8.name())
                }.getOrDefault(decoded)
            }
        }

        // Older/simple share intents may omit open_app_url. Rebuild the canonical
        // Bilibili web URL from bilibili://video/<id>.
        if (uri.host.equals("video", ignoreCase = true)) {
            val videoId = uri.rawPath.orEmpty()
                .trim('/')
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
                ?: return null
            val pathId = when {
                videoId.startsWith("BV", ignoreCase = true) -> videoId
                videoId.all(Char::isDigit) -> "av$videoId"
                else -> return null
            }
            return "https://www.bilibili.com/video/$pathId/"
        }
        return null
    }

    private fun queryParameter(uri: URI, name: String): String? {
        val rawValue = uri.rawQuery
            ?.split('&')
            ?.firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?: return null
        return runCatching {
            URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }
}
