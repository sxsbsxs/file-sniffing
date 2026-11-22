package com.sxsbsxs.filesniffing

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sxsbsxs.filesniffing.data.extractor.NiconicoCookieProvider

@Composable
fun NiconicoLoginScreen(
    onBack: () -> Unit,
    onLoginStateChanged: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var loggedIn by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun refreshLoginState() {
        CookieManager.getInstance().flush()
        loggedIn = NiconicoCookieProvider.hasSessionInWebView()
        checked = true
        onLoginStateChanged(loggedIn)
    }

    LaunchedEffect(Unit) {
        refreshLoginState()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                runCatching {
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.webChromeClient = null
                    view.webViewClient = WebViewClient()
                    view.removeAllViews()
                    view.destroy()
                }
            }
            webView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                "Niconico 账号授权",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (loggedIn) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (loggedIn) "Niconico 已登录" else "请在下方登录 Niconico",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Text(
                    "在 Niconico 官方页面完成登录。应用不会读取或上传你的密码；只会在本机读取 user_session 会话，用于受限视频解析和下载。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.userAgentString = WebSettings.getDefaultUserAgent(viewContext)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val hasSession = NiconicoCookieProvider.hasSessionInWebView()
                            if (hasSession) {
                                loggedIn = true
                                checked = true
                                onLoginStateChanged(true)
                            }
                        }
                    }
                    loadUrl("https://account.nicovideo.jp/login")
                }.also { webView = it }
            }
        )

        if (checked && !loggedIn) {
            Text(
                "尚未检测到 user_session。请确认登录已完成；如果登录成功但状态未更新，点击下方检查。",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { webView?.reload() },
                modifier = Modifier.weight(1f)
            ) {
                Text("重新加载")
            }
            Button(
                onClick = { refreshLoginState() },
                modifier = Modifier.weight(1f)
            ) {
                Text("检查登录状态")
            }
            Button(
                onClick = onDone,
                enabled = loggedIn,
                modifier = Modifier.weight(1f)
            ) {
                Text("完成")
            }
        }
    }
}
