package com.sxsbsxs.filesniffing.data.extractor

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * Metadata returned by yt-dlp's single-video JSON output.
 *
 * Signed media URLs and request headers are intentionally not represented in this model.
 * They are ephemeral credentials and must never be copied into Room, logs or UI state.
 */
data class YtDlpVideoInfo(
    val videoId: String,
    val title: String,
    val webpageUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Double?,
    val uploader: String?,
    val formats: List<YtDlpFormat>
) {
    val selectableVideoFormats: List<YtDlpFormat>
        get() = formats
            .filter { it.hasVideo && it.hasTransportAddress && !it.isDrm }
            .sortedWith(
                compareByDescending<YtDlpFormat> { it.height ?: 0 }
                    .thenByDescending { it.bitrateKbps ?: 0.0 }
            )

    val videoOnlyFormats: List<YtDlpFormat>
        get() = formats
            .filter { it.hasVideo && it.hasTransportAddress && !it.hasAudio && !it.isDrm }
            .sortedWith(
                compareByDescending<YtDlpFormat> { it.height ?: 0 }
                    .thenByDescending { it.bitrateKbps ?: 0.0 }
            )

    val audioOnlyFormats: List<YtDlpFormat>
        get() = formats
            .filter { !it.hasVideo && it.hasTransportAddress && it.hasAudio && !it.isDrm }
            .sortedByDescending { it.audioBitrateKbps ?: it.bitrateKbps ?: 0.0 }

    val muxedFormats: List<YtDlpFormat>
        get() = formats
            .filter { it.hasVideo && it.hasTransportAddress && it.hasAudio && !it.isDrm }
            .sortedWith(
                compareByDescending<YtDlpFormat> { it.height ?: 0 }
                    .thenByDescending { it.bitrateKbps ?: 0.0 }
            )
}

/** A safe subset of one yt-dlp format entry. No direct media URL or header is retained. */
data class YtDlpFormat(
    val videoId: String,
    val formatId: String,
    val extension: String?,
    val resolution: String?,
    val width: Int?,
    val height: Int?,
    val framesPerSecond: Double?,
    val videoCodec: String?,
    val audioCodec: String?,
    val bitrateKbps: Double?,
    val audioBitrateKbps: Double?,
    val fileSizeBytes: Long?,
    val protocol: String?,
    val language: String?,
    val isDrm: Boolean,
    /** True when yt-dlp returned a direct URL, manifest URL, or concrete fragment list. */
    val hasTransportAddress: Boolean = true
) {
    val hasVideo: Boolean
        get() = (!videoCodec.isNullOrBlank() && !videoCodec.equals("none", true)) ||
            width != null || height != null

    val hasAudio: Boolean
        get() = !audioCodec.isNullOrBlank() && !audioCodec.equals("none", true)

    val qualityLabel: String
        get() = when {
            hasVideo && height != null -> "${height}p"
            hasVideo && !resolution.isNullOrBlank() -> resolution
            hasVideo -> "视频"
            hasAudio && audioBitrateKbps != null -> "${audioBitrateKbps.toInt()} kbps"
            hasAudio -> "音频"
            else -> extension?.uppercase(Locale.ROOT) ?: "格式"
        }
}

data class YtDlpPlaylistEntry(
    val videoId: String,
    val title: String,
    val webpageUrl: String
)

data class YtDlpPlaylistInfo(
    val title: String,
    val entries: List<YtDlpPlaylistEntry>
)

/**
 * Parses a single-video object emitted by yt-dlp -J / --dump-single-json.
 * Playlist JSON with an entries array is rejected rather than ambiguously choosing an entry.
 */
internal object YtDlpInfoJsonParser {
    private val safeFormatId = Regex("^[A-Za-z0-9_.-]{1,96}$")
    private val resolutionPattern = Regex("^(\\d{2,5})x(\\d{2,5})$")
    private val heightPattern = Regex("^(\\d{2,5})p$", RegexOption.IGNORE_CASE)

    fun parsePlaylist(json: String): YtDlpPlaylistInfo {
        require(json.isNotBlank()) { "yt-dlp 返回了空播放列表 JSON" }
        val root = runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw IllegalArgumentException("yt-dlp 播放列表 JSON 无效")
        val entries = root.array("entries")
            ?.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val item = element.asJsonObject
                val id = item.string("id")
                    ?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{11}$")) }
                    ?: return@mapNotNull null
                val title = item.string("title")?.take(180) ?: id
                val page = item.string("webpage_url")
                    ?: item.string("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: "https://www.youtube.com/watch?v=$id"
                YtDlpPlaylistEntry(id, title, page)
            }
            .orEmpty()
            .distinctBy { it.videoId }
            .take(200)
        require(entries.isNotEmpty()) { "yt-dlp 没有返回可用的播放列表视频" }
        return YtDlpPlaylistInfo(
            title = root.string("title")?.take(180) ?: "YouTube 播放列表",
            entries = entries
        )
    }

    fun parse(json: String): YtDlpVideoInfo {
        require(json.isNotBlank()) { "yt-dlp 返回了空 JSON" }
        val root = runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw IllegalArgumentException("yt-dlp 返回的 JSON 无效")

        require(!root.has("entries")) {
            "当前解析器只接受单个视频结果，请先展开播放列表"
        }

        val videoId = root.string("id")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("yt-dlp JSON 缺少视频 ID")
        val title = root.string("title")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("yt-dlp JSON 缺少视频标题")

        val formats = root.array("formats")
            ?.mapNotNull { element -> parseFormat(element, videoId) }
            .orEmpty()
            .distinctBy { it.formatId }

        return YtDlpVideoInfo(
            videoId = videoId,
            title = title,
            webpageUrl = root.string("webpage_url") ?: root.string("original_url"),
            thumbnailUrl = root.string("thumbnail"),
            durationSeconds = root.number("duration"),
            uploader = root.string("uploader") ?: root.string("channel"),
            formats = formats
        )
    }

    /**
     * For muxed formats, pass only the video format. For separate streams, pass a real
     * audio-only format returned by this same video's JSON. This never invents format IDs.
     */
    fun buildFormatSelector(
        videoFormat: YtDlpFormat,
        audioFormat: YtDlpFormat? = null
    ): String {
        require(
            videoFormat.hasVideo && videoFormat.hasTransportAddress &&
                !videoFormat.isDrm && safeFormatId.matches(videoFormat.formatId)
        ) {
            "所选视频格式不可下载"
        }
        if (videoFormat.hasAudio) {
            require(audioFormat == null) { "混合音视频格式不应再指定音频轨道" }
            return videoFormat.formatId
        }

        val audio = requireNotNull(audioFormat) {
            "该画质只有视频轨道，请从当前视频的可用音频格式中选择音轨"
        }
        require(
            audio.videoId == videoFormat.videoId &&
                !audio.hasVideo && audio.hasAudio && audio.hasTransportAddress &&
                !audio.isDrm && safeFormatId.matches(audio.formatId)
        ) {
            "所选音频格式无效，或不属于当前视频"
        }
        return "${videoFormat.formatId}+${audio.formatId}"
    }

    private fun parseFormat(element: JsonElement, videoId: String): YtDlpFormat? {
        if (!element.isJsonObject) return null
        val item = element.asJsonObject
        val formatId = item.string("format_id")
            ?.takeIf { safeFormatId.matches(it) }
            ?: return null

        val width = item.int("width")
        val rawHeight = item.int("height")
        val rawResolution = item.string("resolution")
        val parsedResolution = parseResolution(rawResolution)
        val height = rawHeight ?: parsedResolution?.second
        val resolvedWidth = width ?: parsedResolution?.first
        val videoCodec = item.string("vcodec")
        val audioCodec = item.string("acodec")
        val drmFamilies = item.array("drm_families")
        val hasDrmFamily = drmFamilies?.any { !it.isJsonNull } == true
        val isDrm = item.boolean("has_drm") == true ||
            item.boolean("is_drm") == true ||
            hasDrmFamily
        // Do not offer metadata-only entries which cannot be passed to a downloader.
        // URLs are inspected only as a boolean and are never copied into the model.
        val hasTransportAddress = item.string("url") != null ||
            item.string("manifest_url") != null ||
            item.array("fragments")?.any { fragment ->
                fragment.isJsonObject && fragment.asJsonObject.string("url") != null
            } == true

        return YtDlpFormat(
            videoId = videoId,
            formatId = formatId,
            extension = item.string("ext"),
            resolution = rawResolution ?: height?.let { "${it}p" },
            width = resolvedWidth,
            height = height,
            framesPerSecond = item.number("fps"),
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            bitrateKbps = item.number("tbr"),
            audioBitrateKbps = item.number("abr"),
            fileSizeBytes = item.long("filesize") ?: item.long("filesize_approx"),
            protocol = item.string("protocol"),
            language = item.string("language"),
            isDrm = isDrm,
            hasTransportAddress = hasTransportAddress
        )
    }

    private fun parseResolution(value: String?): Pair<Int?, Int?>? {
        if (value.isNullOrBlank()) return null
        resolutionPattern.matchEntire(value)?.let { match ->
            return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
        }
        heightPattern.matchEntire(value)?.let { match ->
            return null to match.groupValues[1].toIntOrNull()
        }
        return null
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull || !it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asDouble
            ?.takeIf { it.isFinite() && it >= 0.0 }

    private fun JsonObject.int(key: String): Int? =
        number(key)?.toInt()?.takeIf { it > 0 }

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asLong
            ?.takeIf { it > 0L }

    private fun JsonObject.boolean(key: String): Boolean? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean

    private fun JsonObject.array(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
}
