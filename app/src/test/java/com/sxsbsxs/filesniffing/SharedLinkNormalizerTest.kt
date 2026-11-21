package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLinkNormalizerTest {
    @Test
    fun unwrapsBilibiliOpenAppUrl() {
        val input = "bilibili://video/117397612726092?page=0&open_app_url=" +
            "https%3A%2F%2Fm.bilibili.com%2Fvideo%2FBV1m7pu6wEpi%3Ffoo%3Dbar%26from%3Dshare"

        assertEquals(
            "https://m.bilibili.com/video/BV1m7pu6wEpi?foo=bar&from=share",
            SharedLinkNormalizer.normalize(input)
        )
    }

    @Test
    fun rebuildsCanonicalUrlWhenEmbeddedUrlIsMissing() {
        assertEquals(
            "https://www.bilibili.com/video/av117397612726092/",
            SharedLinkNormalizer.normalize("bilibili://video/117397612726092?page=0")
        )
    }

    @Test
    fun extractsDeepLinkFromShareText() {
        val normalized = SharedLinkNormalizer.normalize(
            "打开视频：bilibili://video/117397612726092?page=0"
        )
        assertTrue(normalized?.startsWith("https://www.bilibili.com/video/av117397612726092") == true)
    }

    @Test
    fun keepsNormalHttpsUrlUnchanged() {
        assertEquals(
            "https://example.com/watch?id=1",
            SharedLinkNormalizer.normalize("https://example.com/watch?id=1")
        )
    }
}
