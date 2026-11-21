package com.sxsbsxs.filesniffing.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sxsbsxs.filesniffing.DownloadTaskUi
import com.sxsbsxs.filesniffing.NiconicoUrlParser
import com.sxsbsxs.filesniffing.SniffedResource
import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity
import com.sxsbsxs.filesniffing.data.preferences.AppPreferences
import com.sxsbsxs.filesniffing.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadCenterViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val repository: DownloadRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            preferences.migrateLegacyFolderIfNeeded()
        }
    }

    val selectedDownloadFolder: StateFlow<String?> =
        preferences.selectedDownloadFolder.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    val downloadHistory: StateFlow<List<DownloadRecordEntity>> =
        repository.observeAll().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun setDownloadFolder(uri: String) {
        viewModelScope.launch { preferences.setSelectedDownloadFolder(uri) }
    }

    fun persistTask(
        task: DownloadTaskUi,
        resource: SniffedResource? = null,
        destinationTreeUri: String? = null
    ) {
        viewModelScope.launch {
            val previous = repository.findById(task.id)
            repository.save(
                DownloadRecordEntity(
                    id = task.id,
                    sourceUrl = resource?.let { current ->
                        if (current.kind.name == "PLAYLIST" &&
                            NiconicoUrlParser.extractVideoId(current.sourcePage) != null
                        ) current.sourcePage else current.url
                    } ?: previous?.sourceUrl.orEmpty(),
                    fileName = task.fileName,
                    resourceKind = resource?.kind?.name ?: previous?.resourceKind ?: "OTHER",
                    mimeType = resource?.mimeType ?: previous?.mimeType,
                    state = task.state,
                    progress = task.progress,
                    detail = task.detail,
                    destinationUri = task.fileUri ?: previous?.destinationUri,
                    createdAtMillis = previous?.createdAtMillis ?: System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis(),
                    referer = resource?.sourcePage ?: previous?.referer.orEmpty(),
                    destinationTreeUri = destinationTreeUri ?: previous?.destinationTreeUri,
                    downloadedBytes = task.downloadedBytes.takeIf { it >= 0L }
                        ?: previous?.downloadedBytes ?: -1L,
                    totalBytes = task.totalBytes.takeIf { it >= 0L }
                        ?: previous?.totalBytes ?: -1L,
                    speedBytesPerSecond = task.speedBytesPerSecond.takeIf { it >= 0L }
                        ?: previous?.speedBytesPerSecond ?: -1L,
                    adaptiveVideoHeight = task.adaptiveVideoHeight ?: previous?.adaptiveVideoHeight
                )
            )
        }
    }

    fun loadRetryRecord(id: String, onResult: (DownloadRecordEntity?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.findById(id))
        }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
