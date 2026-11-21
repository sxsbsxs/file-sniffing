package com.sxsbsxs.filesniffing

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class ResourceKind(val label: String) {
    VIDEO("视频"),
    AUDIO("音频"),
    IMAGE("图片"),
    PLAYLIST("媒体清单"),
    OTHER("文件")
}

data class SniffedResource(
    val url: String,
    val kind: ResourceKind,
    val fileName: String,
    val mimeType: String?,
    val pageTitle: String,
    val sourcePage: String
) {
    val id: String get() = url
}

object ResourceSniffer {
    private val videoExtensions = setOf("mp4", "webm", "mov", "m4v", "3gp", "mkv", "ts", "m4s")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
    private val playlistExtensions = setOf("m3u8", "mpd")

    fun detect(
        rawUrl: String,
        hintedKind: String? = null,
        hintedMime: String? = null,
        pageTitle: String = "",
        sourcePage: String = ""
    ): SniffedResource? {
        val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        if (parsed.host.isNullOrBlank()) return null

        val path = parsed.lastPathSegment.orEmpty()
        val extension = path.substringAfterLast('.', "").lowercase()
        val mime = hintedMime?.substringBefore(';')?.trim()?.lowercase()
        val kind = when {
            extension in playlistExtensions || mime?.contains("mpegurl") == true ||
                mime?.contains("dash+xml") == true -> ResourceKind.PLAYLIST
            extension in videoExtensions || mime?.startsWith("video/") == true ||
                hintedKind.equals("video", ignoreCase = true) -> ResourceKind.VIDEO
            extension in audioExtensions || mime?.startsWith("audio/") == true ||
                hintedKind.equals("audio", ignoreCase = true) -> ResourceKind.AUDIO
            extension in imageExtensions || mime?.startsWith("image/") == true ||
                hintedKind.equals("image", ignoreCase = true) -> ResourceKind.IMAGE
            else -> return null
        }

        val decodedName = runCatching {
            URLDecoder.decode(path, StandardCharsets.UTF_8.name())
        }.getOrDefault(path)
        val fileName = decodedName
            .substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .takeIf { it.isNotBlank() && it != "." }
            ?: "media_resource.$extension"

        return SniffedResource(
            url = parsed.toString(),
            kind = kind,
            fileName = fileName,
            mimeType = mime,
            pageTitle = pageTitle,
            sourcePage = sourcePage
        )
    }
}

data class DownloadTaskUi(
    val id: String,
    val fileName: String,
    val progress: Int,
    val state: String,
    val detail: String
)
