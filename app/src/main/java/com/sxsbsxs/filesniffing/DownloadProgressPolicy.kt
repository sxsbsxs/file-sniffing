package com.sxsbsxs.filesniffing

/**
 * WorkManager receives progress updates from multiple callback threads and different
 * download phases. Keep the visible percentage monotonic; unknown updates must not
 * erase a value already reported by the current task.
 */
internal object DownloadProgressPolicy {
    fun monotonic(previous: Int, incoming: Int): Int = when {
        incoming < 0 -> previous
        previous < 0 -> incoming.coerceIn(0, 99)
        else -> maxOf(previous, incoming).coerceIn(0, 99)
    }
}
