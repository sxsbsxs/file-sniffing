package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAccessDiagnosticsTest {
    @Test
    fun humanVerificationIsReportedWithoutAttemptingToBypassIt() {
        val result = PageAccessDiagnostics.classify(
            "https://example.com/video",
            humanVerification = true
        )

        assertEquals("HUMAN_VERIFICATION", result?.code)
        assertTrue(result?.nextStep?.contains("不会绕过") == true)
    }

    @Test
    fun regionRestrictionIsReportedSeparatelyFromVerification() {
        val result = PageAccessDiagnostics.classify(
            "https://www.nicovideo.jp/watch/sm37593092",
            regionRestricted = true
        )

        assertEquals("REGION_RESTRICTED", result?.code)
        assertTrue(result?.message?.contains("地区") == true)
    }

    @Test
    fun accessDeniedDoesNotPretendThatPageAssetsAreVideoStreams() {
        val result = PageAccessDiagnostics.classify(
            "https://example.com/video",
            accessDenied = true
        )

        assertEquals("ACCESS_DENIED", result?.code)
        assertTrue(result?.message?.contains("不代表已经获取到视频流") == true)
    }

    @Test
    fun identifiesNicoVideoDownloaderServiceEndingNotice() {
        val result = PageAccessDiagnostics.classify(
            "https://nicovideodl.jp/watch/sm37593092",
            nicovideodlServiceEnding = true,
            nicovideodlPremiumExpired = true
        )

        assertEquals("NICOVIDEODL_SERVICE_ENDING", result?.code)
        assertTrue(result?.message?.contains("最高 720p") == true)
    }

    @Test
    fun ignoresNicoVideoDlNoticeOnUnrelatedHost() {
        val result = PageAccessDiagnostics.classify(
            "https://example.com/watch/sm37593092",
            nicovideodlServiceEnding = true
        )

        assertNull(result)
    }

    @Test
    fun identifiesNicoVideoDownloaderQualityLimit() {
        val result = PageAccessDiagnostics.classify(
            "https://www.nicovideodl.jp/watch/sm37593092",
            nicovideodlPremiumExpired = true
        )

        assertEquals("NICOVIDEODL_QUALITY_LIMITED", result?.code)
        assertTrue(result?.nextStep?.contains("媒体清单") == true)
    }

    @Test
    fun ordinaryPagesDoNotReceiveAnUnnecessaryDiagnostic() {
        assertNull(PageAccessDiagnostics.classify("https://example.com"))
    }
}
