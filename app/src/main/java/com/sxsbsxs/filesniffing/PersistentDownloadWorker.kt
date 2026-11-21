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
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@HiltWorker
class PersistentDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val okDownloadTransferEngine: OkDownloadTransferEngine,
    private val downloadRepository: DownloadRepository
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
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

            if (kind == ResourceKind.PLAYLIST && fileName.endsWith(".m3u8", ignoreCase = true)) {
                val result = HlsDownloader.download(
                    context = applicationContext,
                    destinationTree = tree,
                    resource = resource
                ) { progress, detail ->
                    publishProgressAsync(progress, detail, fileName)
                }
                persistRecord(
                    fileName = result.fileName,
                    sourceUrl = url,
                    kind = kind,
                    mimeType = mime,
                    state = "completed",
                    progress = 100,
                    detail = "HLS 分段已合并保存",
                    documentUri = result.documentUri.toString()
                )
                return@withContext Result.success(
                    workDataOf(
                        KEY_FILE_NAME to result.fileName,
                        KEY_FILE_URI to result.documentUri.toString(),
                        KEY_DETAIL to "HLS 分段已合并保存"
                    )
                )
            }

            if (kind == ResourceKind.PLAYLIST && fileName.endsWith(".mpd", ignoreCase = true)) {
                val result = DashDownloader.download(
                    context = applicationContext,
                    destinationTree = tree,
                    resource = resource
                ) { progress, detail ->
                    publishProgressAsync(progress, detail, fileName)
                }
                persistRecord(
                    fileName = result.fileName,
                    sourceUrl = url,
                    kind = kind,
                    mimeType = mime,
                    state = "completed",
                    progress = 100,
                    detail = "DASH 分段已合并保存",
                    documentUri = result.documentUri.toString()
                )
                return@withContext Result.success(
                    workDataOf(
                        KEY_FILE_NAME to result.fileName,
                        KEY_FILE_URI to result.documentUri.toString(),
                        KEY_DETAIL to "DASH 分段已合并保存（${result.segmentCount} 段）"
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
            createdDocument = DocumentsContract.createDocument(
                applicationContext.contentResolver,
                parent,
                mimeType,
                targetName
            ) ?: error("无法在所选文件夹创建文件")

            val headers = buildMap<String, List<String>> {
                put("User-Agent", listOf(android.webkit.WebSettings.getDefaultUserAgent(applicationContext)))
                put("Accept", listOf("*/*"))
                if (referer.isNotBlank()) put("Referer", listOf(referer))
            }
            publishProgressAsync(-1, "正在下载（OkDownload）", targetName)
            okDownloadTransferEngine.downloadToDocument(
                url = url,
                destinationDocument = createdDocument,
                headers = headers
            ) { progress, detail ->
                publishProgressAsync(progress, detail, targetName)
            }

            persistRecord(
                fileName = targetName,
                sourceUrl = url,
                kind = kind,
                mimeType = mimeType,
                state = "completed",
                progress = 100,
                detail = "已保存到所选文件夹",
                documentUri = createdDocument.toString()
            )
            Result.success(
                workDataOf(
                    KEY_FILE_NAME to targetName,
                    KEY_FILE_URI to createdDocument.toString(),
                    KEY_DETAIL to "已保存到所选文件夹"
                )
            )
        } catch (cancelled: CancellationException) {
            createdDocument?.let { runCatching { applicationContext.contentResolver.delete(it, null, null) } }
            persistRecord(
                fileName = fileName,
                sourceUrl = url,
                kind = kind,
                mimeType = mime,
                state = "cancelled",
                progress = 0,
                detail = "下载已取消",
                documentUri = null
            )
            throw cancelled
        } catch (error: Exception) {
            createdDocument?.let { runCatching { applicationContext.contentResolver.delete(it, null, null) } }
            persistRecord(
                fileName = fileName,
                sourceUrl = url,
                kind = kind,
                mimeType = mime,
                state = "failed",
                progress = 0,
                detail = error.message ?: "下载失败",
                documentUri = null
            )
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "下载失败")))
        }
    }

    private fun publishProgressAsync(progress: Int, detail: String, fileName: String) {
        setProgressAsync(workDataOf(KEY_PROGRESS to progress, KEY_DETAIL to detail, KEY_FILE_NAME to fileName))
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
        val id = id.toString()
        val previous = downloadRepository.findById(id)
        val now = System.currentTimeMillis()
        downloadRepository.save(
            DownloadRecordEntity(
                id = id,
                sourceUrl = sourceUrl,
                fileName = fileName,
                resourceKind = kind.name,
                mimeType = mimeType,
                state = state,
                progress = progress,
                detail = detail,
                destinationUri = documentUri ?: previous?.destinationUri,
                createdAtMillis = previous?.createdAtMillis ?: now,
                updatedAtMillis = now
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
        return safeFileName(java.net.URLDecoder.decode(raw.trim(), Charsets.UTF_8.name()))
    }

    private fun ensureExtension(name: String, mime: String, kind: ResourceKind): String {
        val safe = safeFileName(name)
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
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "download.bin" }

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
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "file_sniffing_downloads"
        private const val NOTIFICATION_ID = 4107
    }
}
