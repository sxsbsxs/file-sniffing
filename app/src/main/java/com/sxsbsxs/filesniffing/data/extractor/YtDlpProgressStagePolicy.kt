package com.sxsbsxs.filesniffing.data.extractor

internal object YtDlpProgressStagePolicy {
    private val completedDownloadLine = Regex(
        """(?i)(?:\[download\]\s*100(?:\.0+)?%|\(100(?:\.0+)?%\))"""
    )
    private val postProcessingMarkers = listOf(
        "[merger]",
        "merging formats",
        "[extractaudio]",
        "extracting audio",
        "[videoremuxer]",
        "[fixup",
        "post-process",
        "postprocessing",
        "remux"
    )

    fun isPostProcessing(progress: Float, line: String): Boolean {
        if (progress.isFinite() && progress >= 99.9f) return true
        if (completedDownloadLine.containsMatchIn(line)) return true
        val normalized = line.lowercase()
        return postProcessingMarkers.any(normalized::contains)
    }

    fun detail(line: String): String {
        val normalized = line.lowercase()
        return when {
            normalized.contains("[merger]") || normalized.contains("merging formats") ->
                "正在合并视频与音频流"
            normalized.contains("[extractaudio]") || normalized.contains("extracting audio") ->
                "正在提取音轨并转换音频"
            normalized.contains("[videoremuxer]") || normalized.contains("remux") ->
                "正在封装媒体文件"
            normalized.contains("[fixup") || normalized.contains("post-process") ||
                normalized.contains("postprocessing") ->
                "正在进行媒体后处理"
            else -> "媒体流已到达阶段性完成，正在处理剩余流或合并封装"
        }
    }
}
