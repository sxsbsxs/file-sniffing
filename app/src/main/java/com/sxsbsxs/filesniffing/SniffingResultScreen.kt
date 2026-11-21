package com.sxsbsxs.filesniffing

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val BrowserInk = Color(0xFF17191F)
private val BrowserMuted = Color(0xFF777D88)
private val BrowserBlue = Color(0xFF4B66E8)
private val BrowserLine = Color(0xFFE9EBEF)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SniffingResultScreen(
    pageUrl: String,
    resources: List<SniffedResource>,
    isLoading: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    onResourceFound: (SniffedResource) -> Unit,
    onBack: () -> Unit,
    onDownload: (SniffedResource) -> Unit
) {
    var currentFilter by remember { mutableStateOf("全部") }
    var pageTitle by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val filteredResources = resources.filter {
        when (currentFilter) {
            "视频" -> it.kind == ResourceKind.VIDEO || it.kind == ResourceKind.PLAYLIST
            "音频" -> it.kind == ResourceKind.AUDIO
            "图片" -> it.kind == ResourceKind.IMAGE
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = BrowserInk)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pageTitle.ifBlank { "正在分析网页" },
                    color = BrowserInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pageUrl,
                    color = BrowserMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = {
                onLoadingChange(true)
                webView?.reload()
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = BrowserMuted)
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color.White),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    addJavascriptInterface(
                        ResourceBridge(
                            onFound = onResourceFound,
                            sourcePage = pageUrl,
                            mainHandler = Handler(Looper.getMainLooper())
                        ),
                        "FileSniffBridge"
                    )
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title.orEmpty()
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 100) onLoadingChange(false)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            onLoadingChange(true)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            onLoadingChange(false)
                            view?.evaluateJavascript(RESOURCE_CAPTURE_SCRIPT, null)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val requested = request?.url?.toString().orEmpty()
                            val found = ResourceSniffer.detect(
                                rawUrl = requested,
                                sourcePage = pageUrl
                            )
                            if (found != null) {
                                Handler(Looper.getMainLooper()).post { onResourceFound(found) }
                            }
                            return null
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) onLoadingChange(false)
                        }
                    }
                    loadUrl(pageUrl)
                }
            },
            update = { view ->
                if (view.url != pageUrl) view.loadUrl(pageUrl)
            }
        )
        DisposableEffect(webView) {
            onDispose {
                webView?.apply {
                    stopLoading()
                    removeJavascriptInterface("FileSniffBridge")
                    destroy()
                }
                webView = null
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F7F9))
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "发现的资源",
                        color = BrowserInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Text(
                        text = if (isLoading) "正在扫描页面请求…" else "已捕获 ${resources.size} 项可识别资源",
                        color = BrowserMuted,
                        fontSize = 12.sp
                    )
                }
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(42.dp),
                        color = BrowserBlue,
                        trackColor = Color(0xFFE4E7F2)
                    )
                } else {
                    Surface(color = Color(0xFFECEFFB), shape = CircleShape) {
                        Text(
                            text = resources.size.toString(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = BrowserBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("全部", "视频", "音频", "图片").forEach { filter ->
                    FilterChip(
                        selected = currentFilter == filter,
                        onClick = { currentFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (filteredResources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFB3B8C1),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isLoading) "正在寻找媒体资源" else "暂未发现可识别的媒体资源",
                            color = BrowserInk,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "页面里的资源会在捕获到后显示在这里",
                            color = BrowserMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(filteredResources, key = { it.id }) { item ->
                        ResourceRow(item = item, onDownload = { onDownload(item) })
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ResourceRow(
    item: SniffedResource,
    onDownload: () -> Unit
) {
    val icon = when (item.kind) {
        ResourceKind.VIDEO, ResourceKind.PLAYLIST -> Icons.Rounded.VideoLibrary
        ResourceKind.AUDIO -> Icons.Rounded.MusicNote
        ResourceKind.IMAGE -> Icons.Rounded.Image
        else -> Icons.Rounded.Download
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, BrowserLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F2F8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = BrowserBlue)
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    color = BrowserInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${item.kind.label} · ${item.mimeType ?: item.url.substringBefore('?').takeLast(48)}",
                    color = BrowserMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "下载资源",
                    tint = BrowserBlue
                )
            }
        }
    }
}

private class ResourceBridge(
    private val onFound: (SniffedResource) -> Unit,
    private val sourcePage: String,
    private val mainHandler: Handler
) {
    @JavascriptInterface
    fun onResource(url: String, kind: String, mime: String, title: String) {
        val resource = ResourceSniffer.detect(
            rawUrl = url,
            hintedKind = kind,
            hintedMime = mime,
            pageTitle = title,
            sourcePage = sourcePage
        ) ?: return
        mainHandler.post { onFound(resource) }
    }
}

private const val RESOURCE_CAPTURE_SCRIPT = """
(function() {
  if (window.__fileSniffObserverReady) return;
  window.__fileSniffObserverReady = true;
  function report(url, kind, mime) {
    if (!url || !window.FileSniffBridge) return;
    try { window.FileSniffBridge.onResource(String(url), String(kind || ''), String(mime || ''), document.title || ''); } catch (e) {}
  }
  function inspect(root) {
    try {
      root.querySelectorAll('video,audio,source,img').forEach(function(el) {
        var url = el.currentSrc || el.src || el.getAttribute('src');
        var tag = el.tagName.toLowerCase();
        var kind = tag === 'video' ? 'video' : (tag === 'audio' ? 'audio' : (tag === 'img' ? 'image' : ''));
        report(url, kind, el.getAttribute('type') || '');
        if (el.querySelectorAll) {
          el.querySelectorAll('source').forEach(function(source) {
            report(source.src || source.getAttribute('src'), kind, source.type || '');
          });
        }
      });
    } catch (e) {}
  }
  inspect(document);
  try {
    performance.getEntriesByType('resource').forEach(function(entry) { report(entry.name, '', ''); });
    new PerformanceObserver(function(list) {
      list.getEntries().forEach(function(entry) { report(entry.name, '', ''); });
    }).observe({entryTypes: ['resource']});
  } catch (e) {}
  try {
    new MutationObserver(function(records) {
      records.forEach(function(record) {
        record.addedNodes.forEach(function(node) {
          if (node && node.querySelectorAll) {
            if (node.matches && node.matches('video,audio,source,img')) {
              var tag = node.tagName.toLowerCase();
              report(node.currentSrc || node.src || node.getAttribute('src'), tag === 'img' ? 'image' : tag, node.getAttribute('type') || '');
            }
            inspect(node);
          }
        });
      });
    }).observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['src']});
  } catch (e) {}
})();
"""
