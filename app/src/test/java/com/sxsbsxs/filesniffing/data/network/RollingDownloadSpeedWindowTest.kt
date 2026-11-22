package com.sxsbsxs.filesniffing.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RollingDownloadSpeedWindowTest {
    @Test
    fun averagesBytesAcrossTheRecentWindow() {
        val speed = RollingDownloadSpeedWindow(windowMillis = 5_000L)
        speed.reset(bytes = 0L, nowNanos = 0L)
        assertEquals(1_000L, speed.record(1_000L, 1_000_000_000L))
        assertEquals(2_000L, speed.record(4_000L, 2_000_000_000L))
        assertEquals(1_666L, speed.record(5_000L, 3_000_000_000L))
    }

    @Test
    fun resumeResetsTheOldTransferBaseline() {
        val speed = RollingDownloadSpeedWindow(windowMillis = 5_000L)
        speed.reset(bytes = 10_000L, nowNanos = 10_000_000_000L)
        assertEquals(2_000L, speed.record(12_000L, 11_000_000_000L))
    }

    @Test
    fun staleSamplesExpireAfterTheWindow() {
        val speed = RollingDownloadSpeedWindow(windowMillis = 5_000L)
        speed.reset(bytes = 0L, nowNanos = 0L)
        assertEquals(1_000L, speed.record(1_000L, 1_000_000_000L))
        assertEquals(0L, speed.record(1_000L, 8_000_000_000L))
    }
}
