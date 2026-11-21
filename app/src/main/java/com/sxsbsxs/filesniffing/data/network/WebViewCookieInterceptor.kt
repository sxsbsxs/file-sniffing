package com.sxsbsxs.filesniffing.data.network

import android.webkit.CookieManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Shares the logged-in WebView session with the HTTP downloader without exporting
 * cookies to storage or to a different host. CookieManager performs the URL/domain
 * matching for each network exchange, including redirect targets.
 */
internal class WebViewCookieInterceptor(
    private val cookieProvider: (String) -> String? = { url ->
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    }
) : Interceptor {

    internal fun withWebViewCookies(request: Request): Request {
        if (!request.header("Cookie").isNullOrBlank()) return request
        val cookie = runCatching {
            cookieProvider(request.url.toString())
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return request
        return request.newBuilder()
            .header("Cookie", cookie)
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(withWebViewCookies(chain.request()))
}
