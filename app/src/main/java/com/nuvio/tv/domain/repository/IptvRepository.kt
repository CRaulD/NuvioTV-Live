package com.nuvio.tv.domain.repository

import com.nuvio.tv.domain.model.TvChannel
import kotlinx.coroutines.flow.Flow

interface IptvRepository {
    /** Channels list from the active playlist */
    fun getChannels(): Flow<List<TvChannel>>

    /** Available groups list */
    fun getGroups(): Flow<List<String>>

    /** Channels filtered by group */
    fun getChannelsByGroup(group: String): Flow<List<TvChannel>>

    /** Download + parse the M3U playlist */
    suspend fun refreshPlaylist()

    /** Download + parse + cache EPG */
    suspend fun refreshEpg()

    /** Current program for a channel */
    suspend fun getCurrentProgram(tvgId: String): com.nuvio.tv.domain.model.EpgProgram?

    /** Programs for a channel (Flow) */
    fun getProgramsByChannel(tvgId: String): Flow<List<com.nuvio.tv.domain.model.EpgProgram>>

    /** Programs overlapping a time window for multiple channels (EPG grid) */
    suspend fun getProgramsForChannels(
        tvgIds: List<String>,
        windowStart: Long,
        windowEnd: Long
    ): List<com.nuvio.tv.domain.model.EpgProgram>

    // Config
    suspend fun getM3uUrl(): String?
    suspend fun getEpgUrl(): String?
    suspend fun saveUrls(m3uUrl: String, epgUrl: String)

    // Favorites
    suspend fun getFavorites(): List<String>
    suspend fun isFavorite(channelId: String): Boolean
    suspend fun toggleFavorite(channelId: String)
}
