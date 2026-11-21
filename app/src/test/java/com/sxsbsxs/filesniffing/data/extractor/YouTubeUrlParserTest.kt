package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {
    @Test
    fun extractsWatchUrlIdAndIgnoresOtherQueryParameters() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123")
        )
    }

    @Test
    fun extractsShortLinkId() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=12")
        )
    }

    @Test
    fun extractsShortsLiveAndEmbedIds() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, YouTubeUrlParser.extractVideoId("https://www.youtube.com/shorts/$id"))
        assertEquals(id, YouTubeUrlParser.extractVideoId("https://www.youtube.com/live/$id"))
        assertEquals(id, YouTubeUrlParser.extractVideoId("https://www.youtube-nocookie.com/embed/$id"))
    }

    @Test
    fun rejectsLookalikeHostsAndInvalidIds() {
        assertNull(YouTubeUrlParser.extractVideoId("https://youtube.com.attacker.example/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeUrlParser.extractVideoId("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=short"))
        assertNull(YouTubeUrlParser.extractVideoId("javascript://youtube.com/watch?v=dQw4w9WgXcQ"))
    }
}
