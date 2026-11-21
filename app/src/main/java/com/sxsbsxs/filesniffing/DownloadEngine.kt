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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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

                val responseMime = connection.contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val mimeType = responseMime ?: resource.mimeType ?: defaultMimeType(resource.kind)
                val serverName = parseContentDisposition(connection.getHeaderField("Content-Disposition"))
                val targetName = ensureExtension(
                    serverName ?: resource.fileName,
                    mimeType,
                    resource.kind
                )

                val parentDocument = DocumentsContract.buildDocumentUriUsingTree(
                    destinationTree,
                    DocumentsContract.getTreeDocumentId(destinationTree)
                )
                val resolver = context.contentResolver
                createdDocument = DocumentsContract.createDocument(
                    resolver,
                    parentDocument,
                    mimeType,
                    targetName
                ) ?: error("无法在所选文件夹创建文件")

                val totalBytes = connection.contentLengthLong
                var currentTask = initial.copy(
                    fileName = targetName,
                    progress = if (totalBytes > 0L) 0 else -1,
                    state = "downloading",
                    detail = if (totalBytes > 0L) "正在下载" else "正在下载 · 文件大小未知"
                )
                postUpdate(onUpdate, currentTask)

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
                                currentTask = currentTask.copy(
                                    progress = progress,
                                    detail = "${formatBytes(copied)} / ${formatBytes(totalBytes)}"
                                )
                                postUpdate(onUpdate, currentTask)
                            } else if (copied % (512 * 1024L) < buffer.size) {
                                currentTask = currentTask.copy(
                                    detail = "已下载 ${formatBytes(copied)}"
                                )
                                postUpdate(onUpdate, currentTask)
                            }
                        }
                    }
                }
                postUpdate(
                    onUpdate,
                    currentTask.copy(
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

    private fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val encoded = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        val plain = Regex("filename\\s*=\\s*\\\"?([^\\\";]+)", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        val raw = encoded ?: plain ?: return null
        val decoded = runCatching {
            URLDecoder.decode(raw.trim(), StandardCharsets.UTF_8.name())
        }.getOrDefault(raw.trim())
        return safeFileName(decoded).takeIf { it.isNotBlank() }
    }

    private fun ensureExtension(name: String, mime: String, kind: ResourceKind): String {
        val safe = safeFileName(name)
        val extension = safe.substringAfterLast('.', "")
        if (extension.isNotBlank() && extension.length <= 8) return safe
        val inferred = when {
            mime.contains("mpegurl", ignoreCase = true) -> "m3u8"
            mime.contains("dash+xml", ignoreCase = true) -> "mpd"
            mime.contains("mp4", ignoreCase = true) -> "mp4"
            mime.contains("webm", ignoreCase = true) -> "webm"
            mime.contains("png", ignoreCase = true) -> "png"
            mime.contains("webp", ignoreCase = true) -> "webp"
            mime.contains("gif", ignoreCase = true) -> "gif"
            mime.contains("jpeg", ignoreCase = true) -> "jpg"
            mime.contains("mpeg", ignoreCase = true) -> "mp3"
            mime.contains("ogg", ignoreCase = true) -> "ogg"
            kind == ResourceKind.VIDEO -> "mp4"
            kind == ResourceKind.AUDIO -> "mp3"
            kind == ResourceKind.IMAGE -> "jpg"
            kind == ResourceKind.PLAYLIST -> "m3u8"
            else -> "bin"
        }
        return "$safe.$inferred"
    }

    private fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.takeIf { it.isNotBlank() && it != "." } ?: "download"
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

    private fun postUpdate(callback: (DownloadTaskUi) -> Unit, task: DownloadTaskUi) {
        mainHandler.post { callback(task) }
    }
}
