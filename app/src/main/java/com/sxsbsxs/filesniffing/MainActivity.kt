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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
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
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sxsbsxs.filesniffing.presentation.downloads.DownloadCenterViewModel
import com.sxsbsxs.filesniffing.presentation.sniffing.SniffingViewModel

private val AppBackground = Color(0xFFF6F7F9)
private val Ink = Color(0xFF17191F)
private val SecondaryText = Color(0xFF777D88)
private val Hairline = Color(0xFFE9EBEF)
private val Accent = Color(0xFF4B66E8)
private val SoftAccent = Color(0xFFEEF1FF)

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
private fun FileSniffingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            background = AppBackground,
            surface = Color.White,
            onSurface = Ink,
            secondary = Accent
        ),
        content = content
    )
}

@Composable
private fun FileSniffingApp(sharedLink: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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
    val visibleDownloadTasks = (
        downloadTasks.toList() + storedDownloadHistory.map { record ->
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
        containerColor = AppBackground,
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
                        val candidate = Uri.parse(pageUrl.trim())
                        val valid = (candidate.scheme == "http" || candidate.scheme == "https") &&
                            !candidate.host.isNullOrBlank()
                        when {
                            pageUrl.isBlank() -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("先粘贴一个网页链接")
                            }
                            !valid -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("请输入有效的 http 或 https 链接")
                            }
                            else -> {
                                sniffingViewModel.beginInspection(pageUrl.trim())
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
                        DownloadEngine.retry(context.applicationContext, task.id) { updated ->
                            val index = downloadTasks.indexOfFirst { it.id == updated.id }
                            if (index < 0) downloadTasks.add(0, updated)
                            else downloadTasks[index] = updated
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
        containerColor = Color.White,
        contentColor = Ink,
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
                selectedIconColor = Accent,
                selectedTextColor = Accent,
                unselectedIconColor = SecondaryText,
                unselectedTextColor = SecondaryText,
                indicatorColor = SoftAccent
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
                selectedIconColor = Accent,
                selectedTextColor = Accent,
                unselectedIconColor = SecondaryText,
                unselectedTextColor = SecondaryText,
                indicatorColor = SoftAccent
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
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        HeaderBrand()
        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = "找到网页里的\n媒体资源。",
            color = Ink,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "粘贴链接，识别页面中可访问的视频、音频与图片。",
            color = SecondaryText,
            fontSize = 15.sp,
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(30.dp))
        UrlInputCard(
            value = pageUrl,
            onValueChange = onUrlChange,
            onPaste = onPaste,
            onClear = onClear
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartSniffing,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 1.dp
            )
        ) {
            Text(
                text = "开始嗅探",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "保存位置",
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        FolderPickerCard(
            folderLabel = folderLabel,
            hasFolder = hasFolder,
            onClick = onPickFolder
        )

        Spacer(modifier = Modifier.height(26.dp))
        SupportedTypesRow()
        Spacer(modifier = Modifier.height(22.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF8B929D),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "本地优先 · 不需要注册账号",
                color = Color(0xFF8B929D),
                fontSize = 12.sp
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
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF526DF0), Color(0xFF344BC4))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CenterFocusStrong,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "FILE SNIFFING",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                text = "资源发现工具",
                color = SecondaryText,
                fontSize = 11.sp
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
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Text(
                text = "网页链接",
                color = SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(13.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = Color(0xFF9AA0AA),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(11.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        cursorBrush = Brush.solidColor(Accent),
                        decorationBox = { innerTextField ->
                            if (value.isEmpty()) {
                                Text(
                                    text = "https://example.com/video",
                                    color = Color(0xFFB3B8C1),
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
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清空链接",
                            tint = SecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onPaste,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = "粘贴链接",
                            tint = Accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SoftAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasFolder) "已选择文件夹" else "选择保存文件夹",
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = folderLabel,
                    color = SecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "更改",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SupportedTypesRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ResourceTypePill(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.VideoLibrary,
            label = "视频"
        )
        ResourceTypePill(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.MusicNote,
            label = "音频"
        )
        ResourceTypePill(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Image,
            label = "图片"
        )
    }
}

@Composable
private fun ResourceTypePill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF0F1F4),
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color(0xFF5F6570),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 24.dp, bottom = 30.dp)
    ) {
        Text(
            text = "下载",
            color = Ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.7).sp
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "查看和管理保存到手机的文件",
            color = SecondaryText,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(26.dp))
        FolderPickerCard(
            folderLabel = folderLabel,
            hasFolder = hasFolder,
            onClick = onChooseFolder
        )

        Spacer(modifier = Modifier.height(24.dp))
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .padding(horizontal = 26.dp, vertical = 38.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F2F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color(0xFF9AA1AC),
                            modifier = Modifier.size(33.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "还没有下载任务",
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "嗅探到资源并开始下载后，任务进度会显示在这里。",
                        color = SecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    TextButton(onClick = onBackToSniff) {
                        Text("去嗅探资源", color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "下载任务",
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${tasks.size} 项",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            tasks.forEach { task ->
                DownloadTaskCard(
                    task,
                    onOpen = { onOpenTask(task) },
                    onRetry = { onRetryTask(task) },
                    onCancel = { onCancelTask(task) }
                )
                Spacer(modifier = Modifier.height(10.dp))
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(SoftAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            finished -> "已完成"
                            failed -> "下载失败"
                            task.state == "cancelled" -> "已取消"
                            task.state == "downloading" -> "正在下载"
                            else -> "等待下载"
                        },
                        color = when {
                            finished -> Color(0xFF299B68)
                            failed -> Color(0xFFD24B4B)
                            task.state == "cancelled" -> SecondaryText
                            else -> SecondaryText
                        },
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = if (finished) "100%" else if (task.state == "downloading" && task.progress >= 0) "${task.progress}%" else "—",
                    color = Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (task.progress < 0 && task.state == "downloading") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Accent,
                    trackColor = Color(0xFFEDEFF3)
                )
            } else {
                LinearProgressIndicator(
                    progress = task.progress.coerceAtLeast(0) / 100f,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = if (failed) Color(0xFFD24B4B) else Accent,
                    trackColor = Color(0xFFEDEFF3)
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = task.detail,
                color = SecondaryText,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            when {
                finished && task.fileUri != null -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onOpen) {
                        Text("打开文件", color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                failed || task.state == "cancelled" -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onRetry) {
                        Text(
                            text = if (task.fileUri != null) "继续断点下载" else "重新下载",
                            color = Accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                task.state == "downloading" || task.state == "queued" -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onCancel) {
                        Text("取消任务", color = Color(0xFFD24B4B), fontWeight = FontWeight.SemiBold)
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
