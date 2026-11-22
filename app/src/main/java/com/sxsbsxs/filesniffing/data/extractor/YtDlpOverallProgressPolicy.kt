package com.sxsbsxs.filesniffing.data.extractor

import kotlin.math.roundToInt

/**
 * Maps yt-dlp's media-transfer and post-processing callbacks onto the same
 * whole-task progress bar used later for copying and publishing the final file.
 *
 * The yt-dlp postprocessor hook exposes lifecycle states, not an FFmpeg percentage.
 * Therefore the merge phase uses explicit phase boundaries rather than invented
 * per-second percentages: started=80, processing=84, finished=88, full yt-dlp
 * completion=90. The output copy then occupies 90–99 and WorkManager success
 * displays 100%.
 */
internal object YtDlpOverallProgressPolicy {
    const val STREAM_DOWNLOAD_END = 80
    const val POST_PROCESS_START = 80
    const val POST_PROCESS_ACTIVE = 84
    const val POST_PROCESS_FINISHED = 88
    const val POST_PROCESS_COMPLETE = 90

    fun streamDownloadPercent(streamPercent: Float): Int {
        if (!streamPercent.isFinite()) return 0
        return (streamPercent.coerceIn(0f, 100f) * STREAM_DOWNLOAD_END / 100f)
            .roundToInt()
            .coerceIn(0, STREAM_DOWNLOAD_END)
    }

    fun postProcessingPercent(status: String?): Int = when (status?.lowercase()) {
        "processing" -> POST_PROCESS_ACTIVE
        "finished" -> POST_PROCESS_FINISHED
        else -> POST_PROCESS_START
    }

    fun postProcessingCompletePercent(): Int = POST_PROCESS_COMPLETE
}
