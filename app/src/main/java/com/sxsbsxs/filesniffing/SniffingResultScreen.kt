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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SniffingResultScreen(
    pageUrl: String,
    resources: List<SniffedResource>,
    isLoading: Boolean,
    inspectionError: String?,
    onLoadingChange: (Boolean) -> Unit,
    onResourceFound: (SniffedResource) -> Unit,
    onBack: () -> Unit,
    onDownload: (SniffedResource) -> Unit,
    onDownloadBatch: (List<SniffedResource>) -> Unit
) {
    var currentFilter by remember { mutableStateOf("全部") }
    var selectedResourceIds by remember { mutableStateOf(emptySet<String>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var pageError by remember { mutableStateOf<String?>(null) }
    var rendererCrashed by remember(pageUrl) { mutableStateOf(false) }
    var webViewGeneration by remember(pageUrl) { mutableIntStateOf(0) }
    val webViewHolder = remember { WebViewHolder() }
    val diagnosticHandler = remember { Handler(Looper.getMainLooper()) }
    var youtubeDiagnostics by remember(pageUrl) {
        mutableStateOf(
            YouTubeDiagnosticState(
                isYouTubePage = YouTubeRequestDiagnostics.isYouTubePageUrl(pageUrl)
            )
        )
    }

    fun postYouTubeDiagnosticUpdate(
        update: (YouTubeDiagnosticState) -> YouTubeDiagnosticState
    ) {
        diagnosticHandler.post { youtubeDiagnostics = update(youtubeDiagnostics) }
    }

    fun observeYouTubeRequest(rawUrl: String, headers: Map<String, String> = emptyMap()) {
        val observation = YouTubeRequestDiagnostics.inspect(rawUrl, headers)
        if (observation.isYouTubeRelated) {
            postYouTubeDiagnosticUpdate { it.recordRequest(observation) }
        }
    }

    fun observePerformanceResource(rawUrl: String, initiatorType: String, responseStatus: Int) {
        val observation = YouTubeRequestDiagnostics.inspect(rawUrl, emptyMap())
        if (observation.isYouTubeRelated) {
            postYouTubeDiagnosticUpdate {
                it.recordPerformance(observation, initiatorType, responseStatus)
            }
        }
    }
    val filteredResources = resources.filter {
        when (currentFilter) {
            "视频" -> it.kind == ResourceKind.VIDEO || it.kind == ResourceKind.PLAYLIST || it.kind == ResourceKind.SEGMENT
            "音频" -> it.kind == ResourceKind.AUDIO
            "图片" -> it.kind == ResourceKind.IMAGE
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = pageTitle.ifBlank { "正在分析网页" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pageUrl,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    pageError = null
                    onLoadingChange(true)
                    if (rendererCrashed) {
                        rendererCrashed = false
                        webViewGeneration += 1
                    } else {
                        runCatching { webViewHolder.view?.reload() }
                            .onFailure {
                                rendererCrashed = true
                                onLoadingChange(false)
                                pageError = "网页内核无法继续运行，请点击刷新重建页面"
                            }
                    }
                }
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Detailed request diagnostics are available from the toolbar info action.

        DisposableEffect(Unit) {
            onDispose {
                webViewHolder.view?.apply {
                    stopLoading()
                    removeJavascriptInterface("FileSniffBridge")
                    destroy()
                }
                webViewHolder.view = null
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "资源",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    resources.size.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedResourceIds = emptySet()
                }) {
                    Text(
                        if (selectionMode) "取消选择" else "多选",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("全部", "视频", "音频", "图片").forEach { filter ->
                    TextButton(onClick = { currentFilter = filter }) {
                        Text(
                            filter,
                            color = if (currentFilter == filter) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selectionMode && selectedResourceIds.isNotEmpty()) {
                    TextButton(onClick = {
                        onDownloadBatch(resources.filter { it.id in selectedResourceIds })
                        selectedResourceIds = emptySet()
                        selectionMode = false
                    }) {
                        Text(
                            "下载所选 " + selectedResourceIds.size,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (filteredResources.isEmpty())            Spacer(Modifier.height(10.dp))
            if (filteredResources.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        when {
                            isLoading -> "正在扫描网页资源…"
                            pageError != null -> "网页加载失败"
                            else -> "暂未发现媒体资源"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (pageError != null) "检查网络后点击刷新重试" else "网页加载并播放媒体后，识别结果会显示在这里",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filteredResources, key = { it.id }) { item ->
                        ResourceRow(
                            item = item,
                            selected = item.id in selectedResourceIds,
                            selectionMode = selectionMode,
                            onSelectionChange = { selected ->
                                selectedResourceIds = if (selected) {
                                    selectedResourceIds + item.id
                                } else {
                                    selectedResourceIds - item.id
                                }
                            },
                            onDownload = { onDownload(item) }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
        if (showDiagnostics && youtubeDiagnostics.isYouTubePage) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text("关闭", color = MaterialTheme.colorScheme.primary)
                }
            },
            title = { Text("网页请求诊断") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Player API：" + youtubeDiagnostics.playerApiRequests + " · 视频请求：" + youtubeDiagnostics.googleVideoRequests)
                    Text("性能记录：" + youtubeDiagnostics.performanceEntries + " · HTTP 错误：" + youtubeDiagnostics.youtubeHttpErrors)
                    Text("网络错误：" + youtubeDiagnostics.networkErrors + " · 媒体域错误：" + youtubeDiagnostics.googleVideoHttpErrors)
                    Text("观察到 Cookie 头：" + (if (youtubeDiagnostics.cookieHeaderObserved) "是" else "未见"))
                    Text("观察到 Authorization 头：" + (if (youtubeDiagnostics.authorizationHeaderObserved) "是" else "未见"))
                    Text(
                        "请求回调只记录类别、计数和状态，不保存 URL 参数、Cookie 或 Token。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun ResourceRow(
    item: SniffedResource,
    selected: Boolean,
    selectionMode: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onDownload: () -> Unit
) {
    val icon = when (item.kind) {
        ResourceKind.VIDEO, ResourceKind.PLAYLIST, ResourceKind.SEGMENT -> Icons.Rounded.VideoLibrary
        ResourceKind.AUDIO -> Icons.Rounded.MusicNote
        ResourceKind.IMAGE -> Icons.Rounded.Image
        else -> Icons.Rounded.Download
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectionChange
                )
            }
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(13.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.fileName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.kind.label + " · " + (item.mimeType ?: item.url.substringBefore('?').takeLast(48)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.kind == ResourceKind.PLAYLIST || item.kind == ResourceKind.SEGMENT) {
                    Text(
                        when {
                            item.kind == ResourceKind.SEGMENT -> "单个媒体分段，不是完整视频"
                            item.fileName.endsWith(".m3u8", ignoreCase = true) -> "HLS 自适应流 · Media3 导出 MP4"
                            item.fileName.endsWith(".mpd", ignoreCase = true) -> "DASH 自适应流 · Media3 导出 MP4"
                            else -> "媒体清单"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "下载资源",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

private class WebViewHolder {
    var view: WebView? = null
}

private data class YouTubeDiagnosticState(
    val isYouTubePage: Boolean = false,
    val playerApiRequests: Int = 0,
    val googleVideoRequests: Int = 0,
    val otherYouTubeRequests: Int = 0,
    val performanceEntries: Int = 0,
    val youtubeHttpErrors: Int = 0,
    val googleVideoHttpErrors: Int = 0,
    val networkErrors: Int = 0,
    val cookieHeaderObserved: Boolean = false,
    val authorizationHeaderObserved: Boolean = false,
    val lastHttpStatus: Int? = null,
    val lastNetworkErrorCode: Int? = null,
    val lastInitiatorType: String = ""
) {
    fun recordRequest(
        observation: YouTubeRequestDiagnostics.Observation
    ): YouTubeDiagnosticState = copy(
        isYouTubePage = true,
        playerApiRequests = playerApiRequests + if (observation.isPlayerApi) 1 else 0,
        googleVideoRequests = googleVideoRequests + if (observation.isGoogleVideo) 1 else 0,
        otherYouTubeRequests = otherYouTubeRequests +
            if (observation.requestType == YouTubeRequestDiagnostics.RequestType.YOUTUBE_PAGE) 1 else 0,
        cookieHeaderObserved = cookieHeaderObserved || observation.isCookieHeaderPresent(),
        authorizationHeaderObserved = authorizationHeaderObserved || observation.isAuthorizationHeaderPresent()
    )

    fun recordPerformance(
        observation: YouTubeRequestDiagnostics.Observation,
        initiatorType: String,
        responseStatus: Int
    ): YouTubeDiagnosticState = copy(
        isYouTubePage = true,
        performanceEntries = performanceEntries + 1,
        lastInitiatorType = initiatorType.take(40),
        lastHttpStatus = responseStatus.takeIf { it in 100..599 } ?: lastHttpStatus
    )

    fun recordHttpError(
        observation: YouTubeRequestDiagnostics.Observation,
        status: Int
    ): YouTubeDiagnosticState = copy(
        isYouTubePage = true,
        youtubeHttpErrors = youtubeHttpErrors + 1,
        googleVideoHttpErrors = googleVideoHttpErrors + if (observation.isGoogleVideo) 1 else 0,
        lastHttpStatus = status.takeIf { it in 100..599 } ?: lastHttpStatus
    )

    fun recordNetworkError(
        observation: YouTubeRequestDiagnostics.Observation,
        errorCode: Int
    ): YouTubeDiagnosticState = copy(
        isYouTubePage = true,
        networkErrors = networkErrors + 1,
        lastNetworkErrorCode = errorCode.takeIf { it != 0 } ?: lastNetworkErrorCode
    )
}

private class ResourceBridge(
    private val onFound: (SniffedResource) -> Unit,
    private val sourcePage: String,
    private val mainHandler: Handler,
    private val onPerformanceEntry: (String, String, Int) -> Unit
) {
    @JavascriptInterface
    fun onResource(url: String, kind: String, mime: String, title: String) {
        val resource = runCatching {
            ResourceSniffer.detect(
                rawUrl = url,
                hintedKind = kind,
                hintedMime = mime,
                pageTitle = title,
                sourcePage = sourcePage
            )
        }.getOrNull() ?: return
        mainHandler.post { onFound(resource) }
    }

    @JavascriptInterface
    fun onPerformanceResource(url: String, initiatorType: String, responseStatus: Int) {
        mainHandler.post {
            onPerformanceEntry(url, initiatorType, responseStatus)
        }
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
    function reportPerformanceEntry(entry) {
      report(entry.name, '', '');
      try {
        window.FileSniffBridge.onPerformanceResource(
          String(entry.name),
          String(entry.initiatorType || ''),
          Number(entry.responseStatus || 0)
        );
      } catch (e) {}
    }
    performance.getEntriesByType('resource').forEach(reportPerformanceEntry);
    new PerformanceObserver(function(list) {
      list.getEntries().forEach(reportPerformanceEntry);
    }).observe({entryTypes: ['resource']});
  } catch (e) {}
  try {
    new MutationObserver(function(records) {
      records.forEach(function(record) {
        if (record.type === 'attributes') {
          var target = record.target;
          if (target && target.tagName) {
            var targetTag = target.tagName.toLowerCase();
            var targetKind = targetTag === 'video' ? 'video' : (targetTag === 'audio' ? 'audio' : (targetTag === 'img' ? 'image' : ''));
            report(target.currentSrc || target.src || target.getAttribute('src'), targetKind, target.getAttribute('type') || '');
          }
        }
        record.addedNodes.forEach(function(node) {
          if (node && node.querySelectorAll) {
            if (node.matches && node.matches('video,audio,source,img')) {
              var tag = node.tagName.toLowerCase();
              var parent = node.closest ? node.closest('video,audio') : null;
              var kind = tag === 'img' ? 'image' : (parent ? parent.tagName.toLowerCase() : tag);
              report(node.currentSrc || node.src || node.getAttribute('src'), kind, node.getAttribute('type') || '');
            }
            inspect(node);
          }
        });
      });
    }).observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['src']});
  } catch (e) {}
})();
"""
