package com.sonora.player.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sonora.player.data.local.dao.FavoriteDao
import com.sonora.player.data.local.dao.FolderDao
import com.sonora.player.data.local.dao.HistoryDao
import com.sonora.player.data.local.dao.PlaylistDao
import com.sonora.player.data.local.dao.StatsDao
import com.sonora.player.data.local.dao.TrackDao

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        TrackStatsEntity::class,
        FolderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun statsDao(): StatsDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val NAME = "sonora.db"

        /**
         * Future schema changes must add a [androidx.room.migration.Migration] here
         * (schemas are exported to app/schemas). Destructive fallback is intentionally NOT
         * enabled: losing playlists on upgrade is not acceptable.
         */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun build(context: Context): SonoraDatabase =
            Room.databaseBuilder(context, SonoraDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
