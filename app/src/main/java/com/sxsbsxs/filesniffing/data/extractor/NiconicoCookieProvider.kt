package com.sxsbsxs.filesniffing.data.extractor

import android.content.Context
import android.webkit.CookieManager
import com.sxsbsxs.filesniffing.NiconicoUrlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the Niconico session from the app's own WebView cookie jar.
 *
 * Only the user_session cookie is exported, into a private, short-lived Netscape file
 * for yt-dlp. The user's password is never read by the app or sent to a developer server.
 */
internal object NiconicoCookiePolicy {
    private const val SESSION_COOKIE_NAME = "user_session"

    fun userSessionValue(cookieHeader: String): String? {
        val value = cookieHeader
            .split(';')
            .asSequence()
            .map(String::trim)
            .firstNotNullOfOrNull { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) {
                    null
                } else if (item.substring(0, separator).trim() == SESSION_COOKIE_NAME) {
                    item.substring(separator + 1).trim()
                } else {
                    null
                }
            }
            ?.takeIf { it.isNotBlank() && !it.equals("deleted", ignoreCase = true) }
            ?: return null

        // A cookie value must never be allowed to create extra Netscape file columns/rows.
        if (value.any { it == '\\t' || it == '\\r' || it == '\\n' }) return null
        return value
    }

    fun hasUserSession(cookieHeader: String): Boolean = userSessionValue(cookieHeader) != null

    fun toNetscapeCookieFile(cookieHeader: String): String? {
        val session = userSessionValue(cookieHeader) ?: return null
        return buildString {
            append("# Netscape HTTP Cookie File\n")
            // The Niconico session is deliberately scoped to Niconico's own subdomains.
            append(".nicovideo.jp\\tTRUE\\t/\\tTRUE\\t0\\tuser_session\\t")
            append(session)
            append('\\n')
        }
    }
}

@Singleton
class NiconicoCookieProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasLoginSession(): Boolean = hasSessionInWebView()

    suspend fun createCookieFileIfAvailable(url: String): File? {
        if (!isNiconicoUrl(url)) return null

        // CookieManager is backed by WebView and is read on the main looper.
        val cookieHeader = withContext(Dispatchers.Main.immediate) {
            runCatching {
                CookieManager.getInstance().getCookie(NICONICO_COOKIE_URL).orEmpty()
            }.getOrDefault("")
        }
        val content = NiconicoCookiePolicy.toNetscapeCookieFile(cookieHeader) ?: return null

        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "yt-dlp-auth")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("无法创建 Niconico 临时授权目录")
            }
            File.createTempFile("niconico-", ".cookies", directory).apply {
                writeText(content, Charsets.UTF_8)
                // This file lives in app-private cache and is removed after each yt-dlp command.
                setReadable(false, false)
                setReadable(true, true)
                setWritable(false, false)
                setWritable(true, true)
            }
        }
    }

    private fun isNiconicoUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "nico.ms" ||
            host == "nicovideo.jp" ||
            host.endsWith(".nicovideo.jp")
    }

    companion object {
        private const val NICONICO_COOKIE_URL = "https://www.nicovideo.jp"

        fun hasSessionInWebView(): Boolean {
            val cookieHeader = runCatching {
                CookieManager.getInstance().getCookie(NICONICO_COOKIE_URL).orEmpty()
            }.getOrDefault("")
            return NiconicoCookiePolicy.hasUserSession(cookieHeader)
        }
    }
}
