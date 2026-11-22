package com.sxsbsxs.filesniffing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity
import com.sxsbsxs.filesniffing.data.repository.DownloadRepository
import com.sxsbsxs.filesniffing.NiconicoUrlParser
import com.sxsbsxs.filesniffing.data.extractor.YouTubeUrlParser
import com.sxsbsxs.filesniffing.data.extractor.YtDlpRuntime
import com.sxsbsxs.filesniffing.data.network.OkDownloadTransferEngine
import com.sxsbsxs.filesniffing.data.network.DownloadSpeedLimiter
import com.sxsbsxs.filesniffing.data.network.DownloadConnectionPolicy
import com.sxsbsxs.filesniffing.data.preferences.AppPreferences
import com.sxsbsxs.filesniffing.data.storage.DownloadOutput
import com.sxsbsxs.filesniffing.data.media.Media3AdaptiveProcessor
import com.liulishuo.okdownload.core.dispatcher.DownloadDispatcher
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

@HiltWorker
class PersistentDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val okDownloadTransferEngine: OkDownloadTransferEngine,
    private val media3AdaptiveProcessor: Media3AdaptiveProcessor,
    private val downloadRepository: DownloadRepository,
    private val ytDlpRuntime: YtDlpRuntime,
    private val appPreferences: AppPreferences,
    private val downloadSpeedLimiter: DownloadSpeedLimiter
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val requestedUrl = inputData.getString(KEY_URL).orEmpty()
        val treeValue = inputData.getString(KEY_TREE).orEmpty()
        val requestedFileName = inputData.getString(KEY_FILE_NAME).orEmpty()
        val kindName = inputData.getString(KEY_KIND).orEmpty()
        val requestedMime = inputData.getString(KEY_MIME)
        val referer = inputData.getString(KEY_REFERER).orEmpty()
        val requestedKind = runCatching {
            ResourceKind.valueOf(kindName)
        }.getOrDefault(ResourceKind.OTHER)
        val niconicoPageUrl = when {
            NiconicoUrlParser.extractVideoId(requestedUrl) != null -> requestedUrl
            requestedKind == ResourceKind.PLAYLIST &&
                NiconicoUrlParser.extractVideoId(referer) != null -> referer
            else -> null
        }
        val youtubePageUrl = when {
            YouTubeUrlParser.extractVideoId(requestedUrl) != null -> requestedUrl
            requestedKind == ResourceKind.VIDEO &&
                YouTubeUrlParser.extractVideoId(referer) != null -> referer
            else -> null
        }
        val requestedPath = Uri.parse(requestedUrl).lastPathSegment.orEmpty()
        val requestedExtension = requestedPath.substringAfterLast('.', "").lowercase()
        val directMediaExtensions = setOf(
            "mp4", "m4v", "webm", "mov", "mkv", "avi", "3gp", "mpeg", "mpg",
            "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "wma"
        )
        // Provider watch pages (including Bilibili and other yt-dlp sites) are parsed
        // directly by yt-dlp. Recognized direct media files still use the HTTP engine.
        val genericYtDlpPageUrl = requestedUrl.takeIf {
            requestedKind in setOf(ResourceKind.VIDEO, ResourceKind.AUDIO) &&
                requestedUrl == referer &&
                requestedExtension !in directMediaExtensions &&
                (it.startsWith("https://") || it.startsWith("http://"))
        }
        val ytDlpPageUrl = niconicoPageUrl ?: youtubePageUrl ?: genericYtDlpPageUrl

        if ((requestedUrl.isBlank() && ytDlpPageUrl == null) ||
            requestedFileName.isBlank()
        ) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "下载任务参数不完整"))
        }

        var url = requestedUrl
        var fileName = requestedFileName
        var kind = requestedKind
        var mime = requestedMime
        var resource = SniffedResource(
            url = url,
            kind = kind,
            fileName = fileName,
            mimeType = mime,
            pageTitle = "",
            sourcePage = referer
        )
        val recordSourceUrl = ytDlpPageUrl ?: requestedUrl
        val tree = treeValue.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val adaptiveVideoHeight = inputData.getInt(KEY_ADAPTIVE_VIDEO_HEIGHT, -1).takeIf { it > 0 }
        val tuning = appPreferences.downloadTuning.first()
        downloadSpeedLimiter.setLimitBytesPerSecond(tuning.speedLimitBytesPerSecond)
        DownloadDispatcher.setMaxParallelRunningCount(tuning.parallelDownloads)
        DownloadConnectionPolicy.setPreferredMaximumConnections(tuning.connectionsPerTask)
        var isAdaptiveManifest = kind == ResourceKind.PLAYLIST &&
            (fileName.endsWith(".m3u8", ignoreCase = true) ||
                fileName.endsWith(".mpd", ignoreCase = true))
        var createdDocument: Uri? = null

        try {
            setForeground(createForegroundInfo(fileName, -1, "正在准备下载"))
            publishProgressAsync(-1, "正在准备下载", fileName)
            persistRecord(
                fileName = fileName,
                sourceUrl = recordSourceUrl,
                kind = kind,
                mimeType = mime,
                state = "downloading",
                progress = -1,
                detail = "正在准备下载",
                documentUri = null
            )

            if (ytDlpPageUrl != null) {
                val providerName = when {
                    niconicoPageUrl != null -> "N站"
                    youtubePageUrl != null -> "YouTube"
                    else -> "视频站"
                }
                publishProgressAsync(-1, "正在通过 yt-dlp 提取 $providerName 媒体流", fileName)
                val tempDirectory = java.io.File(applicationContext.cacheDir, "yt-dlp-${id}")
                val downloadedFile = ytDlpRuntime.download(
                    url = ytDlpPageUrl,
                    outputDirectory = tempDirectory,
                    audioOnly = kind == ResourceKind.AUDIO,
                    processId = id.toString()
                ) { progress, _, line ->
                    val percent = progress.takeIf { it.isFinite() && it >= 0f }
                        ?.toInt()?.coerceIn(0, 100) ?: -1
                    publishProgressAsync(
                        percent,
                        line.ifBlank { "yt-dlp 正在下载并合并音视频" },
                        fileName
                    )
                }
                val extension = downloadedFile.extension.lowercase().ifBlank { "mp4" }
                val baseName = fileName.substringBeforeLast('.', fileName)
                val finalName = FileNameSanitizer.sanitize("$baseName.$extension", "youtube-video.$extension")
                val outputMime = when (extension) {
                    "mp4", "m4v" -> "video/mp4"
                    "webm" -> if (kind == ResourceKind.AUDIO) "audio/webm" else "video/webm"
                    "mkv" -> "video/x-matroska"
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    "ogg", "oga" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    "opus" -> "audio/opus"
                    "wav" -> "audio/wav"
                    else -> "application/octet-stream"
                }
                val destination = DownloadOutput.create(
                    context = applicationContext,
                    destinationTree = tree,
                    displayName = finalName,
                    mimeType = outputMime
                )
                createdDocument = destination
                try {
                    applicationContext.contentResolver.openOutputStream(destination, "w")?.use { output ->
                        downloadedFile.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("无法打开 Download 输出文件")
                    DownloadOutput.markComplete(applicationContext, destination)
                } catch (copyError: Exception) {
                    DownloadOutput.delete(applicationContext, destination)
                    throw copyError
                } finally {
                    tempDirectory.deleteRecursively()
                }
                persistRecord(
                    fileName = finalName,
                    sourceUrl = recordSourceUrl,
                    kind = if (kind == ResourceKind.AUDIO) ResourceKind.AUDIO else ResourceKind.VIDEO,
                    mimeType = outputMime,
                    state = "completed",
                    progress = 100,
                    detail = if (kind == ResourceKind.AUDIO) "yt-dlp 音频下载完成" else "yt-dlp 下载完成，FFmpeg 已处理音视频合并",
                    documentUri = destination.toString()
                )
                return@withContext Result.success(
                    workDataOf(
                        KEY_FILE_NAME to finalName,
                        KEY_FILE_URI to destination.toString(),
                        KEY_DETAIL to if (kind == ResourceKind.AUDIO) "$providerName 音频下载完成" else "$providerName 下载完成"
                    )
                )
            }

            if (isAdaptiveManifest) {
                val result = media3AdaptiveProcessor.exportAdaptiveStream(
                    resource = resource,
                    destinationTree = tree,
                    maxVideoHeight = adaptiveVideoHeight,
                    maxConcurrentConnections = tuning.connectionsPerTask
                ) { progress, detail ->
                    publishProgressAsync(progress, detail, fileName)
                }
                persistRecord(
                    fileName = result.fileName,
                    sourceUrl = recordSourceUrl,
                    kind = kind,
                    mimeType = "video/mp4",
                    state = "completed",
                    progress = 100,
                    detail = "Media3 已导出 MP4",
                    documentUri = result.documentUri.toString()
                )
                return@withContext Result.success(
                    workDataOf(
                        KEY_FILE_NAME to result.fileName,
                        KEY_FILE_URI to result.documentUri.toString(),
                        KEY_DETAIL to "Media3 已导出 MP4"
                    )
                )
            }
            val parsed = Uri.parse(url)
            require(parsed.scheme == "http" || parsed.scheme == "https") {
                "只支持 HTTP 或 HTTPS 资源"
            }
            val mimeType = mime ?: defaultMimeType(kind, fileName)
            var targetName = ensureExtension(fileName, mimeType, kind)
            val reusableDocument = inputData.getString(KEY_PARTIAL_URI)
                ?.let { rawUri ->
                    val candidate = Uri.parse(rawUri)
                    runCatching {
                        applicationContext.contentResolver.openFileDescriptor(candidate, "rw")?.use { }
                            ?: error("断点文件不可写")
                        candidate
                    }.getOrNull()
                }
            var destinationDocument = reusableDocument ?: DownloadOutput.create(
                context = applicationContext,
                destinationTree = tree,
                displayName = targetName,
                mimeType = mimeType
            )
            createdDocument = destinationDocument
            persistRecord(
                fileName = targetName,
                sourceUrl = recordSourceUrl,
                kind = kind,
                mimeType = mimeType,
                state = "downloading",
                progress = 0,
                detail = if (reusableDocument != null) "正在从断点继续下载" else "正在下载",
                documentUri = destinationDocument.toString()
            )

            val headers = buildMap<String, List<String>> {
                put("User-Agent", listOf("FileSniffing/0.9.11 (Android)"))
                put("Accept", listOf("*/*"))
                if (referer.isNotBlank()) put("Referer", listOf(referer))
            }
            publishProgressAsync(
                -1,
                if (reusableDocument != null) "正在恢复断点下载" else "正在下载（OkDownload）",
                targetName,
                destinationDocument.toString()
            )
            val transferResult = okDownloadTransferEngine.downloadToDocument(
                url = url,
                destinationDocument = destinationDocument,
                headers = headers
            ) { snapshot ->
                val detail = if (snapshot.totalBytes > 0L) "下载中" else "正在接收数据"
                publishProgressAsync(
                    progress = snapshot.progress,
                    detail = detail,
                    fileName = targetName,
                    partialUri = destinationDocument.toString(),
                    downloadedBytes = snapshot.downloadedBytes,
                    totalBytes = snapshot.totalBytes,
                    speedBytesPerSecond = snapshot.speedBytesPerSecond
                )
            }

            val responseHeaders = transferResult.responseHeaders
            fun responseHeader(name: String): String? = responseHeaders.entries
                .firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value?.firstOrNull()
            val responseMime = responseHeader("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.contains('/') }
                ?: mimeType
            val serverFileName = parseContentDisposition(responseHeader("Content-Disposition"))
            if (!serverFileName.isNullOrBlank()) {
                val finalName = ensureExtension(serverFileName, responseMime, kind)
                if (finalName != targetName) {
                    val renamed = DownloadOutput.rename(
                        context = applicationContext,
                        documentUri = destinationDocument,
                        displayName = finalName
                    )
                    if (renamed != null) {
                        destinationDocument = renamed
                        createdDocument = renamed
                        targetName = finalName
                    }
                }
            }

            val prefix = applicationContext.contentResolver
                .openInputStream(destinationDocument)
                ?.use { input ->
                    val bytes = ByteArray(4096)
                    val count = input.read(bytes)
                    if (count > 0) bytes.copyOf(count) else ByteArray(0)
                } ?: ByteArray(0)
            val detectedContainer = NativeMediaProbe.detectContainer(prefix)
            check(detectedContainer != "html") {
                "下载地址返回了 HTML 页面，而不是媒体文件；资源可能需要登录或有效的 Referer"
            }
            DownloadOutput.markComplete(applicationContext, destinationDocument)
            val completedDetail = if (detectedContainer == "unknown") {
                if (tree == null) "已保存到 Download 文件夹" else "已保存到所选文件夹"
            } else {
                "下载完成，检测格式：$detectedContainer"
            }

            persistRecord(
                fileName = targetName,
                sourceUrl = recordSourceUrl,
                kind = kind,
                mimeType = responseMime,
                state = "completed",
                progress = 100,
                detail = completedDetail,
                documentUri = createdDocument.toString()
            )
            Result.success(
                workDataOf(
                    KEY_FILE_NAME to targetName,
                    KEY_FILE_URI to createdDocument.toString(),
                    KEY_DETAIL to completedDetail
                )
            )
        } catch (cancelled: CancellationException) {
            val paused = isPauseRequested()
            if (ytDlpPageUrl != null) {
                ytDlpRuntime.cancel(id.toString())
                if (!paused) {
                    runCatching {
                        java.io.File(applicationContext.cacheDir, "yt-dlp-${id}").deleteRecursively()
                    }
                }
            }
            withContext(NonCancellable) {
                if (!paused && isAdaptiveManifest) {
                    runCatching {
                        media3AdaptiveProcessor.discardAdaptiveCache(
                            resource = resource,
                            destinationTree = tree,
                            maxVideoHeight = adaptiveVideoHeight
                        )
                    }
                }
                val previous = downloadRepository.findById(id.toString())
                persistRecord(
                    fileName = fileName,
                    sourceUrl = recordSourceUrl,
                    kind = kind,
                    mimeType = mime,
                    state = if (paused) "paused" else "cancelled",
                    progress = if (paused) previous?.progress ?: 0 else 0,
                    detail = if (paused) "已暂停，继续时将尝试从断点恢复" else "下载已取消，可重新下载",
                    documentUri = createdDocument?.toString()
                )
            }
            throw cancelled
        } catch (error: Exception) {
            if (ytDlpPageUrl != null) {
                runCatching {
                    java.io.File(applicationContext.cacheDir, "yt-dlp-${id}").deleteRecursively()
                }
            }
            var rootCause: Throwable = error
            while (rootCause.cause != null) {
                rootCause = rootCause.cause!!
            }
            val rawDetail = rootCause.message?.takeIf { it.isNotBlank() }
                ?: error.message?.takeIf { it.isNotBlank() }
                ?: "没有返回错误描述"
            val safeDetail = rawDetail
                .replace(Regex("(?i)(cookie|authorization|token)\\s*[:=]\\s*[^\\s]+")) {
                    it.groupValues[1] + "=[redacted]"
                }
                .replace(Regex("https?://[^\\s]+"), "[URL 已隐藏]")
                .take(220)
            val detail = "下载失败（${rootCause.javaClass.simpleName}）：$safeDetail"
            persistRecord(
                fileName = fileName,
                sourceUrl = recordSourceUrl,
                kind = kind,
                mimeType = mime,
                state = "failed",
                progress = 0,
                detail = detail,
                documentUri = createdDocument?.toString()
            )
            Result.failure(workDataOf(KEY_ERROR to detail))
        }
    }

    private fun publishProgressAsync(
        progress: Int,
        detail: String,
        fileName: String,
        partialUri: String? = null,
        downloadedBytes: Long = -1L,
        totalBytes: Long = -1L,
        speedBytesPerSecond: Long = -1L
    ) {
        val data = androidx.work.Data.Builder()
            .putInt(KEY_PROGRESS, progress)
            .putString(KEY_DETAIL, detail)
            .putString(KEY_FILE_NAME, fileName)
            .apply {
                if (!partialUri.isNullOrBlank()) putString(KEY_PARTIAL_URI, partialUri)
                if (downloadedBytes >= 0L) putLong(KEY_DOWNLOADED_BYTES, downloadedBytes)
                if (totalBytes >= 0L) putLong(KEY_TOTAL_BYTES, totalBytes)
                if (speedBytesPerSecond >= 0L) putLong(KEY_SPEED_BYTES_PER_SECOND, speedBytesPerSecond)
            }
            .build()
        setProgressAsync(data)
        setForegroundAsync(createForegroundInfo(fileName, progress, detail))
    }

    private fun createForegroundInfo(fileName: String, progress: Int, detail: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "文件下载", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("File Sniffing · 下载中")
            .setContentText("$fileName · $detail")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun isPauseRequested(): Boolean =
        applicationContext.getSharedPreferences(
            "download_pause_requests",
            Context.MODE_PRIVATE
        ).getBoolean(id.toString(), false)

    private suspend fun persistRecord(
        fileName: String,
        sourceUrl: String,
        kind: ResourceKind,
        mimeType: String?,
        state: String,
        progress: Int,
        detail: String,
        documentUri: String?
    ) {
        val recordId = this.id.toString()
        val previous = downloadRepository.findById(recordId)
        val now = System.currentTimeMillis()
        downloadRepository.save(
            DownloadRecordEntity(
                id = recordId,
                sourceUrl = sourceUrl,
                fileName = fileName,
                resourceKind = kind.name,
                mimeType = mimeType,
                state = state,
                progress = progress,
                detail = detail,
                destinationUri = documentUri ?: previous?.destinationUri,
                createdAtMillis = previous?.createdAtMillis ?: now,
                updatedAtMillis = now,
                referer = inputData.getString(KEY_REFERER).orEmpty(),
                destinationTreeUri = inputData.getString(KEY_TREE)?.takeIf { it.isNotBlank() },
                downloadedBytes = previous?.downloadedBytes ?: -1L,
                totalBytes = previous?.totalBytes ?: -1L,
                speedBytesPerSecond = previous?.speedBytesPerSecond ?: -1L,
                adaptiveVideoHeight = previous?.adaptiveVideoHeight
                    ?: inputData.getInt(KEY_ADAPTIVE_VIDEO_HEIGHT, -1).takeIf { it > 0 }
            )
        )
    }

    private fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val encoded = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        val plain = Regex("filename\\s*=\\s*\\\"?([^\\\";]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        val raw = encoded ?: plain ?: return null
        return FileNameSanitizer.sanitize(
            java.net.URLDecoder.decode(raw.trim(), Charsets.UTF_8.name()),
            "download.bin"
        )
    }

    private fun ensureExtension(name: String, mime: String, kind: ResourceKind): String {
        val safe = FileNameSanitizer.sanitize(name, "download")
        val ext = safe.substringAfterLast('.', "")
        if (ext.isNotBlank() && ext.length <= 8) return safe
        val inferred = when {
            mime.contains("mpegurl", true) -> "m3u8"
            mime.contains("dash+xml", true) -> "mpd"
            mime.contains("pdf", true) -> "pdf"
            mime.contains("epub", true) -> "epub"
            mime.contains("wordprocessingml.document", true) -> "docx"
            mime.contains("spreadsheetml.sheet", true) -> "xlsx"
            mime.contains("presentationml.presentation", true) -> "pptx"
            mime.contains("application/msword", true) -> "doc"
            mime.contains("vnd.ms-excel", true) -> "xls"
            mime.contains("vnd.ms-powerpoint", true) -> "ppt"
            mime.contains("opendocument.text", true) -> "odt"
            mime.contains("opendocument.spreadsheet", true) -> "ods"
            mime.contains("opendocument.presentation", true) -> "odp"
            mime.contains("rtf", true) -> "rtf"
            mime.contains("x-7z-compressed", true) -> "7z"
            mime.contains("rar", true) -> "rar"
            mime.contains("zip", true) -> "zip"
            mime.contains("gzip", true) -> "gz"
            mime.contains("x-tar", true) -> "tar"
            mime.contains("x-bzip2", true) -> "bz2"
            mime.contains("x-xz", true) -> "xz"
            mime.contains("android-package-archive", true) -> "apk"
            mime.contains("java-archive", true) -> "jar"
            mime.contains("x-msdownload", true) -> "exe"
            mime.contains("woff2", true) -> "woff2"
            mime.contains("woff", true) -> "woff"
            mime.contains("font-ttf", true) -> "ttf"
            mime.contains("font-otf", true) -> "otf"
            mime.contains("json", true) -> "json"
            mime.contains("csv", true) -> "csv"
            mime.contains("xml", true) -> "xml"
            mime.contains("yaml", true) -> "yaml"
            mime.contains("plain", true) -> "txt"
            mime.contains("mp4", true) -> "mp4"
            mime.contains("webm", true) -> "webm"
            mime.contains("png", true) -> "png"
            mime.contains("webp", true) -> "webp"
            mime.contains("jpeg", true) -> "jpg"
            mime.contains("mpeg", true) -> "mp3"
            mime.contains("quicktime", true) -> "mov"
            mime.contains("matroska", true) -> "mkv"
            kind == ResourceKind.VIDEO -> "mp4"
            kind == ResourceKind.SEGMENT -> "ts"
            kind == ResourceKind.AUDIO -> "mp3"
            kind == ResourceKind.IMAGE -> "jpg"
            kind == ResourceKind.PLAYLIST -> if (mime.contains("dash", true)) "mpd" else "m3u8"
            else -> ""
        }
        return if (inferred.isBlank()) safe else "$safe.$inferred"
    }

    private fun safeFileName(name: String): String =
        FileNameSanitizer.sanitize(name, "download.bin")

    private fun defaultMimeType(kind: ResourceKind, fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val extensionMime = when (extension) {
            "pdf" -> "application/pdf"
            "epub" -> "application/epub+zip"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "rtf" -> "application/rtf"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz", "tgz" -> "application/gzip"
            "bz2" -> "application/x-bzip2"
            "xz" -> "application/x-xz"
            "apk", "aab", "apks", "xapk" -> "application/vnd.android.package-archive"
            "jar", "war" -> "application/java-archive"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "xml" -> "application/xml"
            "yaml", "yml" -> "application/yaml"
            "txt", "md", "log" -> "text/plain"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "opus" -> "audio/opus"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "avif" -> "image/avif"
            "svg" -> "image/svg+xml"
            else -> null
        }
        if (extensionMime != null) return extensionMime

        return when (kind) {
            ResourceKind.VIDEO -> "video/mp4"
            ResourceKind.SEGMENT -> "video/mp2t"
            ResourceKind.AUDIO -> "audio/mpeg"
            ResourceKind.IMAGE -> "image/jpeg"
            ResourceKind.PLAYLIST -> "application/vnd.apple.mpegurl"
            ResourceKind.DOCUMENT,
            ResourceKind.ARCHIVE,
            ResourceKind.APPLICATION,
            ResourceKind.FONT,
            ResourceKind.TEXT_DATA,
            ResourceKind.OTHER -> "application/octet-stream"
        }
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_URL = "url"
        const val KEY_TREE = "tree_uri"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_KIND = "kind"
        const val KEY_MIME = "mime"
        const val KEY_REFERER = "referer"
        const val KEY_PROGRESS = "progress"
        const val KEY_DETAIL = "detail"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_PARTIAL_URI = "partial_uri"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SPEED_BYTES_PER_SECOND = "speed_bytes_per_second"
        const val KEY_ADAPTIVE_VIDEO_HEIGHT = "adaptive_video_height"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "file_sniffing_downloads"
        private const val NOTIFICATION_ID = 4107
    }
}
