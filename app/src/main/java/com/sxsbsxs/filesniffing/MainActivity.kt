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
import com.sxsbsxs.filesniffing.ui.theme.FileSniffingTheme
import com.sxsbsxs.filesniffing.presentation.downloads.DownloadCenterViewModel
import com.sxsbsxs.filesniffing.presentation.sniffing.SniffingViewModel
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
            Intent.ACTION_VIEW -> source.dataString.orEmpty()
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
    LaunchedEffect(sharedLink) {
        if (sharedLink.isNotBlank()) {
            pageUrl = sharedLink
            sniffingViewModel.closeInspection()
            selectedTab = 0
        }
    }
    val downloadTasks = remember { mutableStateListOf<DownloadTaskUi>() }
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
            downloadCenterViewModel.persistTask(task)
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
            coroutineScope.launch {
                snackbarHostState.showSnackbar("保存位置已更新")
            }
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
                        "已在本机保留诊断报告。复制后可以发给开发者定位闪退原因。",
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
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("诊断报告已复制")
                        }
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

    val enqueueDownload: (SniffedResource, Boolean, Int?) -> Unit =
        { resource, showQueuedMessage, adaptiveVideoHeight ->
        if (
            Build.VERSION.SDK_INT >= 33 &&
            !askedForNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val folder = selectedFolderUri
        if (folder == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Android 9 及以下请先选择保存文件夹") }
        } else {
            DownloadEngine.start(
                context = context.applicationContext,
                destinationTree = folder?.let(Uri::parse),
                resource = resource,
                adaptiveVideoHeight = adaptiveVideoHeight
            ) { task ->
                val index = downloadTasks.indexOfFirst { it.id == task.id }
                if (index < 0) downloadTasks.add(0, task)
                else downloadTasks[index] = task
                downloadCenterViewModel.persistTask(task, resource, folder)
                if (task.state == "failed") {
                    coroutineScope.launch { snackbarHostState.showSnackbar(task.detail) }
                }
            }
            if (showQueuedMessage) {
                coroutineScope.launch { snackbarHostState.showSnackbar("已加入下载队列 · Download") }
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
            if (selectedTab == 0 && sniffingState.activePageUrl != null) {
                SniffingResultScreen(
                    pageUrl = sniffingState.activePageUrl.orEmpty(),
                    resources = sniffingState.resources,
                    isLoading = sniffingState.isPageLoading,
                    inspectionError = sniffingState.errorMessage,
                    onLoadingChange = sniffingViewModel::setPageLoading,
                    onResourceFound = sniffingViewModel::addResource,
                    onBack = { sniffingViewModel.closeInspection() },
                    onDownload = { resource, height -> enqueueDownload(resource, true, height) },
                    onInspectAdaptiveQualities = { resource, callback ->
                        sniffingViewModel.inspectAdaptiveQualities(resource, callback)
                    },
                    onDownloadBatch = { selected ->
                        selected.forEach { resource -> enqueueDownload(resource, false, null) }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已将 ${selected.size} 项加入下载队列")
                        }
                    },
                    onNiconicoPageReady = { targetUrl ->
                        sniffingViewModel.extractNiconicoResource(targetUrl) { resource, error ->
                            if (resource != null) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("N站视频流已解析，可选择画质并下载")
                                }
                            } else if (!error.isNullOrBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("N站解析失败：$error")
                                }
                            }
                        }
                    }
                )
            } else if (selectedTab == 0) {
                HomeScreen(
                    pageUrl = pageUrl,
                    onUrlChange = { pageUrl = it },
                    folderLabel = folderLabel,
                    hasFolder = hasOutputFolder,
                    onPaste = {
                        val pasted = clipboard.getText()?.text.orEmpty().trim()
                        if (pasted.isNotEmpty()) pageUrl = pasted
                        else coroutineScope.launch {
                            snackbarHostState.showSnackbar("剪贴板里没有可粘贴的文字")
                        }
                    },
                    onClear = { pageUrl = "" },
                    onPickFolder = { folderPicker.launch(null) },
                    onStartSniffing = {
                        val trimmedUrl = pageUrl.trim()
                        val normalizedUrl = SharedLinkNormalizer.normalize(trimmedUrl)
                        when {
                            trimmedUrl.isBlank() -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("先粘贴一个网页链接")
                            }
                            trimmedUrl.length > 4096 -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("链接过长，请检查后重试")
                            }
                            normalizedUrl.isNullOrBlank() -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("请输入有效网页链接，或粘贴 B 站分享链接")
                            }
                            else -> runCatching {
                                pageUrl = normalizedUrl
                                sniffingViewModel.beginInspection(normalizedUrl)
                            }.onFailure {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("无法开始解析，请重试")
                                }
                            }
                        }
                    }
                )
            } else if (selectedTab == 1) {
                DownloadsScreen(
                    folderLabel = folderLabel,
                    hasFolder = selectedFolderUri != null,
                    tasks = visibleDownloadTasks,
                    tuning = downloadTuning,
                    onUpdateTuning = downloadCenterViewModel::updateDownloadTuning,
                    onChooseFolder = { folderPicker.launch(null) },
                    onBackToSniff = { selectedTab = 0 },
                    onRetryTask = { task ->
                        downloadCenterViewModel.loadRetryRecord(task.id) { record ->
                            if (record == null || record.sourceUrl.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("任务缺少原始参数，请重新嗅探后下载")
                                }
                            } else {
                                DownloadEngine.retry(
                                    context.applicationContext,
                                    record,
                                    partialUri = task.fileUri
                                ) { updated ->
                                    val index = downloadTasks.indexOfFirst { it.id == updated.id }
                                    if (index < 0) downloadTasks.add(0, updated)
                                    else downloadTasks[index] = updated
                                }
                            }
                        }
                    },
                    onPauseTask = { task ->
                        DownloadEngine.pause(context.applicationContext, task.id)
                    },
                    onCancelTask = { task ->
                        DownloadEngine.cancel(context.applicationContext, task.id)
                    },
                    onOpenTask = { task ->
                        task.fileUri?.let(Uri::parse)?.let { openLocalDocument(context, it) }
                    }
                )
            } else {
                LocalFilesScreen(
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
            icon = { Icon(Icons.Rounded.Search, contentDescription = "嗅探", modifier = Modifier.size(21.dp)) },
            label = { Text("嗅探", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
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
    folderLabel: String,
    hasFolder: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onPickFolder: () -> Unit,
    onStartSniffing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "嗅探",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasFolder) folderLabel else "未设置保存位置",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(25.dp))
        UrlInputCard(
            value = pageUrl,
            onValueChange = onUrlChange,
            onPaste = onPaste,
            onClear = onClear
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onStartSniffing,
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
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("开始嗅探", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        FolderPickerCard(
            folderLabel = folderLabel,
            hasFolder = hasFolder,
            onClick = onPickFolder
        )
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
                                "粘贴或输入网页地址",
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
                        Text("开始嗅探", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            filteredTasks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "没有符合条件的任务",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        statusFilter = "全部"
                        kindFilter = "全部"
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
                    items(filteredTasks, key = { it.id }) { task ->
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
                    Text("单任务连接数：$connectionsDraft", fontWeight = FontWeight.Medium)
                    Slider(
                        value = connectionsDraft.toFloat(),
                        onValueChange = { connectionsDraft = it.toInt().coerceIn(1, 16) },
                        valueRange = 1f..16f,
                        steps = 14
                    )
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
                        "连接数只是上限，小文件会自动减少；服务器不支持分段时会退回单连接。速度上限由所有下载任务共享。",
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
