package com.sxsbsxs.filesniffing.data.extractor

internal object YtDlpProgressStagePolicy {
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

    /** A per-format 100% event is not proof that all selected streams have finished. */
    fun isPostProcessing(line: String): Boolean {
        val normalized = line.lowercase()
        return postProcessingMarkers.any(normalized::contains)
    }

    fun detailForPostprocessor(postProcessor: String?, line: String): String {
        val stage = postProcessor.orEmpty().lowercase()
        val normalized = line.lowercase()
        return when {
            stage.contains("merger") || normalized.contains("[merger]") ||
                normalized.contains("merging formats") -> "正在合并视频与音频流"
            stage.contains("extractaudio") || normalized.contains("[extractaudio]") ||
                normalized.contains("extracting audio") -> "正在提取音轨并转换音频"
            stage.contains("remux") || normalized.contains("[videoremuxer]") ||
                normalized.contains("remux") -> "正在封装媒体文件"
            else -> "正在进行媒体后处理：${postProcessor ?: "FFmpeg"}"
        }
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
