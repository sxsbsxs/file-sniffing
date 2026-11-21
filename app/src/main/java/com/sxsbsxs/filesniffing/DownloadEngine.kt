package com.sxsbsxs.filesniffing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID

object DownloadEngine {
    const val DOWNLOAD_TAG = "file-sniffing-download"

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
            .build()

        onUpdate(
            DownloadTaskUi(
                id = request.id.toString(),
                fileName = resource.fileName,
                progress = 0,
                state = "queued",
                detail = "已加入后台下载队列"
            )
        )

        val manager = WorkManager.getInstance(context.applicationContext)
        manager.enqueue(request)
        val liveData = manager.getWorkInfoByIdLiveData(request.id)
        val observer = object : Observer<WorkInfo> {
            override fun onChanged(info: WorkInfo?) {
                if (info == null) return
                onUpdate(toTask(info))
                if (info.state.isFinished) {
                    liveData.removeObserver(this)
                }
            }
        }
        liveData.observeForever(observer)
    }

    fun retry(
        context: Context,
        taskId: String,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val liveData = manager.getWorkInfoByIdLiveData(UUID.fromString(taskId))
        val observer = object : Observer<WorkInfo> {
            private var handled = false

            override fun onChanged(info: WorkInfo?) {
                if (info == null || handled || !info.state.isFinished) return
                handled = true
                liveData.removeObserver(this)
                if (info.state != WorkInfo.State.FAILED && info.state != WorkInfo.State.CANCELLED) return

                val request = OneTimeWorkRequestBuilder<PersistentDownloadWorker>()
                    .setInputData(info.inputData)
                    .addTag(DOWNLOAD_TAG)
                    .build()
                val originalName = info.inputData.getString(PersistentDownloadWorker.KEY_FILE_NAME) ?: "download"
                onUpdate(
                    DownloadTaskUi(
                        id = request.id.toString(),
                        fileName = originalName,
                        progress = 0,
                        state = "queued",
                        detail = "正在重新加入后台队列"
                    )
                )
                manager.enqueue(request)
                observeRequest(manager, request.id, onUpdate)
            }
        }
        liveData.observeForever(observer)
    }

    private fun observeRequest(
        manager: WorkManager,
        id: UUID,
        onUpdate: (DownloadTaskUi) -> Unit
    ) {
        val liveData = manager.getWorkInfoByIdLiveData(id)
        val observer = object : Observer<WorkInfo> {
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
        val inputName = info.inputData.getString(PersistentDownloadWorker.KEY_FILE_NAME)
            ?: "download"
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
        )
    }
}
