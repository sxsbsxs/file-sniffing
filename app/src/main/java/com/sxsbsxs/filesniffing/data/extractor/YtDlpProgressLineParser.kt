package com.sxsbsxs.filesniffing.data.extractor

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.pow
import kotlin.math.roundToLong

internal data class YtDlpLineMetrics(
    val streamId: String? = null,
    val status: String? = null,
    val downloadedBytes: Long = -1L,
    val totalBytes: Long = -1L,
    val totalBytesEstimated: Boolean = false,
    val speedBytesPerSecond: Long = -1L,
    val activeConnections: Int = -1,
    val progressPercent: Float = -1f,
    val isPostProcessing: Boolean = false,
    val postProcessor: String? = null
) {
    val hasDownloadProgress: Boolean
        get() = downloadedBytes >= 0L || progressPercent >= 0f ||
            status == "downloading" || status == "finished"
}

internal object YtDlpProgressLineParser {
    private const val DOWNLOAD_PREFIX = "FILE_SNIFFING_PROGRESS|"
    private const val POSTPROCESS_PREFIX = "FILE_SNIFFING_POSTPROCESS|"

    private val ariaTransfer = Regex(
        """\[#\S+\s+([0-9]+(?:\.[0-9]+)?\s*[KMGT]?i?B)/([0-9]+(?:\.[0-9]+)?\s*[KMGT]?i?B)\(([0-9]+(?:\.[0-9]+)?)%\).*?\bCN:(\d+)\b.*?\bDL:\s*([0-9]+(?:\.[0-9]+)?\s*[KMGT]?i?B)""",
        RegexOption.IGNORE_CASE
    )
    private val ariaRate = Regex(
        """\bDL:\s*([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)\b""",
        RegexOption.IGNORE_CASE
    )
    private val ytDlpRate = Regex(
        """\bat\s+([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)/s\b""",
        RegexOption.IGNORE_CASE
    )
    private val nativeProgress = Regex(
        """\[download\]\s*([0-9]+(?:\.[0-9]+)?)%\s+of\s+([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(line: String, fallbackProgress: Float = -1f): YtDlpLineMetrics {
        if (line.startsWith(DOWNLOAD_PREFIX)) {
            parseDownloadJson(line.removePrefix(DOWNLOAD_PREFIX))?.let { return it }
        }
        if (line.startsWith(POSTPROCESS_PREFIX)) {
            val fields = line.removePrefix(POSTPROCESS_PREFIX).split("|", limit = 2)
            return YtDlpLineMetrics(
                status = fields.getOrNull(0),
                isPostProcessing = true,
                postProcessor = fields.getOrNull(1)?.take(80)
            )
        }

        val aria = ariaTransfer.find(line)
        if (aria != null) {
            return YtDlpLineMetrics(
                streamId = "aria2",
                downloadedBytes = parseSize(aria.groupValues[1]),
                totalBytes = parseSize(aria.groupValues[2]),
                progressPercent = aria.groupValues[3].toFloatOrNull() ?: -1f,
                activeConnections = aria.groupValues[4].toIntOrNull() ?: -1,
                speedBytesPerSecond = parseSize(aria.groupValues[5])
            )
        }

        val speedMatch = ariaRate.find(line) ?: ytDlpRate.find(line)
        val speed = speedMatch?.let { parseSize(it.groupValues[1] + it.groupValues[2]) } ?: -1L
        val native = nativeProgress.find(line)
        if (native != null) {
            val percent = native.groupValues[1].toFloatOrNull() ?: fallbackProgress
            val total = parseSize(native.groupValues[2] + native.groupValues[3])
            return YtDlpLineMetrics(
                streamId = "native",
                downloadedBytes = if (percent >= 0f && total > 0L) {
                    (total * percent / 100.0).toLong()
                } else -1L,
                totalBytes = total,
                speedBytesPerSecond = speed,
                progressPercent = percent
            )
        }

        val normalized = line.lowercase()
        val postprocess = listOf(
            "[merger]", "[extractaudio]", "[videoremuxer]", "[fixup",
            "merging formats", "post-process", "postprocessing", "remux"
        ).any(normalized::contains)
        return YtDlpLineMetrics(
            streamId = if (speed >= 0L) "native" else null,
            speedBytesPerSecond = speed,
            isPostProcessing = postprocess,
            progressPercent = fallbackProgress
        )
    }

    private fun parseDownloadJson(value: String): YtDlpLineMetrics? = runCatching {
        val fields = value.split("|", limit = 2)
        require(fields.size == 2)
        val streamId = fields[0].trim().takeIf {
            it.isNotBlank() && !it.equals("NA", ignoreCase = true)
        } ?: "default"
        val progress = JsonParser.parseString(fields[1]).asJsonObject
        val downloaded = progress.longValue("downloaded_bytes") ?: -1L
        val exactTotal = progress.longValue("total_bytes")
        val estimatedTotal = progress.longValue("total_bytes_estimate")
        val total = exactTotal?.takeIf { it > 0L }
            ?: estimatedTotal?.takeIf { it > 0L }
            ?: -1L
        val estimated = exactTotal == null || exactTotal <= 0L
        val percent = if (downloaded >= 0L && total > 0L) {
            (downloaded * 100.0 / total).toFloat().coerceIn(0f, 100f)
        } else {
            progress.floatValue("percent") ?: -1f
        }
        YtDlpLineMetrics(
            streamId = streamId,
            status = progress.stringValue("status"),
            downloadedBytes = downloaded,
            totalBytes = total,
            totalBytesEstimated = estimated,
            speedBytesPerSecond = progress.doubleValue("speed")?.roundToLong()?.coerceAtLeast(0L) ?: -1L,
            progressPercent = percent
        )
    }.getOrNull()

    private fun JsonObject.longValue(name: String): Long? =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asLong }.getOrNull() }

    private fun JsonObject.doubleValue(name: String): Double? =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun JsonObject.floatValue(name: String): Float? =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asFloat }.getOrNull() }

    private fun JsonObject.stringValue(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }

    private fun parseSize(raw: String): Long {
        val match = Regex(
            """^\s*([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)\s*$""",
            RegexOption.IGNORE_CASE
        ).matchEntire(raw) ?: return -1L
        val value = match.groupValues[1].toDoubleOrNull() ?: return -1L
        val unit = match.groupValues[2].uppercase()
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
