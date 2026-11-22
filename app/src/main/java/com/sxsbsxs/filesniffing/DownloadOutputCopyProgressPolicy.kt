package com.sxsbsxs.filesniffing

internal object DownloadOutputCopyProgressPolicy {
    fun percent(copiedBytes: Long, totalBytes: Long): Int = when {
        totalBytes <= 0L -> -1
        else -> (90 + (copiedBytes.coerceIn(0L, totalBytes).toDouble() * 9.0 / totalBytes).toInt())
            .coerceIn(90, 99)
    }
}
