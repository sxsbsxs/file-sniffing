package com.sxsbsxs.filesniffing

import java.net.URI
import java.util.Locale

/**
 * Privacy-safe interpretation of page-level verification and availability messages.
 *
 * Only boolean signals are received from the WebView. Page text, resource URLs,
 * cookies and authentication values are never returned to native code or stored.
 */
object PageAccessDiagnostics {
    data class Result(
        val code: String,
        val title: String,
        val message: String,
        val nextStep: String
    )

    fun classify(
        pageUrl: String,
        humanVerification: Boolean = false,
        regionRestricted: Boolean = false,
        accessDenied: Boolean = false,
        nicovideodlServiceEnding: Boolean = false,
        nicovideodlPremiumExpired: Boolean = false
    ): Result? {
        if (humanVerification) {
            return Result(
                code = "HUMAN_VERIFICATION",
                title = "网页要求完成验证",
                message = "当前页面出现验证码或人机验证。验证未完成前，站点可能不会加载视频、音频或媒体清单。",
                nextStep = "可在上方网页预览中按站点要求手动完成验证，然后点击刷新重新扫描。嗅探器不会绕过站点验证。"
            )
        }
        if (regionRestricted) {
            return Result(
                code = "REGION_RESTRICTED",
                title = "站点限制当前地区访问",
                message = "页面明确提示当前国家或地区不可用。媒体播放流程没有正常启动，因此暂时没有可提取的视频资源。",
                nextStep = "请使用站点允许的访问方式；应用不会伪造媒体地址或绕过地区限制。"
            )
        }
        if (accessDenied) {
            return Result(
                code = "ACCESS_DENIED",
                title = "站点拒绝了当前请求",
                message = "页面返回访问拒绝或请求拦截提示。普通图片、脚本和 JSON 资源不代表已经获取到视频流。",
                nextStep = "可稍后刷新，或检查站点是否要求正常登录、降低请求频率或使用受支持的入口。"
            )
        }

        if (isNicoVideoDlHost(pageUrl) && nicovideodlServiceEnding) {
            return Result(
                code = "NICOVIDEODL_SERVICE_ENDING",
                title = "nicovideodl.jp 已公告将结束服务",
                message = buildString {
                    append("该站页面公告提到域名将在 11 月到期并结束服务。")
                    if (nicovideodlPremiumExpired) append("公告还称 Premium 已到期、当前最高 720p。")
                    append("这个第三方页面不再适合作为稳定的媒体提取入口。")
                },
                nextStep = "若页面没有实际加载视频或 HLS/DASH 清单，嗅探器只能列出页面自身资源，不能从公告页生成视频下载地址。"
            )
        }
        if (isNicoVideoDlHost(pageUrl) && nicovideodlPremiumExpired) {
            return Result(
                code = "NICOVIDEODL_QUALITY_LIMITED",
                title = "第三方站点提示画质受限",
                message = "该站页面公告称 Premium 已到期，当前最高 720p。实际可下载画质仍以页面真正加载的视频或媒体清单为准。",
                nextStep = "请先确认页面已开始播放或生成媒体清单；只有图片、脚本或 JSON 请求时，不会显示为视频下载资源。"
            )
        }
        return null
    }

    private fun isNicoVideoDlHost(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()
            ?.lowercase(Locale.ROOT)
            ?: return false
        return host == "nicovideodl.jp" || host == "www.nicovideodl.jp"
    }
}
