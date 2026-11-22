package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NiconicoGuestExtractorRecoveryPolicyTest {

    @Test
    fun refreshesOnlyForLegacyGuestErrorOnNiconicoWatchUrl() {
        assertTrue(
            NiconicoGuestExtractorRecoveryPolicy.shouldRefreshAndRetry(
                "https://www.nicovideo.jp/watch/sm37593092",
                "ERROR: [niconico] sm37593092: This video is only available for registered users"
            )
        )
    }

    @Test
    fun doesNotRefreshForOtherNiconicoFailures() {
        assertFalse(
            NiconicoGuestExtractorRecoveryPolicy.shouldRefreshAndRetry(
                "https://www.nicovideo.jp/watch/sm37593092",
                "Premium members only"
            )
        )
    }

    @Test
    fun doesNotRefreshForSameErrorOnUnrelatedSites() {
        assertFalse(
            NiconicoGuestExtractorRecoveryPolicy.shouldRefreshAndRetry(
                "https://www.youtube.com/watch?v=abc123",
                "This video is only available for registered users"
            )
        )
    }

    @Test
    fun rejectsInvalidAndNonWatchUrls() {
        assertFalse(
            NiconicoGuestExtractorRecoveryPolicy.shouldRefreshAndRetry(
                "not a url",
                "This video is only available for registered users"
            )
        )
    }
}
