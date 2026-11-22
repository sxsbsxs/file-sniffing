package com.sxsbsxs.filesniffing.data.network

import java.util.concurrent.locks.LockSupport
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/** A shared response-body rate cap for concurrent direct HTTP and Media3 downloads. */
@Singleton
class DownloadSpeedLimiter @Inject constructor() : Interceptor {
    @Volatile
    private var maxBytesPerSecond: Long = 0L

    private val scheduleLock = Any()
    private var nextReadSlotNanos = 0L

    fun setLimitBytesPerSecond(value: Long) {
        synchronized(scheduleLock) {
            maxBytesPerSecond = value.coerceAtLeast(0L)
            nextReadSlotNanos = 0L
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        return response.newBuilder().body(LimitedResponseBody(body, this)).build()
    }

    internal fun throttle(bytesRead: Long) {
        if (bytesRead <= 0L || maxBytesPerSecond <= 0L) return
        val waitNanos = synchronized(scheduleLock) {
            val limit = maxBytesPerSecond
            if (limit <= 0L) 0L else {
                val now = System.nanoTime()
                val scheduledStart = maxOf(now, nextReadSlotNanos)
                val duration = (bytesRead.toDouble() * 1_000_000_000.0 / limit)
                    .toLong().coerceAtLeast(0L)
                nextReadSlotNanos = scheduledStart + duration
                (scheduledStart - now).coerceAtLeast(0L)
            }
        }
        if (waitNanos > 0L) LockSupport.parkNanos(waitNanos)
    }

    private class LimitedResponseBody(
        private val delegate: ResponseBody,
        private val limiter: DownloadSpeedLimiter
    ) : ResponseBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun source(): BufferedSource =
            object : ForwardingSource(delegate.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val count = super.read(sink, byteCount)
                    if (count > 0L) limiter.throttle(count)
                    return count
                }
            }.buffer()
    }
}
