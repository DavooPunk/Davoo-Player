package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        ImportedTrackEntity::class,
        TrackStatsEntity::class,
        TrackTagOverrideEntity::class,
        ExcludedFolderEntity::class,
        TrackLyricsEntity::class,
        TrackLyricsTranslationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun importedTrackDao(): ImportedTrackDao
    abstract fun trackStatsDao(): TrackStatsDao
    abstract fun trackTagOverrideDao(): TrackTagOverrideDao
    abstract fun excludedFolderDao(): ExcludedFolderDao
    abstract fun trackLyricsDao(): TrackLyricsDao
    abstract fun trackLyricsTranslationDao(): TrackLyricsTranslationDao

    companion object {
        @Volatile
        private var INSTANCE: SonoraDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_stats` (
                        `trackId` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `lastPlayedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_tag_overrides` (
                        `trackId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumArtist` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `trackNumber` INTEGER NOT NULL,
                        `customArtworkUri` TEXT,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `excluded_folders` (
                        `folderName` TEXT NOT NULL,
                        `excludedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`folderName`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_lyrics` (
                        `trackId` TEXT NOT NULL,
                        `rawLrcText` TEXT NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_lyrics_translations` (
                        `trackId` TEXT NOT NULL,
                        `languageCode` TEXT NOT NULL,
                        `rawTranslatedText` TEXT NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `track_lyrics` ADD COLUMN `offsetMs` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): SonoraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_music.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
