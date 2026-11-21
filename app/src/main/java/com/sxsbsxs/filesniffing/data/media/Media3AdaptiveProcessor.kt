package com.sxsbsxs.filesniffing.data.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.sxsbsxs.filesniffing.SniffedResource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Media3ExportResult(
    val documentUri: Uri,
    val fileName: String
)

@OptIn(UnstableApi::class)
@Singleton
class Media3AdaptiveProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun exportAdaptiveStream(
        resource: SniffedResource,
        destinationTree: Uri,
        onProgress: (Int, String) -> Unit
    ): Media3ExportResult = withContext(Dispatchers.IO) {
        require(resource.fileName.endsWith(".m3u8", true) ||
            resource.fileName.endsWith(".mpd", true)) {
            "Media3 导出只处理 HLS 或 DASH 自适应媒体清单"
        }

        val headers = buildMap {
            put("User-Agent", android.webkit.WebSettings.getDefaultUserAgent(context))
            put("Accept", "*/*")
            if (resource.sourcePage.isNotBlank()) put("Referer", resource.sourcePage)
        }
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(headers)
        val sourceMediaItem = MediaItem.fromUri(resource.url)

        onProgress(-1, "Media3 正在分析播放轨道")
        val downloadRequest = prepareDownloadRequest(sourceMediaItem, dataSourceFactory)
        val selectedMediaItem = downloadRequest.toMediaItem()

        val tempFile = File(context.cacheDir, "media3-${UUID.randomUUID()}.mp4")
        try {
            onProgress(-1, "已选择自适应音视频轨道，正在转封装")
            runTransformer(selectedMediaItem, dataSourceFactory, tempFile, onProgress)

            require(tempFile.exists() && tempFile.length() > 0L) {
                "Media3 导出结束，但没有生成有效文件"
            }
            val outputName = buildOutputName(resource.fileName)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                destinationTree,
                DocumentsContract.getTreeDocumentId(destinationTree)
            )
            val destination = DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                "video/mp4",
                outputName
            ) ?: error("无法在所选文件夹创建 MP4 文件")

            try {
                FileInputStream(tempFile).use { source ->
                    val output = context.contentResolver.openOutputStream(destination, "w")
                        ?: error("无法写入 MP4 文件")
                    output.use { sink ->
                        val buffer = ByteArray(128 * 1024)
                        var copied = 0L
                        val total = tempFile.length()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            sink.write(buffer, 0, count)
                            copied += count
                            val progress = if (total > 0L) {
                                (90 + (copied * 10.0 / total).toInt()).coerceIn(90, 99)
                            } else {
                                -1
                            }
                            onProgress(progress, "正在保存 MP4：$copied / $total 字节")
                        }
                        sink.flush()
                    }
                }
            } catch (error: Exception) {
                runCatching { context.contentResolver.delete(destination, null, null) }
                throw error
            }
            onProgress(100, "Media3 已导出 MP4")
            Media3ExportResult(destination, outputName)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private suspend fun prepareDownloadRequest(
        mediaItem: MediaItem,
        dataSourceFactory: OkHttpDataSource.Factory
    ) = suspendCancellableCoroutine { continuation ->
        val trackSelection = DefaultTrackSelector.Parameters.Builder(context)
            .setForceHighestSupportedBitrate(true)
            .build()
        val helper = DownloadHelper.Factory()
            .setDataSourceFactory(dataSourceFactory)
            .setTrackSelectionParameters(trackSelection)
            .create(mediaItem)

        continuation.invokeOnCancellation {
            runCatching { helper.release() }
        }

        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                if (!continuation.isActive) {
                    helper.release()
                    return
                }
                runCatching {
                    helper.getDownloadRequest(null)
                }.onSuccess { request ->
                    helper.release()
                    continuation.resume(request)
                }.onFailure { error ->
                    helper.release()
                    continuation.resumeWithException(error)
                }
            }

            override fun onPrepareError(helper: DownloadHelper, error: IOException) {
                helper.release()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        })
    }

    private suspend fun runTransformer(
        mediaItem: MediaItem,
        dataSourceFactory: OkHttpDataSource.Factory,
        outputFile: File,
        onProgress: (Int, String) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
            val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
                context,
                DefaultDecoderFactory.Builder(context).build(),
                Clock.DEFAULT,
                mediaSourceFactory
            )
            lateinit var transformer: Transformer
            val progressHolder = ProgressHolder()
            val poller = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(
                            (progressHolder.progress * 0.9f).toInt().coerceIn(0, 89),
                            "Media3 正在转封装：${progressHolder.progress}%"
                        )
                    }
                    mainHandler.postDelayed(this, 500L)
                }
            }

            transformer = Transformer.Builder(context)
                .setAssetLoaderFactory(assetLoaderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: androidx.media3.transformer.ExportResult
                    ) {
                        mainHandler.removeCallbacks(poller)
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException: androidx.media3.transformer.ExportException
                    ) {
                        mainHandler.removeCallbacks(poller)
                        if (continuation.isActive) {
                            continuation.resumeWithException(exportException)
                        }
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                mainHandler.post {
                    mainHandler.removeCallbacks(poller)
                    transformer.cancel()
                }
            }

            runCatching {
                transformer.start(mediaItem, outputFile.absolutePath)
                mainHandler.post(poller)
            }.onFailure { error ->
                mainHandler.removeCallbacks(poller)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    private fun buildOutputName(original: String): String {
        val base = original.substringBeforeLast('.', original)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
            .ifBlank { "media_video" }
        return "$base.mp4"
    }

}
