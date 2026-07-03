package com.nuvio.tv.data.repository

import com.nuvio.tv.core.iptv.EpgParser
import com.nuvio.tv.core.iptv.M3uParser
import com.nuvio.tv.data.local.ConfigDao
import com.nuvio.tv.data.local.EpgDao
import com.nuvio.tv.data.local.EpgProgramEntity
import com.nuvio.tv.data.local.FavoriteDao
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.domain.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvRepositoryImpl @Inject constructor(
    private val configDao: ConfigDao,
    private val favoriteDao: FavoriteDao,
    private val epgDao: EpgDao,
    private val okHttpClient: OkHttpClient
) : IptvRepository {

    private val _channels = MutableStateFlow<List<TvChannel>>(emptyList())

    override fun getChannels(): Flow<List<TvChannel>> = _channels.asStateFlow()

    override fun getGroups(): Flow<List<String>> = _channels.map { channels ->
        channels.mapNotNull { it.group }.distinct().sorted()
    }

    override fun getChannelsByGroup(group: String): Flow<List<TvChannel>> = _channels.map { channels ->
        channels.filter { it.group == group }
    }

    override suspend fun refreshPlaylist() {
        val m3uUrl = configDao.getM3uUrl() ?: return
        android.util.Log.d("IptvRepo", "refreshPlaylist: url=$m3uUrl")
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(m3uUrl).build()
                val response = okHttpClient.newCall(request).execute()
                android.util.Log.d("IptvRepo", "HTTP ${response.code} ${response.message}")
                val body = response.body?.string() ?: return@withContext
                android.util.Log.d("IptvRepo", "body size=${body.length}")

                val channels = M3uParser.parse(body)
                android.util.Log.d("IptvRepo", "parsed ${channels.size} channels")
                _channels.value = channels

                configDao.updateRefreshTimestamp(System.currentTimeMillis())
            } catch (e: Exception) {
                android.util.Log.e("IptvRepo", "refreshPlaylist failed", e)
            }
        }
    }

    override suspend fun refreshEpg() {
        val epgUrl = configDao.getEpgUrl() ?: return
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(epgUrl).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body ?: return@withContext

                // Parse XMLTV em streaming
                val programs = EpgParser.parse(body.byteStream())

                // Clear old + insert new
                epgDao.clearAll()

                // Inserir em lotes para não travar o banco
                programs.chunked(500).forEach { batch ->
                    epgDao.insertAll(batch.map { EpgProgramEntity.fromDomain(it) })
                }

                // Limpar programas que já terminaram há mais de 24h
                val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                epgDao.deleteOlderThan(threshold)
            } catch (e: Exception) {
                // Log would go here
            }
        }
    }

    override suspend fun getCurrentProgram(tvgId: String): EpgProgram? {
        return epgDao.getCurrentProgram(tvgId)?.toDomain()
    }

    override fun getProgramsByChannel(tvgId: String): Flow<List<EpgProgram>> {
        return epgDao.getProgramsByChannel(tvgId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getM3uUrl(): String? = configDao.getM3uUrl()
    override suspend fun getEpgUrl(): String? = configDao.getEpgUrl()

    override suspend fun saveUrls(m3uUrl: String, epgUrl: String) {
        configDao.upsertConfig(
            com.nuvio.tv.data.local.ConfigEntity(
                id = 1,
                m3uUrl = m3uUrl,
                epgUrl = epgUrl,
                lastRefreshTs = 0L
            )
        )
    }

    override suspend fun getFavorites(): List<String> = favoriteDao.getAll()

    override suspend fun isFavorite(channelId: String): Boolean = favoriteDao.isFavorite(channelId)

    override suspend fun toggleFavorite(channelId: String) {
        if (favoriteDao.isFavorite(channelId)) {
            favoriteDao.remove(channelId)
        } else {
            favoriteDao.add(com.nuvio.tv.data.local.FavoriteEntity(channelId))
        }
    }
}
