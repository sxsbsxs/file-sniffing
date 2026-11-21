package com.sxsbsxs.filesniffing.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveCacheKeyTest {

    @Test
    fun sameRequestUsesSameCacheNamespace() {
        val first = key()
        val second = key()
        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
    }

    @Test
    fun qualityAndSessionChangesUseDifferentCacheNamespaces() {
        val baseline = key()
        assertNotEquals(baseline, key(height = 720))
        assertNotEquals(baseline, key(cookies = "sid=changed"))
        assertNotEquals(baseline, key(tree = "content://tree/another-folder"))
    }

    private fun key(
        height: Int? = 1080,
        cookies: String = "sid=private-session",
        tree: String = "content://tree/downloads"
    ) = AdaptiveCacheKey.create(
        sourceUrl = "https://cdn.example/video.m3u8?token=opaque",
        referer = "https://example.test/watch/1",
        fileName = "video.m3u8",
        destinationTree = tree,
        maxVideoHeight = height,
        sessionCookies = cookies
    )
}
