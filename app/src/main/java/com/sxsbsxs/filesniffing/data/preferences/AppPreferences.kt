package com.sxsbsxs.filesniffing.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val selectedFolderKey = stringPreferencesKey("selected_download_folder")

    val selectedDownloadFolder: Flow<String?>
        get() = context.fileSniffingDataStore.data.map { preferences ->
            preferences[selectedFolderKey]
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
}
