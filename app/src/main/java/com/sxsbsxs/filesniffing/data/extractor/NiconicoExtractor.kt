package com.sxsbsxs.filesniffing.data.extractor

import android.webkit.CookieManager
import com.google.gson.JsonObject
import com.sxsbsxs.filesniffing.FileNameSanitizer
import com.sxsbsxs.filesniffing.NiconicoUrlParser
import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.SniffedResource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Uses Niconico's public watch API and its per-watch access-right endpoint.
 * Authentication is inherited only from the user's existing WebView CookieManager session;
 * no passwords, cookies, access keys or signed stream URLs are persisted by this extractor.
 */
@Singleton
class NiconicoExtractor @Inject constructor(
    private val httpClient: OkHttpClient
) {
    suspend fun extract(pageUrl: String): SniffedResource = withContext(Dispatchers.IO) {
        val videoId = NiconicoUrlParser.extractVideoId(pageUrl)
            ?: throw IllegalArgumentException("不是有效的 Niconico 视频链接")
        val canonicalUrl = NiconicoUrlParser.canonicalWatchUrl(videoId)
            ?: throw IllegalArgumentException("无法规范化 N站视频链接")

        // WebView and OkHttp do not automatically share cookies. Explicitly forward the
        // current Niconico session to both API calls; never log or persist this header.
        val cookieHeader = runCatching {
            CookieManager.getInstance().getCookie("https://www.nicovideo.jp").orEmpty()
        }.getOrDefault("")
        val loggedIn = cookieHeader.split(';')
            .any { it.trim().startsWith("user_session=") }
        val apiVersion = if (loggedIn) "v3" else "v3_guest"
        val actionTrackId = "AAAAAAAAAA_${System.currentTimeMillis()}"
        val watchApiUrl =
            "https://www.nicovideo.jp/api/watch/$apiVersion/$videoId?actionTrackId=$actionTrackId"

        val watchJson = executeJson(
            Request.Builder()
                .url(watchApiUrl)
                .header("Accept", "application/json;charset=utf-8")
                .header("X-Frontend-ID", FRONTEND_ID)
                .header("X-Frontend-Version", FRONTEND_VERSION)
                .header("Referer", canonicalUrl)
                .get()
                .build(),
            "N站视频信息请求失败",
            cookieHeader
        )
        val watch = NiconicoApiParser.parseWatchResponse(watchJson)

        val payload = JsonObject().apply {
            add("outputs", NiconicoApiParser.buildOutputs(watch))
        }.toString()
        val accessUrl =
            "https://nvapi.nicovideo.jp/v1/watch/$videoId/access-rights/hls" +
                "?actionTrackId=${java.net.URLEncoder.encode(watch.watchTrackId, Charsets.UTF_8.name())}"
        val manifestJson = executeJson(
            Request.Builder()
                .url(accessUrl)
                .header("Accept", "application/json;charset=utf-8")
                .header("Content-Type", "application/json")
                .header("X-Frontend-ID", FRONTEND_ID)
                .header("X-Frontend-Version", FRONTEND_VERSION)
                .header("X-Access-Right-Key", watch.accessRightKey)
                .header("X-Request-With", "https://www.nicovideo.jp")
                .header("Referer", canonicalUrl)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            "N站视频流申请失败",
            cookieHeader
        )
        val manifestUrl = NiconicoApiParser.parseHlsContentUrl(manifestJson)
        val safeTitle = FileNameSanitizer.sanitize(watch.title, videoId)
        SniffedResource(
            url = manifestUrl,
            kind = ResourceKind.PLAYLIST,
            fileName = "$safeTitle-$videoId.m3u8",
            mimeType = "application/vnd.apple.mpegurl",
            pageTitle = watch.title.ifBlank { videoId },
            sourcePage = canonicalUrl
        )
    }

    private fun executeJson(
        request: Request,
        fallbackMessage: String,
        cookieHeader: String
    ): String =
        httpClient.newCall(
            request.newBuilder().apply {
                if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader)
            }.build()
        ).execute().use { response ->
            val body = response.body?.string()
                ?: throw java.io.IOException("$fallbackMessage：响应为空")
            if (!response.isSuccessful) {
                val reason = runCatching {
                    val root = com.google.gson.JsonParser.parseString(body).asJsonObject
                    root.getAsJsonObject("data")?.get("reasonCode")?.asString
                        ?: root.getAsJsonObject("meta")?.get("errorCode")?.asString
                }.getOrNull()
                if (!reason.isNullOrBlank()) {
                    throw java.io.IOException(when (reason.uppercase()) {
                        "DOMESTIC_VIDEO", "HIGH_RISK_COUNTRY_VIDEO" ->
                            "该视频受到地区播放限制，应用不会绕过地区限制"
                        "CHANNEL_MEMBER_ONLY" -> "该视频仅限频道会员观看"
                        "PPV_VIDEO" -> "该视频需要单独购买后才能观看"
                        "PREMIUM_ONLY" -> "该视频需要 Niconico Premium 权限"
                        else -> "$fallbackMessage（$reason）"
                    })
                }
                throw java.io.IOException("$fallbackMessage（HTTP ${response.code}）")
            }
            body
        }

    private companion object {
        const val FRONTEND_ID = "6"
        const val FRONTEND_VERSION = "0"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
