package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressPolicyTest {

    @Test
    fun keepsHighestKnownPercentageWhenIncomingProgressGoesBackwards() {
        assertEquals(63, DownloadProgressPolicy.monotonic(63, 28))
    }

    @Test
    fun unknownPhaseDoesNotEraseKnownProgress() {
        assertEquals(47, DownloadProgressPolicy.monotonic(47, -1))
    }

    @Test
    fun firstKnownProgressReplacesIndeterminateState() {
        assertEquals(12, DownloadProgressPolicy.monotonic(-1, 12))
    }

    @Test
    fun progressNeverShowsCompleteBeforeWorkerSucceeds() {
        assertEquals(99, DownloadProgressPolicy.monotonic(96, 100))
    }
}
