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
import com.sxsbsxs.filesniffing.data.network.OkDownloadTransferEngine
import com.sxsbsxs.filesniffing.data.media.Media3AdaptiveProcessor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@HiltWorker
class PersistentDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val okDownloadTransferEngine: OkDownloadTransferEngine,
    private val media3AdaptiveProcessor: Media3AdaptiveProcessor,
    private val downloadRepository: DownloadRepository
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL).orEmpty()
        val treeValue = inputData.getString(KEY_TREE).orEmpty()
        val fileName = inputData.getString(KEY_FILE_NAME).orEmpty()
        val kindName = inputData.getString(KEY_KIND).orEmpty()
        val mime = inputData.getString(KEY_MIME)
        val referer = inputData.getString(KEY_REFERER).orEmpty()

        if (url.isBlank() || treeValue.isBlank() || fileName.isBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "下载任务参数不完整"))
        }

        val kind = runCatching { ResourceKind.valueOf(kindName) }.getOrDefault(ResourceKind.OTHER)
        val resource = SniffedResource(
            url = url,
            kind = kind,
            fileName = fileName,
            mimeType = mime,
            pageTitle = "",
            sourcePage = referer
        )
        val tree = Uri.parse(treeValue)
        var createdDocument: Uri? = null

        try {
            setForeground(createForegroundInfo(fileName, -1, "正在准备下载"))
            publishProgressAsync(-1, "正在准备下载", fileName)
            persistRecord(
                fileName = fileName,
                sourceUrl = url,
                kind = kind,
                mimeType = mime,
                state = "downloading",
                progress = -1,
                detail = "正在准备下载",
                documentUri = null
            )

            val isAdaptiveManifest = kind == ResourceKind.PLAYLIST &&
                (fileName.endsWith(".m3u8", ignoreCase = true) ||
                    fileName.endsWith(".mpd", ignoreCase = true))
            if (isAdaptiveManifest) {
                val result = media3AdaptiveProcessor.exportAdaptiveStream(
                    resource = resource,
                    destinationTree = tree
                ) { progress, detail ->
                    publishProgressAsync(progress, detail, fileName)
                }
                persistRecord(
                    fileName = result.fileName,
                    sourceUrl = url,
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
            val mimeType = mime ?: defaultMimeType(kind)
            val targetName = ensureExtension(fileName, mimeType, kind)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree)
            )
            val reusableDocument = inputData.getString(KEY_PARTIAL_URI)
                ?.let { rawUri ->
                    val candidate = Uri.parse(rawUri)
                    runCatching {
                        applicationContext.contentResolver.openFileDescriptor(candidate, "rw")?.use { }
                            ?: error("断点文件不可写")
                        candidate
                    }.getOrNull()
                }
            val destinationDocument = reusableDocument ?: DocumentsContract.createDocument(
                applicationContext.contentResolver,
                parent,
                mimeType,
                targetName
            ) ?: error("无法在所选文件夹创建文件")
            createdDocument = destinationDocument
            persistRecord(
                fileName = targetName,
                sourceUrl = url,
                kind = kind,
                mimeType = mimeType,
                state = "downloading",
                progress = 0,
                detail = if (reusableDocument != null) "正在从断点继续下载" else "正在下载",
                documentUri = destinationDocument.toString()
            )

            val headers = buildMap<String, List<String>> {
                put("User-Agent", listOf(android.webkit.WebSettings.getDefaultUserAgent(applicationContext)))
                put("Accept", listOf("*/*"))
                if (referer.isNotBlank()) put("Referer", listOf(referer))
            }
            publishProgressAsync(
                -1,
                if (reusableDocument != null) "正在恢复断点下载" else "正在下载（OkDownload）",
                targetName,
                destinationDocument.toString()
            )
            okDownloadTransferEngine.downloadToDocument(
                url = url,
                destinationDocument = destinationDocument,
                headers = headers
            ) { progress, detail ->
                publishProgressAsync(progress, detail, targetName, destinationDocument.toString())
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
            val completedDetail = if (detectedContainer == "unknown") {
                "已保存到所选文件夹"
            } else {
                "下载完成，检测格式：$detectedContainer"
            }

            persistRecord(
                fileName = targetName,
                sourceUrl = url,
                kind = kind,
                mimeType = mimeType,
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
            withContext(NonCancellable) {
                persistRecord(
                    fileName = fileName,
                    sourceUrl = url,
                    kind = kind,
                    mimeType = mime,
                    state = "cancelled",
                    progress = 0,
                    detail = "下载已取消，可继续断点下载",
                    documentUri = createdDocument?.toString()
                )
            }
            throw cancelled
        } catch (error: Exception) {
            persistRecord(
                fileName = fileName,
                sourceUrl = url,
                kind = kind,
                mimeType = mime,
                state = "failed",
                progress = 0,
                detail = error.message ?: "下载失败，可重试",
                documentUri = createdDocument?.toString()
            )
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "下载失败")))
        }
    }

    private fun publishProgressAsync(
        progress: Int,
        detail: String,
        fileName: String,
        partialUri: String? = null
    ) {
        val data = androidx.work.Data.Builder()
            .putInt(KEY_PROGRESS, progress)
            .putString(KEY_DETAIL, detail)
            .putString(KEY_FILE_NAME, fileName)
            .apply {
                if (!partialUri.isNullOrBlank()) putString(KEY_PARTIAL_URI, partialUri)
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
                destinationTreeUri = inputData.getString(KEY_TREE)
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
        val safe = FileNameSanitizer.sanitize(name, "download.bin")
        val ext = safe.substringAfterLast('.', "")
        if (ext.isNotBlank() && ext.length <= 8) return safe
        val inferred = when {
            mime.contains("mp4", true) -> "mp4"
            mime.contains("webm", true) -> "webm"
            mime.contains("png", true) -> "png"
            mime.contains("webp", true) -> "webp"
            mime.contains("jpeg", true) -> "jpg"
            mime.contains("mpeg", true) -> "mp3"
            kind == ResourceKind.VIDEO -> "mp4"
            kind == ResourceKind.SEGMENT -> "ts"
            kind == ResourceKind.AUDIO -> "mp3"
            kind == ResourceKind.IMAGE -> "jpg"
            kind == ResourceKind.PLAYLIST -> "m3u8"
            else -> "bin"
        }
        return "$safe.$inferred"
    }

    private fun safeFileName(name: String): String =
        FileNameSanitizer.sanitize(name, "download.bin")

    private fun defaultMimeType(kind: ResourceKind): String = when (kind) {
        ResourceKind.VIDEO -> "video/mp4"
        ResourceKind.SEGMENT -> "video/mp2t"
        ResourceKind.AUDIO -> "audio/mpeg"
        ResourceKind.IMAGE -> "image/jpeg"
        ResourceKind.PLAYLIST -> "application/vnd.apple.mpegurl"
        ResourceKind.OTHER -> "application/octet-stream"
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
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "file_sniffing_downloads"
        private const val NOTIFICATION_ID = 4107
    }
}
