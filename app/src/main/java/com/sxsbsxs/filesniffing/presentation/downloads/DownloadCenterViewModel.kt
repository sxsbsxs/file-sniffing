package com.sxsbsxs.filesniffing.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity
import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.SniffedResource
import java.util.UUID
import com.sxsbsxs.filesniffing.data.preferences.AppPreferences
import com.sxsbsxs.filesniffing.data.preferences.DownloadTuning
import com.sxsbsxs.filesniffing.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.sxsbsxs.filesniffing.NiconicoUrlParser
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

    val downloadTuning: StateFlow<DownloadTuning> =
        preferences.downloadTuning.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DownloadTuning()
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

    fun updateDownloadTuning(tuning: DownloadTuning) {
        viewModelScope.launch { preferences.updateDownloadTuning(tuning) }
    }

    /**
     * Persist the source and task metadata before WorkManager can start the worker.
     * This makes the source URL durable even if worker construction or preflight fails.
     */
    suspend fun persistInitialTask(
        taskId: UUID,
        resource: SniffedResource,
        destinationTreeUri: String?
    ) {
        val id = taskId.toString()
        val previous = repository.findById(id)
        val sourceUrl = if (resource.kind == ResourceKind.PLAYLIST) {
            NiconicoUrlParser.extractVideoId(resource.sourcePage)
                ?.let(NiconicoUrlParser::canonicalWatchUrl)
                ?: resource.url
        } else {
            resource.url
        }
        val now = System.currentTimeMillis()
        repository.save(
            DownloadRecordEntity(
                id = id,
                sourceUrl = sourceUrl,
                fileName = resource.fileName,
                resourceKind = resource.kind.name,
                mimeType = resource.mimeType,
                state = "queued",
                progress = 0,
                detail = "已加入后台下载队列",
                destinationUri = previous?.destinationUri,
                createdAtMillis = previous?.createdAtMillis ?: now,
                updatedAtMillis = now,
                referer = resource.sourcePage,
                destinationTreeUri = destinationTreeUri,
                downloadedBytes = previous?.downloadedBytes ?: -1L,
                totalBytes = previous?.totalBytes ?: -1L,
                speedBytesPerSecond = previous?.speedBytesPerSecond ?: -1L,
                adaptiveVideoHeight = previous?.adaptiveVideoHeight,
                formatSelector = resource.formatSelector
            )
        )
    }

    fun loadRetryRecord(id: String, onResult: (DownloadRecordEntity?) -> Unit) {
        viewModelScope.launch {
            // WorkInfo intentionally exposes output/progress but not the original inputData.
            // New tasks now persist their full source metadata before WorkManager enqueue.
            onResult(repository.findById(id))
        }
    }

    /** Persist a new retry attempt before WorkManager is allowed to start it. */
    suspend fun persistRetryTask(
        taskId: UUID,
        previous: DownloadRecordEntity,
        partialUri: String?
    ) {
        val now = System.currentTimeMillis()
        val superseded = previous.copy(
            state = "superseded",
            detail = "已由新的重试任务替代",
            speedBytesPerSecond = -1L,
            updatedAtMillis = now
        )
        val retry = previous.copy(
            id = taskId.toString(),
            state = "queued",
            progress = 0,
            detail = "已重新加入后台下载队列",
            destinationUri = partialUri?.takeIf { it.isNotBlank() } ?: previous.destinationUri,
            createdAtMillis = now,
            updatedAtMillis = now,
            speedBytesPerSecond = -1L
        )
        repository.replaceForRetry(superseded, retry)
    }

    suspend fun updateTaskState(
        id: String,
        state: String,
        detail: String
    ) {
        val previous = repository.findById(id) ?: return
        repository.save(
            previous.copy(
                state = state,
                detail = detail,
                progress = if (state == "cancelled") 0 else previous.progress,
                speedBytesPerSecond = -1L,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun reconcileWorkManagerTask(task: com.sxsbsxs.filesniffing.DownloadTaskUi) {
        val previous = repository.findById(task.id) ?: return
        // A superseded retry row is historical metadata. The old WorkManager request may
        // still report FAILED; never let that terminal snapshot resurrect the old card.
        if (previous.state == "superseded") return
        val isTerminal = task.state in setOf("paused", "cancelled", "completed", "failed")
        val staleDownloadingRow = task.state == "queued" && previous.state == "downloading"
        if (!isTerminal && !staleDownloadingRow) return

        val reconciled = previous.copy(
            fileName = task.fileName,
            state = task.state,
            progress = task.progress.takeIf { it >= 0 } ?: previous.progress,
            detail = task.detail,
            destinationUri = task.fileUri ?: previous.destinationUri,
            downloadedBytes = task.downloadedBytes.takeIf { it >= 0L } ?: previous.downloadedBytes,
            totalBytes = task.totalBytes.takeIf { it >= 0L } ?: previous.totalBytes,
            speedBytesPerSecond = -1L,
            updatedAtMillis = System.currentTimeMillis()
        )
        if (reconciled != previous) repository.save(reconciled)
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
