package com.sxsbsxs.filesniffing.data.network

import com.sxsbsxs.filesniffing.ResourceKind
import java.util.Locale

/** Rejects known-incomplete or clearly non-media HTTP responses before publishing a file. */
internal object DownloadIntegrityPolicy {
    private val mediaKinds = setOf(
        ResourceKind.VIDEO,
        ResourceKind.AUDIO,
        ResourceKind.PLAYLIST,
        ResourceKind.SEGMENT
    )
    private val alwaysInvalidMediaMimeTypes = setOf(
        "text/html",
        "application/xhtml+xml",
        "application/json",
        "text/json",
        "application/problem+json",
        "application/xml",
        "text/xml"
    )

    fun failureReason(
        kind: ResourceKind,
        downloadedBytes: Long,
        totalBytes: Long,
        responseMimeType: String?,
        detectedContainer: String
    ): String? {
        if (downloadedBytes <= 0L) return "服务器返回了空文件，未保存为完成状态"
        if (totalBytes > 0L && downloadedBytes < totalBytes) {
            return "下载未完整：已接收 $downloadedBytes / $totalBytes 字节"
        }

        if (kind !in mediaKinds) return null

        val mime = responseMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        if (detectedContainer.equals("html", ignoreCase = true) ||
            mime in alwaysInvalidMediaMimeTypes ||
            (mime == "text/plain" && kind in setOf(ResourceKind.VIDEO, ResourceKind.AUDIO, ResourceKind.PLAYLIST, ResourceKind.SEGMENT))
        ) {
            return "下载地址返回了网页或错误数据，而不是媒体文件；请检查链接、访问权限或 Referer"
        }
        return null
    }
}
