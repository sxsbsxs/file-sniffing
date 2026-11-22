package com.sxsbsxs.filesniffing

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.Executor
import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity

object DownloadEngine {
    const val DOWNLOAD_TAG = "file-sniffing-download"
    private const val PAUSE_REQUESTS_PREF = "download_pause_requests"
    private const val FILE_NAME_TAG_PREFIX = "file-name:"
    private const val RESOURCE_KIND_TAG_PREFIX = "resource-kind:"
    private const val MIME_TYPE_TAG_PREFIX = "mime-type:"
    private const val ADAPTIVE_HEIGHT_TAG_PREFIX = "adaptive-height:"

    fun start(
        context: Context,
        destinationTree: Uri?,
        resource: SniffedResource,
        adaptiveVideoHeight: Int? = null,
        taskId: UUID = UUID.randomUUID(),
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        // Niconico HLS URLs are short-lived. Queue only the stable watch URL;
        // PersistentDownloadWorker resolves a fresh manifest when the task actually starts.
        val niconicoWatchUrl = if (resource.kind == ResourceKind.PLAYLIST) {
            NiconicoUrlParser.extractVideoId(resource.sourcePage)
                ?.let(NiconicoUrlParser::canonicalWatchUrl)
        } else {
            null
        }
        val workerUrl = niconicoWatchUrl ?: resource.url

        val request = OneTimeWorkRequestBuilder<PersistentDownloadWorker>()
            .setId(taskId)
            .setInputData(
                androidx.work.workDataOf(
                    PersistentDownloadWorker.KEY_TASK_ID to taskId.toString(),
                    PersistentDownloadWorker.KEY_URL to workerUrl,
                    PersistentDownloadWorker.KEY_TREE to destinationTree?.toString(),
                    PersistentDownloadWorker.KEY_FILE_NAME to resource.fileName,
                    PersistentDownloadWorker.KEY_KIND to resource.kind.name,
                    PersistentDownloadWorker.KEY_MIME to resource.mimeType,
                    PersistentDownloadWorker.KEY_REFERER to resource.sourcePage,
                    PersistentDownloadWorker.KEY_ADAPTIVE_VIDEO_HEIGHT to (adaptiveVideoHeight ?: -1),
                    PersistentDownloadWorker.KEY_FORMAT_SELECTOR to resource.formatSelector
                )
            )
            .addTag(DOWNLOAD_TAG)
            .addTag(fileNameTag(resource.fileName))
            .addTag(resourceKindTag(resource.kind.name))
            .addTag(mimeTypeTag(resource.mimeType))
            .addTag(adaptiveHeightTag(adaptiveVideoHeight))
            .build()

        onUpdate(
            DownloadTaskUi(
                id = request.id.toString(),
                fileName = resource.fileName,
                progress = 0,
                state = "queued",
                detail = "已加入后台下载队列",
                resourceKind = resource.kind.name,
                mimeType = resource.mimeType,
                adaptiveVideoHeight = adaptiveVideoHeight
            )
        )

        val appContext = context.applicationContext
        val manager = WorkManager.getInstance(appContext)
        enqueueAndObserve(appContext, manager, request, onUpdate)
    }

    fun retry(
        context: Context,
        record: DownloadRecordEntity,
        partialUri: String? = null,
        taskId: UUID = UUID.randomUUID(),
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val destinationTree = record.destinationTreeUri
        if (record.sourceUrl.isBlank()) {
            onUpdate(
                DownloadTaskUi(
                    id = record.id,
                    fileName = record.fileName,
                    progress = record.progress,
                    state = "failed",
                    detail = "缺少原始下载参数，请重新嗅探后下载"
                )
            )
            return
        }

        val retryData = Data.Builder()
            .putString(PersistentDownloadWorker.KEY_TASK_ID, taskId.toString())
            .putString(PersistentDownloadWorker.KEY_URL, record.sourceUrl)
            .putString(PersistentDownloadWorker.KEY_TREE, destinationTree)
            .putString(PersistentDownloadWorker.KEY_FILE_NAME, record.fileName)
            .putString(PersistentDownloadWorker.KEY_KIND, record.resourceKind)
            .putString(PersistentDownloadWorker.KEY_REFERER, record.referer)
            .apply {
                record.mimeType?.let { putString(PersistentDownloadWorker.KEY_MIME, it) }
                record.formatSelector?.let {
                    putString(PersistentDownloadWorker.KEY_FORMAT_SELECTOR, it)
                }
                putInt(PersistentDownloadWorker.KEY_ADAPTIVE_VIDEO_HEIGHT, record.adaptiveVideoHeight ?: -1)
                (partialUri ?: record.destinationUri)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { putString(PersistentDownloadWorker.KEY_PARTIAL_URI, it) }
            }
            .build()

        val request = OneTimeWorkRequestBuilder<PersistentDownloadWorker>()
            .setId(taskId)
            .setInputData(retryData)
            .addTag(DOWNLOAD_TAG)
            .addTag(fileNameTag(record.fileName))
            .addTag(resourceKindTag(record.resourceKind))
            .addTag(mimeTypeTag(record.mimeType))
            .addTag(adaptiveHeightTag(record.adaptiveVideoHeight))
            .build()

        onUpdate(
            DownloadTaskUi(
                id = request.id.toString(),
                fileName = record.fileName,
                progress = 0,
                state = "queued",
                detail = "正在重新加入后台队列",
                resourceKind = record.resourceKind,
                mimeType = record.mimeType,
                adaptiveVideoHeight = record.adaptiveVideoHeight
            )
        )

        val appContext = context.applicationContext
        appContext.getSharedPreferences(PAUSE_REQUESTS_PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(record.id)
            .apply()
        val manager = WorkManager.getInstance(appContext)
        enqueueAndObserve(appContext, manager, request, onUpdate)
    }

    private fun enqueueAndObserve(
        context: Context,
        manager: WorkManager,
        request: androidx.work.WorkRequest,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val queuedTask = DownloadTaskUi(
            id = request.id.toString(),
            fileName = request.tags.firstOrNull { it.startsWith(FILE_NAME_TAG_PREFIX) }
                ?.removePrefix(FILE_NAME_TAG_PREFIX)
                ?.let { Uri.decode(it) }
                ?: "下载任务",
            progress = 0,
            state = "queued",
            detail = "已加入后台下载队列"
        )
        val operation = manager.enqueue(request)
        operation.result.addListener({
            val failure = runCatching { operation.result.get() }.exceptionOrNull()
            if (failure != null) {
                val cause = failure.cause ?: failure
                val detail = "无法加入后台下载队列（${cause.javaClass.simpleName}）：${cause.message.orEmpty().take(140)}"
                Handler(Looper.getMainLooper()).post {
                    onUpdate(queuedTask.copy(state = "failed", progress = 0, detail = detail))
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    observeRequest(context, manager, request.id, onUpdate)
                }
            }
        }, Executor { command -> command.run() })
    }

    private fun fileNameTag(fileName: String): String =
        FILE_NAME_TAG_PREFIX + Uri.encode(fileName)

    private fun resourceKindTag(kind: String): String =
        RESOURCE_KIND_TAG_PREFIX + kind

    private fun mimeTypeTag(mimeType: String?): String =
        MIME_TYPE_TAG_PREFIX + (mimeType?.let(Uri::encode) ?: "none")

    private fun adaptiveHeightTag(height: Int?): String =
        ADAPTIVE_HEIGHT_TAG_PREFIX + (height?.toString() ?: "auto")

    private fun observeRequest(
        context: Context,
        manager: WorkManager,
        id: UUID,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val liveData = manager.getWorkInfoByIdLiveData(id)
        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(info: WorkInfo?) {
                if (info == null) return
                onUpdate(toTask(info, context))
                if (info.state.isFinished) liveData.removeObserver(this)
            }
        }
        liveData.observeForever(observer)
    }

    fun pause(
        context: Context,
        taskId: String,
        onPaused: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val manager = WorkManager.getInstance(appContext)
        observeCancellation(manager, taskId, onPaused)
        runCatching {
            appContext.getSharedPreferences(PAUSE_REQUESTS_PREF, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(taskId, true)
                .apply()
            manager.cancelWorkById(UUID.fromString(taskId))
        }
    }

    fun cancel(
        context: Context,
        taskId: String,
        onCancelled: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val manager = WorkManager.getInstance(appContext)
        observeCancellation(manager, taskId, onCancelled)
        runCatching {
            appContext.getSharedPreferences(PAUSE_REQUESTS_PREF, Context.MODE_PRIVATE)
                .edit()
                .remove(taskId)
                .apply()
            manager.cancelWorkById(UUID.fromString(taskId))
        }
    }

    private fun observeCancellation(
        manager: WorkManager,
        taskId: String,
        onCancelled: () -> Unit
    ) {
        val id = runCatching { UUID.fromString(taskId) }.getOrNull() ?: return
        val liveData = manager.getWorkInfoByIdLiveData(id)
        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(info: WorkInfo?) {
                if (info == null || !info.state.isFinished) return
                liveData.removeObserver(this)
                if (info.state == WorkInfo.State.CANCELLED) onCancelled()
            }
        }
        liveData.observeForever(observer)
    }

    fun observeAll(context: Context, onUpdate: (DownloadTaskUi) -> Unit): () -> Unit {
        val liveData = WorkManager.getInstance(context.applicationContext)
            .getWorkInfosByTagLiveData(DOWNLOAD_TAG)
        val observer = Observer<List<WorkInfo>> { infos ->
            infos.orEmpty().forEach { info -> onUpdate(toTask(info, context.applicationContext)) }
        }
        liveData.observeForever(observer)
        return { liveData.removeObserver(observer) }
    }

    private fun toTask(info: WorkInfo, context: Context): DownloadTaskUi {
        val taggedName = info.tags
            .firstOrNull { it.startsWith(FILE_NAME_TAG_PREFIX) }
            ?.removePrefix(FILE_NAME_TAG_PREFIX)
            ?.let { Uri.decode(it) }
        val inputName = taggedName ?: "download"
        val outputName = info.outputData.getString(PersistentDownloadWorker.KEY_FILE_NAME)
        val progressName = info.progress.getString(PersistentDownloadWorker.KEY_FILE_NAME)
        val pauseWasRequested = info.state == WorkInfo.State.CANCELLED &&
            context.getSharedPreferences(PAUSE_REQUESTS_PREF, Context.MODE_PRIVATE)
                .getBoolean(info.id.toString(), false)
        val state = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "queued"
            WorkInfo.State.RUNNING -> "downloading"
            WorkInfo.State.SUCCEEDED -> "completed"
            WorkInfo.State.FAILED -> "failed"
            WorkInfo.State.CANCELLED -> if (pauseWasRequested) "paused" else "cancelled"
        }
        val progress = when {
            info.state == WorkInfo.State.SUCCEEDED -> 100
            info.state == WorkInfo.State.RUNNING || pauseWasRequested ->
                info.progress.getInt(PersistentDownloadWorker.KEY_PROGRESS, -1)
            else -> 0
        }
        val lastProgressDetail = info.progress
            .getString(PersistentDownloadWorker.KEY_DETAIL)
            ?.takeIf { it.isNotBlank() }
        val missingFailureDetail = lastProgressDetail
            ?.let { "任务在「${it.take(80)}」阶段异常退出（Worker 未返回错误详情）；可以尝试重试" }
            ?: "任务异常退出（未返回错误详情）；可以尝试重试，若提示缺少原始参数，请重新解析链接"
        val detail = when (info.state) {
            WorkInfo.State.FAILED ->
                info.outputData.getString(PersistentDownloadWorker.KEY_ERROR)
                    ?: missingFailureDetail
            WorkInfo.State.CANCELLED -> if (pauseWasRequested) "已暂停，可继续下载" else "下载已取消"
            WorkInfo.State.SUCCEEDED ->
                info.outputData.getString(PersistentDownloadWorker.KEY_DETAIL) ?: "下载完成"
            else ->
                info.progress.getString(PersistentDownloadWorker.KEY_DETAIL)
                    ?: if (state == "queued") "等待后台任务执行" else "正在下载"
        }
        return DownloadTaskUi(
            id = info.id.toString(),
            fileName = if (state == "completed") outputName ?: progressName ?: inputName
                else progressName ?: outputName ?: inputName,
            progress = progress,
            state = state,
            detail = detail,
            fileUri = info.outputData.getString(PersistentDownloadWorker.KEY_FILE_URI)
                ?: info.progress.getString(PersistentDownloadWorker.KEY_PARTIAL_URI),
            resourceKind = info.tags.firstOrNull { it.startsWith(RESOURCE_KIND_TAG_PREFIX) }
                ?.removePrefix(RESOURCE_KIND_TAG_PREFIX),
            mimeType = info.tags.firstOrNull { it.startsWith(MIME_TYPE_TAG_PREFIX) }
                ?.removePrefix(MIME_TYPE_TAG_PREFIX)
                ?.takeUnless { it == "none" }
                ?.let { Uri.decode(it) },
            downloadedBytes = info.progress.getLong(PersistentDownloadWorker.KEY_DOWNLOADED_BYTES, -1L),
            totalBytes = info.progress.getLong(PersistentDownloadWorker.KEY_TOTAL_BYTES, -1L),
            totalBytesEstimated = info.progress.getBoolean(PersistentDownloadWorker.KEY_TOTAL_BYTES_ESTIMATED, false),
            speedBytesPerSecond = info.progress.getLong(PersistentDownloadWorker.KEY_SPEED_BYTES_PER_SECOND, -1L),
            adaptiveVideoHeight = info.tags
                .firstOrNull { it.startsWith(ADAPTIVE_HEIGHT_TAG_PREFIX) }
                ?.removePrefix(ADAPTIVE_HEIGHT_TAG_PREFIX)
                ?.toIntOrNull()
        )
    }
}
