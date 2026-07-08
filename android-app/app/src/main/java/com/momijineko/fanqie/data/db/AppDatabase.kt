package com.momijineko.fanqie.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ProgressEntity::class, GroupEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun progressDao(): ProgressDao
    abstract fun groupDao(): GroupDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN score TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE progress ADD COLUMN totalChapters INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`groupId` TEXT NOT NULL, `name` TEXT NOT NULL, `bookIdsJson` TEXT NOT NULL DEFAULT '[]', `isCloud` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`groupId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`keyword` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`keyword`))")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fanqie.db",
                ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
