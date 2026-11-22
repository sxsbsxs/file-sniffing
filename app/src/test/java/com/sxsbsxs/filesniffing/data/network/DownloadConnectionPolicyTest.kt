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
    fun directDownloadsCanScaleToTwoHundredFiftySixRangeBlocks() {
        DownloadConnectionPolicy.setPreferredMaximumConnections(256)
        assertEquals(128, DownloadConnectionPolicy.connectionsFor(128L * 1024L * 1024L))
        assertEquals(256, DownloadConnectionPolicy.connectionsFor(256L * 1024L * 1024L))
        assertEquals(256, DownloadConnectionPolicy.connectionsFor(1024L * 1024L * 1024L))
        DownloadConnectionPolicy.setPreferredMaximumConnections(16)
    }

    @Test
    fun configuredCeilingChangesTheActualBlockCountUpToTwoHundredFiftySix() {
        try {
            DownloadConnectionPolicy.setPreferredMaximumConnections(8)
            assertEquals(8, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(16)
            assertEquals(16, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(32)
            assertEquals(32, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(64)
            assertEquals(64, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(128)
            assertEquals(128, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))

            DownloadConnectionPolicy.setPreferredMaximumConnections(256)
            assertEquals(256, DownloadConnectionPolicy.connectionsFor(400L * 1024L * 1024L))
        } finally {
            DownloadConnectionPolicy.setPreferredMaximumConnections(16)
        }
    }

    @Test
    fun processParallelTaskBudgetShrinksWhenPerTaskCeilingIncreases() {
        try {
            DownloadConnectionPolicy.setPreferredMaximumConnections(16)
            assertEquals(4, DownloadConnectionPolicy.parallelTasksFor(4))

            DownloadConnectionPolicy.setPreferredMaximumConnections(64)
            assertEquals(4, DownloadConnectionPolicy.parallelTasksFor(4))

            DownloadConnectionPolicy.setPreferredMaximumConnections(128)
            assertEquals(2, DownloadConnectionPolicy.parallelTasksFor(4))

            DownloadConnectionPolicy.setPreferredMaximumConnections(256)
            assertEquals(1, DownloadConnectionPolicy.parallelTasksFor(4))
        } finally {
            DownloadConnectionPolicy.setPreferredMaximumConnections(16)
        }
    }

    @Test
    fun explicitConnectionCountsAreClampedToSupportedRange() {
        assertEquals(256, DownloadConnectionPolicy.clampExplicitConnectionCount(900))
        assertEquals(1, DownloadConnectionPolicy.clampExplicitConnectionCount(0))
        assertEquals(32, DownloadConnectionPolicy.clampExplicitConnectionCount(32))
    }

    @Test
    fun connectionCeilingIsClampedToSupportedRange() {
        try {
            DownloadConnectionPolicy.setPreferredMaximumConnections(900)
            assertEquals(256, DownloadConnectionPolicy.connectionsFor(5L * 1024L * 1024L * 1024L))
            DownloadConnectionPolicy.setPreferredMaximumConnections(0)
            assertEquals(1, DownloadConnectionPolicy.connectionsFor(50L * 1024L * 1024L))
        } finally {
            DownloadConnectionPolicy.setPreferredMaximumConnections(16)
        }
    }
}
