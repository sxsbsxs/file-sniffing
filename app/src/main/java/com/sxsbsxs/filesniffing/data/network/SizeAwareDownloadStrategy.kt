package com.sxsbsxs.filesniffing.data.network

import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.core.download.DownloadStrategy

/**
 * Chooses a sensible range count after OkDownload has inspected the server response.
 *
 * OkDownload itself falls back to one connection when the server does not support
 * byte ranges or when its output stream cannot seek. Unknown/chunked lengths also
 * stay on one connection rather than spawning speculative requests.
 */
internal object DownloadConnectionPolicy {
    private const val MIB = 1024L * 1024L

    @Volatile
    private var preferredMaximumConnections: Int = 12

    fun setPreferredMaximumConnections(value: Int) {
        preferredMaximumConnections = value.coerceIn(1, 16)
    }

    fun connectionsFor(totalBytes: Long): Int {
        val sizeBasedCount = when {
            totalBytes <= 0L -> 1
            totalBytes < 1L * MIB -> 1
            totalBytes < 8L * MIB -> 2
            totalBytes < 64L * MIB -> 4
            totalBytes < 256L * MIB -> 8
            else -> 16
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
