package com.sxsbsxs.filesniffing

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Converts Hilt construction failures for the download worker into an explicit
 * WorkManager failure output instead of WorkManager's opaque "worker failed"
 * state with an empty output Data object.
 */
internal class DiagnosticWorkerFactory(
    private val delegate: HiltWorkerFactory
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        if (workerClassName != PersistentDownloadWorker::class.java.name) {
            return delegate.createWorker(appContext, workerClassName, workerParameters)
        }

        val worker = try {
            delegate.createWorker(appContext, workerClassName, workerParameters)
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            val detail = formatCreationFailure(error)
            Log.e(TAG, detail, error)
            return WorkerCreationFailureWorker(appContext, workerParameters, detail)
        }

        if (worker != null) return worker

        val detail = "下载任务启动失败（WorkerFactory）：HiltWorkerFactory 未注册 PersistentDownloadWorker；请检查 Hilt Worker 代码生成和依赖配置"
        Log.e(TAG, detail)
        return WorkerCreationFailureWorker(appContext, workerParameters, detail)
    }

    private fun formatCreationFailure(error: Throwable): String {
        var root = error
        while (root.cause != null && root.cause !== root) {
            root = root.cause!!
        }
        val raw = root.message?.takeIf { it.isNotBlank() } ?: "构造下载 Worker 时没有错误描述"
        val safe = raw
            .replace(Regex("(?i)(cookie|authorization|token)\\s*[:=]\\s*[^\\s]+")) {
                it.groupValues[1] + "=[redacted]"
            }
            .replace(Regex("https?://[^\\s]+"), "[URL 已隐藏]")
            .take(200)
        return "下载任务启动失败（${root.javaClass.simpleName}）：$safe"
    }

    private companion object {
        const val TAG = "FileSniffingWorkerFactory"
    }
}

private class WorkerCreationFailureWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val diagnostic: String
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        Log.e("FileSniffingWorkerFactory", diagnostic)
        return Result.failure(
            workDataOf(PersistentDownloadWorker.KEY_ERROR to diagnostic)
        )
    }
}
