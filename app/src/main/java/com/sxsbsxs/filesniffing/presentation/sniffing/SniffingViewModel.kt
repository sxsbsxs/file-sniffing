package com.sxsbsxs.filesniffing.presentation.sniffing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.NiconicoUrlParser
import com.sxsbsxs.filesniffing.SniffedResource
import com.sxsbsxs.filesniffing.data.local.SniffedResourceEntity
import com.sxsbsxs.filesniffing.data.repository.SniffedResourceRepository
import com.sxsbsxs.filesniffing.data.media.AdaptiveQualityOption
import com.sxsbsxs.filesniffing.data.media.Media3AdaptiveProcessor
import com.sxsbsxs.filesniffing.data.extractor.NiconicoExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SniffingViewModel @Inject constructor(
    private val resourceRepository: SniffedResourceRepository,
    private val media3AdaptiveProcessor: Media3AdaptiveProcessor,
    private val niconicoExtractor: NiconicoExtractor
) : ViewModel() {

    private val _uiState = MutableStateFlow(SniffingUiState())
    val uiState = _uiState.asStateFlow()

    private var observePageJob: Job? = null
    private var niconicoExtractionJob: Job? = null

    fun beginInspection(pageUrl: String) {
        observePageJob?.cancel()
        niconicoExtractionJob?.cancel()
        _uiState.value = SniffingUiState(
            activePageUrl = pageUrl,
            isPageLoading = true
        )
        observePageJob = viewModelScope.launch {
            try {
                resourceRepository.observeForPage(pageUrl).collectLatest { stored ->
                    val mapped = stored.mapNotNull(::toResource)
                    _uiState.update { state ->
                        if (state.activePageUrl == pageUrl) {
                            val transientIds = state.transientResources.map { it.id }.toSet()
                            state.copy(
                                resources = (mapped.filterNot { it.id in transientIds } +
                                    state.transientResources).distinctBy { it.id },
                                errorMessage = null
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message
                    ?.take(160)
                    ?.takeIf { it.isNotBlank() }
                    ?: "本地资源记录读取失败"
                _uiState.update { state ->
                    if (state.activePageUrl == pageUrl) {
                        state.copy(isPageLoading = false, errorMessage = message)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun inspectAdaptiveQualities(
        resource: SniffedResource,
        onResult: (List<AdaptiveQualityOption>, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                onResult(media3AdaptiveProcessor.inspectAdaptiveQualities(resource), null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onResult(emptyList(), error.message?.take(180)?.ifBlank { null } ?: "读取视频画质失败")
            }
        }
    }

    /**
     * Resolve a Niconico watch page to the short-lived HLS manifest issued for this session.
     * The result stays in memory; the download worker refreshes the manifest again on start/retry.
     */
    fun extractNiconicoResource(
        pageUrl: String,
        onResult: (SniffedResource?, String?) -> Unit
    ) {
        val videoId = NiconicoUrlParser.extractVideoId(pageUrl)
        if (videoId == null) {
            onResult(null, "不是有效的 Niconico 视频链接")
            return
        }
        niconicoExtractionJob?.cancel()
        niconicoExtractionJob = viewModelScope.launch {
            try {
                val resource = niconicoExtractor.extract(pageUrl)
                if (NiconicoUrlParser.extractVideoId(_uiState.value.activePageUrl.orEmpty()) != videoId) {
                    return@launch
                }
                _uiState.update { state ->
                    val transient = state.transientResources
                        .filterNot { NiconicoUrlParser.extractVideoId(it.sourcePage) == videoId } + resource
                    val visible = state.resources.filterNot { it.id == resource.id } + resource
                    state.copy(
                        transientResources = transient,
                        resources = visible.distinctBy { it.id }
                    )
                }
                onResult(resource, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (NiconicoUrlParser.extractVideoId(_uiState.value.activePageUrl.orEmpty()) == videoId) {
                    onResult(
                        null,
                        error.message?.take(180)?.ifBlank { null } ?: "N站视频解析失败"
                    )
                }
            }
        }
    }

    fun setPageLoading(loading: Boolean) {
        _uiState.update { it.copy(isPageLoading = loading) }
    }

    fun setPageTitle(title: String) {
        _uiState.update { it.copy(pageTitle = title) }
    }

    fun addResource(resource: SniffedResource) {
        val pageUrl = _uiState.value.activePageUrl ?: return
        viewModelScope.launch {
            try {
                resourceRepository.save(
                    SniffedResourceEntity(
                        pageUrl = pageUrl,
                        resourceUrl = resource.url,
                        resourceKind = resource.kind.name,
                        fileName = resource.fileName,
                        mimeType = resource.mimeType,
                        pageTitle = resource.pageTitle,
                        firstSeenAtMillis = System.currentTimeMillis()
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message
                    ?.take(160)
                    ?.takeIf { it.isNotBlank() }
                    ?: "保存嗅探记录失败"
                _uiState.update { state ->
                    if (state.errorMessage == null) state.copy(errorMessage = message) else state
                }
            }
        }
    }

    fun closeInspection() {
        observePageJob?.cancel()
        niconicoExtractionJob?.cancel()
        observePageJob = null
        _uiState.value = SniffingUiState()
    }

    private fun toResource(entity: SniffedResourceEntity): SniffedResource? {
        val kind = runCatching { ResourceKind.valueOf(entity.resourceKind) }.getOrNull() ?: return null
        return SniffedResource(
            url = entity.resourceUrl,
            kind = kind,
            fileName = entity.fileName,
            mimeType = entity.mimeType,
            pageTitle = entity.pageTitle,
            sourcePage = entity.pageUrl
        )
    }
}
