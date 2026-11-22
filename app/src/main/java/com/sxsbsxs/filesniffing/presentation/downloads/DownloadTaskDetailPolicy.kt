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
        if (previousState != "failed" || incomingState != "failed") {
            return incomingDetail
        }

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
