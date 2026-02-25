package com.theveloper.pixelplay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for locally stored songs on the watch.
 * Tracks songs that have been transferred from the phone for offline playback.
 */
@Database(entities = [LocalSongEntity::class], version = 1, exportSchema = false)
abstract class WearMusicDatabase : RoomDatabase() {
    abstract fun localSongDao(): LocalSongDao
}
