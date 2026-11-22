package com.sxsbsxs.filesniffing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueuePolicyTest {
    private val url = "https://www.nicovideo.jp/watch/sm37593092"

    @Test
    fun blocksSameUrlAlreadyQueuedInCurrentSession() {
        assertTrue(
            DownloadQueuePolicy.isAlreadyActive(
                sourceUrl = url,
                activeSessionUrls = listOf(url),
                persistedActiveUrls = emptyList()
            )
        )
    }

    @Test
    fun blocksSameUrlAlreadyRunningAfterAppRestart() {
        assertTrue(
            DownloadQueuePolicy.isAlreadyActive(
                sourceUrl = url,
                activeSessionUrls = emptyList(),
                persistedActiveUrls = listOf(url)
            )
        )
    }

    @Test
    fun allowsDifferentUrl() {
        assertFalse(
            DownloadQueuePolicy.isAlreadyActive(
                sourceUrl = url,
                activeSessionUrls = listOf("https://www.nicovideo.jp/watch/sm12345678"),
                persistedActiveUrls = emptyList()
            )
        )
    }

    @Test
    fun allowsDifferentFormatsForTheSameVideo() {
        assertFalse(
            DownloadQueuePolicy.isAlreadyActive(
                sourceUrl = "$url#720p-format",
                activeSessionUrls = listOf("$url#1080p-format"),
                persistedActiveUrls = emptyList()
            )
        )
    }

    @Test
    fun blankUrlDoesNotMatchEveryOtherBlankEntry() {
        assertFalse(
            DownloadQueuePolicy.isAlreadyActive(
                sourceUrl = "",
                activeSessionUrls = listOf(""),
                persistedActiveUrls = emptyList()
            )
        )
    }
}
