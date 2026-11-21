package com.sxsbsxs.filesniffing.di

import android.content.Context
import androidx.room.Room
import com.sxsbsxs.filesniffing.data.local.DownloadRecordDao
import com.sxsbsxs.filesniffing.data.local.FileSniffDatabase
import com.sxsbsxs.filesniffing.data.local.SniffedResourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FileSniffDatabase =
        Room.databaseBuilder(
            context,
            FileSniffDatabase::class.java,
            "file-sniffing.db"
        ).addMigrations(
            FileSniffDatabase.MIGRATION_1_2,
            FileSniffDatabase.MIGRATION_2_3
        ).build()

    @Provides
    fun provideDownloadRecordDao(database: FileSniffDatabase): DownloadRecordDao =
        database.downloadRecordDao()

    @Provides
    fun provideSniffedResourceDao(database: FileSniffDatabase): SniffedResourceDao =
        database.sniffedResourceDao()
}
