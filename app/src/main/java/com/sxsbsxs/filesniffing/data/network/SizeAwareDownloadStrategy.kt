package com.sxsbsxs.filesniffing.data.network

import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.core.download.DownloadStrategy

/**
 * Chooses a byte-range block count after OkDownload has inspected the response.
 *
 * Multi-block still requires server Range support and a seekable output stream.
 * For compatible servers, a 4 MiB target block means normal video-sized files
 * can use the configured ceiling instead of being stuck at 2/4/8 connections.
 */
internal object DownloadConnectionPolicy {
    private const val MIB = 1024L * 1024L
    private const val TARGET_BLOCK_BYTES = 4L * MIB
    private const val MAX_CONNECTIONS = 32

    @Volatile
    private var preferredMaximumConnections: Int = 16

    fun setPreferredMaximumConnections(value: Int) {
        preferredMaximumConnections = value.coerceIn(1, MAX_CONNECTIONS)
    }

    fun connectionsFor(totalBytes: Long): Int {
        if (totalBytes <= 0L) return 1
        val estimatedBlocks = ((totalBytes - 1L) / TARGET_BLOCK_BYTES + 1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        return estimatedBlocks.coerceAtLeast(1).coerceAtMost(preferredMaximumConnections)
    }
}

class SizeAwareDownloadStrategy : DownloadStrategy() {
    override fun determineBlockCount(task: DownloadTask, totalLength: Long): Int {
        val explicitlyConfigured = task.getSetConnectionCount()
        if (explicitlyConfigured != null) return explicitlyConfigured
        return DownloadConnectionPolicy.connectionsFor(totalLength)
    }
}
