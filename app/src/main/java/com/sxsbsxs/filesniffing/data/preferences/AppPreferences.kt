package com.sxsbsxs.filesniffing.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fileSniffingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "file_sniffing_preferences"
)

data class DownloadTuning(
    val connectionsPerTask: Int = 64,
    val parallelDownloads: Int = 3,
    val speedLimitMegabytesPerSecond: Int = 0
) {
    val speedLimitBytesPerSecond: Long
        get() = speedLimitMegabytesPerSecond.coerceAtLeast(0).toLong() * 1024L * 1024L
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val selectedFolderKey = stringPreferencesKey("selected_download_folder")
    private val connectionsPerTaskKey = intPreferencesKey("download_connections_per_task")
    private val parallelDownloadsKey = intPreferencesKey("download_parallel_tasks")
    private val speedLimitKey = intPreferencesKey("download_speed_limit_mbps")

    val selectedDownloadFolder: Flow<String?>
        get() = context.fileSniffingDataStore.data.map { preferences ->
            preferences[selectedFolderKey]
        }

    val downloadTuning: Flow<DownloadTuning>
        get() = context.fileSniffingDataStore.data.map { preferences ->
            DownloadTuning(
                connectionsPerTask = (preferences[connectionsPerTaskKey] ?: 64).coerceIn(1, 512),
                parallelDownloads = (preferences[parallelDownloadsKey] ?: 3).coerceIn(1, 4),
                speedLimitMegabytesPerSecond = (preferences[speedLimitKey] ?: 0)
                    .takeIf { it in SPEED_LIMIT_OPTIONS } ?: 0
            )
        }

    suspend fun updateDownloadTuning(tuning: DownloadTuning) {
        context.fileSniffingDataStore.edit { preferences ->
            preferences[connectionsPerTaskKey] = tuning.connectionsPerTask.coerceIn(1, 512)
            preferences[parallelDownloadsKey] = tuning.parallelDownloads.coerceIn(1, 4)
            preferences[speedLimitKey] = tuning.speedLimitMegabytesPerSecond
                .takeIf { it in SPEED_LIMIT_OPTIONS } ?: 0
        }
    }

    suspend fun migrateLegacyFolderIfNeeded() {
        context.fileSniffingDataStore.edit { preferences ->
            if (preferences[selectedFolderKey] == null) {
                val legacyUri = context.getSharedPreferences("file_sniffing", Context.MODE_PRIVATE)
                    .getString("download_tree_uri", null)
                if (!legacyUri.isNullOrBlank()) {
                    preferences[selectedFolderKey] = legacyUri
                }
            }
        }
    }

    suspend fun setSelectedDownloadFolder(uri: String) {
        context.fileSniffingDataStore.edit { preferences ->
            preferences[selectedFolderKey] = uri
        }
    }

    suspend fun clearSelectedDownloadFolder() {
        context.fileSniffingDataStore.edit { preferences ->
            preferences.remove(selectedFolderKey)
        }
    }

    private companion object {
        val SPEED_LIMIT_OPTIONS = setOf(0, 1, 2, 5, 10, 20)
    }
}
