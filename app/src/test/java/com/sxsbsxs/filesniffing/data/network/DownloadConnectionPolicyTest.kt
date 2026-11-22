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
    fun smallFilesUseOnlyAHandfulOfConnections() {
        assertEquals(2, DownloadConnectionPolicy.connectionsFor(2L * 1024L * 1024L))
        assertEquals(2, DownloadConnectionPolicy.connectionsFor(7L * 1024L * 1024L))
        assertEquals(4, DownloadConnectionPolicy.connectionsFor(32L * 1024L * 1024L))
        assertEquals(8, DownloadConnectionPolicy.connectionsFor(128L * 1024L * 1024L))
    }

    @Test
    fun largeFilesScaleWithTheConfiguredCeiling() {
        try {
            DownloadConnectionPolicy.setPreferredMaximumConnections(64)
            assertEquals(64, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))
            assertEquals(64, DownloadConnectionPolicy.connectionsFor(700L * 1024L * 1024L))
            assertEquals(64, DownloadConnectionPolicy.connectionsFor(1500L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(128)
            assertEquals(100, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))
            assertEquals(128, DownloadConnectionPolicy.connectionsFor(700L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(256)
            assertEquals(256, DownloadConnectionPolicy.connectionsFor(1L * 1024L * 1024L * 1024L))
            assertEquals(256, DownloadConnectionPolicy.connectionsFor(5L * 1024L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(512)
            assertEquals(512, DownloadConnectionPolicy.connectionsFor(5L * 1024L * 1024L * 1024L))
        } finally {
            DownloadConnectionPolicy.setPreferredMaximumConnections(64)
        }
    }

    @Test
    fun connectionCeilingIsClampedToSupportedRange() {
        DownloadConnectionPolicy.setPreferredMaximumConnections(900)
        assertEquals(512, DownloadConnectionPolicy.connectionsFor(5L * 1024L * 1024L * 1024L))
        DownloadConnectionPolicy.setPreferredMaximumConnections(0)
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(50L * 1024L * 1024L))
        DownloadConnectionPolicy.setPreferredMaximumConnections(64)
    }
}
