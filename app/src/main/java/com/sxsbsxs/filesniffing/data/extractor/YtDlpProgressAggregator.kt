package com.sxsbsxs.filesniffing.data.extractor

internal data class YtDlpAggregateProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val totalBytesEstimated: Boolean,
    val speedBytesPerSecond: Long,
    val connectionCount: Int,
    val progressPercent: Float,
    val completedStreams: Int,
    val expectedStreams: Int,
    val allExpectedStreamsFinished: Boolean
)

internal class YtDlpProgressAggregator(expectedStreams: Int) {
    private data class StreamState(
        var downloadedBytes: Long = 0L,
        var totalBytes: Long = -1L,
        var totalBytesEstimated: Boolean = false,
        var speedBytesPerSecond: Long = 0L,
        var connectionCount: Int = 0,
        var progressPercent: Float = 0f,
        var finished: Boolean = false
    )

    private val expected = expectedStreams.coerceIn(1, 2)
    private val streams = linkedMapOf<String, StreamState>()

    fun update(sample: YtDlpLineMetrics): YtDlpAggregateProgress {
        val stream = streams.getOrPut(sample.streamId ?: "default") { StreamState() }
        if (sample.downloadedBytes >= 0L) stream.downloadedBytes = sample.downloadedBytes
        if (sample.totalBytes > 0L) {
            stream.totalBytes = sample.totalBytes
            stream.totalBytesEstimated = sample.totalBytesEstimated
        }
        if (sample.progressPercent >= 0f) {
            stream.progressPercent = sample.progressPercent.coerceIn(0f, 100f)
        } else if (stream.totalBytes > 0L) {
            stream.progressPercent =
                (stream.downloadedBytes * 100.0 / stream.totalBytes).toFloat().coerceIn(0f, 100f)
        }
        if (sample.speedBytesPerSecond >= 0L) stream.speedBytesPerSecond = sample.speedBytesPerSecond
        if (sample.activeConnections >= 0) stream.connectionCount = sample.activeConnections
        if (sample.status == "finished") {
            stream.finished = true
            stream.progressPercent = 100f
            stream.speedBytesPerSecond = 0L
            stream.connectionCount = 0
            if (stream.totalBytes <= 0L && stream.downloadedBytes > 0L) {
                stream.totalBytes = stream.downloadedBytes
                stream.totalBytesEstimated = false
            }
        }

        val downloaded = streams.values.fold(0L) { total, value -> safeAdd(total, value.downloadedBytes) }
        val allTotalsKnown = streams.size >= expected && streams.values.all { it.totalBytes > 0L }
        val total = if (allTotalsKnown) {
            streams.values.fold(0L) { sum, value -> safeAdd(sum, value.totalBytes) }
        } else -1L
        val percent = (
            streams.values.sumOf { it.progressPercent.toDouble() } / expected.toDouble()
        ).toFloat().coerceIn(0f, 100f)
        val speed = streams.values.fold(0L) { sum, value -> safeAdd(sum, value.speedBytesPerSecond) }
        val connections = streams.values.sumOf { it.connectionCount }.coerceAtLeast(0)
        val completed = streams.values.count { it.finished }

        return YtDlpAggregateProgress(
            downloadedBytes = downloaded,
            totalBytes = total,
            totalBytesEstimated = allTotalsKnown && streams.values.any { it.totalBytesEstimated },
            speedBytesPerSecond = speed,
            connectionCount = connections,
            progressPercent = percent,
            completedStreams = completed,
            expectedStreams = expected,
            allExpectedStreamsFinished = completed >= expected
        )
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
