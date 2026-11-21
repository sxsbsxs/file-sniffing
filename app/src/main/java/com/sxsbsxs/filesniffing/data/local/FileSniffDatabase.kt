package com.sxsbsxs.filesniffing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadRecordEntity::class,
        SniffedResourceEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class FileSniffDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao
    abstract fun sniffedResourceDao(): SniffedResourceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_records ADD COLUMN referer TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE download_records ADD COLUMN destinationTreeUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_records ADD COLUMN downloadedBytes INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE download_records ADD COLUMN totalBytes INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE download_records ADD COLUMN speedBytesPerSecond INTEGER NOT NULL DEFAULT -1")
            }
        }
    }
}
