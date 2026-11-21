package com.sxsbsxs.filesniffing.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadConnectionPolicyTest {
    @Test
    fun unknownAndVerySmallFilesUseOneConnection() {
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(-1L))
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(0L))
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(512L * 1024L))
    }

    @Test
    fun smallFilesUseTwoConnections() {
        assertEquals(2, DownloadConnectionPolicy.connectionsFor(2L * 1024L * 1024L))
        assertEquals(2, DownloadConnectionPolicy.connectionsFor(7L * 1024L * 1024L))
    }

    @Test
    fun mediumFilesUseFourOrEightConnections() {
        assertEquals(4, DownloadConnectionPolicy.connectionsFor(32L * 1024L * 1024L))
        assertEquals(8, DownloadConnectionPolicy.connectionsFor(128L * 1024L * 1024L))
    }

    @Test
    fun largeFilesAreCappedAtTwelveConnections() {
        assertEquals(12, DownloadConnectionPolicy.connectionsFor(256L * 1024L * 1024L))
        assertEquals(12, DownloadConnectionPolicy.connectionsFor(4L * 1024L * 1024L * 1024L))
    }
}
