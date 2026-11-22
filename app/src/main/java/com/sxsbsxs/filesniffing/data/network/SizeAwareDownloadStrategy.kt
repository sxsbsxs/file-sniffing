package com.sxsbsxs.filesniffing.data.network

import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.core.download.DownloadStrategy

/**
 * Chooses a real byte-range block count after OkDownload has inspected the server response.
 * Small files use fewer ranges; very large files can use the user's 1..512 connection ceiling.
 *
 * OkDownload itself falls back to one connection when the server does not support byte ranges
 * or when the output stream cannot seek. Unknown/chunked lengths also stay on one connection.
 */
internal object DownloadConnectionPolicy {
    private const val MIB = 1024L * 1024L

    @Volatile
    private var preferredMaximumConnections: Int = 64

    fun setPreferredMaximumConnections(value: Int) {
        preferredMaximumConnections = value.coerceIn(1, 512)
    }

    fun connectionsFor(totalBytes: Long): Int {
        val sizeBasedCount = when {
            totalBytes <= 0L -> 1
            totalBytes < 1L * MIB -> 1
            totalBytes < 8L * MIB -> 2
            totalBytes < 64L * MIB -> 4
            totalBytes < 256L * MIB -> 8
            totalBytes < 512L * MIB -> 16
            totalBytes < 1L * 1024L * MIB -> 32
            totalBytes < 2L * 1024L * MIB -> 64
            totalBytes < 4L * 1024L * MIB -> 128
            else -> preferredMaximumConnections
        }
        return sizeBasedCount.coerceAtMost(preferredMaximumConnections)
    }
}

class SizeAwareDownloadStrategy : DownloadStrategy() {
    override fun determineBlockCount(task: DownloadTask, totalLength: Long): Int {
        val explicitlyConfigured = task.getSetConnectionCount()
        if (explicitlyConfigured != null) return explicitlyConfigured
        return DownloadConnectionPolicy.connectionsFor(totalLength)
    }
}
