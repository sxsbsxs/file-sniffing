package com.sxsbsxs.filesniffing.data.network

import com.sxsbsxs.filesniffing.ResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadIntegrityPolicyTest {
    @Test
    fun rejectsEmptyAndKnownIncompleteResponses() {
        assertEquals(
            "服务器返回了空文件，未保存为完成状态",
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.VIDEO, 0L, 0L, "video/mp4", "unknown"
            )
        )
        assertEquals(
            "下载未完整：已接收 800 / 1000 字节",
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.VIDEO, 800L, 1000L, "video/mp4", "mp4"
            )
        )
    }

    @Test
    fun rejectsHtmlAndJsonResponsesForMedia() {
        assertEquals(
            "下载地址返回了网页或错误数据，而不是媒体文件；请检查链接、访问权限或 Referer",
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.VIDEO, 1000L, 1000L, "text/html; charset=utf-8", "unknown"
            )
        )
        assertEquals(
            "下载地址返回了网页或错误数据，而不是媒体文件；请检查链接、访问权限或 Referer",
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.AUDIO, 1000L, 1000L, "application/json", "unknown"
            )
        )
    }

    @Test
    fun rejectsJsonErrorPayloadEvenWhenServerUsesGenericBinaryMime() {
        assertEquals(
            "下载地址返回了网页或错误数据，而不是媒体文件；请检查链接、访问权限或 Referer",
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.VIDEO, 1000L, 1000L, "application/octet-stream", "json"
            )
        )
    }

    @Test
    fun trustsRecognizedMediaSignatureOverIncorrectPlainTextMime() {
        assertNull(
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.VIDEO, 1000L, 1000L, "text/plain", "mp4"
            )
        )
        assertEquals(
            "下载地址返回了网页或错误数据，而不是媒体文件；请检查链接、访问权限或 Referer",
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.VIDEO, 1000L, 1000L, "text/plain", "unknown"
            )
        )
    }

    @Test
    fun permitsNonEmptyUnknownLengthBinaryAndLegitimateTextDocuments() {
        assertNull(
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.VIDEO, 512L, -1L, "application/octet-stream", "unknown"
            )
        )
        assertNull(
            DownloadIntegrityPolicy.failureReason(
                ResourceKind.TEXT_DATA, 120L, 120L, "text/plain", "unknown"
            )
        )
    }
}
