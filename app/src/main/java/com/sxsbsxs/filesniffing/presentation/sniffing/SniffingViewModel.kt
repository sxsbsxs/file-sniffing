package com.sxsbsxs.filesniffing.presentation.sniffing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.SniffedResource
import com.sxsbsxs.filesniffing.data.local.SniffedResourceEntity
import com.sxsbsxs.filesniffing.data.repository.SniffedResourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SniffingViewModel @Inject constructor(
    private val resourceRepository: SniffedResourceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SniffingUiState())
    val uiState = _uiState.asStateFlow()

    private var observePageJob: Job? = null

    fun beginInspection(pageUrl: String) {
        observePageJob?.cancel()
        _uiState.value = SniffingUiState(
            activePageUrl = pageUrl,
            isPageLoading = true
        )
        observePageJob = viewModelScope.launch {
            resourceRepository.observeForPage(pageUrl).collectLatest { stored ->
                val mapped = stored.mapNotNull(::toResource)
                _uiState.update { state ->
                    if (state.activePageUrl == pageUrl) state.copy(resources = mapped) else state
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
        }
    }

    fun closeInspection() {
        observePageJob?.cancel()
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
