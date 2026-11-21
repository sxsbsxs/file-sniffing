package com.sxsbsxs.filesniffing.data.network

import android.net.Uri
import com.liulishuo.okdownload.DownloadListener
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.OkDownload
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.cause.ResumeFailedCause
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class OkDownloadTransferEngine @Inject constructor(
    private val initializedEngine: OkDownload
) {

    suspend fun downloadToDocument(
        url: String,
        destinationDocument: Uri,
        headers: Map<String, List<String>>,
        onProgress: (progress: Int, detail: String) -> Unit
    ) {
        suspendCancellableCoroutine { continuation ->
            val receivedBytes = AtomicLong(0L)
            val totalBytes = AtomicLong(-1L)

            val task = DownloadTask.Builder(url, destinationDocument)
                .setHeaderMapFields(headers)
                .setConnectionCount(3)
                .setAutoCallbackToUIThread(false)
                .setMinIntervalMillisCallbackProcess(500)
                .setPassIfAlreadyCompleted(false)
                .build()

            continuation.invokeOnCancellation {
                task.cancel()
            }

            task.enqueue(object : DownloadListener {
                override fun taskStart(task: DownloadTask) {
                    onProgress(-1, "OkDownload 已开始传输")
                }

                override fun connectTrialStart(
                    task: DownloadTask,
                    requestHeaderFields: Map<String, List<String>>
                ) = Unit

                override fun connectTrialEnd(
                    task: DownloadTask,
                    responseCode: Int,
                    responseHeaderFields: Map<String, List<String>>
                ) {
                    if (responseCode !in 200..299) {
                        onProgress(-1, "服务器响应 HTTP $responseCode")
                    }
                    val length = responseHeaderFields.entries
                        .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
                        ?.value
                        ?.firstOrNull()
                        ?.toLongOrNull()
                    if (length != null && length > 0L) totalBytes.set(length)
                }

                override fun downloadFromBeginning(
                    task: DownloadTask,
                    info: BreakpointInfo,
                    cause: ResumeFailedCause
                ) {
                    val total = info.totalLength
                    val offset = info.totalOffset
                    if (total > 0L) totalBytes.set(total)
                    if (offset > 0L) receivedBytes.set(offset)
                }

                override fun downloadFromBreakpoint(task: DownloadTask, info: BreakpointInfo) {
                    val total = info.totalLength
                    val offset = info.totalOffset
                    if (total > 0L) totalBytes.set(total)
                    if (offset > 0L) receivedBytes.set(offset)
                }

                override fun connectStart(
                    task: DownloadTask,
                    blockIndex: Int,
                    requestHeaderFields: Map<String, List<String>>
                ) = Unit

                override fun connectEnd(
                    task: DownloadTask,
                    blockIndex: Int,
                    responseCode: Int,
                    responseHeaderFields: Map<String, List<String>>
                ) {
                    if (responseCode !in 200..299) {
                        onProgress(-1, "分段连接响应 HTTP $responseCode")
                    }
                }

                override fun fetchStart(task: DownloadTask, blockIndex: Int, contentLength: Long) {
                    val info = task.info
                    if (info != null) {
                        if (info.totalLength > 0L) totalBytes.set(info.totalLength)
                        if (info.totalOffset > receivedBytes.get()) {
                            receivedBytes.set(info.totalOffset)
                        }
                    }
                }

                override fun fetchProgress(task: DownloadTask, blockIndex: Int, increaseBytes: Long) {
                    val current = receivedBytes.addAndGet(increaseBytes)
                    val total = totalBytes.get()
                    if (total > 0L) {
                        val progress = ((current * 100.0) / total).toInt().coerceIn(0, 99)
                        onProgress(progress, "已下载 $current / $total 字节")
                    } else {
                        onProgress(-1, "已下载 $current 字节")
                    }
                }

                override fun fetchEnd(task: DownloadTask, blockIndex: Int, contentLength: Long) = Unit

                override fun taskEnd(task: DownloadTask, cause: EndCause, realCause: Exception?) {
                    if (!continuation.isActive) return
                    when (cause) {
                        EndCause.COMPLETED -> continuation.resume(Unit)
                        EndCause.CANCELED -> continuation.resumeWithException(
                            kotlinx.coroutines.CancellationException("下载已取消")
                        )
                        else -> continuation.resumeWithException(
                            realCause ?: IOException("OkDownload 结束状态：$cause")
                        )
                    }
                }
            })
        }
    }
}
