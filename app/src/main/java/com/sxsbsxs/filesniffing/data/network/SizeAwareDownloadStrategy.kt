package com.sxsbsxs.filesniffing.data.network

import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.core.download.DownloadStrategy

/**
 * Chooses a safe byte-range block count after OkDownload has inspected the response.
 *
 * This is a configurable ceiling, not a promise that every file will use that many
 * live sockets. Actual block count is bounded by file size, and OkDownload still
 * requires a Range-capable server plus a seekable destination for multi-block output.
 *
 * OkDownload pre-splits a task; it does not currently provide Gopeed's dynamic
 * slow-start or work-stealing scheduler.
 */
internal object DownloadConnectionPolicy {
    private const val MIB = 1024L * 1024L
    private const val TARGET_BLOCK_BYTES = 1L * MIB
    private const val MAX_CONNECTIONS = 256
    private const val MAX_PARALLEL_TASKS = 4

    @Volatile
    private var preferredMaximumConnections: Int = 16

    fun setPreferredMaximumConnections(value: Int) {
        preferredMaximumConnections = value.coerceIn(1, MAX_CONNECTIONS)
    }

    /**
     * Keep the theoretical app-wide direct-download block budget at or below 256.
     * High per-file settings therefore reduce simultaneously running direct tasks.
     */
    fun parallelTasksFor(requestedTasks: Int): Int {
        val configuredTasks = requestedTasks.coerceIn(1, MAX_PARALLEL_TASKS)
        val processTaskBudget = (MAX_CONNECTIONS / preferredMaximumConnections).coerceAtLeast(1)
        return configuredTasks.coerceAtMost(processTaskBudget)
    }

    fun connectionsFor(totalBytes: Long): Int {
        if (totalBytes <= 0L) return 1
        val estimatedBlocks = ((totalBytes - 1L) / TARGET_BLOCK_BYTES + 1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        return estimatedBlocks.coerceAtLeast(1).coerceAtMost(preferredMaximumConnections)
    }

    fun clampExplicitConnectionCount(value: Int): Int =
        value.coerceIn(1, MAX_CONNECTIONS)
}

class SizeAwareDownloadStrategy : DownloadStrategy() {
    override fun determineBlockCount(task: DownloadTask, totalLength: Long): Int {
        val explicitlyConfigured = task.getSetConnectionCount()
        if (explicitlyConfigured != null) {
            return DownloadConnectionPolicy.clampExplicitConnectionCount(explicitlyConfigured)
        }
        return DownloadConnectionPolicy.connectionsFor(totalLength)
    }
}
