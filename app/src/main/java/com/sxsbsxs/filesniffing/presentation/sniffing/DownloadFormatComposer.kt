package com.sxsbsxs.filesniffing.presentation.sniffing

import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.SniffedResource

/**
 * Composes an exact yt-dlp selection from metadata belonging to the same source URL.
 * This is deliberately pure Kotlin so its safety and size-estimation rules can be tested.
 */
internal object DownloadFormatComposer {
    private val safeFormatId = Regex("^[A-Za-z0-9_.-]{1,96}$")

    fun video(
        video: SniffedResource,
        audio: SniffedResource? = null
    ): SniffedResource? {
        if (video.kind != ResourceKind.VIDEO) return null
        val videoId = video.videoFormatId ?: return null
        if (!safeFormatId.matches(videoId)) return null

        if (video.hasEmbeddedAudio) {
            if (audio != null) return null
            return video.copy(
                formatSelector = videoId,
                qualityLabel = listOfNotNull(video.qualityLabel, "内嵌音轨").joinToString(" + "),
                fileSizeBytes = video.fileSizeBytes,
                audioFormatId = null,
                audioQualityLabel = "内嵌音轨",
                audioLanguage = null,
                fileName = outputName(video.fileName, null)
            )
        }

        val selectedAudio = audio ?: return null
        if (selectedAudio.kind != ResourceKind.AUDIO || selectedAudio.url != video.url) return null
        val audioId = selectedAudio.audioFormatId
            ?: selectedAudio.formatSelector
            ?: return null
        if (!safeFormatId.matches(audioId) || selectedAudio.hasEmbeddedAudio.not()) return null

        val estimatedSize = if (
            video.fileSizeBytes != null && selectedAudio.fileSizeBytes != null
        ) video.fileSizeBytes + selectedAudio.fileSizeBytes else null

        return video.copy(
            formatSelector = "$videoId+$audioId",
            qualityLabel = listOfNotNull(
                video.qualityLabel,
                selectedAudio.qualityLabel,
                selectedAudio.audioLanguage
            ).joinToString(" · "),
            fileSizeBytes = estimatedSize,
            audioFormatId = audioId,
            audioQualityLabel = selectedAudio.qualityLabel,
            audioLanguage = selectedAudio.audioLanguage,
            fileName = outputName(video.fileName, audioId)
        )
    }

    fun audio(audio: SniffedResource?): SniffedResource? {
        if (audio?.kind != ResourceKind.AUDIO) return null
        val id = audio.audioFormatId ?: audio.formatSelector ?: return null
        if (!safeFormatId.matches(id)) return null
        return audio
    }

    private fun outputName(sourceName: String, audioId: String?): String {
        val base = sourceName.substringBeforeLast('.', sourceName)
        val suffix = audioId?.let { "-audio-$it" }.orEmpty()
        return "$base$suffix.mp4"
    }
}
