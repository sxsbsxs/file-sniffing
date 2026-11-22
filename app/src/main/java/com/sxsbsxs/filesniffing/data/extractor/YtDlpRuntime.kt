package com.sxsbsxs.filesniffing.data.extractor

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Seal-style bridge to the upstream yt-dlp Android runtime.
 *
 * This runtime does not embed a browser or read browser cookie databases.
 * Site access is determined by yt-dlp and the user's normal network connection.
 */
@Singleton
class YtDlpRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val initializationMutex = Mutex()
    private val executableLock = ReentrantReadWriteLock()
    @Volatile private var initialized = false

    /** Initializes yt-dlp/FFmpeg and silently refreshes the extractor from the Nightly channel. */
    suspend fun prepare() = withContext(Dispatchers.IO) {
        ensureInitialized()
    }
    suspend fun inspectPlaylist(url: String): YtDlpPlaylistInfo = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "请输入有效的播放列表链接"
        }
        ensureInitialized()
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--skip-download")
                addOption("--playlist-end", "200")
                addOption("--socket-timeout", "20")
            }
            val response = executableLock.read { YoutubeDL.getInstance().execute(request) }
            YtDlpInfoJsonParser.parsePlaylist(response.out)
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        } finally {
        }
    }

    suspend fun inspect(url: String): YtDlpVideoInfo = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "请输入有效的视频链接"
        }
        ensureInitialized()
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--skip-download")
                addOption("--socket-timeout", "20")
            }
            val response = executableLock.read { YoutubeDL.getInstance().execute(request) }
            YtDlpInfoJsonParser.parse(response.out)
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        } finally {
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
        require(YtDlpFormatSelectorPolicy.isSafe(formatSelector)) {
            "下载格式选择无效，请重新解析后选择可用格式"
        }
        ensureInitialized()
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("无法创建下载临时目录")
        }
        val outputPrefix = "ytdlp-$processId"
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--newline")
                addOption("--no-mtime")
                addOption("--continue")
                val effectiveFormat = if (
                    audioOnly && formatSelector == "bv*+ba/b"
                ) "bestaudio/best" else formatSelector
                addOption("-f", effectiveFormat)
                if (audioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                } else {
                    addOption("--merge-output-format", "mp4")
                }
                addOption("-o", File(outputDirectory, "$outputPrefix.%(ext)s").absolutePath)
            }
            suspendCancellableCoroutine { continuation ->
                val executionThread = Thread({
                    try {
                        if (!continuation.isActive) return@Thread
                        executableLock.read {
                            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                                onProgress?.invoke(progress, eta, line.take(240))
                            }
                        }
                        val outputFile = outputDirectory.listFiles()
                            ?.filter { it.isFile && it.name.startsWith("$outputPrefix.") &&
                                !it.name.endsWith(".part", ignoreCase = true) && it.length() > 0L }
                            ?.maxByOrNull { it.lastModified() }
                            ?: throw IOException("yt-dlp 已结束，但没有找到输出文件")
                        if (continuation.isActive) {
                            runCatching { continuation.resume(outputFile) }
                        }
                    } catch (cancelled: CancellationException) {
                        if (continuation.isActive) {
                            runCatching { continuation.resumeWithException(cancelled) }
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) {
                            runCatching {
                                continuation.resumeWithException(
                                    IOException(sanitizeError(error.message), error)
                                )
                            }
                        }
                    }
                }, "yt-dlp-" + processId.take(20)).apply {
                    isDaemon = true
                }

                continuation.invokeOnCancellation {
                    // Cancel the native process immediately; the Worker must not wait for
                    // the blocking yt-dlp call to return before its pause/cancel takes effect.
                    cancel(processId)
                    executionThread.interrupt()
                }
                executionThread.start()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    private suspend fun ensureInitialized() {
        initializationMutex.withLock {
            if (!initialized) {
                YoutubeDL.getInstance().init(context)
                com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
                initialized = true
            }
            updateYtDlpIfDue()
        }
    }

    /**
     * Seal keeps the yt-dlp executable fresh independently of the Android wrapper dependency.
     * The wrapper stays pinned, while the Python yt-dlp program is silently checked weekly.
     * Failed checks are retried after a cooldown and never block use of the bundled fallback.
     */
    private fun updateYtDlpIfDue() {
        val preferences = context.getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = preferences.getLong(KEY_LAST_ATTEMPT, 0L)
        val lastSuccess = preferences.getLong(KEY_LAST_SUCCESS, 0L)
        val installedVersion = YoutubeDL.getInstance().versionName(context)

        if (!YtDlpUpdatePolicy.shouldAttemptUpdate(
                nowMillis = now,
                lastAttemptMillis = lastAttempt,
                lastSuccessMillis = lastSuccess,
                hasInstalledVersion = !installedVersion.isNullOrBlank()
            )
        ) return

        // Record the attempt before network work so a failed/offline check cannot loop each parse.
        preferences.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        try {
            val status = executableLock.write {
                YoutubeDL.getInstance().updateYoutubeDL(
                    context,
                    YoutubeDL.UpdateChannel.NIGHTLY
                )
            }
            val latestVersion = YoutubeDL.getInstance().versionName(context)
            if (!latestVersion.isNullOrBlank()) {
                preferences.edit()
                    .putLong(KEY_LAST_SUCCESS, now)
                    .putString(KEY_LAST_VERSION, latestVersion)
                    .apply()
            }
            Log.i(TAG, "yt-dlp Nightly check complete: $status, version=$latestVersion")
        } catch (error: Exception) {
            Log.w(TAG, "yt-dlp Nightly update failed; keeping the bundled executable", error)
        }
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
                "YouTube 要求登录或人机验证；当前版本不提供网页登录，请尝试公开可访问的视频，或稍后更新 yt-dlp"
            safe.contains("Unsupported URL", true) -> "当前 yt-dlp 版本不支持此链接"
            else -> safe
        }
    }

    private companion object {
        const val TAG = "YtDlpRuntime"
        const val UPDATE_PREFERENCES = "yt_dlp_updater"
        const val KEY_LAST_ATTEMPT = "last_attempt_ms"
        const val KEY_LAST_SUCCESS = "last_success_ms"
        const val KEY_LAST_VERSION = "last_version"
    }
}
