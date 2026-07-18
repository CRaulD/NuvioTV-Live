package com.nuvio.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ConfigEntity::class, FavoriteEntity::class, EpgProgramEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun epgDao(): EpgDao
}