package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpProgressLineParserTest {

    @Test
    fun parsesAria2cDownloadSpeedAndConnectionCount() {
        val metrics = YtDlpProgressLineParser.parse(
            "[#a1b2c3 12MiB/80MiB(15%) CN:16 DL:4.5MiB ETA:15s]"
        )
        assertEquals(4_718_592L, metrics.speedBytesPerSecond)
        assertEquals(16, metrics.activeConnections)
    }

    @Test
    fun parsesYtDlpNativeDownloadSpeed() {
        val metrics = YtDlpProgressLineParser.parse(
            "[download] 25.0% of 20.00MiB at 2.00MiB/s ETA 00:07"
        )
        assertEquals(2_097_152L, metrics.speedBytesPerSecond)
        assertEquals(-1, metrics.activeConnections)
    }

    @Test
    fun unknownStatusLineDoesNotInventSpeedOrConnections() {
        val metrics = YtDlpProgressLineParser.parse("[Merger] Merging formats into video.mp4")
        assertEquals(-1L, metrics.speedBytesPerSecond)
        assertEquals(-1, metrics.activeConnections)
    }
}
