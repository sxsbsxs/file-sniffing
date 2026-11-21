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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onLoadingChange: (Boolean) -> Unit,
    onResourceFound: (SniffedResource) -> Unit,
    onBack: () -> Unit,
    onDownload: (SniffedResource) -> Unit,
    onDownloadBatch: (List<SniffedResource>) -> Unit
) {
    var currentFilter by remember { mutableStateOf("全部") }
    var selectedResourceIds by remember { mutableStateOf(emptySet<String>()) }
    var pageTitle by remember { mutableStateOf("") }
    var pageError by remember { mutableStateOf<String?>(null) }
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
                    webViewHolder.view?.reload()
                }
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp)
                    .clip(RoundedCornerShape(18.dp)),
                factory = { context ->
                    WebView(context).apply {
                        webViewHolder.view = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.safeBrowsingEnabled = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        addJavascriptInterface(
                            ResourceBridge(
                                onFound = onResourceFound,
                                sourcePage = pageUrl,
                                mainHandler = diagnosticHandler,
                                onPerformanceEntry = ::observePerformanceResource
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
                                pageError = null
                                if (YouTubeRequestDiagnostics.isYouTubePageUrl(url.orEmpty())) {
                                    postYouTubeDiagnosticUpdate { it.copy(isYouTubePage = true) }
                                }
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
                                if (request != null) {
                                    observeYouTubeRequest(requested, request.requestHeaders)
                                }
                                val found = ResourceSniffer.detect(
                                    rawUrl = requested,
                                    sourcePage = view?.url ?: pageUrl
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
                                if (request != null) {
                                    val observation = YouTubeRequestDiagnostics.inspect(
                                        request.url.toString(),
                                        request.requestHeaders
                                    )
                                    if (observation.isYouTubeRelated) {
                                        postYouTubeDiagnosticUpdate {
                                            it.recordNetworkError(observation, error?.errorCode ?: 0)
                                        }
                                    }
                                }
                                if (request?.isForMainFrame == true) {
                                    pageError = error?.description
                                        ?.toString()
                                        ?.take(160)
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "网页加载失败，请检查网络或链接"
                                    onLoadingChange(false)
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                if (request != null) {
                                    val observation = YouTubeRequestDiagnostics.inspect(
                                        request.url.toString(),
                                        request.requestHeaders
                                    )
                                    if (observation.isYouTubeRelated) {
                                        val status = errorResponse?.statusCode ?: 0
                                        postYouTubeDiagnosticUpdate {
                                            it.recordHttpError(observation, status)
                                        }
                                    }
                                }
                                if (request?.isForMainFrame == true) {
                                    val statusCode = errorResponse?.statusCode
                                    val reason = errorResponse?.reasonPhrase.orEmpty()
                                    pageError = if (statusCode != null) {
                                        "网页返回 HTTP $statusCode" +
                                            reason.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                                    } else {
                                        "网页响应异常"
                                    }
                                    onLoadingChange(false)
                                }
                            }
                        }
                        loadUrl(pageUrl)
                    }
                },
                update = { view ->
                    if (view.url != pageUrl) view.loadUrl(pageUrl)
                }
            )
        }

        if (pageError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "网页加载失败",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = pageError.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "检查网络后，点击右上角刷新重试",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (youtubeDiagnostics.isYouTubePage) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "YouTube 请求诊断",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Player API ${youtubeDiagnostics.playerApiRequests} · Google Video ${youtubeDiagnostics.googleVideoRequests} · 性能记录 ${youtubeDiagnostics.performanceEntries}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "HTTP 错误 ${youtubeDiagnostics.youtubeHttpErrors}（媒体域 ${youtubeDiagnostics.googleVideoHttpErrors}） · 网络错误 ${youtubeDiagnostics.networkErrors}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "请求回调可见 Cookie 头：${if (youtubeDiagnostics.cookieHeaderObserved) "是" else "未见"} · Authorization 头：${if (youtubeDiagnostics.authorizationHeaderObserved) "是" else "未见"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = when {
                            youtubeDiagnostics.googleVideoRequests > 0 ->
                                "已观察到媒体域请求；通用嗅探器不会把它直接当作完整视频地址。"
                            youtubeDiagnostics.playerApiRequests > 0 ->
                                "已观察到播放器信息请求，但尚未观察到 Google Video 媒体请求。"
                            else ->
                                "尚未观察到播放器请求；请等待页面加载并开始播放后再检查。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (youtubeDiagnostics.lastHttpStatus != null ||
                        youtubeDiagnostics.lastNetworkErrorCode != null ||
                        youtubeDiagnostics.lastInitiatorType.isNotBlank()
                    ) {
                        Text(
                            text = buildString {
                                youtubeDiagnostics.lastHttpStatus?.let { append("最近 HTTP：$it") }
                                youtubeDiagnostics.lastNetworkErrorCode?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append("最近网络错误码：$it")
                                }
                                youtubeDiagnostics.lastInitiatorType.takeIf { it.isNotBlank() }?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append("发起类型：$it")
                                }
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = "仅记录请求类别、计数和状态；不保存 URL 参数、Cookie 或 Token。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }

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
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "发现的资源",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = if (isLoading) {
                            "正在扫描网页请求…"
                        } else {
                            "已捕获 ${resources.size} 项可识别资源"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = resources.size.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("全部", "视频", "音频", "图片").forEach { filter ->
                    FilterChip(
                        selected = currentFilter == filter,
                        onClick = { currentFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) }
                    )
                }
            }

            if (selectedResourceIds.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onDownloadBatch(resources.filter { it.id in selectedResourceIds })
                        selectedResourceIds = emptySet()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "下载已选 ${selectedResourceIds.size} 项",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            if (filteredResources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(17.dp)
                                    .size(30.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when {
                            isLoading -> "正在寻找媒体资源"
                            pageError != null -> "无法读取网页内容"
                            else -> "暂未发现可识别的媒体资源"
                        },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (pageError != null) {
                            "请检查网络或链接后点击右上角刷新"
                        } else {
                            "捕获到的资源会显示在这里"
                        },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredResources, key = { it.id }) { item ->
                        ResourceRow(
                            item = item,
                            selected = item.id in selectedResourceIds,
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
}

@Composable
private fun ResourceRow(
    item: SniffedResource,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onDownload: () -> Unit
) {
    val icon = when (item.kind) {
        ResourceKind.VIDEO, ResourceKind.PLAYLIST, ResourceKind.SEGMENT -> Icons.Rounded.VideoLibrary
        ResourceKind.AUDIO -> Icons.Rounded.MusicNote
        ResourceKind.IMAGE -> Icons.Rounded.Image
        else -> Icons.Rounded.Download
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(13.dp)
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.fileName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.kind.label} · ${item.mimeType ?: item.url.substringBefore('?').takeLast(48)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.kind == ResourceKind.PLAYLIST || item.kind == ResourceKind.SEGMENT) {
                    Text(
                        text = when {
                            item.kind == ResourceKind.SEGMENT -> "单个媒体分段，不是完整视频"
                            item.fileName.endsWith(".m3u8", ignoreCase = true) -> "HLS 自适应流 · Media3 导出 MP4"
                            item.fileName.endsWith(".mpd", ignoreCase = true) -> "DASH 自适应流 · Media3 导出 MP4"
                            else -> "媒体清单"
                        },
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectionChange
            )
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "下载资源",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
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
        cookieHeaderObserved = cookieHeaderObserved || observation.cookieHeaderPresent,
        authorizationHeaderObserved = authorizationHeaderObserved || observation.authorizationHeaderPresent
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
        val resource = ResourceSniffer.detect(
            rawUrl = url,
            hintedKind = kind,
            hintedMime = mime,
            pageTitle = title,
            sourcePage = sourcePage
        ) ?: return
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
