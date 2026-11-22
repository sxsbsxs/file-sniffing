package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SniffedResourceDownloadKeyTest {
    private val watchUrl = "https://www.nicovideo.jp/watch/sm37593092"

    @Test
    fun niconicoAdaptiveManifestUsesStableWatchUrlInsteadOfExpiringManifestUrl() {
        val first = niconicoManifest(
            "https://media.example/signed/first/master.m3u8?token=first"
        )
        val refreshed = niconicoManifest(
            "https://media.example/signed/refreshed/master.m3u8?token=second"
        )

        assertEquals(first.downloadKey, refreshed.downloadKey)
        assertEquals("$watchUrl#PLAYLIST", first.downloadKey)
    }

    @Test
    fun distinctVideoFormatsRemainDistinctQueueItems() {
        val base = SniffedResource(
            url = watchUrl,
            kind = ResourceKind.VIDEO,
            fileName = "sample.mp4",
            mimeType = "video/mp4",
            pageTitle = "Sample",
            sourcePage = watchUrl,
            formatSelector = "137+140"
        )
        val otherQuality = base.copy(formatSelector = "248+140")

        assertNotEquals(base.downloadKey, otherQuality.downloadKey)
    }

    private fun niconicoManifest(url: String) = SniffedResource(
        url = url,
        kind = ResourceKind.PLAYLIST,
        fileName = "sample.m3u8",
        mimeType = "application/vnd.apple.mpegurl",
        pageTitle = "Sample",
        sourcePage = watchUrl
    )
}
