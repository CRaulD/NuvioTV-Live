package com.nuvio.tv.domain.repository

import com.nuvio.tv.domain.model.TvChannel
import kotlinx.coroutines.flow.Flow

interface IptvRepository {
    /** Lista de canais da playlist ativa */
    fun getChannels(): Flow<List<TvChannel>>

    /** Lista de grupos disponíveis */
    fun getGroups(): Flow<List<String>>

    /** Canais filtrados por grupo */
    fun getChannelsByGroup(group: String): Flow<List<TvChannel>>

    /** Download + parse da playlist M3U */
    suspend fun refreshPlaylist()

    /** Download + parse + cache do EPG */
    suspend fun refreshEpg()

    /** Programa atual de um canal */
    suspend fun getCurrentProgram(tvgId: String): com.nuvio.tv.domain.model.EpgProgram?

    /** Programas de um canal (Flow) */
    fun getProgramsByChannel(tvgId: String): Flow<List<com.nuvio.tv.domain.model.EpgProgram>>

    // Config
    suspend fun getM3uUrl(): String?
    suspend fun getEpgUrl(): String?
    suspend fun saveUrls(m3uUrl: String, epgUrl: String)

    // Favorites
    suspend fun getFavorites(): List<String>
    suspend fun isFavorite(channelId: String): Boolean
    suspend fun toggleFavorite(channelId: String)
}
