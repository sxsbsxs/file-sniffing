package com.sxsbsxs.filesniffing.data.extractor

import java.io.File
import java.io.IOException

/**
 * Separates a completed yt-dlp output from format-specific files left behind
 * when a download is paused during a video/audio merge.
 *
 * yt-dlp names the intermediate streams with format suffixes, for example
 * "job.f137.mp4" and "job.f140.m4a". Only "job.mp4" (or another supported
 * final extension) can be published as the completed output.
 */
internal object YtDlpOutputFilePolicy {
    private val finalExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp",
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "wma"
    )

    fun isFinalOutputCandidate(file: File, outputPrefix: String): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val prefix = "$outputPrefix."
        if (!file.name.startsWith(prefix)) return false
        val extension = file.name.removePrefix(prefix).lowercase()
        return extension in finalExtensions
    }

    fun findFinalOutput(directory: File, outputPrefix: String): File? =
        directory.listFiles()
            ?.filter { isFinalOutputCandidate(it, outputPrefix) }
            ?.maxByOrNull { it.lastModified() }

    fun findCompletedOutput(directory: File, outputPrefix: String): File? {
        val marker = markerFile(directory, outputPrefix)
        if (!marker.isFile) return null

        val metadata = runCatching { marker.readLines() }.getOrNull()
        if (metadata == null || metadata.size < 3) {
            marker.delete()
            return null
        }

        val fileName = metadata[0]
        val expectedLength = metadata[1].toLongOrNull()
        val expectedModified = metadata[2].toLongOrNull()
        val candidate = File(directory, fileName)
        val valid = candidate.name == fileName &&
            isFinalOutputCandidate(candidate, outputPrefix) &&
            expectedLength != null &&
            expectedModified != null &&
            candidate.length() == expectedLength &&
            candidate.lastModified() == expectedModified
        if (!valid) {
            marker.delete()
            return null
        }
        return candidate
    }

    /**
     * Preserve format-specific files and .part files for yt-dlp's --continue,
     * but remove an unmarked final-name file that could be a killed FFmpeg mux.
     */
    fun prepareForDownload(directory: File, outputPrefix: String) {
        val marker = markerFile(directory, outputPrefix)
        val markerTemp = markerTempFile(directory, outputPrefix)
        if (marker.exists() && !marker.delete()) {
            throw IOException("无法清理 yt-dlp 完成标记")
        }
        if (markerTemp.exists() && !markerTemp.delete()) {
            throw IOException("无法清理 yt-dlp 临时完成标记")
        }
        directory.listFiles()
            ?.filter { isFinalOutputCandidate(it, outputPrefix) }
            ?.forEach { file ->
                if (!file.delete()) {
                    throw IOException("无法清理上次未完成的最终媒体文件")
                }
            }
    }

    fun markCompleted(directory: File, outputPrefix: String, output: File) {
        require(isFinalOutputCandidate(output, outputPrefix)) {
            "yt-dlp 输出不是合并后的最终媒体文件"
        }
        val marker = markerFile(directory, outputPrefix)
        val temp = markerTempFile(directory, outputPrefix)
        val content = listOf(
            output.name,
            output.length().toString(),
            output.lastModified().toString()
        ).joinToString("\n")

        temp.writeText(content, Charsets.UTF_8)
        if (marker.exists() && !marker.delete()) {
            temp.delete()
            throw IOException("无法更新 yt-dlp 完成标记")
        }
        if (!temp.renameTo(marker)) {
            temp.delete()
            throw IOException("无法写入 yt-dlp 完成标记")
        }
    }

    private fun markerFile(directory: File, outputPrefix: String): File =
        File(directory, "$outputPrefix.complete")

    private fun markerTempFile(directory: File, outputPrefix: String): File =
        File(directory, "$outputPrefix.complete.tmp")
}
