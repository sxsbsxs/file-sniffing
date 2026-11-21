package com.sxsbsxs.filesniffing

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.WebSettings
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

data class DashDownloadResult(
    val documentUri: Uri,
    val fileName: String,
    val segmentCount: Int
)

object DashDownloader {
    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private const val MAX_SEGMENTS = 20_000

    fun download(
        context: Context,
        destinationTree: Uri,
        resource: SniffedResource,
        onProgress: (Int, String) -> Unit
    ): DashDownloadResult {
        val manifestUrl = URL(resource.url)
        val xmlBytes = fetchBytes(context, manifestUrl, resource.sourcePage, MAX_MANIFEST_BYTES)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(ByteArrayInputStream(xmlBytes)))
        val root = document.documentElement ?: error("DASH MPD 文件无效")
        require(localName(root) == "MPD") { "资源不是有效的 DASH MPD 清单" }
        val dynamic = root.getAttribute("type").equals("dynamic", ignoreCase = true)
        require(!dynamic) { "暂不支持动态 DASH 直播清单" }

        val rootBase = resolveBase(manifestUrl, directText(root, "BaseURL"))
        val period = directChildren(root, "Period").firstOrNull()
            ?: error("DASH 清单没有 Period")
        val periodBase = resolveBase(rootBase, directText(period, "BaseURL"))
        val candidates = mutableListOf<RepresentationCandidate>()

        for (adaptation in directChildren(period, "AdaptationSet")) {
            val adaptationBase = resolveBase(periodBase, directText(adaptation, "BaseURL"))
            val adaptationType = adaptation.getAttribute("contentType")
                .ifBlank { adaptation.getAttribute("mimeType") }
            val representations = directChildren(adaptation, "Representation")
                .ifEmpty { listOf(adaptation) }

            for (representation in representations) {
                val repBase = resolveBase(adaptationBase, directText(representation, "BaseURL"))
                val mime = representation.getAttribute("mimeType")
                    .ifBlank { adaptation.getAttribute("mimeType") }
                val contentType = representation.getAttribute("contentType")
                    .ifBlank { adaptationType }
                val bandwidth = representation.getAttribute("bandwidth").toLongOrNull() ?: 0L
                val id = representation.getAttribute("id").ifBlank { "0" }
                val listElement = directChild(representation, "SegmentList")
                    ?: directChild(adaptation, "SegmentList")
                    ?: directChild(period, "SegmentList")
                val templateElement = directChild(representation, "SegmentTemplate")
                    ?: directChild(adaptation, "SegmentTemplate")
                    ?: directChild(period, "SegmentTemplate")

                val segments = when {
                    listElement != null -> parseSegmentList(listElement, repBase)
                    templateElement != null -> parseSegmentTemplate(
                        templateElement,
                        repBase,
                        id,
                        bandwidth
                    )
                    else -> null
                } ?: continue

                candidates += RepresentationCandidate(
                    bandwidth = bandwidth,
                    isVideo = contentType.contains("video", true) || mime.startsWith("video/", true),
                    base = repBase,
                    mime = mime,
                    segments = segments
                )
            }
        }

        val selected = candidates
            .filter { it.segments.media.isNotEmpty() }
            .let { values ->
                val videos = values.filter { it.isVideo }
                (videos.ifEmpty { values }).maxByOrNull { it.bandwidth }
            } ?: error("DASH 清单没有可下载的 SegmentList 或 SegmentTemplate")

        val segmentUrls = selected.segments.media
        require(segmentUrls.size <= MAX_SEGMENTS) { "DASH 分段数量过多，已停止下载" }
        require(segmentUrls.isNotEmpty()) { "DASH Representation 没有媒体分段" }

        val hasInitialization = selected.segments.initialization != null
        val outputExtension = when {
            hasInitialization -> "mp4"
            selected.mime.contains("mp2t", true) -> "ts"
            else -> "mp4"
        }
        val outputName = makeOutputName(resource.fileName, outputExtension)
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            destinationTree,
            DocumentsContract.getTreeDocumentId(destinationTree)
        )
        val mimeType = if (outputExtension == "ts") "video/mp2t" else "video/mp4"
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            mimeType,
            outputName
        ) ?: error("无法在所选文件夹创建 DASH 输出文件")

        try {
            val output = context.contentResolver.openOutputStream(documentUri, "w")
                ?: error("无法写入 DASH 输出文件")
            output.use { raw ->
                BufferedOutputStream(raw).use { sink ->
                    selected.segments.initialization?.let { init ->
                        streamTo(context, init, resource.sourcePage, sink)
                    }
                    val total = segmentUrls.size
                    segmentUrls.forEachIndexed { index, segment ->
                        streamTo(context, segment, resource.sourcePage, sink)
                        val progress = ((index + 1) * 100f / total).toInt().coerceIn(1, 99)
                        onProgress(progress, "已合并 DASH 分段 ${index + 1}/$total")
                    }
                    sink.flush()
                }
            }
            onProgress(100, "已合并 ${segmentUrls.size} 个 DASH 分段")
            return DashDownloadResult(documentUri, outputName, segmentUrls.size)
        } catch (error: Exception) {
            runCatching { context.contentResolver.delete(documentUri, null, null) }
            throw error
        }
    }

    private data class RepresentationCandidate(
        val bandwidth: Long,
        val isVideo: Boolean,
        val base: URL,
        val mime: String,
        val segments: SegmentSet
    )

    private data class SegmentSet(
        val initialization: URL?,
        val media: List<URL>
    )

    private fun parseSegmentList(element: Element, base: URL): SegmentSet {
        require(directChildren(element, "SegmentURL").none { it.hasAttribute("mediaRange") }) {
            "暂不支持 DASH SegmentList 字节范围"
        }
        val init = directChild(element, "Initialization")
            ?.getAttribute("sourceURL")
            ?.takeIf { it.isNotBlank() }
            ?.let { URL(base, it) }
        val media = directChildren(element, "SegmentURL").mapNotNull { item ->
            item.getAttribute("media")
                .takeIf { it.isNotBlank() }
                ?.let { URL(base, it) }
        }
        return SegmentSet(init, media)
    }

    private fun parseSegmentTemplate(
        element: Element,
        base: URL,
        representationId: String,
        bandwidth: Long
    ): SegmentSet {
        val initializationTemplate = element.getAttribute("initialization").takeIf { it.isNotBlank() }
        val mediaTemplate = element.getAttribute("media").takeIf { it.isNotBlank() }
            ?: error("DASH SegmentTemplate 缺少 media 模板")
        val startNumber = element.getAttribute("startNumber").toLongOrNull() ?: 1L
        val initialization = initializationTemplate?.let { template ->
            URL(base, substitute(template, representationId, bandwidth, startNumber, 0L))
        }
        val timeline = directChild(element, "SegmentTimeline")
        val mediaUrls = mutableListOf<URL>()

        if (timeline != null) {
            var nextNumber = startNumber
            var nextTime = 0L
            for (segment in directChildren(timeline, "S")) {
                val duration = segment.getAttribute("d").toLongOrNull()
                    ?: error("DASH SegmentTimeline 缺少时长")
                require(duration > 0L) { "DASH SegmentTimeline 分段时长无效" }
                val explicitTime = segment.getAttribute("t").toLongOrNull()
                if (explicitTime != null) nextTime = explicitTime
                val repeatCount = segment.getAttribute("r").toIntOrNull() ?: 0
                require(repeatCount >= 0) { "暂不支持 DASH SegmentTimeline 负数 repeat" }
                require(repeatCount <= MAX_SEGMENTS) { "DASH SegmentTimeline repeat 过大" }
                repeat(repeatCount + 1) {
                    require(mediaUrls.size < MAX_SEGMENTS) { "DASH 分段数量超过限制" }
                    val resolved = substitute(
                        mediaTemplate,
                        representationId,
                        bandwidth,
                        nextNumber,
                        nextTime
                    )
                    mediaUrls += URL(base, resolved)
                    nextNumber++
                    nextTime += duration
                }
            }
        } else {
            error("当前版本仅支持带 SegmentTimeline 的 DASH SegmentTemplate")
        }
        return SegmentSet(initialization, mediaUrls)
    }

    private fun substitute(
        template: String,
        representationId: String,
        bandwidth: Long,
        number: Long,
        time: Long
    ): String {
        var result = template
            .replace("$" + "RepresentationID" + "$", representationId)
            .replace("$" + "Bandwidth" + "$", bandwidth.toString())
            .replace("$" + "Time" + "$", time.toString())
        val numberPattern = Regex("\\$Number(?:%0(\\d+)d)?\\$")
        result = numberPattern.replace(result) { match ->
            val width = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
            if (width > 0) number.toString().padStart(width, '0') else number.toString()
        }
        return result
    }

    private fun directText(element: Element, name: String): String? =
        directChild(element, name)?.textContent?.trim()?.takeIf { it.isNotBlank() }

    private fun directChild(parent: Element, wanted: String): Element? =
        directChildren(parent, wanted).firstOrNull()

    private fun directChildren(parent: Element, wanted: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType == Node.ELEMENT_NODE && localName(node) == wanted) {
                result += node as Element
            }
        }
        return result
    }

    private fun localName(node: Node): String =
        node.localName ?: node.nodeName.substringAfter(':')

    private fun resolveBase(parent: URL, child: String?): URL =
        if (child.isNullOrBlank()) parent else URL(parent, child.trim())

    private fun fetchBytes(context: Context, url: URL, referer: String, maxBytes: Int): ByteArray {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(context))
            setRequestProperty("Accept", "*/*")
            if (referer.isNotBlank()) setRequestProperty("Referer", referer)
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "读取 DASH MPD 失败：HTTP $status" }
            require(connection.contentLengthLong <= maxBytes || connection.contentLengthLong < 0L) {
                "DASH MPD 超过大小限制"
            }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "DASH MPD 超过大小限制" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
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
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(context))
            setRequestProperty("Accept", "*/*")
            if (referer.isNotBlank()) setRequestProperty("Referer", referer)
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "下载 DASH 分段失败：HTTP $status" }
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

    private fun makeOutputName(input: String, extension: String): String {
        val base = input.substringBeforeLast('.', input)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
            .ifBlank { "dash_video" }
        return "$base.$extension"
    }
}
