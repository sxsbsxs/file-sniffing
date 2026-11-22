package com.sxsbsxs.filesniffing.data.extractor

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seal-style bridge to the upstream yt-dlp Android runtime.
 *
 * Supported-site cookies are exported from the WebView cookie database to a private,
 * short-lived Netscape file. The file is deleted after each process and never returned
 * to UI state, Room, diagnostics or logs.
 */
@Singleton
class YtDlpRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun inspectPlaylist(url: String): YtDlpPlaylistInfo = withContext(Dispatchers.IO) {
        require(YouTubeUrlParser.extractPlaylistId(url) != null) {
            "请输入有效的 YouTube 播放列表链接"
        }
        ensureInitialized()
        val cookies = createTemporaryCookieFile()
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--skip-download")
                addOption("--playlist-end", "200")
                addOption("--socket-timeout", "20")
                cookies?.let { addOption("--cookies", it.absolutePath) }
            }
            val response = YoutubeDL.getInstance().execute(request)
            YtDlpInfoJsonParser.parsePlaylist(response.out)
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        } finally {
            cookies?.delete()
        }
    }

    suspend fun inspect(url: String): YtDlpVideoInfo = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "请输入有效的视频链接"
        }
        ensureInitialized()
        val cookies = createTemporaryCookieFile()
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--skip-download")
                addOption("--socket-timeout", "20")
                cookies?.let { addOption("--cookies", it.absolutePath) }
            }
            val response = YoutubeDL.getInstance().execute(request)
            YtDlpInfoJsonParser.parse(response.out)
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        } finally {
            cookies?.delete()
        }
    }

    suspend fun download(
        url: String,
        outputDirectory: File,
        formatSelector: String = "bv*+ba/b",
        audioOnly: Boolean = false,
        processId: String = UUID.randomUUID().toString(),
        onProgress: ((Float, Long, String) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "请输入有效的视频链接"
        }
        require(formatSelector.isNotBlank() && formatSelector.length <= 160) {
            "下载格式选择无效"
        }
        ensureInitialized()
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("无法创建下载临时目录")
        }
        val outputPrefix = "ytdlp-$processId"
        val cookies = createTemporaryCookieFile()
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--newline")
                addOption("--no-mtime")
                addOption("--continue")
                addOption("-f", if (audioOnly) "bestaudio/best" else formatSelector)
                if (audioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                } else {
                    addOption("--merge-output-format", "mp4")
                }
                addOption("-o", File(outputDirectory, "$outputPrefix.%(ext)s").absolutePath)
                cookies?.let { addOption("--cookies", it.absolutePath) }
            }
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                onProgress?.invoke(progress, eta, line.take(240))
            }
            outputDirectory.listFiles()
                ?.filter { it.isFile && it.name.startsWith("$outputPrefix.") &&
                    !it.name.endsWith(".part", ignoreCase = true) && it.length() > 0L }
                ?.maxByOrNull { it.lastModified() }
                ?: throw IOException("yt-dlp 已结束，但没有找到输出文件")
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        } finally {
            cookies?.delete()
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    private fun ensureInitialized() {
        YoutubeDL.getInstance().init(context)
        com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
    }

    /**
     * Mirrors Seal's cookie handoff: flush WebView, read Chromium's Cookie DB, and preserve
     * host, path, secure and expiry attributes. Only Google/YouTube and Niconico domains are
     * included. Cookie contents remain in the app-private cache directory.
     */
    private fun createTemporaryCookieFile(): File? {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
        runCatching { cookieManager?.flush() }

        val databaseRows = readSupportedCookiesFromDatabase()
        val rows = if (databaseRows.isNotEmpty()) {
            databaseRows
        } else {
            readSupportedCookiesFromCookieManager(cookieManager)
        }
        if (rows.isEmpty()) return null

        return File(context.cacheDir, "yt-cookies-${UUID.randomUUID()}.txt").apply {
            writeText("# Netscape HTTP Cookie File\n" + rows.distinct().joinToString("\n") + "\n")
        }
    }

    private fun readSupportedCookiesFromDatabase(): List<String> = runCatching {
        val dbFile = File(context.dataDir, "app_webview/Default/Cookies")
        if (!dbFile.isFile) return@runCatching emptyList()
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val columns = mutableListOf("host_key", "expires_utc", "path", "name", "value", "is_secure")
            val hasHttpOnly = db.rawQuery("PRAGMA table_info(cookies)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == "is_httponly") {
                        found = true
                        break
                    }
                }
                found
            }
            if (hasHttpOnly) columns += "is_httponly"

            db.query("cookies", columns.toTypedArray(), null, null, null, null, null).use { cursor ->
                val hostIndex = cursor.getColumnIndexOrThrow("host_key")
                val expiryIndex = cursor.getColumnIndexOrThrow("expires_utc")
                val pathIndex = cursor.getColumnIndexOrThrow("path")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val valueIndex = cursor.getColumnIndexOrThrow("value")
                val secureIndex = cursor.getColumnIndexOrThrow("is_secure")
                val httpOnlyIndex = cursor.getColumnIndex("is_httponly")
                buildList {
                    while (cursor.moveToNext()) {
                        val domain = cursor.getString(hostIndex)?.trim().orEmpty()
                        val name = cursor.getString(nameIndex)?.trim().orEmpty()
                        val value = cursor.getString(valueIndex).orEmpty()
                        val cookiePath = cursor.getString(pathIndex)?.ifBlank { "/" } ?: "/"
                        if (!isSupportedCookieDomain(domain) || name.isBlank() ||
                            name.any { it.isWhitespace() } ||
                            value.any { it == '\t' || it == '\r' || it == '\n' } ||
                            cookiePath.any { it == '\t' || it == '\r' || it == '\n' }
                        ) continue

                        val secure = cursor.getLong(secureIndex) != 0L
                        val chromeExpiry = cursor.getLong(expiryIndex)
                        val expiry = if (chromeExpiry > 0L) {
                            (chromeExpiry / 1_000_000L - CHROME_EPOCH_DELTA_SECONDS).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                        if (expiry > 0L && expiry <= System.currentTimeMillis() / 1_000L) continue
                        val httpOnly = httpOnlyIndex >= 0 && cursor.getLong(httpOnlyIndex) != 0L
                        val outputDomain = if (httpOnly) "#HttpOnly_$domain" else domain
                        val includeSubdomains = if (domain.startsWith(".")) "TRUE" else "FALSE"
                        add(listOf(
                            outputDomain,
                            includeSubdomains,
                            cookiePath,
                            if (secure) "TRUE" else "FALSE",
                            expiry.toString(),
                            name,
                            value
                        ).joinToString("\t"))
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun readSupportedCookiesFromCookieManager(cookieManager: CookieManager?): List<String> {
        if (cookieManager == null) return emptyList()
        val sources = listOf(
            ".youtube.com" to "https://www.youtube.com/",
            ".google.com" to "https://accounts.google.com/",
            ".nicovideo.jp" to "https://www.nicovideo.jp/",
            ".nicovideo.jp" to "https://account.nicovideo.jp/",
            ".bilibili.com" to "https://www.bilibili.com/",
            ".bilibili.com" to "https://passport.bilibili.com/",
            ".bilibili.tv" to "https://www.bilibili.tv/"
        )
        return sources.flatMap { (domain, origin) ->
            runCatching { cookieManager.getCookie(origin).orEmpty() }
                .getOrDefault("")
                .split(';')
                .mapNotNull { pair ->
                    val separator = pair.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    val name = pair.substring(0, separator).trim()
                    val value = pair.substring(separator + 1).trim()
                    if (name.isBlank() || value.isBlank() ||
                        name.any { it.isWhitespace() } ||
                        value.any { it == '\t' || it == '\r' || it == '\n' }
                    ) null else {
                        "$domain\tTRUE\t/\tTRUE\t0\t$name\t$value"
                    }
                }
        }.distinct()
    }

    private fun isSupportedCookieDomain(rawDomain: String): Boolean {
        val domain = rawDomain.removePrefix(".").lowercase(Locale.ROOT)
        return SUPPORTED_COOKIE_DOMAINS.any { domain == it || domain.endsWith(".$it") }
    }

    private fun sanitizeError(message: String?): String {
        val safe = message.orEmpty()
            .replace(Regex("(?i)(cookie|authorization|token)\\s*[:=]\\s*[^\\s]+"), "$1=[redacted]")
            .replace(Regex("https?://[^\\s]+"), "[URL 已隐藏]")
            .lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.take(220)
        return when {
            safe.isNullOrBlank() -> "yt-dlp 解析或下载失败"
            safe.contains("Sign in to confirm", true) ||
                safe.contains("not a bot", true) ->
                "YouTube 要求登录或人机验证；请检查应用内网页登录状态，或稍后更新 yt-dlp"
            safe.contains("Unsupported URL", true) -> "当前 yt-dlp 版本不支持此链接"
            else -> safe
        }
    }

    private companion object {
        const val CHROME_EPOCH_DELTA_SECONDS = 11_644_473_600L
        val SUPPORTED_COOKIE_DOMAINS = setOf(
            "youtube.com",
            "google.com",
            "nicovideo.jp",
            "nimg.jp",
            "bilibili.com",
            "bilibili.tv"
        )
    }
}
