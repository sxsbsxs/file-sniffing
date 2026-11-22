package com.sxsbsxs.filesniffing.data.extractor

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real yt-dlp runtime bridge. This is intentionally separate from the WebView resource sniffer:
 * page requests are not a reliable source of YouTube media URLs.
 *
 * Cookies are exported to a short-lived Netscape file for the duration of one yt-dlp process,
 * then deleted. Signed media URLs and cookie values are never returned to UI state or logged.
 */
@Singleton
class YtDlpRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) {
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
                ?.filter { it.isFile && it.name.startsWith("$outputPrefix.") && it.length() > 0L }
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
        // YoutubeDL.init is synchronized and safe to call again after Application startup.
        YoutubeDL.getInstance().init(context)
        com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
    }

    private fun createTemporaryCookieFile(): File? {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return null
        val sources = listOf(
            ".youtube.com" to "https://www.youtube.com",
            ".google.com" to "https://accounts.google.com"
        )
        val rows = sources.flatMap { (domain, origin) ->
            runCatching { cookieManager.getCookie(origin).orEmpty() }
                .getOrDefault("")
                .split(';')
                .mapNotNull { pair ->
                    val separator = pair.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    val name = pair.substring(0, separator).trim()
                    val value = pair.substring(separator + 1).trim()
                    if (name.isBlank() || value.isBlank() ||
                        name.any { it.isWhitespace() } || value.any { it.isWhitespace() }
                    ) null else "$domain\tTRUE\t/\tTRUE\t0\t$name\t$value"
                }
        }.distinct()

        if (rows.isEmpty()) return null
        return File(context.cacheDir, "yt-cookies-${UUID.randomUUID()}.txt").apply {
            writeText("# Netscape HTTP Cookie File\n" + rows.joinToString("\n") + "\n")
        }
    }

    private fun sanitizeError(message: String?): String {
        val safe = message.orEmpty()
            .replace(Regex("(?i)(cookie|authorization|token)\\s*[:=]\\s*[^\\s]+"), "$1=[redacted]")
            .replace(Regex("https?://[^\\s]+"), "[media-url-redacted]")
            .lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.take(220)
        return when {
            safe.isNullOrBlank() -> "yt-dlp 解析或下载失败"
            safe.contains("Sign in to confirm", true) ||
                safe.contains("not a bot", true) ->
                "YouTube 要求登录或人机验证；请在应用内登录后重试，或稍后更新 yt-dlp"
            safe.contains("Unsupported URL", true) -> "当前 yt-dlp 版本不支持此链接"
            else -> safe
        }
    }
}
