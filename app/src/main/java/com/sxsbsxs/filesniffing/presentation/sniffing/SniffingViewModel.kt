package com.sxsbsxs.filesniffing.presentation.sniffing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sxsbsxs.filesniffing.FileNameSanitizer
import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.ResourceSniffer
import com.sxsbsxs.filesniffing.SharedLinkNormalizer
import com.sxsbsxs.filesniffing.SniffedResource
import com.sxsbsxs.filesniffing.data.extractor.YouTubeUrlParser
import com.sxsbsxs.filesniffing.data.extractor.YtDlpRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Direct link resolver. It never creates a WebView or opens the supplied page.
 * Supported extractors fetch metadata/media information directly through yt-dlp.
 */
@HiltViewModel
class SniffingViewModel @Inject constructor(
    private val ytDlpRuntime: YtDlpRuntime
) : ViewModel() {

    private val _uiState = MutableStateFlow(SniffingUiState())
    val uiState = _uiState.asStateFlow()

    private var resolveJob: Job? = null

    fun resolveUrl(rawUrl: String, onResult: (List<SniffedResource>?, String?) -> Unit) {
        val normalizedUrl = SharedLinkNormalizer.normalize(rawUrl)
        if (normalizedUrl.isNullOrBlank()) {
            onResult(null, "请输入有效的视频或文件链接")
            return
        }

        resolveJob?.cancel()
        _uiState.value = SniffingUiState(
            activePageUrl = normalizedUrl,
            isPageLoading = true
        )

        resolveJob = viewModelScope.launch {
            try {
                // Direct file URLs do not need a site extractor.
                val directResource = ResourceSniffer.detect(
                    rawUrl = normalizedUrl,
                    pageTitle = "",
                    sourcePage = normalizedUrl
                )
                val resources: List<SniffedResource>
                val summary: String

                if (directResource != null) {
                    resources = listOf(directResource)
                    summary = "检测到直链文件"
                } else if (YouTubeUrlParser.isPlaylistUrl(normalizedUrl)) {
                    val playlist = ytDlpRuntime.inspectPlaylist(normalizedUrl)
                    resources = playlist.entries.map { entry ->
                        val safeTitle = FileNameSanitizer.sanitize(entry.title, entry.videoId)
                        SniffedResource(
                            url = entry.webpageUrl,
                            kind = ResourceKind.VIDEO,
                            fileName = "$safeTitle.mp4",
                            mimeType = "video/mp4",
                            pageTitle = entry.title,
                            sourcePage = normalizedUrl
                        )
                    }
                    summary = "播放列表：${resources.size} 个视频"
                } else {
                    val info = ytDlpRuntime.inspect(normalizedUrl)
                    val videoFormats = info.selectableVideoFormats
                    val audioFormats = info.audioOnlyFormats
                    if (videoFormats.isEmpty() && audioFormats.isEmpty()) {
                        throw IllegalStateException("yt-dlp 没有返回可下载的视频或音频格式")
                    }

                    val isAudioOnly = videoFormats.isEmpty()
                    val safeTitle = FileNameSanitizer.sanitize(
                        info.title,
                        info.videoId.ifBlank { "video" }
                    )
                    val kind = if (isAudioOnly) ResourceKind.AUDIO else ResourceKind.VIDEO
                    val extension = if (isAudioOnly) "mp3" else "mp4"
                    resources = listOf(
                        SniffedResource(
                            url = normalizedUrl,
                            kind = kind,
                            fileName = "$safeTitle.$extension",
                            mimeType = if (isAudioOnly) "audio/mpeg" else "video/mp4",
                            pageTitle = info.title,
                            sourcePage = normalizedUrl
                        )
                    )

                    val bestHeight = videoFormats.mapNotNull { it.height }.maxOrNull()
                    summary = buildString {
                        if (isAudioOnly) {
                            append("可用音频格式：").append(audioFormats.size).append(" 种")
                        } else {
                            append("可用视频格式：").append(videoFormats.size).append(" 种")
                            if (bestHeight != null) append(" · 最高 ").append(bestHeight).append("p")
                            if (audioFormats.isNotEmpty()) {
                                append(" · 音频轨道：").append(audioFormats.size).append(" 种")
                            }
                        }
                        info.uploader?.takeIf { it.isNotBlank() }?.let {
                            append(" · ").append(it.take(80))
                        }
                    }
                }

                if (_uiState.value.activePageUrl != normalizedUrl) return@launch
                _uiState.value = SniffingUiState(
                    activePageUrl = normalizedUrl,
                    pageTitle = resources.firstOrNull()?.pageTitle.orEmpty(),
                    isPageLoading = false,
                    resources = resources,
                    resultSummary = summary
                )
                onResult(resources, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(200)
                    ?.takeIf { it.isNotBlank() }
                    ?: "链接解析失败，请检查链接或更新 yt-dlp"
                if (_uiState.value.activePageUrl == normalizedUrl) {
                    _uiState.value = SniffingUiState(
                        activePageUrl = normalizedUrl,
                        isPageLoading = false,
                        errorMessage = message
                    )
                    onResult(null, message)
                }
            }
        }
    }

    fun closeInspection() {
        resolveJob?.cancel()
        resolveJob = null
        _uiState.value = SniffingUiState()
    }
}
