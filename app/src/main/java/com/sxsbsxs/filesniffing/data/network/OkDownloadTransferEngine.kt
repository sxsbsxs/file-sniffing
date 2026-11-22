package com.sxsbsxs.filesniffing.data.network

import android.net.Uri
import com.liulishuo.okdownload.DownloadListener
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.OkDownload
import com.liulishuo.okdownload.core.dispatcher.DownloadDispatcher
import com.sxsbsxs.filesniffing.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
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

data class DownloadTransferProgress(
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val connectionCount: Int = 1
)

data class DownloadTransferResult(
    val responseHeaders: Map<String, List<String>>,
    val downloadedBytes: Long,
    val totalBytes: Long
)

private fun updateTotalFromHeaders(
    responseCode: Int,
    headers: Map<String, List<String>>,
    totalBytes: AtomicLong
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value?.firstOrNull()
    val contentRange = header("Content-Range")
    if (responseCode == 206 && !contentRange.isNullOrBlank()) {
        val total = contentRange.substringAfterLast('/', "").toLongOrNull()
        if (total != null && total > 0L) {
            totalBytes.set(total)
            return
        }
    }
    // Content-Length on a 206 response is only the current range length, not
    // the complete resource size. Only use it for a full 200 response.
    if (responseCode == 200) {
        val length = header("Content-Length")?.toLongOrNull()
        if (length != null && length > 0L) totalBytes.set(length)
    }
}

@Singleton
class OkDownloadTransferEngine @Inject constructor(
    private val initializedEngine: OkDownload,
    private val appPreferences: AppPreferences
) {

    suspend fun downloadToDocument(
        url: String,
        destinationDocument: Uri,
        headers: Map<String, List<String>>,
        onProgress: (DownloadTransferProgress) -> Unit
    ): DownloadTransferResult {
        val tuning = appPreferences.downloadTuning.first()
        DownloadDispatcher.setMaxParallelRunningCount(tuning.parallelDownloads)
        DownloadConnectionPolicy.setPreferredMaximumConnections(tuning.connectionsPerTask)
        return suspendCancellableCoroutine { continuation ->
            val receivedBytes = AtomicLong(0L)
            val totalBytes = AtomicLong(-1L)
            val lastReportedBytes = AtomicLong(0L)
            val lastReportedAtNanos = AtomicLong(System.nanoTime())
            val reportLock = Any()
            val latestResponseHeaders = java.util.concurrent.atomic.AtomicReference<Map<String, List<String>>>(emptyMap())

            fun reportProgress(current: Long, total: Long, connectionCount: Int) {
                val now = System.nanoTime()
                val speed = synchronized(reportLock) {
                    val previousBytes = lastReportedBytes.get()
                    val elapsedNanos = now - lastReportedAtNanos.get()
                    if (current >= previousBytes && elapsedNanos > 0L) {
                        lastReportedBytes.set(current)
                        lastReportedAtNanos.set(now)
                        ((current - previousBytes) * 1_000_000_000.0 / elapsedNanos).toLong()
                    } else {
                        -1L
                    }
                }
                val percent = if (total > 0L) {
                    ((current * 100.0) / total).toInt().coerceIn(0, 99)
                } else {
                    -1
                }
                onProgress(
                    DownloadTransferProgress(
                        progress = percent,
                        downloadedBytes = current,
                        totalBytes = total,
                        speedBytesPerSecond = speed,
                        connectionCount = connectionCount.coerceAtLeast(1)
                    )
                )
            }

            val task = DownloadTask.Builder(url, destinationDocument)
                .setHeaderMapFields(headers)
                // Keep the network read size aligned with modern high-throughput clients.
                .setReadBufferSize(64 * 1024)
                .setFlushBufferSize(64 * 1024)
                // Batch disk syncs to reduce per-block write overhead without
                // allowing an unbounded amount of buffered data.
                .setSyncBufferSize(1024 * 1024)
                .setSyncBufferIntervalMillis(1000)
                // Connection count is selected by SizeAwareDownloadStrategy after
                // the server's trial response reveals the resource length.
                .setAutoCallbackToUIThread(false)
                .setMinIntervalMillisCallbackProcess(500)
                .setPassIfAlreadyCompleted(false)
                .build()

            continuation.invokeOnCancellation {
                task.cancel()
            }

            task.enqueue(object : DownloadListener {
                override fun taskStart(task: DownloadTask) {
                    onProgress(DownloadTransferProgress(-1, 0L, -1L, -1L))
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
                    latestResponseHeaders.set(responseHeaderFields)
                    updateTotalFromHeaders(responseCode, responseHeaderFields, totalBytes)
                }

                override fun downloadFromBeginning(
                    task: DownloadTask,
                    info: BreakpointInfo,
                    cause: ResumeFailedCause
                ) {
                    val total = info.totalLength
                    val offset = info.totalOffset
                    if (total > 0L) totalBytes.set(total)
                    if (offset > 0L) receivedBytes.updateAndGet { maxOf(it, offset) }
                    synchronized(reportLock) {
                        if (offset > lastReportedBytes.get()) {
                            lastReportedBytes.set(offset)
                            lastReportedAtNanos.set(System.nanoTime())
                        }
                    }
                }

                override fun downloadFromBreakpoint(task: DownloadTask, info: BreakpointInfo) {
                    val total = info.totalLength
                    val offset = info.totalOffset
                    if (total > 0L) totalBytes.set(total)
                    if (offset > 0L) receivedBytes.updateAndGet { maxOf(it, offset) }
                    synchronized(reportLock) {
                        if (offset > lastReportedBytes.get()) {
                            lastReportedBytes.set(offset)
                            lastReportedAtNanos.set(System.nanoTime())
                        }
                    }
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
                    if (responseHeaderFields.isNotEmpty()) latestResponseHeaders.set(responseHeaderFields)
                    updateTotalFromHeaders(responseCode, responseHeaderFields, totalBytes)
                }

                override fun fetchStart(task: DownloadTask, blockIndex: Int, contentLength: Long) {
                    val info = task.info
                    if (info != null) {
                        if (info.totalLength > 0L) totalBytes.set(info.totalLength)
                        if (info.totalOffset > 0L) {
                            receivedBytes.updateAndGet { maxOf(it, info.totalOffset) }
                        }
                    }
                }

                override fun fetchProgress(task: DownloadTask, blockIndex: Int, increaseBytes: Long) {
                    val current = receivedBytes.addAndGet(increaseBytes)
                    val activeConnections = runCatching { task.info?.blockCount ?: 1 }.getOrDefault(1)
                    reportProgress(current, totalBytes.get(), activeConnections)
                }

                override fun fetchEnd(task: DownloadTask, blockIndex: Int, contentLength: Long) = Unit

                override fun taskEnd(task: DownloadTask, cause: EndCause, realCause: Exception?) {
                    if (!continuation.isActive) return
                    when (cause) {
                        EndCause.COMPLETED -> continuation.resume(
                            DownloadTransferResult(
                                responseHeaders = latestResponseHeaders.get(),
                                downloadedBytes = receivedBytes.get(),
                                totalBytes = totalBytes.get()
                            )
                        )
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
