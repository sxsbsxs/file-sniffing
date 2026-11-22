package com.sxsbsxs.filesniffing

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.sxsbsxs.filesniffing.ui.theme.FileSniffingTheme
import com.sxsbsxs.filesniffing.presentation.downloads.DownloadCenterViewModel
import java.util.UUID
import com.sxsbsxs.filesniffing.presentation.sniffing.SniffingViewModel
import com.sxsbsxs.filesniffing.presentation.sniffing.DownloadOptionsSheet
import com.sxsbsxs.filesniffing.data.preferences.DownloadTuning

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sharedLink = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLink.value = extractSharedLink(intent)
        setContent {
            FileSniffingTheme {
                FileSniffingApp(sharedLink = sharedLink.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedLink.value = extractSharedLink(intent)
    }

    private fun extractSharedLink(source: Intent?): String {
        if (source == null) return ""
        val sharedText = when (source.action) {
            Intent.ACTION_SEND -> source.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            else -> ""
        }.trim()
        return SharedLinkNormalizer.normalize(sharedText)
            ?: SharedLinkNormalizer.extractCandidate(sharedText)
            ?: sharedText
    }
}

@Composable
private fun FileSniffingApp(sharedLink: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val startupCrashReport = remember(context) { CrashReporter.read(context) }
    var showCrashReportDialog by rememberSaveable {
        mutableStateOf(!startupCrashReport.isNullOrBlank())
    }
    val sniffingViewModel: SniffingViewModel = hiltViewModel()
    val downloadCenterViewModel: DownloadCenterViewModel = hiltViewModel()
    val sniffingState by sniffingViewModel.uiState.collectAsState()
    val selectedFolderUri by downloadCenterViewModel.selectedDownloadFolder.collectAsState()
    val downloadTuning by downloadCenterViewModel.downloadTuning.collectAsState()
    val storedDownloadHistory by downloadCenterViewModel.downloadHistory.collectAsState()

    var askedForNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pageUrl by rememberSaveable { mutableStateOf(sharedLink) }
    var pendingDownloadOptions by remember { mutableStateOf<List<SniffedResource>?>(null) }

    LaunchedEffect(sharedLink) {
        if (sharedLink.isNotBlank()) {
            pageUrl = sharedLink
            selectedTab = 0
            sniffingViewModel.closeInspection()
        }
    }

    val downloadTasks = remember { mutableStateListOf<DownloadTaskUi>() }
    // Prevent a fast double tap from creating duplicate WorkManager requests for one URL.
    val activeDownloadUrls = remember { mutableMapOf<String, String>() }
    val storedTasksById = storedDownloadHistory.associateBy { it.id }
    val liveTasksWithStoredUri = downloadTasks.map { task ->
        val stored = storedTasksById[task.id]
        when {
            stored?.state == "paused" -> task.copy(
                state = "paused",
                progress = stored.progress,
                detail = stored.detail,
                fileUri = task.fileUri ?: stored.destinationUri,
                downloadedBytes = stored.downloadedBytes,
                totalBytes = stored.totalBytes,
                speedBytesPerSecond = stored.speedBytesPerSecond
            )
            task.fileUri != null -> task
            else -> task.copy(fileUri = stored?.destinationUri)
        }
    }
    val visibleDownloadTasks = (
        liveTasksWithStoredUri + storedDownloadHistory.map { record ->
            DownloadTaskUi(
                id = record.id,
                fileName = record.fileName,
                progress = record.progress,
                state = record.state,
                detail = record.detail,
                fileUri = record.destinationUri,
                resourceKind = record.resourceKind,
                mimeType = record.mimeType,
                downloadedBytes = record.downloadedBytes,
                totalBytes = record.totalBytes,
                speedBytesPerSecond = record.speedBytesPerSecond,
                adaptiveVideoHeight = record.adaptiveVideoHeight
            )
        }
    ).distinctBy { it.id }

    DisposableEffect(context) {
        val removeObserver = DownloadEngine.observeAll(context.applicationContext) { task ->
            val index = downloadTasks.indexOfFirst { it.id == task.id }
            if (index < 0) downloadTasks.add(0, task)
            else downloadTasks[index] = task
        }
        onDispose { removeObserver() }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
            }
            downloadCenterViewModel.setDownloadFolder(uri.toString())
            coroutineScope.launch { snackbarHostState.showSnackbar("保存位置已更新") }
        }
    }

    val folderLabel = selectedFolderUri
        ?.let { folderNameFromUri(Uri.parse(it)) }
        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Download（默认）" else "选择手机文件夹"
    val hasOutputFolder = selectedFolderUri != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    if (showCrashReportDialog && !startupCrashReport.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = {
                showCrashReportDialog = false
                CrashReporter.clear(context)
            },
            title = { Text("检测到上次异常退出") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "已在本机保留诊断报告。复制后可以发给开发者定位问题。",
                        fontSize = 13.sp
                    )
                    Text(
                        startupCrashReport.lineSequence().take(9).joinToString("\n"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 9,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(startupCrashReport))
                        CrashReporter.clear(context)
                        showCrashReportDialog = false
                        coroutineScope.launch { snackbarHostState.showSnackbar("诊断报告已复制") }
                    }
                ) { Text("复制诊断报告") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        CrashReporter.clear(context)
                        showCrashReportDialog = false
                    }
                ) { Text("关闭") }
            }
        )
    }

    val enqueueDownload: (SniffedResource) -> Unit = enqueue@{ resource ->
        val alreadyActive = DownloadQueuePolicy.isAlreadyActive(
            sourceUrl = resource.downloadKey,
            activeSessionUrls = activeDownloadUrls.values,
            persistedActiveUrls = storedDownloadHistory
                .filter { it.state == "queued" || it.state == "downloading" || it.state == "paused" }
                .map { "${it.sourceUrl}#${it.formatSelector ?: it.resourceKind}" }
        )
        if (alreadyActive) {
            coroutineScope.launch { snackbarHostState.showSnackbar("相同链接已在下载队列中") }
            return@enqueue
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            !askedForNotificationPermission &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val folder = selectedFolderUri
        if (folder == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Android 9 及以下请先选择保存文件夹")
            }
        } else {
            val taskId = UUID.randomUUID()
            activeDownloadUrls[taskId.toString()] = resource.downloadKey
            coroutineScope.launch {
                try {
                    // Commit the retry source before enqueue, so a fast Worker cannot race this write.
                    downloadCenterViewModel.persistInitialTask(
                        taskId = taskId,
                        resource = resource,
                        destinationTreeUri = folder
                    )
                    DownloadEngine.start(
                        context = context.applicationContext,
                        destinationTree = folder?.let(Uri::parse),
                        resource = resource,
                        taskId = taskId
                    ) { task ->
                        val index = downloadTasks.indexOfFirst { it.id == task.id }
                        if (index < 0) downloadTasks.add(0, task)
                        else downloadTasks[index] = task
                        if (task.state == "queued" || task.state == "downloading") {
                            activeDownloadUrls[task.id] = resource.downloadKey
                        } else {
                            activeDownloadUrls.remove(task.id)
                        }
                        if (task.state == "failed") {
                            coroutineScope.launch { snackbarHostState.showSnackbar(task.detail) }
                        }
                    }
                    snackbarHostState.showSnackbar("已加入下载队列")
                } catch (error: Exception) {
                    activeDownloadUrls.remove(taskId.toString())
                    snackbarHostState.showSnackbar(
                        "无法创建下载任务：" + (error.message ?: "请重试")
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
        bottomBar = {
            MainNavigationBar(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    pageUrl = pageUrl,
                    onUrlChange = { pageUrl = it },
                    onPaste = {
                        val pasted = clipboard.getText()?.text.orEmpty().trim()
                        if (pasted.isNotEmpty()) {
                            pageUrl = SharedLinkNormalizer.normalize(pasted)
                                ?: SharedLinkNormalizer.extractCandidate(pasted)
                                ?: pasted
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("剪贴板里没有可粘贴的文字")
                            }
                        }
                    },
                    onClear = {
                        pageUrl = ""
                        sniffingViewModel.closeInspection()
                    },
                    isResolving = sniffingState.isPageLoading,
                    resources = sniffingState.resources,
                    resultSummary = sniffingState.resultSummary,
                    errorMessage = sniffingState.errorMessage,
                    onResolve = {
                        val candidate = pageUrl.trim()
                        if (candidate.isBlank()) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("请先粘贴视频链接") }
                        } else if (candidate.length > 4096) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("链接过长，请检查后重试") }
                        } else {
                            val normalized = SharedLinkNormalizer.normalize(candidate)
                            if (normalized.isNullOrBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("暂不支持这个链接格式")
                                }
                            } else {
                                pageUrl = normalized
                                sniffingViewModel.resolveUrl(normalized) { resources, error ->
                                    if (!error.isNullOrBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("解析失败：$error")
                                        }
                                    } else if (!resources.isNullOrEmpty()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("解析完成：${resources.size} 项")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onConfigureDownload = { pendingDownloadOptions = it }
                )
                1 -> DownloadsScreen(
                    folderLabel = folderLabel,
                    hasFolder = hasOutputFolder,
                    tasks = visibleDownloadTasks,
                    tuning = downloadTuning,
                    onUpdateTuning = downloadCenterViewModel::updateDownloadTuning,
                    onChooseFolder = { folderPicker.launch(null) },
                    onBackToSniff = { selectedTab = 0 },
                    onRetryTask = { task ->
                        downloadCenterViewModel.loadRetryRecord(task.id) { record ->
                            if (record == null || record.sourceUrl.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("任务缺少原始参数，请重新粘贴链接后下载")
                                }
                            } else {
                                val retryTaskId = UUID.randomUUID()
                                coroutineScope.launch {
                                    try {
                                        downloadCenterViewModel.persistRetryTask(
                                            taskId = retryTaskId,
                                            previous = record,
                                            partialUri = task.fileUri
                                        )
                                        DownloadEngine.retry(
                                            context = context.applicationContext,
                                            record = record,
                                            partialUri = task.fileUri,
                                            taskId = retryTaskId
                                        ) { updated ->
                                            val index = downloadTasks.indexOfFirst { it.id == updated.id }
                                            if (index < 0) downloadTasks.add(0, updated)
                                            else downloadTasks[index] = updated
                                        }
                                    } catch (error: Exception) {
                                        snackbarHostState.showSnackbar(
                                            "无法重新加入下载队列：" + (error.message ?: "请重试")
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onPauseTask = { task ->
                        DownloadEngine.pause(context.applicationContext, task.id) {
                            coroutineScope.launch {
                                downloadCenterViewModel.updateTaskState(
                                    task.id,
                                    state = "paused",
                                    detail = "已暂停，可继续下载"
                                )
                            }
                        }
                    },
                    onCancelTask = { task ->
                        DownloadEngine.cancel(context.applicationContext, task.id) {
                            coroutineScope.launch {
                                downloadCenterViewModel.updateTaskState(
                                    task.id,
                                    state = "cancelled",
                                    detail = "下载已取消，可重新下载"
                                )
                            }
                        }
                    },
                    onOpenTask = { task ->
                        task.fileUri?.let(Uri::parse)?.let { openLocalDocument(context, it) }
                    }
                )
                else -> LocalFilesScreen(
                    treeUri = selectedFolderUri?.let(Uri::parse),
                    folderLabel = folderLabel,
                    onChooseFolder = { folderPicker.launch(null) },
                    onOpenFile = { uri, mimeType ->
                        openLocalDocument(context, uri, mimeType)
                    }
                )
            }
        }
    }

    pendingDownloadOptions?.let { options ->
        DownloadOptionsSheet(
            resources = options,
            onDismiss = { pendingDownloadOptions = null },
            onStartDownload = { resource ->
                pendingDownloadOptions = null
                enqueueDownload(resource)
            }
        )
    }
}

@Composable
private fun MainNavigationBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Rounded.Search, contentDescription = "解析", modifier = Modifier.size(21.dp)) },
            label = { Text("解析", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.surface
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Rounded.Download, contentDescription = "下载", modifier = Modifier.size(21.dp)) },
            label = { Text("下载", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.surface
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = "文件", modifier = Modifier.size(21.dp)) },
            label = { Text("文件", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
private fun HomeScreen(
    pageUrl: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    isResolving: Boolean,
    resources: List<SniffedResource>,
    resultSummary: String,
    errorMessage: String?,
    onResolve: () -> Unit,
    onConfigureDownload: (List<SniffedResource>) -> Unit
) {
    val resourceGroups = remember(resources) {
        resources.groupBy { it.url }.values.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 12.dp)
    ) {
        Text(
            "链接解析",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(22.dp))
        UrlInputCard(
            value = pageUrl,
            onValueChange = onUrlChange,
            onPaste = onPaste,
            onClear = onClear
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onResolve,
            enabled = !isResolving,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(9.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            if (isResolving) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(22.dp)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                )
                Spacer(Modifier.width(10.dp))
                Text("正在解析", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("解析链接", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(9.dp)
            ) {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp
                )
            }
        }

        if (resources.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "解析结果",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (resultSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    resultSummary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(resourceGroups, key = { group -> group.first().url }) { group ->
                    val representative = group.first()
                    val videoFormats = group.filter {
                        it.kind == ResourceKind.VIDEO &&
                            !it.videoFormatId.isNullOrBlank() &&
                            !it.formatSelector.isNullOrBlank()
                    }
                    val audioFormats = group.filter {
                        it.kind == ResourceKind.AUDIO &&
                            (!it.audioFormatId.isNullOrBlank() || !it.formatSelector.isNullOrBlank())
                    }
                    val hasVideoResource = group.any { it.kind == ResourceKind.VIDEO }
                    val highestVideoHeight = videoFormats.mapNotNull { option ->
                        option.qualityLabel?.substringBefore('p')?.toIntOrNull()
                    }.maxOrNull()
                    val groupDetails = buildList {
                        if (videoFormats.isNotEmpty()) {
                            add("视频 " + videoFormats.size + " 种")
                            highestVideoHeight?.let { add("最高 " + it + "p") }
                        }
                        if (audioFormats.isNotEmpty()) add("音轨 " + audioFormats.size + " 种")
                        if (videoFormats.isEmpty() && audioFormats.isEmpty()) {
                            add(representative.kind.label + " · 直链文件")
                        }
                    }.joinToString(" · ")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val thumbnailPainter = rememberAsyncImagePainter(representative.thumbnailUrl)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.size(width = 80.dp, height = 54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (thumbnailPainter.state is AsyncImagePainter.State.Success) {
                                            Image(
                                                painter = thumbnailPainter,
                                                contentDescription = "视频封面",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                if (videoFormats.isNotEmpty()) Icons.Rounded.VideoLibrary
                                                else Icons.Rounded.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        representative.pageTitle.ifBlank { representative.fileName },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val metadata = buildList {
                                        representative.uploader?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        representative.durationSeconds?.let { add(formatResourceDuration(it)) }
                                    }
                                    Text(
                                        metadata.ifEmpty { listOf(groupDetails) }.joinToString(" · "),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                groupDetails,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = { onConfigureDownload(group) },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        videoFormats.isNotEmpty() -> "选择画质与音轨"
                                        audioFormats.isNotEmpty() -> "选择音质"
                                        else -> "下载文件"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isResolving) "正在直接请求视频站解析接口…" else "粘贴视频链接后解析。应用不会打开网页。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UrlInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "粘贴或输入视频链接",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = if (value.isEmpty()) onPaste else onClear,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (value.isEmpty()) Icons.Rounded.ContentPaste else Icons.Rounded.Close,
                    contentDescription = if (value.isEmpty()) "粘贴链接" else "清空链接",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatResourceDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val remaining = total % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, remaining)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, remaining)
    }
}

private fun formatResourceBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> String.format("%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> String.format("%.1f MB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format("%.1f KB", value / 1024.0)
    else -> "$value B"
}

@Composable
private fun FolderPickerCard(
    folderLabel: String,
    hasFolder: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "保存位置",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                folderLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (hasFolder) "更改" else "选择",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DownloadsScreen(
    folderLabel: String,
    hasFolder: Boolean,
    tasks: List<DownloadTaskUi>,
    onChooseFolder: () -> Unit,
    onBackToSniff: () -> Unit,
    onRetryTask: (DownloadTaskUi) -> Unit,
    onPauseTask: (DownloadTaskUi) -> Unit,
    onCancelTask: (DownloadTaskUi) -> Unit,
    onOpenTask: (DownloadTaskUi) -> Unit,
    tuning: DownloadTuning,
    onUpdateTuning: (DownloadTuning) -> Unit
) {
    var statusFilter by rememberSaveable { mutableStateOf("全部") }
    var showDownloadSettings by rememberSaveable { mutableStateOf(false) }
    var connectionsDraft by remember(tuning.connectionsPerTask) {
        mutableIntStateOf(tuning.connectionsPerTask)
    }
    var parallelDraft by remember(tuning.parallelDownloads) {
        mutableIntStateOf(tuning.parallelDownloads)
    }
    var speedLimitDraft by remember(tuning.speedLimitMegabytesPerSecond) {
        mutableIntStateOf(tuning.speedLimitMegabytesPerSecond)
    }
    var kindFilter by rememberSaveable { mutableStateOf("全部") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf("最近") }
    val activeCount = tasks.count { it.state == "downloading" || it.state == "queued" }
    val pausedCount = tasks.count { it.state == "paused" }
    val completedCount = tasks.count { it.state == "completed" }
    val failedCount = tasks.count { it.state == "failed" || it.state == "cancelled" }
    val availableKinds = tasks.mapNotNull { task ->
        task.resourceKind?.let { raw -> runCatching { ResourceKind.valueOf(raw) }.getOrNull() }
    }.distinctBy { it.name }.sortedBy { it.label }
    val statusFilteredTasks = tasks.filter { task ->
        when (statusFilter) {
            "进行中" -> task.state == "downloading" || task.state == "queued"
            "已暂停" -> task.state == "paused"
            "已完成" -> task.state == "completed"
            "异常" -> task.state == "failed" || task.state == "cancelled"
            else -> true
        }
    }
    val filteredTasks = if (kindFilter == "全部") {
        statusFilteredTasks
    } else {
        statusFilteredTasks.filter { it.resourceKind == kindFilter }
    }
    val searchedTasks = filteredTasks.filter { task ->
        searchQuery.isBlank() ||
            task.fileName.contains(searchQuery, ignoreCase = true) ||
            task.detail.contains(searchQuery, ignoreCase = true)
    }
    val displayedTasks = when (sortOrder) {
        "名称" -> searchedTasks.sortedBy { it.fileName.lowercase(java.util.Locale.ROOT) }
        "进度" -> searchedTasks.sortedByDescending { it.progress.coerceAtLeast(0) }
        else -> searchedTasks
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "下载中心",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showDownloadSettings = true }) {
                Icon(Icons.Rounded.Tune, contentDescription = "下载设置")
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    tasks.size.toString() + " 个任务",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                activeCount to "进行中",
                pausedCount to "已暂停",
                completedCount to "已完成",
                failedCount to "异常"
            ).forEach { pair ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        pair.first.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        pair.second,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
        FolderPickerCard(folderLabel, hasFolder, onChooseFolder)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("全部", "进行中", "已暂停", "已完成", "异常").forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { statusFilter = filter },
                    label = { Text(filter, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        if (availableKinds.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = kindFilter == "全部",
                    onClick = { kindFilter = "全部" },
                    label = { Text("全部类型", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                availableKinds.forEach { kind ->
                    FilterChip(
                        selected = kindFilter == kind.name,
                        onClick = { kindFilter = kind.name },
                        label = { Text(kind.label, fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        if (tasks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(11.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isBlank()) {
                                    Text(
                                        "搜索文件名或错误信息",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "清除搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("最近", "名称", "进度").forEach { order ->
                    FilterChip(
                        selected = sortOrder == order,
                        onClick = { sortOrder = order },
                        label = { Text("按" + order, fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        when {
            tasks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "暂无下载任务",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "解析网页并下载资源后，会显示在这里",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onBackToSniff) {
                        Text("开始解析", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            displayedTasks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "没有匹配的下载任务" else "没有符合条件的任务",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        statusFilter = "全部"
                        kindFilter = "全部"
                        searchQuery = ""
                    }) {
                        Text("清除筛选", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(displayedTasks, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onOpen = { onOpenTask(task) },
                            onRetry = { onRetryTask(task) },
                            onPause = { onPauseTask(task) },
                            onCancel = { onCancelTask(task) }
                        )
                    }
                }
            }
        }
    }

    if (showDownloadSettings) {
        AlertDialog(
            onDismissRequest = { showDownloadSettings = false },
            title = { Text("下载性能") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("直链分段连接上限：$connectionsDraft", fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512).forEach { count ->
                            FilterChip(
                                selected = connectionsDraft == count,
                                onClick = { connectionsDraft = count },
                                label = { Text(count.toString()) }
                            )
                        }
                    }
                    Text("同时下载任务：$parallelDraft", fontWeight = FontWeight.Medium)
                    Slider(
                        value = parallelDraft.toFloat(),
                        onValueChange = { parallelDraft = it.toInt().coerceIn(1, 4) },
                        valueRange = 1f..4f,
                        steps = 2
                    )
                    Text("总速度上限", fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0, 1, 2, 5, 10, 20).forEach { limit ->
                            FilterChip(
                                selected = speedLimitDraft == limit,
                                onClick = { speedLimitDraft = limit },
                                label = { Text(if (limit == 0) "不限" else "${limit} MB/s") }
                            )
                        }
                    }
                    Text(
                        "直链支持 1–512 个分段连接；大文件约每 4 MiB 分一段，小文件自动减少。服务器不支持 Range 时会退回单连接。HLS/DASH 分片并发另行限制为最多 32。速度上限由所有下载任务共享。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateTuning(
                        DownloadTuning(
                            connectionsPerTask = connectionsDraft,
                            parallelDownloads = parallelDraft,
                            speedLimitMegabytesPerSecond = speedLimitDraft
                        )
                    )
                    showDownloadSettings = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadSettings = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskUi,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    val finished = task.state == "completed"
    val failed = task.state == "failed"
    val kindLabel = task.resourceKind
        ?.let { raw -> runCatching { ResourceKind.valueOf(raw).label }.getOrNull() }
        ?: "文件"
    val statusLabel = when {
        finished -> "已完成"
        failed -> "下载失败"
        task.state == "paused" -> "已暂停"
        task.state == "cancelled" -> "已取消"
        task.state == "downloading" -> "正在下载"
        else -> "等待中"
    }
    val statusColor = when {
        failed -> MaterialTheme.colorScheme.error
        finished -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val transferSummary = buildList {
        if (task.downloadedBytes >= 0L && task.totalBytes > 0L) {
            add(formatDownloadBytes(task.downloadedBytes) + " / " + formatDownloadBytes(task.totalBytes))
        } else if (task.downloadedBytes > 0L) {
            add("已下载 " + formatDownloadBytes(task.downloadedBytes) + " · 总大小未知")
        } else if (task.totalBytes > 0L) {
            add("文件大小 " + formatDownloadBytes(task.totalBytes))
        }
        if (task.state == "downloading" && task.totalBytes > task.downloadedBytes &&
            task.speedBytesPerSecond > 0L
        ) {
            val remaining = (task.totalBytes - task.downloadedBytes) / task.speedBytesPerSecond
            add("剩余 " + formatDownloadDuration(remaining))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    task.fileName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    kindLabel + " · " + statusLabel,
                    color = statusColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (task.state == "downloading" && task.speedBytesPerSecond > 0L) {
                Text(
                    formatDownloadBytes(task.speedBytesPerSecond) + "/s",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    if (finished) "100%" else if (task.state == "downloading" && task.progress >= 0) {
                        task.progress.coerceIn(0, 100).toString() + "%"
                    } else " ",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (transferSummary.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    transferSummary.joinToString(" · "),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.state == "downloading" && task.speedBytesPerSecond > 0L) {
                    Text(
                        if (task.progress >= 0) task.progress.coerceIn(0, 100).toString() + "%" else "下载中",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (task.progress < 0 && task.state == "downloading") {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            LinearProgressIndicator(
                progress = if (finished) 1f else task.progress.coerceIn(0, 100) / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp)
                    .height(3.dp),
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        if (failed && task.detail.isNotBlank()) {
            Text(
                task.detail,
                modifier = Modifier.padding(start = 52.dp, top = 7.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            when {
                finished && task.fileUri != null -> TextButton(onClick = onOpen) {
                    Text("打开文件", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                failed || task.state == "cancelled" || task.state == "paused" -> TextButton(onClick = onRetry) {
                    Text(
                        if (task.state == "paused" || task.fileUri != null) "继续下载" else "重新下载",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
                task.state == "downloading" ->
                    TextButton(onClick = onPause) {
                        Text("暂停", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                task.state == "downloading" || task.state == "queued" -> TextButton(onClick = onCancel) {
                    Text("取消", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
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

private fun formatDownloadBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val kb = value / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
    return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0)
}

private fun formatDownloadDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    return when {
        safeSeconds < 60L -> "$safeSeconds 秒"
        safeSeconds < 3600L -> "${safeSeconds / 60} 分"
        else -> "${safeSeconds / 3600} 小时 ${(safeSeconds % 3600) / 60} 分"
    }
}

private fun openLocalDocument(
    context: android.content.Context,
    uri: Uri,
    mimeType: String? = null
) {
    val resolvedMime = mimeType
        ?: context.contentResolver.getType(uri)
        ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolvedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.onFailure {
        android.widget.Toast.makeText(
            context,
            "没有找到可以打开此文件的应用",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun folderNameFromUri(uri: Uri): String {
    return runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        documentId.substringAfter(':', documentId).substringAfterLast('/')
            .ifBlank { "已选择文件夹" }
    }.getOrDefault("已选择文件夹")
}
