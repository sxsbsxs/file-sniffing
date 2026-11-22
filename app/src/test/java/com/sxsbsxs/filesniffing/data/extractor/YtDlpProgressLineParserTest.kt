package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpProgressLineParserTest {
    @Test
    fun parsesStructuredYtDlpBytesTotalSpeedAndFormatId() {
        val metrics = YtDlpProgressLineParser.parse(
            """FILE_SNIFFING_PROGRESS|137|{"status":"downloading","downloaded_bytes":10485760,"total_bytes":83886080,"speed":2097152.0,"eta":35}"""
        )
        assertEquals("137", metrics.streamId)
        assertEquals("downloading", metrics.status)
        assertEquals(10L * 1024L * 1024L, metrics.downloadedBytes)
        assertEquals(80L * 1024L * 1024L, metrics.totalBytes)
        assertEquals(2L * 1024L * 1024L, metrics.speedBytesPerSecond)
        assertEquals(12.5f, metrics.progressPercent, 0.01f)
        assertTrue(metrics.hasDownloadProgress)
    }

    @Test
    fun usesEstimateOnlyWhenExactTotalIsMissing() {
        val metrics = YtDlpProgressLineParser.parse(
            """FILE_SNIFFING_PROGRESS|140|{"status":"downloading","downloaded_bytes":5242880,"total_bytes":null,"total_bytes_estimate":10485760,"speed":1024.0}"""
        )
        assertEquals(10L * 1024L * 1024L, metrics.totalBytes)
        assertTrue(metrics.totalBytesEstimated)
    }

    @Test
    fun parsesPostprocessorStatusWithoutInventingTransferSpeed() {
        val metrics = YtDlpProgressLineParser.parse("FILE_SNIFFING_POSTPROCESS|started|Merger")
        assertTrue(metrics.isPostProcessing)
        assertEquals("Merger", metrics.postProcessor)
        assertEquals(-1L, metrics.speedBytesPerSecond)
    }

    @Test
    fun parsesAria2cFallbackSpeedAndConnectionCount() {
        val metrics = YtDlpProgressLineParser.parse(
            "[#a1b2c3 12MiB/80MiB(15%) CN:16 DL:4.5MiB ETA:15s]"
        )
        assertEquals(4_718_592L, metrics.speedBytesPerSecond)
        assertEquals(16, metrics.activeConnections)
        assertEquals(12L * 1024L * 1024L, metrics.downloadedBytes)
        assertEquals(80L * 1024L * 1024L, metrics.totalBytes)
    }

    @Test
    fun parsesYtDlpNativeDownloadSpeedAsFallback() {
        val metrics = YtDlpProgressLineParser.parse(
            "[download] 25.0% of 20.00MiB at 2.00MiB/s ETA 00:07"
        )
        assertEquals(2_097_152L, metrics.speedBytesPerSecond)
        assertEquals(20L * 1024L * 1024L, metrics.totalBytes)
        assertEquals(5L * 1024L * 1024L, metrics.downloadedBytes)
    }

    @Test
    fun unknownStatusLineDoesNotInventSpeedOrConnections() {
        val metrics = YtDlpProgressLineParser.parse("[Merger] Merging formats into video.mp4")
        assertEquals(-1L, metrics.speedBytesPerSecond)
        assertEquals(-1, metrics.activeConnections)
    }
}
