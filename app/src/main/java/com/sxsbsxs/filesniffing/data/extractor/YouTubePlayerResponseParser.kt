package com.sxsbsxs.filesniffing.data.extractor

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * A privacy-safe summary of the web player's initial response.
 *
 * Media URLs, signatures, request headers and tokens are deliberately never
 * copied into this model. It is a page diagnostic, not a media extractor.
 */
internal data class YouTubePlayerResponseSummary(
    val status: String,
    val reason: String?,
    val videoId: String?,
    val title: String?,
    val formatCount: Int,
    val adaptiveFormatCount: Int,
    val hasSabrStream: Boolean
) {
    val hasStreamingMedia: Boolean
        get() = formatCount > 0 || adaptiveFormatCount > 0 || hasSabrStream

    val requiresLogin: Boolean
        get() = status.equals("LOGIN_REQUIRED", ignoreCase = true) ||
            status.equals("AGE_CHECK_REQUIRED", ignoreCase = true)

    val isBotCheck: Boolean
        get() = requiresLogin && reason.orEmpty().contains(
            Regex("bot|robot|机器人|自动程序", RegexOption.IGNORE_CASE)
        )

    val diagnosis: String
        get() = when {
            isBotCheck -> "YouTube 返回了人机验证拦截页；这不是媒体流，网页嗅探无法从该响应生成下载地址。"
            requiresLogin -> "YouTube 要求登录或年龄验证；当前页面没有提供可用媒体流。"
            status.equals("UNPLAYABLE", ignoreCase = true) ->
                "YouTube 判定该视频当前不可播放：${reason.orEmpty()}"
            !hasStreamingMedia ->
                "YouTube 播放器响应没有返回普通格式、自适应格式或 SABR 流入口。"
            else ->
                "播放器返回了媒体元数据；仍需专用提取器解析签名并验证实际轨道。"
        }
}

/** Parses only whitelisted playback metadata; never retains signed stream URLs. */
internal object YouTubePlayerResponseParser {
    private const val MAX_JSON_CHARS = 512 * 1024

    fun parse(json: String): YouTubePlayerResponseSummary? {
        if (json.isBlank() || json.length > MAX_JSON_CHARS) return null
        val root = runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null

        val playability = root.objectOrNull("playabilityStatus")
        val details = root.objectOrNull("videoDetails")
        val streaming = root.objectOrNull("streamingData")
        return YouTubePlayerResponseSummary(
            status = playability?.string("status") ?: "UNKNOWN",
            reason = playability?.string("reason")?.take(240),
            videoId = details?.string("videoId")?.take(32),
            title = details?.string("title")?.take(160),
            formatCount = streaming?.count("formats") ?: 0,
            adaptiveFormatCount = streaming?.count("adaptiveFormats") ?: 0,
            hasSabrStream = streaming?.boolean("serverAbrStreamingUrlAvailable") == true ||
                streaming?.string("serverAbrStreamingUrl") != null ||
                streaming?.string("server_abr_streaming_url") != null
        )
    }

    private fun JsonObject.objectOrNull(key: String) =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(key: String): Boolean? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean

    /** Accept both raw YouTube arrays and the count-only shape returned by our WebView probe. */
    private fun JsonObject.count(key: String): Int =
        get(key)?.let { value ->
            when {
                value.isJsonArray -> value.asJsonArray.size()
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber ->
                    value.asInt.coerceAtLeast(0)
                else -> 0
            }
        } ?: 0
}
