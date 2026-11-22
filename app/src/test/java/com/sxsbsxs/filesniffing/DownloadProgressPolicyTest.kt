package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressPolicyTest {

    @Test
    fun keepsHighestKnownPercentageWhenIncomingProgressGoesBackwards() {
        assertEquals(63, DownloadProgressPolicy.monotonic(63, 28))
    }

    @Test
    fun monotonicHighWaterIgnoresUnknownIncomingValues() {
        assertEquals(47, DownloadProgressPolicy.monotonic(47, -1))
    }

    @Test
    fun firstKnownProgressReplacesAnUnsetHighWaterMark() {
        assertEquals(12, DownloadProgressPolicy.monotonic(-1, 12))
    }

    @Test
    fun indeterminatePhaseDoesNotExposeAStalePercentage() {
        assertEquals(-1, DownloadProgressPolicy.visible(89, -1))
    }

    @Test
    fun percentageResumesFromTheHighWaterMarkAfterPostProcessing() {
        assertEquals(90, DownloadProgressPolicy.visible(89, 90))
        assertEquals(89, DownloadProgressPolicy.monotonic(89, 90 - 1))
    }

    @Test
    fun progressNeverShowsCompleteBeforeWorkerSucceeds() {
        assertEquals(99, DownloadProgressPolicy.monotonic(96, 100))
    }
}
