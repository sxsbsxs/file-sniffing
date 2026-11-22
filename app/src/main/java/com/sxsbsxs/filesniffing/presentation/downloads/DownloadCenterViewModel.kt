package com.sxsbsxs.filesniffing.presentation.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sxsbsxs.filesniffing.PersistentDownloadWorker
import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity
import com.sxsbsxs.filesniffing.data.preferences.AppPreferences
import com.sxsbsxs.filesniffing.data.preferences.DownloadTuning
import com.sxsbsxs.filesniffing.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadCenterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
     * Worker inputData is the durable copy of the original request. Recover it when an older
     * UI/Room write race left sourceUrl blank, so users can retry without pasting the link again.
     */
    fun loadRetryRecord(id: String, onResult: (DownloadRecordEntity?) -> Unit) {
        viewModelScope.launch {
            val existing = repository.findById(id)
            if (!existing?.sourceUrl.isNullOrBlank()) {
                onResult(existing)
                return@launch
            }

            val workInfo = withContext(Dispatchers.IO) {
                runCatching {
                    WorkManager.getInstance(context)
                        .getWorkInfoById(UUID.fromString(id))
                        .get()
                }.getOrNull()
            }
            val input = workInfo?.inputData
            val sourceUrl = input?.getString(PersistentDownloadWorker.KEY_URL)
                ?.takeIf { it.isNotBlank() }
            if (sourceUrl == null) {
                onResult(existing)
                return@launch
            }

            val now = System.currentTimeMillis()
            val recovered = if (existing != null) {
                existing.copy(
                    sourceUrl = sourceUrl,
                    fileName = existing.fileName.ifBlank {
                        input.getString(PersistentDownloadWorker.KEY_FILE_NAME).orEmpty()
                    },
                    resourceKind = existing.resourceKind.ifBlank {
                        input.getString(PersistentDownloadWorker.KEY_KIND).orEmpty().ifBlank { "VIDEO" }
                    },
                    mimeType = existing.mimeType
                        ?: input.getString(PersistentDownloadWorker.KEY_MIME),
                    referer = existing.referer.ifBlank {
                        input.getString(PersistentDownloadWorker.KEY_REFERER).orEmpty()
                    },
                    destinationTreeUri = existing.destinationTreeUri
                        ?: input.getString(PersistentDownloadWorker.KEY_TREE),
                    adaptiveVideoHeight = existing.adaptiveVideoHeight
                        ?: input.getInt(PersistentDownloadWorker.KEY_ADAPTIVE_VIDEO_HEIGHT, -1)
                            .takeIf { it > 0 },
                    updatedAtMillis = now
                )
            } else {
                DownloadRecordEntity(
                    id = id,
                    sourceUrl = sourceUrl,
                    fileName = input.getString(PersistentDownloadWorker.KEY_FILE_NAME)
                        ?.takeIf { it.isNotBlank() } ?: "download",
                    resourceKind = input.getString(PersistentDownloadWorker.KEY_KIND)
                        ?.takeIf { it.isNotBlank() } ?: "VIDEO",
                    mimeType = input.getString(PersistentDownloadWorker.KEY_MIME),
                    state = "failed",
                    progress = 0,
                    detail = "已从后台任务恢复原始下载参数",
                    destinationUri = null,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    referer = input.getString(PersistentDownloadWorker.KEY_REFERER).orEmpty(),
                    destinationTreeUri = input.getString(PersistentDownloadWorker.KEY_TREE),
                    adaptiveVideoHeight = input.getInt(
                        PersistentDownloadWorker.KEY_ADAPTIVE_VIDEO_HEIGHT,
                        -1
                    ).takeIf { it > 0 }
                )
            }
            repository.save(recovered)
            onResult(recovered)
        }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
