package com.sxsbsxs.filesniffing.data.extractor

import kotlin.math.pow
import kotlin.math.roundToLong

internal data class YtDlpLineMetrics(
    val speedBytesPerSecond: Long = -1L,
    val activeConnections: Int = -1
)

/**
 * Reads the live status line emitted by yt-dlp or the youtubedl-android aria2c bridge.
 * It intentionally reports the current downloader's rate/connection count rather than
 * inventing an aggregate across separate video and audio format processes.
 */
internal object YtDlpProgressLineParser {
    private val aria2Rate = Regex(
        """\bDL:\s*([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)\b""",
        RegexOption.IGNORE_CASE
    )
    private val ytDlpRate = Regex(
        """\bat\s+([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)/s\b""",
        RegexOption.IGNORE_CASE
    )
    private val connectionCount = Regex("""\bCN:(\d+)\b""", RegexOption.IGNORE_CASE)

    fun parse(line: String): YtDlpLineMetrics {
        val count = connectionCount.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val rate = aria2Rate.find(line) ?: ytDlpRate.find(line)
        val speed = rate?.let { match ->
            toBytesPerSecond(
                match.groupValues[1].toDoubleOrNull() ?: return@let -1L,
                match.groupValues[2]
            )
        } ?: -1L
        return YtDlpLineMetrics(
            speedBytesPerSecond = speed,
            activeConnections = count
        )
    }

    private fun toBytesPerSecond(value: Double, rawUnit: String): Long {
        val unit = rawUnit.uppercase()
        val power = when (unit.firstOrNull()) {
            'K' -> 1
            'M' -> 2
            'G' -> 3
            'T' -> 4
            else -> 0
        }
        val base = if (unit.contains("IB")) 1024.0 else 1000.0
        return (value * base.pow(power)).roundToLong().coerceAtLeast(0L)
    }
}
