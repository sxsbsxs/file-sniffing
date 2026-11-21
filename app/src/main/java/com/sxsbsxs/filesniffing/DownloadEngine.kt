package com.sxsbsxs.filesniffing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity

object DownloadEngine {
    const val DOWNLOAD_TAG = "file-sniffing-download"
    private const val FILE_NAME_TAG_PREFIX = "file-name:"

    fun start(
        context: Context,
        destinationTree: Uri,
        resource: SniffedResource,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val request = OneTimeWorkRequestBuilder<PersistentDownloadWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    PersistentDownloadWorker.KEY_TASK_ID to UUID.randomUUID().toString(),
                    PersistentDownloadWorker.KEY_URL to resource.url,
                    PersistentDownloadWorker.KEY_TREE to destinationTree.toString(),
                    PersistentDownloadWorker.KEY_FILE_NAME to resource.fileName,
                    PersistentDownloadWorker.KEY_KIND to resource.kind.name,
                    PersistentDownloadWorker.KEY_MIME to resource.mimeType,
                    PersistentDownloadWorker.KEY_REFERER to resource.sourcePage
                )
            )
            .addTag(DOWNLOAD_TAG)
            .addTag(fileNameTag(resource.fileName))
            .build()

        onUpdate(
            DownloadTaskUi(
                id = request.id.toString(),
                fileName = resource.fileName,
                progress = 0,
                state = "queued",
                detail = "已加入后台下载队列",
                resourceKind = resource.kind.name,
                mimeType = resource.mimeType
            )
        )

        val manager = WorkManager.getInstance(context.applicationContext)
        manager.enqueue(request)
        observeRequest(manager, request.id, onUpdate)
    }

    fun retry(
        context: Context,
        record: DownloadRecordEntity,
        partialUri: String? = null,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val destinationTree = record.destinationTreeUri
        if (record.sourceUrl.isBlank() || destinationTree.isNullOrBlank()) {
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
            .putString(PersistentDownloadWorker.KEY_TASK_ID, UUID.randomUUID().toString())
            .putString(PersistentDownloadWorker.KEY_URL, record.sourceUrl)
            .putString(PersistentDownloadWorker.KEY_TREE, destinationTree)
            .putString(PersistentDownloadWorker.KEY_FILE_NAME, record.fileName)
            .putString(PersistentDownloadWorker.KEY_KIND, record.resourceKind)
            .putString(PersistentDownloadWorker.KEY_REFERER, record.referer)
            .apply {
                record.mimeType?.let { putString(PersistentDownloadWorker.KEY_MIME, it) }
                (partialUri ?: record.destinationUri)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { putString(PersistentDownloadWorker.KEY_PARTIAL_URI, it) }
            }
            .build()

        val request = OneTimeWorkRequestBuilder<PersistentDownloadWorker>()
            .setInputData(retryData)
            .addTag(DOWNLOAD_TAG)
            .addTag(fileNameTag(record.fileName))
            .build()

        onUpdate(
            DownloadTaskUi(
                id = request.id.toString(),
                fileName = record.fileName,
                progress = 0,
                state = "queued",
                detail = "正在重新加入后台队列",
                resourceKind = record.resourceKind,
                mimeType = record.mimeType
            )
        )

        val manager = WorkManager.getInstance(context.applicationContext)
        manager.enqueue(request)
        observeRequest(manager, request.id, onUpdate)
    }

    private fun fileNameTag(fileName: String): String =
        FILE_NAME_TAG_PREFIX + Uri.encode(fileName)

    private fun observeRequest(
        manager: WorkManager,
        id: UUID,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val liveData = manager.getWorkInfoByIdLiveData(id)
        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(info: WorkInfo?) {
                if (info == null) return
                onUpdate(toTask(info))
                if (info.state.isFinished) liveData.removeObserver(this)
            }
        }
        liveData.observeForever(observer)
    }

    fun cancel(context: Context, taskId: String) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelWorkById(UUID.fromString(taskId))
        }
    }

    fun observeAll(context: Context, onUpdate: (DownloadTaskUi) -> Unit): () -> Unit {
        val liveData = WorkManager.getInstance(context.applicationContext)
            .getWorkInfosByTagLiveData(DOWNLOAD_TAG)
        val observer = Observer<List<WorkInfo>> { infos ->
            infos.orEmpty().forEach { info -> onUpdate(toTask(info)) }
        }
        liveData.observeForever(observer)
        return { liveData.removeObserver(observer) }
    }

    private fun toTask(info: WorkInfo): DownloadTaskUi {
        val taggedName = info.tags
            .firstOrNull { it.startsWith(FILE_NAME_TAG_PREFIX) }
            ?.removePrefix(FILE_NAME_TAG_PREFIX)
            ?.let { Uri.decode(it) }
        val inputName = taggedName ?: "download"
        val outputName = info.outputData.getString(PersistentDownloadWorker.KEY_FILE_NAME)
        val progressName = info.progress.getString(PersistentDownloadWorker.KEY_FILE_NAME)
        val state = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "queued"
            WorkInfo.State.RUNNING -> "downloading"
            WorkInfo.State.SUCCEEDED -> "completed"
            WorkInfo.State.FAILED -> "failed"
            WorkInfo.State.CANCELLED -> "cancelled"
        }
        val progress = when {
            info.state == WorkInfo.State.SUCCEEDED -> 100
            info.state == WorkInfo.State.RUNNING ->
                info.progress.getInt(PersistentDownloadWorker.KEY_PROGRESS, -1)
            else -> 0
        }
        val detail = when (info.state) {
            WorkInfo.State.FAILED ->
                info.outputData.getString(PersistentDownloadWorker.KEY_ERROR) ?: "下载失败"
            WorkInfo.State.CANCELLED -> "下载已取消"
            WorkInfo.State.SUCCEEDED ->
                info.outputData.getString(PersistentDownloadWorker.KEY_DETAIL) ?: "下载完成"
            else ->
                info.progress.getString(PersistentDownloadWorker.KEY_DETAIL)
                    ?: if (state == "queued") "等待后台任务执行" else "正在下载"
        }
        return DownloadTaskUi(
            id = info.id.toString(),
            fileName = progressName ?: outputName ?: inputName,
            progress = progress,
            state = state,
            detail = detail,
            fileUri = info.outputData.getString(PersistentDownloadWorker.KEY_FILE_URI)
                ?: info.progress.getString(PersistentDownloadWorker.KEY_PARTIAL_URI),
            resourceKind = info.inputData.getString(PersistentDownloadWorker.KEY_KIND),
            mimeType = info.inputData.getString(PersistentDownloadWorker.KEY_MIME),
            downloadedBytes = info.progress.getLong(PersistentDownloadWorker.KEY_DOWNLOADED_BYTES, -1L),
            totalBytes = info.progress.getLong(PersistentDownloadWorker.KEY_TOTAL_BYTES, -1L),
            speedBytesPerSecond = info.progress.getLong(PersistentDownloadWorker.KEY_SPEED_BYTES_PER_SECOND, -1L)
        )
    }
}
