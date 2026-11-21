package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlayerResponseParserTest {

    @Test
    fun identifiesTheBotCheckPageReturnedForTheSuppliedVideo() {
        // Sanitized shape of the uploaded YouTube player response.
        val summary = YouTubePlayerResponseParser.parse(
            """
            {
              "playabilityStatus": {
                "status": "LOGIN_REQUIRED",
                "reason": "Đăng nhập để xác nhận bạn không phải là robot"
              }
            }
            """.trimIndent()
        )!!

        assertTrue(summary.requiresLogin)
        assertTrue(summary.isBotCheck)
        assertFalse(summary.hasStreamingMedia)
        assertEquals(0, summary.formatCount)
        assertEquals(0, summary.adaptiveFormatCount)
        assertTrue(summary.diagnosis.contains("人机验证"))
    }

    @Test
    fun readsAvailableFormatCountsWithoutKeepingMediaUrls() {
        val summary = YouTubePlayerResponseParser.parse(
            """
            {
              "playabilityStatus": {"status":"OK"},
              "videoDetails": {"videoId":"HJQh7iEnepY","title":"Example"},
              "streamingData": {
                "formats": [{"itag":18,"url":"https://signed.example/a?token=secret"}],
                "adaptiveFormats": [
                  {"itag":137,"url":"https://signed.example/v?token=secret"},
                  {"itag":140,"url":"https://signed.example/a?token=secret"}
                ],
                "serverAbrStreamingUrl": "https://signed.example/sabr?token=secret"
              }
            }
            """.trimIndent()
        )!!

        assertEquals("HJQh7iEnepY", summary.videoId)
        assertEquals("Example", summary.title)
        assertEquals(1, summary.formatCount)
        assertEquals(2, summary.adaptiveFormatCount)
        assertTrue(summary.hasSabrStream)
        assertTrue(summary.hasStreamingMedia)
        val storedText = summary.toString()
        assertFalse(storedText.contains("signed.example"))
        assertFalse(storedText.contains("token=secret"))
    }

    @Test
    fun handlesCountOnlyWebViewProbeWithoutExposingStreamUrls() {
        val summary = YouTubePlayerResponseParser.parse(
            """
            {
              "playabilityStatus": {"status":"OK"},
              "streamingData": {
                "formats": 1,
                "adaptiveFormats": 3,
                "serverAbrStreamingUrlAvailable": true
              }
            }
            """.trimIndent()
        )!!

        assertEquals(1, summary.formatCount)
        assertEquals(3, summary.adaptiveFormatCount)
        assertTrue(summary.hasSabrStream)
    }

    @Test
    fun malformedOrOversizedInputIsIgnored() {
        assertNull(YouTubePlayerResponseParser.parse("not-json"))
        assertNull(YouTubePlayerResponseParser.parse(" ".repeat(512 * 1024 + 1)))
    }
}
