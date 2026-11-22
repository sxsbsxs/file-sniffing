package com.sxsbsxs.filesniffing

/** Prevents a second active task for the same source URL without blocking later retries. */
internal object DownloadQueuePolicy {
    fun isAlreadyActive(
        sourceUrl: String, // stable URL#format selector key
        activeSessionUrls: Collection<String>,
        persistedActiveUrls: Collection<String>
    ): Boolean {
        if (sourceUrl.isBlank()) return false
        return sourceUrl in activeSessionUrls || sourceUrl in persistedActiveUrls
    }
}
