package com.sxsbsxs.filesniffing.data.extractor

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.aria2c.Aria2c
import com.sxsbsxs.filesniffing.NiconicoUrlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
    @Volatile private var aria2cAvailable = false

    /**
     * Initialize yt-dlp and perform the normal weekly extractor refresh before the first
     * user request. The application calls this on an IO scope during startup; parse requests
     * share the initialization mutex and therefore never race a runtime update.
     */
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
            val response = executeCancellable(request, UUID.randomUUID().toString())
            YtDlpInfoJsonParser.parsePlaylist(response.out)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        }
    }

    suspend fun inspect(url: String): YtDlpVideoInfo = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "请输入有效的视频链接"
        }
        ensureInitialized()
        try {
            inspectOnce(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!NiconicoGuestExtractorRecoveryPolicy.shouldRefreshAndRetry(url, error.message)) {
                throw IOException(sanitizeError(error.message), error)
            }

            Log.w(TAG, "Niconico guest extraction returned the legacy registration error; refreshing yt-dlp once")
            if (!forceRefreshYtDlpForNiconico()) {
                throw IOException(sanitizeError(error.message), error)
            }

            try {
                inspectOnce(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (retryError: Exception) {
                throw IOException(sanitizeError(retryError.message), retryError)
            }
        }
    }

    private suspend fun inspectOnce(url: String): YtDlpVideoInfo {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--skip-download")
            addOption("--socket-timeout", "20")
        }
        val response = executeCancellable(request, UUID.randomUUID().toString())
        return YtDlpInfoJsonParser.parse(response.out)
    }

    suspend fun download(
        url: String,
        outputDirectory: File,
        formatSelector: String = "bv*+ba/b",
        audioOnly: Boolean = false,
        processId: String = UUID.randomUUID().toString(),
        concurrentFragments: Int = 8,
        speedLimitBytesPerSecond: Long = 0L,
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
        // Only reuse an output that this runtime previously marked complete after yt-dlp
        // exited successfully. Raw video/audio streams left by a paused merge are not outputs.
        YtDlpOutputFilePolicy.findCompletedOutput(outputDirectory, outputPrefix)
            ?.let { return@withContext it }
        try {
            // Remove an unmarked final file left by an interrupted FFmpeg merge, but keep
            // yt-dlp's format-specific files and .part data so --continue can resume them.
            YtDlpOutputFilePolicy.prepareForDownload(outputDirectory, outputPrefix)
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--newline")
                addOption("--socket-timeout", "30")
                addOption("--retries", "3")
                addOption("--fragment-retries", "3")
                addOption("--extractor-retries", "2")
                addOption("--progress-delta", "0.5")
                addOption(
                    "--progress-template",
                    "download:FILE_SNIFFING_PROGRESS|%(info.format_id)s|%(progress)j"
                )
                addOption(
                    "--progress-template",
                    "postprocess:FILE_SNIFFING_POSTPROCESS|%(progress.status)s|%(progress.postprocessor)s"
                )
                addOption("--no-mtime")
                addOption("--continue")
                val connectionLimit = concurrentFragments.coerceIn(1, 16)
                addOption("--concurrent-fragments", connectionLimit.toString())

                if (aria2cAvailable) {
                    // Gopeed-style segmented HTTP transfer: aria2c handles ordinary HTTP/HTTPS
                    // files, while yt-dlp retains its native HLS/DASH fragment downloader.
                    addOption("--downloader", "libaria2c.so")
                    addOption("--downloader", "dash,m3u8:native")
                    val aria2Arguments = buildString {
                        append("-x$connectionLimit -s$connectionLimit -k1M -c --file-allocation=none")
                        if (speedLimitBytesPerSecond > 0L) {
                            append(" --max-download-limit=$speedLimitBytesPerSecond")
                        }
                    }
                    addOption("--downloader-args", "aria2c:$aria2Arguments")
                }
                if (speedLimitBytesPerSecond > 0L) {
                    // Keep the yt-dlp-native fragment path rate-limited too.
                    addOption("--limit-rate", speedLimitBytesPerSecond.toString())
                }
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
            executeCancellable(request, processId) { progress, eta, line ->
                onProgress?.invoke(progress, eta, line.take(240))
            }
            val completedOutput = YtDlpOutputFilePolicy.findFinalOutput(outputDirectory, outputPrefix)
                ?: throw IOException("yt-dlp 已结束，但没有找到合并后的最终媒体文件")
            YtDlpOutputFilePolicy.markCompleted(outputDirectory, outputPrefix, completedOutput)
            completedOutput
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IOException(sanitizeError(error.message), error)
        }
    }

    /**
     * Executes a yt-dlp command on a dedicated thread so cancellation can destroy the process.
     * The caller does not complete its pause/retry transition until the process thread exits,
     * preventing concurrent writers from touching the same resumable .part files.
     */
    private suspend fun executeCancellable(
        request: YoutubeDLRequest,
        processId: String,
        onProgress: ((Float, Long, String) -> Unit)? = null
    ): com.yausername.youtubedl_android.YoutubeDLResponse {
        val executionThread = AtomicReference<Thread?>()
        return try {
            suspendCancellableCoroutine { continuation ->
                val thread = Thread({
                    try {
                        if (!continuation.isActive) return@Thread
                        val response = executableLock.read {
                            YoutubeDL.getInstance().execute(request, processId, onProgress)
                        }
                        if (continuation.isActive) {
                            runCatching { continuation.resume(response) }
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) {
                            runCatching {
                                // Preserve the upstream extractor text here. The Niconico
                                // recovery policy needs the original marker before UI sanitizing.
                                continuation.resumeWithException(
                                    IOException(error.message ?: "yt-dlp command failed", error)
                                )
                            }
                        }
                    }
                }, "yt-dlp-" + processId.take(20)).apply {
                    isDaemon = true
                }
                executionThread.set(thread)
                continuation.invokeOnCancellation {
                    cancel(processId)
                    thread.interrupt()
                }
                thread.start()
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                executionThread.get()?.join()
            }
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

                // The optional native archive is packaged per ABI by the matching 0.17.3 AAR.
                // If it is absent or fails to unpack, keep the yt-dlp native downloader as fallback.
                val aria2Archive = File(context.applicationInfo.nativeLibraryDir, "libaria2c.zip.so")
                aria2cAvailable = if (aria2Archive.isFile && aria2Archive.length() > 0L) {
                    runCatching {
                        Aria2c.getInstance().init(context)
                        true
                    }.onFailure { error ->
                        Log.w(TAG, "aria2c initialization failed; using yt-dlp native downloader", error)
                    }.getOrDefault(false)
                } else {
                    false
                }
                initialized = true
                updateYtDlpIfDue()
            }
        }
    }

    /**
     * Retry once with a fresh Nightly extractor when an old Niconico extractor mistakes a
     * publicly viewable guest video for registered-user-only content.
     *
     * This is version-scoped so a genuinely restricted video does not trigger a network update
     * on every parse attempt.
     */
    private fun forceRefreshYtDlpForNiconico(): Boolean {
        val preferences = context.getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
        val installedVersion = YoutubeDL.getInstance().versionName(context)
        val lastRecoveryVersion = preferences.getString(KEY_NICONICO_RECOVERY_VERSION, null)
        if (!installedVersion.isNullOrBlank() && installedVersion == lastRecoveryVersion) {
            return false
        }

        return try {
            val now = System.currentTimeMillis()
            val status = executableLock.write {
                YoutubeDL.getInstance().updateYoutubeDL(
                    context,
                    YoutubeDL.UpdateChannel.NIGHTLY
                )
            }
            val latestVersion = YoutubeDL.getInstance().versionName(context)
            val recoveryVersion = latestVersion?.takeIf { it.isNotBlank() }
                ?: installedVersion?.takeIf { it.isNotBlank() }
            if (recoveryVersion != null) {
                preferences.edit()
                    .putLong(KEY_LAST_ATTEMPT, now)
                    .putLong(KEY_LAST_SUCCESS, now)
                    .putString(KEY_LAST_VERSION, recoveryVersion)
                    .putString(KEY_NICONICO_RECOVERY_VERSION, recoveryVersion)
                    .apply()
            }
            Log.i(TAG, "Niconico extractor recovery update: $status, version=$latestVersion")
            recoveryVersion != null
        } catch (error: Exception) {
            Log.w(TAG, "Niconico extractor recovery update failed; keeping current runtime", error)
            false
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
            safe.contains("only available for registered users", true) ->
                "N站解析器仍报告需要注册用户权限。已尝试刷新解析器；请确认该视频当前是否允许游客观看，或是否存在地区限制。"
            safe.contains("invalid session", true) ->
                "Niconico 登录状态已过期，请重新登录后重试。"
            safe.contains("premium members only", true) ->
                "该 Niconico 视频仅限 Premium 会员观看。"
            safe.contains("channel members only", true) ->
                "该 Niconico 视频仅限频道会员观看。"
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
        const val KEY_NICONICO_RECOVERY_VERSION = "niconico_recovery_version"
    }
}
