package com.sxsbsxs.filesniffing

import android.content.Context
import android.net.Uri
import android.webkit.WebSettings
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class HlsDownloadResult(
    val documentUri: Uri,
    val fileName: String
)

object HlsDownloader {
    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private const val MAX_SEGMENTS = 20_000

    fun download(
        context: Context,
        destinationTree: Uri,
        resource: SniffedResource,
        onProgress: (Int, String) -> Unit
    ): HlsDownloadResult {
        val manifestUrl = URL(resource.url)
        var manifestText = readText(context, manifestUrl, resource.sourcePage)
        val selectedManifestUrl = selectVariant(manifestText, manifestUrl)
        if (selectedManifestUrl != null) {
            manifestText = readText(context, selectedManifestUrl, resource.sourcePage)
        }
        val mediaBaseUrl = selectedManifestUrl ?: manifestUrl
        val playlist = parseMediaPlaylist(manifestText, mediaBaseUrl)
        require(playlist.segments.isNotEmpty()) { "HLS 清单中没有可下载的媒体分段" }
        require(playlist.segments.size <= MAX_SEGMENTS) { "分段数量过多，已停止下载" }

        val isFragmentedMp4 = playlist.initializationSegment != null
        val outputName = makeOutputName(
            resource.fileName,
            if (isFragmentedMp4) "mp4" else "ts"
        )
        val parentDocument = DocumentsContract.buildDocumentUriUsingTree(
            destinationTree,
            DocumentsContract.getTreeDocumentId(destinationTree)
        )
        val document = DocumentsContract.createDocument(
            context.contentResolver,
            parentDocument,
            if (isFragmentedMp4) "video/mp4" else "video/mp2t",
            outputName
        ) ?: error("无法在所选文件夹创建 HLS 输出文件")

        try {
            val output = context.contentResolver.openOutputStream(document, "w")
                ?: error("无法写入 HLS 输出文件")
            output.use { raw ->
                BufferedOutputStream(raw).use { sink ->
                    playlist.initializationSegment?.let { init ->
                        streamTo(context, init, resource.sourcePage, sink)
                    }
                    val total = playlist.segments.size
                    playlist.segments.forEachIndexed { index, segment ->
                        streamTo(context, segment, resource.sourcePage, sink)
                        val progress = ((index + 1) * 100f / total).toInt().coerceIn(1, 99)
                        onProgress(
                            progress,
                            "已合并 ${index + 1}/$total 个分段"
                        )
                    }
                    sink.flush()
                }
            }
            val detail = if (playlist.isLiveSnapshot) {
                "已保存当前直播清单中的 playlist.segments.size 个分段（快照）"
            } else {
                "已合并 playlist.segments.size 个分段"
            }
            onProgress(100, detail)
            return HlsDownloadResult(document, outputName)
        } catch (error: Exception) {
            runCatching { context.contentResolver.delete(document, null, null) }
            throw error
        }
    }

    private data class ParsedPlaylist(
        val segments: List<URL>,
        val initializationSegment: URL?,
        val isLiveSnapshot: Boolean
    )

    private fun selectVariant(text: String, masterUrl: URL): URL? {
        val lines = text.lineSequence().map { it.trim() }.toList()
        if (lines.none { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }) {
            return null
        }
        data class Variant(val bandwidth: Long, val url: URL)
        val variants = mutableListOf<Variant>()
        var pendingBandwidth: Long? = null
        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                pendingBandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            } else if (line.isNotBlank() && !line.startsWith("#") && pendingBandwidth != null) {
                variants += Variant(pendingBandwidth, URL(masterUrl, line))
                pendingBandwidth = null
            }
        }
        return variants.maxByOrNull { it.bandwidth }?.url
            ?: error("HLS 主清单中没有可用的播放变体")
    }

    private fun parseMediaPlaylist(text: String, baseUrl: URL): ParsedPlaylist {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        val segments = mutableListOf<URL>()
        var initSegment: URL? = null
        var isLiveSnapshot = true

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                    val method = Regex("METHOD=([^,]+)", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.getOrNull(1)
                        ?.trim()?.trim('"') ?: "UNKNOWN"
                    require(method.equals("NONE", ignoreCase = true)) {
                        "此 HLS 清单使用 $method 加密；当前版本不处理加密密钥"
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE", ignoreCase = true) -> {
                    error("当前版本暂不支持 HLS BYTERANGE 字节范围分段")
                }
                line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                    val uriValue = Regex("URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.getOrNull(1)
                        ?: error("HLS 初始化分段缺少 URI")
                    require(!line.contains("BYTERANGE", ignoreCase = true)) {
                        "当前版本暂不支持初始化分段 BYTERANGE"
                    }
                    initSegment = URL(baseUrl, uriValue)
                }
                line.equals("#EXT-X-ENDLIST", ignoreCase = true) -> {
                    isLiveSnapshot = false
                }
                line.startsWith("#") -> Unit
                else -> segments += URL(baseUrl, line)
            }
        }
        return ParsedPlaylist(segments, initSegment, isLiveSnapshot)
    }

    private fun readText(context: Context, url: URL, referer: String): String {
        val connection = openConnection(context, url, referer)
        try {
            val code = connection.responseCode
            require(code in 200..299) { "读取 HLS 清单失败：HTTP $code" }
            val length = connection.contentLengthLong
            require(length <= MAX_MANIFEST_BYTES || length < 0L) { "HLS 清单超过大小限制" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_MANIFEST_BYTES) { "HLS 清单超过大小限制" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        } finally {
            connection.disconnect()
        }
    }

    private fun streamTo(
        context: Context,
        url: URL,
        referer: String,
        output: BufferedOutputStream
    ) {
        val connection = openConnection(context, url, referer)
        try {
            val code = connection.responseCode
            require(code in 200..299) { "下载媒体分段失败：HTTP $code" }
            BufferedInputStream(connection.inputStream).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(context: Context, url: URL, referer: String): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(context))
            setRequestProperty("Accept", "*/*")
            if (referer.isNotBlank()) setRequestProperty("Referer", referer)
        }
    }

    private fun makeOutputName(input: String, extension: String): String {
        val cleaned = input
            .substringBeforeLast('.', input)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
            .ifBlank { "hls_video" }
        return "$cleaned.$extension"
    }
}
