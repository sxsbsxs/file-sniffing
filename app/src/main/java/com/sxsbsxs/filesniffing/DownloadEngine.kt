package com.sxsbsxs.filesniffing

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

object DownloadEngine {
    private val workers = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(
        context: Context,
        destinationTree: Uri,
        resource: SniffedResource,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val taskId = UUID.randomUUID().toString()
        val initial = DownloadTaskUi(
            id = taskId,
            fileName = resource.fileName,
            progress = 0,
            state = "queued",
            detail = "等待下载"
        )
        mainHandler.post { onUpdate(initial) }

        workers.execute {
            var connection: HttpURLConnection? = null
            var createdDocument: Uri? = null
            try {
                val parsed = Uri.parse(resource.url)
                require(parsed.scheme == "http" || parsed.scheme == "https") {
                    "只支持 HTTP 或 HTTPS 资源"
                }

                val parentDocument = DocumentsContract.buildDocumentUriUsingTree(
                    destinationTree,
                    DocumentsContract.getTreeDocumentId(destinationTree)
                )
                val resolver = context.contentResolver
                val mimeType = resource.mimeType ?: defaultMimeType(resource.kind)
                val safeName = safeFileName(resource.fileName)
                createdDocument = DocumentsContract.createDocument(
                    resolver,
                    parentDocument,
                    mimeType,
                    safeName
                ) ?: error("无法在所选文件夹创建文件")

                connection = (URL(resource.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "*/*")
                    if (resource.sourcePage.isNotBlank()) {
                        setRequestProperty("Referer", resource.sourcePage)
                    }
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    error("服务器返回 HTTP $responseCode")
                }
                val totalBytes = connection.contentLengthLong
                postUpdate(onUpdate, initial.copy(state = "downloading", detail = "正在下载"))

                val input = BufferedInputStream(connection.inputStream)
                val output = resolver.openOutputStream(createdDocument, "w")
                    ?: error("无法写入目标文件")
                input.use { source ->
                    BufferedOutputStream(output).use { target ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            target.write(buffer, 0, count)
                            copied += count
                            if (totalBytes > 0L) {
                                val progress = ((copied * 100.0) / totalBytes)
                                    .roundToInt().coerceIn(0, 99)
                                postUpdate(
                                    onUpdate,
                                    initial.copy(
                                        progress = progress,
                                        state = "downloading",
                                        detail = "${formatBytes(copied)} / ${formatBytes(totalBytes)}"
                                    )
                                )
                            } else if (copied % (512 * 1024L) < buffer.size) {
                                postUpdate(
                                    onUpdate,
                                    initial.copy(
                                        state = "downloading",
                                        detail = "已下载 ${formatBytes(copied)}"
                                    )
                                )
                            }
                        }
                    }
                }
                postUpdate(
                    onUpdate,
                    initial.copy(
                        progress = 100,
                        state = "completed",
                        detail = "已保存到所选文件夹"
                    )
                )
            } catch (error: Exception) {
                createdDocument?.let { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                postUpdate(
                    onUpdate,
                    initial.copy(
                        state = "failed",
                        detail = error.message ?: "下载失败"
                    )
                )
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun postUpdate(
        callback: (DownloadTaskUi) -> Unit,
        task: DownloadTaskUi
    ) {
        mainHandler.post { callback(task) }
    }

    private fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.takeIf { it.isNotBlank() && it != "." } ?: "download.bin"
    }

    private fun defaultMimeType(kind: ResourceKind): String = when (kind) {
        ResourceKind.VIDEO -> "video/mp4"
        ResourceKind.AUDIO -> "audio/mpeg"
        ResourceKind.IMAGE -> "image/jpeg"
        ResourceKind.PLAYLIST -> "application/vnd.apple.mpegurl"
        ResourceKind.OTHER -> "application/octet-stream"
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "${value} B"
        val kb = value / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}
