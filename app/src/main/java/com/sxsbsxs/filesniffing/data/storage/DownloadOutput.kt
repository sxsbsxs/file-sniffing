package com.sxsbsxs.filesniffing.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.sxsbsxs.filesniffing.FileNameSanitizer

/** Uses the public Download collection by default; user-selected SAF trees remain optional. */
object DownloadOutput {
    const val PUBLIC_DOWNLOADS_CACHE_KEY = "public-downloads"

    fun create(context: Context, destinationTree: Uri?, displayName: String, mimeType: String): Uri {
        val safeName = FileNameSanitizer.sanitize(displayName, "download")
        if (destinationTree != null) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                destinationTree,
                DocumentsContract.getTreeDocumentId(destinationTree)
            )
            return DocumentsContract.createDocument(
                context.contentResolver, parent, mimeType, safeName
            ) ?: error("无法在所选文件夹创建文件")
        }

        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Android 9 及以下系统请先选择一个保存文件夹"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法在 Download 文件夹创建文件，请检查存储空间")
    }

    fun rename(context: Context, documentUri: Uri, displayName: String): Uri? {
        val safeName = FileNameSanitizer.sanitize(displayName, "download")
        return if (isMediaStoreDownload(documentUri)) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            }
            documentUri.takeIf {
                context.contentResolver.update(documentUri, values, null, null) > 0
            }
        } else {
            runCatching {
                DocumentsContract.renameDocument(context.contentResolver, documentUri, safeName)
            }.getOrNull()
        }
    }

    fun markComplete(context: Context, documentUri: Uri) {
        if (!isMediaStoreDownload(documentUri) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        val updated = resolver.update(documentUri, values, null, null)
        if (updated == 0) {
            val stillPending = resolver.query(
                documentUri,
                arrayOf(MediaStore.MediaColumns.IS_PENDING),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) == 1 else null
            }
            check(stillPending == false) {
                "文件已下载，但无法发布到 Download 文件夹；请检查存储权限或空间"
            }
        }
    }

    fun delete(context: Context, documentUri: Uri) {
        runCatching { context.contentResolver.delete(documentUri, null, null) }
    }

    private fun isMediaStoreDownload(uri: Uri): Boolean =
        uri.scheme == "content" && uri.authority == "media" &&
            uri.pathSegments.contains("downloads")
}
