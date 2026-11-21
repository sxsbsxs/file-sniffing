package com.sxsbsxs.filesniffing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadRecordEntity::class,
        SniffedResourceEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FileSniffDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao
    abstract fun sniffedResourceDao(): SniffedResourceDao
}
