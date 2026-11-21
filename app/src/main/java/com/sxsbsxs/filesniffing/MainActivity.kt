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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
        val prefixes = listOf("https://", "http://")
        val match = prefixes.mapNotNull { prefix ->
            val index = sharedText.indexOf(prefix, ignoreCase = true)
            if (index >= 0) sharedText.substring(index).takeWhile { !it.isWhitespace() } else null
        }.firstOrNull()?.trimEnd(')', ']', '}', ',', '.', '!', '?', ';')
        return match ?: sharedText
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
        if (task.fileUri != null) task
        else task.copy(fileUri = storedTasksById[task.id]?.destinationUri)
    }
    val visibleDownloadTasks = (
        liveTasksWithStoredUri + storedDownloadHistory.map { record ->
            DownloadTaskUi(
                id = record.id,
                fileName = record.fileName,
                progress = record.progress,
                state = record.state,
                detail = record.detail,
                fileUri = record.destinationUri
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
        ?: "选择手机文件夹"

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

    val enqueueDownload: (SniffedResource, Boolean) -> Unit = { resource, showQueuedMessage ->
        if (
            Build.VERSION.SDK_INT >= 33 &&
            !askedForNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val folder = selectedFolderUri
        if (folder == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("请先选择保存文件夹") }
        } else {
            DownloadEngine.start(
                context = context.applicationContext,
                destinationTree = Uri.parse(folder),
                resource = resource
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
                coroutineScope.launch { snackbarHostState.showSnackbar("已加入下载队列") }
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
            if (selectedTab == 1 || sniffingState.activePageUrl == null) {
                MainNavigationBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
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
                    onDownload = { resource -> enqueueDownload(resource, true) },
                    onDownloadBatch = { selected ->
                        if (selectedFolderUri == null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("请先选择保存文件夹") }
                        } else {
                            selected.forEach { resource -> enqueueDownload(resource, false) }
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("已将 ${selected.size} 项加入下载队列")
                            }
                        }
                    }
                )
            } else if (selectedTab == 0) {
                HomeScreen(
                    pageUrl = pageUrl,
                    onUrlChange = { pageUrl = it },
                    folderLabel = folderLabel,
                    hasFolder = selectedFolderUri != null,
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
                        val candidate = runCatching { Uri.parse(trimmedUrl) }.getOrNull()
                        val valid = candidate != null &&
                            (candidate.scheme.equals("http", ignoreCase = true) ||
                                candidate.scheme.equals("https", ignoreCase = true)) &&
                            !candidate.host.isNullOrBlank()
                        when {
                            trimmedUrl.isBlank() -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("先粘贴一个网页链接")
                            }
                            trimmedUrl.length > 4096 -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("链接过长，请检查后重试")
                            }
                            !valid -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("请输入有效的 http 或 https 链接")
                            }
                            else -> runCatching {
                                sniffingViewModel.beginInspection(trimmedUrl)
                            }.onFailure {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("无法开始解析，请重试")
                                }
                            }
                        }
                    }
                )
            } else {
                DownloadsScreen(
                    folderLabel = folderLabel,
                    hasFolder = selectedFolderUri != null,
                    tasks = visibleDownloadTasks,
                    onChooseFolder = { folderPicker.launch(null) },
                    onBackToSniff = { selectedTab = 0 },
                    onRetryTask = { task ->
                        downloadCenterViewModel.loadRetryRecord(task.id) { record ->
                            if (record == null || record.sourceUrl.isBlank() ||
                                record.destinationTreeUri.isNullOrBlank()
                            ) {
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
                    onCancelTask = { task ->
                        DownloadEngine.cancel(context.applicationContext, task.id)
                    },
                    onOpenTask = { task ->
                        val uri = task.fileUri?.let(Uri::parse)
                        if (uri != null) {
                            val mime = context.contentResolver.getType(uri) ?: "*/*"
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(viewIntent, "打开文件"))
                            }.onFailure {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("没有找到可以打开此文件的应用")
                                }
                            }
                        }
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
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "嗅探",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text("嗅探", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelect(1) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "下载",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text("下载", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 22.dp)
    ) {
        HeaderBrand()
        Spacer(Modifier.height(34.dp))
        Text(
            text = "网页媒体解析",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6).sp
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "粘贴网页链接，自动发现可用的视频、音频和图片资源",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(24.dp))
        UrlInputCard(
            value = pageUrl,
            onValueChange = onUrlChange,
            onPaste = onPaste,
            onClear = onClear
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStartSniffing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 1.dp
            )
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text("开始解析", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(25.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "下载保存位置",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    folderLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onPickFolder) {
                Text(
                    if (hasFolder) "更改" else "选择",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(13.dp)
        ) {
            Text(
                "支持普通网页媒体、HLS 和 DASH 资源。不同网站的访问权限和媒体保护方式可能影响可识别内容。",
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun HeaderBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.CenterFocusStrong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "FILE SNIFFING",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.15.sp
            )
            Text(
                "MEDIA UTILITY",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.1.sp
            )
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)
        ) {
            Text(
                "网页链接",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (value.isEmpty()) {
                                Text(
                                    "粘贴或输入网页地址",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(
                    onClick = if (value.isEmpty()) onPaste else onClear,
                    modifier = Modifier.size(40.dp)
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
}

@Composable
private fun FolderPickerCard(folderLabel: String, hasFolder: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(11.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    if (hasFolder) "保存到" else "选择保存位置",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    folderLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
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
    onCancelTask: (DownloadTaskUi) -> Unit,
    onOpenTask: (DownloadTaskUi) -> Unit
) {
    val activeCount = tasks.count { it.state == "downloading" || it.state == "queued" }
    val completedCount = tasks.count { it.state == "completed" }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "下载",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.6).sp
                )
                Text(
                    "管理正在进行和已保存的文件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(13.dp)
            ) {
                Text(
                    "${tasks.size} 项",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(21.dp))
        FolderPickerCard(folderLabel, hasFolder, onChooseFolder)
        Spacer(Modifier.height(22.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp).size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(17.dp))
                    Text(
                        "暂无下载任务",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "嗅探并下载资源后，任务会显示在这里",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(17.dp))
                    TextButton(onClick = onBackToSniff) {
                        Text(
                            "去嗅探资源",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "下载任务",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "进行中 $activeCount · 已完成 $completedCount",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onOpen = { onOpenTask(task) },
                        onRetry = { onRetryTask(task) },
                        onCancel = { onCancelTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskUi,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val finished = task.state == "completed"
    val failed = task.state == "failed"
    val statusLabel = when {
        finished -> "已完成"
        failed -> "下载失败"
        task.state == "cancelled" -> "已取消"
        task.state == "downloading" -> "正在下载"
        else -> "等待下载"
    }
    val statusContainer = when {
        failed -> MaterialTheme.colorScheme.errorContainer
        finished -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusContent = when {
        failed -> MaterialTheme.colorScheme.onErrorContainer
        finished -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = task.fileName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(color = statusContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = statusContent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (finished) {
                        "100%"
                    } else if (task.state == "downloading" && task.progress >= 0) {
                        "${task.progress.coerceIn(0, 100)}%"
                    } else {
                        "—"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(15.dp))
            if (task.progress < 0 && task.state == "downloading") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    progress = task.progress.coerceIn(0, 100) / 100f,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = task.detail,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    finished && task.fileUri != null -> {
                        TextButton(onClick = onOpen) {
                            Text(
                                "打开文件",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    failed || task.state == "cancelled" -> {
                        TextButton(onClick = onRetry) {
                            Text(
                                if (task.fileUri != null) "继续下载" else "重新下载",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    task.state == "downloading" || task.state == "queued" -> {
                        TextButton(onClick = onCancel) {
                            Text(
                                "取消任务",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun folderNameFromUri(uri: Uri): String {
    return runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        documentId.substringAfter(':', documentId).substringAfterLast('/')
            .ifBlank { "已选择文件夹" }
    }.getOrDefault("已选择文件夹")
}
