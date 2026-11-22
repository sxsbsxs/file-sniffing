package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YtDlpSessionKeyTest {
    private val url = "https://www.nicovideo.jp/watch/sm37593092"

    @Test
    fun sameSelectionProducesSamePrivateSessionKeyAcrossWorkRetries() {
        val first = key(format = "137+140")
        val retry = key(format = "137+140")

        assertEquals(first, retry)
        assertEquals(64, first.length)
        assertFalse(first.contains("nicovideo"))
        assertFalse(first.contains("sm37593092"))
    }

    @Test
    fun differentFormatOrDestinationUsesAnIsolatedSession() {
        assertNotEquals(key(format = "137+140"), key(format = "248+140"))
        assertNotEquals(key(format = "137+140"), key(format = "137+140", destination = "content://tree/other"))
    }

    private fun key(
        format: String,
        destination: String = "content://tree/downloads"
    ) = YtDlpSessionKey.create(
        sourceUrl = url,
        resourceKind = ResourceKind.VIDEO,
        formatSelector = format,
        fileName = "sample-1080p.mp4",
        destinationTreeUri = destination
    )
}
