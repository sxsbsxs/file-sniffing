package com.sxsbsxs.filesniffing.presentation.downloads

/**
 * Keeps a concrete failure written by the worker when WorkManager later exposes
 * only a generic FAILED snapshot. The worker persists these messages before
 * returning Result.failure(), so they are more informative than UI fallbacks.
 */
internal object DownloadTaskDetailPolicy {

    fun choose(
        previousState: String,
        previousDetail: String,
        incomingState: String,
        incomingDetail: String
    ): String {
        if (incomingState != "failed") return incomingDetail

        // The Worker can persist a precise error before WorkManager publishes FAILED.
        // A retry callback and the global WorkInfo observer may then race and write a
        // generic fallback while the Room row still has an intermediate state.
        return if (isConcreteFailure(previousDetail) && !isConcreteFailure(incomingDetail)) {
            previousDetail
        } else {
            incomingDetail
        }
    }

    private fun isConcreteFailure(detail: String): Boolean {
        return detail.startsWith("下载失败（") ||
            detail.startsWith("下载任务启动失败（") ||
            detail.startsWith("无法加入后台下载队列（") ||
            detail.startsWith("下载任务参数不完整")
    }
}
