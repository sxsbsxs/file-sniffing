package com.sxsbsxs.filesniffing.data.extractor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI

internal data class NiconicoWatchManifestRequest(
    val title: String,
    val videoIds: List<String>,
    val audioIds: List<String>,
    val accessRightKey: String,
    val watchTrackId: String
) {
    val outputCount: Int get() = videoIds.size * audioIds.size
}

internal object NiconicoApiParser {
    private const val MAX_JSON_CHARS = 2 * 1024 * 1024

    fun parseWatchResponse(json: String): NiconicoWatchManifestRequest {
        val root = parseObject(json)
        val meta = root.objectOrNull("meta")
        val data = root.objectOrNull("data")
            ?: throw IOException("N站没有返回视频数据")
        val status = meta?.int("status") ?: 200
        if (status != 200) {
            val reason = data.string("reasonCode")
                ?: meta?.string("errorCode")
                ?: status.toString()
            throw IOException(messageForReason(reason))
        }

        val video = data.objectOrNull("video")
        val media = data.objectOrNull("media")
            ?: throw IOException("N站没有返回可用媒体信息")
        val delivery = media.objectOrNull("domand")
            ?: media.objectOrNull("contents")
            ?: media

        val videos = delivery.arrayIds("videos")
        val audios = delivery.arrayIds("audios")
        val accessKey = delivery.string("accessRightKey")
            ?: media.string("accessRightKey")
            ?: throw IOException("N站没有授予视频流访问凭证")
        val watchTrackId = data.objectOrNull("client")?.string("watchTrackId")
            ?: throw IOException("N站没有返回播放会话标识")

        if (videos.isEmpty() || audios.isEmpty()) {
            throw IOException("N站没有返回可用的视频/音频轨道")
        }

        return NiconicoWatchManifestRequest(
            title = video?.string("title")?.take(180).orEmpty(),
            videoIds = videos,
            audioIds = audios,
            accessRightKey = accessKey,
            watchTrackId = watchTrackId
        )
    }

    fun buildOutputs(request: NiconicoWatchManifestRequest): JsonArray =
        JsonArray().apply {
            request.videoIds.forEach { videoId ->
                request.audioIds.forEach { audioId ->
                    add(JsonArray().apply {
                        add(videoId)
                        add(audioId)
                    })
                }
            }
        }

    fun parseHlsContentUrl(json: String): String {
        val root = parseObject(json)
        val status = root.objectOrNull("meta")?.int("status") ?: 200
        if (status != 200) {
            val reason = root.objectOrNull("data")?.string("reasonCode")
                ?: root.objectOrNull("meta")?.string("errorCode")
                ?: status.toString()
            throw IOException(messageForReason(reason))
        }
        val contentUrl = root.objectOrNull("data")?.string("contentUrl")
            ?: throw IOException("N站没有返回 HLS 播放清单地址")
        val uri = runCatching { URI(contentUrl) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
            throw IOException("N站返回了无效的视频流地址")
        }
        return contentUrl
    }

    private fun parseObject(json: String): JsonObject {
        if (json.isBlank() || json.length > MAX_JSON_CHARS) {
            throw IOException("N站接口返回内容为空或超出大小限制")
        }
        return runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw IOException("无法解析 N站接口响应")
    }

    private fun messageForReason(reason: String): String = when (reason.uppercase()) {
        "DOMESTIC_VIDEO", "HIGH_RISK_COUNTRY_VIDEO" ->
            "该视频受到地区播放限制，应用不会绕过地区限制"
        "CHANNEL_MEMBER_ONLY" -> "该视频仅限频道会员观看"
        "PPV_VIDEO" -> "该视频需要单独购买后才能观看"
        "PREMIUM_ONLY" -> "该视频需要 Niconico Premium 权限"
        "HIDDEN_VIDEO" -> "该视频当前隐藏或尚未公开"
        "HARMFUL_VIDEO" -> "该视频受到内容显示或登录设置限制"
        "NOT_FOUND", "DELETED" -> "N站视频不存在或已被删除"
        else -> "N站暂时无法提供该视频（$reason）"
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt

    private fun JsonObject.arrayIds(name: String): List<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.let { track ->
                    val available = track.get("isAvailable")
                    val isAvailable = available == null ||
                        !available.isJsonPrimitive ||
                        !available.asJsonPrimitive.isBoolean ||
                        available.asBoolean
                    track.string("id")?.takeIf { isAvailable }
                }
            }
            ?.distinct()
            .orEmpty()
}
