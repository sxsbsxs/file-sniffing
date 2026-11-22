package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadOutputCopyProgressPolicyTest {
    @Test
    fun doesNotShow99UntilTheLastByteHasBeenCopied() {
        assertEquals(98, DownloadOutputCopyProgressPolicy.percent(90L, 100L))
        assertEquals(98, DownloadOutputCopyProgressPolicy.percent(99L, 100L))
        assertEquals(99, DownloadOutputCopyProgressPolicy.percent(100L, 100L))
    }

    @Test
    fun unknownTotalUsesIndeterminateState() {
        assertEquals(-1, DownloadOutputCopyProgressPolicy.percent(0L, -1L))
    }
}
