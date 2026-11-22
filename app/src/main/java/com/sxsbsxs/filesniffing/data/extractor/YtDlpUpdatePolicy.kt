package com.sxsbsxs.filesniffing.data.extractor

/**
 * Scheduling policy for the self-updating yt-dlp executable.
 * The Android wrapper dependency remains pinned; only the Python yt-dlp program is refreshed.
 */
internal object YtDlpUpdatePolicy {
    const val UPDATE_INTERVAL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    const val RETRY_COOLDOWN_MILLIS = 6L * 60L * 60L * 1000L

    fun shouldAttemptUpdate(
        nowMillis: Long,
        lastAttemptMillis: Long,
        lastSuccessMillis: Long,
        hasInstalledVersion: Boolean
    ): Boolean {
        val due = !hasInstalledVersion ||
            lastSuccessMillis <= 0L ||
            nowMillis - lastSuccessMillis >= UPDATE_INTERVAL_MILLIS
        val retryAllowed = lastAttemptMillis <= 0L ||
            nowMillis - lastAttemptMillis >= RETRY_COOLDOWN_MILLIS
        return due && retryAllowed
    }
}
