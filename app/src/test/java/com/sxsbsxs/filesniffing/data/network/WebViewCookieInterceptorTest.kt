package com.sxsbsxs.filesniffing.data.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebViewCookieInterceptorTest {
    @Test
    fun readsCookiesForTheExactResourceUrl() {
        var queriedUrl: String? = null
        val interceptor = WebViewCookieInterceptor { url ->
            queriedUrl = url
            "session=local-webview"
        }
        val request = Request.Builder()
            .url("https://cdn.example.test/video/stream.mp4")
            .build()

        val result = interceptor.withWebViewCookies(request)

        assertEquals("https://cdn.example.test/video/stream.mp4", queriedUrl)
        assertEquals("session=local-webview", result.header("Cookie"))
    }

    @Test
    fun doesNotOverwriteAnExplicitCookieHeader() {
        val interceptor = WebViewCookieInterceptor {
            throw AssertionError("Cookie provider must not be called for an explicit header")
        }
        val request = Request.Builder()
            .url("https://example.test/file.zip")
            .header("Cookie", "manual=keep")
            .build()

        val result = interceptor.withWebViewCookies(request)

        assertEquals("manual=keep", result.header("Cookie"))
    }

    @Test
    fun leavesTheRequestUnchangedWhenNoWebViewCookieExists() {
        val interceptor = WebViewCookieInterceptor { null }
        val request = Request.Builder().url("https://example.test/public.bin").build()

        val result = interceptor.withWebViewCookies(request)

        assertNull(result.header("Cookie"))
        assertEquals(request, result)
    }
}
