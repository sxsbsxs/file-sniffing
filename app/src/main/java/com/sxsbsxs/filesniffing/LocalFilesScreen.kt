package com.sxsbsxs.filesniffing

import android.net.Uri
import android.content.ContentUris
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LocalDocumentEntry(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val contentUri: Uri? = null
)

private data class LocalDirectoryCrumb(
    val documentId: String,
    val name: String
)

@Composable
fun LocalFilesScreen(
    treeUri: Uri?,
    folderLabel: String,
    onChooseFolder: () -> Unit,
    onBackToParse: () -> Unit,
    onOpenFile: (Uri, String?) -> Unit
) {
    val context = LocalContext.current
    val rootDocumentId = remember(treeUri) {
        treeUri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }.orEmpty()
    }
    var currentDocumentId by remember(treeUri) { mutableStateOf(rootDocumentId) }
    val directoryStack = remember(treeUri) { mutableStateListOf<LocalDirectoryCrumb>() }
    var documents by remember { mutableStateOf<List<LocalDocumentEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("全部") }
    var refreshKey by remember { mutableIntStateOf(0) }

    BackHandler(enabled = directoryStack.isNotEmpty()) {
        directoryStack.removeAt(directoryStack.lastIndex)
        currentDocumentId = directoryStack.lastOrNull()?.documentId ?: rootDocumentId
        searchText = ""
    }

    LaunchedEffect(treeUri, currentDocumentId, refreshKey) {
        val sourceTree = treeUri
        if (sourceTree != null && currentDocumentId.isBlank()) {
            documents = emptyList()
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (sourceTree == null) {
                    check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        "Android 9 及以下请先选择一个保存文件夹"
                    }
                    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.MIME_TYPE,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.DATE_MODIFIED
                    )
                    val cursor = context.contentResolver.query(
                        collection,
                        projection,
                        "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                        arrayOf("${Environment.DIRECTORY_DOWNLOADS}/"),
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    ) ?: error("系统没有返回 Download 文件列表")
                    val rows = mutableListOf<LocalDocumentEntry>()
                    cursor.use {
                        val idColumn = it.getColumnIndex(MediaStore.MediaColumns._ID)
                        val nameColumn = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val mimeColumn = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                        val sizeColumn = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        val dateColumn = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        require(idColumn >= 0 && nameColumn >= 0 && mimeColumn >= 0) {
                            "MediaStore 未返回必要的文件信息"
                        }
                        while (it.moveToNext()) {
                            val id = it.getLong(idColumn)
                            rows += LocalDocumentEntry(
                                documentId = id.toString(),
                                name = it.getString(nameColumn).orEmpty().ifBlank { "未命名文件" },
                                mimeType = it.getString(mimeColumn).orEmpty(),
                                size = if (sizeColumn >= 0 && !it.isNull(sizeColumn)) it.getLong(sizeColumn) else -1L,
                                lastModified = if (dateColumn >= 0 && !it.isNull(dateColumn)) it.getLong(dateColumn) * 1000L else 0L,
                                isDirectory = false,
                                contentUri = ContentUris.withAppendedId(collection, id)
                            )
                        }
                    }
                    rows
                } else {
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceTree, currentDocumentId)
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )
                    val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                        ?: error("系统没有返回文件列表")
                    val rows = mutableListOf<LocalDocumentEntry>()
                    cursor.use {
                        val idColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        val dateColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        require(idColumn >= 0 && nameColumn >= 0 && mimeColumn >= 0) {
                            "文件提供程序未提供必要的文件信息"
                        }
                        while (it.moveToNext()) {
                            val mime = it.getString(mimeColumn).orEmpty()
                            rows += LocalDocumentEntry(
                                documentId = it.getString(idColumn),
                                name = it.getString(nameColumn).orEmpty().ifBlank { "未命名文件" },
                                mimeType = mime,
                                size = if (sizeColumn >= 0 && !it.isNull(sizeColumn)) it.getLong(sizeColumn) else -1L,
                                lastModified = if (dateColumn >= 0 && !it.isNull(dateColumn)) it.getLong(dateColumn) else 0L,
                                isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                            )
                        }
                    }
                    rows.sortedWith(compareBy<LocalDocumentEntry>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                }
            }
        }
        result.onSuccess { documents = it }.onFailure {
            errorMessage = it.message?.take(180) ?: "无法读取此文件夹"
        }
        isLoading = false
    }

    val visibleDocuments = documents.filter { item ->
        val matchesSearch = searchText.isBlank() || item.name.contains(searchText, ignoreCase = true)
        val matchesFilter = when (activeFilter) {
            "文件夹" -> item.isDirectory
            "视频" -> !item.isDirectory && item.mimeType.startsWith("video/")
            "音频" -> !item.isDirectory && item.mimeType.startsWith("audio/")
            "图片" -> !item.isDirectory && item.mimeType.startsWith("image/")
            "文档" -> !item.isDirectory && (
                item.mimeType.startsWith("text/") ||
                    item.mimeType.contains("pdf") ||
                    item.mimeType.contains("document") ||
                    item.mimeType.contains("spreadsheet") ||
                    item.mimeType.contains("presentation")
                )
            else -> true
        }
        matchesSearch && matchesFilter
    }
    val currentTitle = directoryStack.lastOrNull()?.name ?: folderLabel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (directoryStack.isNotEmpty()) {
                IconButton(
                    onClick = {
                        directoryStack.removeAt(directoryStack.lastIndex)
                        currentDocumentId = directoryStack.lastOrNull()?.documentId ?: rootDocumentId
                        searchText = ""
                    }
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回上一级")
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "文件",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    currentTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { refreshKey++ }, enabled = !isLoading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新文件列表")
            }
        }

        if (treeUri == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Spacer(Modifier.height(18.dp))
            Text(
                "还没有选择下载目录",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "选择一个文件夹后，可以在这里查看和打开已下载的文件。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onChooseFolder) {
                Text("选择文件夹", color = MaterialTheme.colorScheme.primary)
            }
        } else if (errorMessage != null) {
            Spacer(Modifier.height(18.dp))
            Text("无法读取文件夹", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            TextButton(onClick = onChooseFolder) { Text("重新选择目录") }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchText.isBlank()) {
                                    Text("搜索当前文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("全部", "文件夹", "视频", "音频", "图片", "文档").forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                documents.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("当前没有下载文件", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "解析失败不会生成下载文件。成功下载后，文件会显示在这里。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBackToParse) { Text("返回解析") }
                    }
                }
                visibleDocuments.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("没有匹配的文件", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        items(visibleDocuments, key = { it.contentUri?.toString() ?: it.documentId }) { item ->
                            LocalDocumentRow(item) {
                                if (item.isDirectory) {
                                    directoryStack.add(LocalDirectoryCrumb(item.documentId, item.name))
                                    currentDocumentId = item.documentId
                                    searchText = ""
                                    activeFilter = "全部"
                                } else {
                                    val documentUri = item.contentUri ?: DocumentsContract.buildDocumentUriUsingTree(
                                        requireNotNull(treeUri),
                                        item.documentId
                                    )
                                    onOpenFile(documentUri, item.mimeType.takeIf { it.isNotBlank() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalDocumentRow(item: LocalDocumentEntry, onClick: () -> Unit) {
    val icon = when {
        item.isDirectory -> Icons.Rounded.FolderOpen
        item.mimeType.startsWith("video/") -> Icons.Rounded.VideoLibrary
        item.mimeType.startsWith("audio/") -> Icons.Rounded.MusicNote
        item.mimeType.startsWith("image/") -> Icons.Rounded.Image
        item.mimeType.startsWith("text/") || item.mimeType.contains("pdf") -> Icons.Rounded.Description
        else -> Icons.Rounded.Download
    }
    val detail = if (item.isDirectory) {
        "文件夹"
    } else {
        buildList {
            if (item.size >= 0L) add(formatLocalBytes(item.size))
            add(item.mimeType.ifBlank { "未知类型" })
            if (item.lastModified > 0L) {
                add(SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(item.lastModified)))
            }
        }.joinToString(" · ")
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

private fun formatLocalBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kilobytes = bytes / 1024.0
    if (kilobytes < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kilobytes)
    val megabytes = kilobytes / 1024.0
    if (megabytes < 1024.0) return String.format(Locale.ROOT, "%.1f MB", megabytes)
    return String.format(Locale.ROOT, "%.2f GB", megabytes / 1024.0)
}
