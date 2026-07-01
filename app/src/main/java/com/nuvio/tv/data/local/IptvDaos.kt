package com.nuvio.tv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): ConfigEntity?

    @Query("SELECT m3uUrl FROM config WHERE id = 1 LIMIT 1")
    suspend fun getM3uUrl(): String?

    @Query("SELECT epgUrl FROM config WHERE id = 1 LIMIT 1")
    suspend fun getEpgUrl(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ConfigEntity)

    @Query("UPDATE config SET lastRefreshTs = :ts WHERE id = 1")
    suspend fun updateRefreshTimestamp(ts: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT channelId FROM favorites")
    suspend fun getAll(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE channelId = :channelId)")
    suspend fun isFavorite(channelId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE channelId = :channelId")
    suspend fun remove(channelId: String)

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}

@Dao
interface EpgDao {
    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :tvgId 
          AND startTime <= :now 
          AND endTime > :now 
        LIMIT 1
    """)
    suspend fun getCurrentProgram(tvgId: String, now: Long = System.currentTimeMillis()): EpgProgramEntity?

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :tvgId 
        ORDER BY startTime ASC
    """)
    fun getProgramsByChannel(tvgId: String): kotlinx.coroutines.flow.Flow<List<EpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE endTime < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAll()
}
