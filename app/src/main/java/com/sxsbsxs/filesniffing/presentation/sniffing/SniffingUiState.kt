package com.sxsbsxs.filesniffing.presentation.sniffing

import com.sxsbsxs.filesniffing.SniffedResource

data class SniffingUiState(
    val activePageUrl: String? = null,
    val pageTitle: String = "",
    val isPageLoading: Boolean = false,
    val resources: List<SniffedResource> = emptyList()
)
