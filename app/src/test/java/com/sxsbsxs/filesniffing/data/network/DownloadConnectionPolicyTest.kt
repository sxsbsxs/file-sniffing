package com.sxsbsxs.filesniffing.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadConnectionPolicyTest {

    @Test
    fun unknownAndVerySmallFilesUseOneConnection() {
        DownloadConnectionPolicy.setPreferredMaximumConnections(16)
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(-1L))
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(0L))
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(512L * 1024L))
    }

    @Test
    fun typicalVideoFilesCanUseTheConfiguredConnections() {
        DownloadConnectionPolicy.setPreferredMaximumConnections(16)
        assertEquals(2, DownloadConnectionPolicy.connectionsFor(2L * 1024L * 1024L))
        assertEquals(7, DownloadConnectionPolicy.connectionsFor(7L * 1024L * 1024L))
        assertEquals(16, DownloadConnectionPolicy.connectionsFor(32L * 1024L * 1024L))
        assertEquals(16, DownloadConnectionPolicy.connectionsFor(128L * 1024L * 1024L))
    }

    @Test
    fun configuredCeilingChangesTheActualBlockCountUpToThirtyTwoForDirectLinks() {
        try {
            DownloadConnectionPolicy.setPreferredMaximumConnections(8)
            assertEquals(8, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(16)
            assertEquals(32, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(32)
            assertEquals(16, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))
        } finally {
            DownloadConnectionPolicy.setPreferredMaximumConnections(16)
        }
    }

    @Test
    fun connectionCeilingIsClampedToSupportedRange() {
        DownloadConnectionPolicy.setPreferredMaximumConnections(900)
        assertEquals(32, DownloadConnectionPolicy.connectionsFor(5L * 1024L * 1024L * 1024L))
        DownloadConnectionPolicy.setPreferredMaximumConnections(0)
        assertEquals(1, DownloadConnectionPolicy.connectionsFor(50L * 1024L * 1024L))
        DownloadConnectionPolicy.setPreferredMaximumConnections(16)
    }
}
