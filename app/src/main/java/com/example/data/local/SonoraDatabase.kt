package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlayHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun sonoraDao(): SonoraDao

    companion object {
        @Volatile
        private var instance: SonoraDatabase? = null

        fun getInstance(context: Context): SonoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_local.db"
                ).build().also { instance = it }
            }
        }
    }
}
