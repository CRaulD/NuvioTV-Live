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
    private val okHttpClient: OkHttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : IptvRepository {

    private val _channels = MutableStateFlow<List<TvChannel>>(emptyList())
    /** channelTvgId → normalized display name from XMLTV <channel> elements */
    private var _channelNameMap: Map<String, String> = emptyMap()
    /** Reverse: normalized name → channelTvgId */
    private var _nameToTvgId: Map<String, String> = emptyMap()

    override fun getChannels(): Flow<List<TvChannel>> = _channels.asStateFlow()

    override fun getGroups(): Flow<List<String>> = _channels.map { channels ->
        channels.mapNotNull { it.group }.distinct().sorted()
    }

    override fun getChannelsByGroup(group: String): Flow<List<TvChannel>> = _channels.map { channels ->
        channels.filter { it.group == group }
    }

    override suspend fun refreshPlaylist() {
        var m3uUrl = configDao.getM3uUrl() ?: return
        // Bypass m3u4u redirect chain (emulator can't follow Dropbox redirects reliably)
        if (m3uUrl.contains("m3u4u.com")) {
            val finalUrl = resolveM3u4uRedirect(m3uUrl)
            if (finalUrl != null) {
                android.util.Log.d("IptvRepo", "refreshPlaylist: resolved m3u4u redirect to $finalUrl")
                m3uUrl = finalUrl
            }
        }
        android.util.Log.d("IptvRepo", "refreshPlaylist: url=$m3uUrl")

        // Try network first, fall back to local cached file
        val cacheFile = java.io.File(appContext.filesDir, "iptv_playlist_cache.m3u")
        var loaded = false

        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(m3uUrl).build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@withContext
                        android.util.Log.d("IptvRepo", "body size=${body.length}")

                        val channels = M3uParser.parse(body)
                        android.util.Log.d("IptvRepo", "parsed ${channels.size} channels via network")
                        channels.take(5).forEachIndexed { i, ch ->
                            android.util.Log.d("IptvRepo", "  m3u[$i]: id='${ch.id}' name='${ch.name}' group='${ch.group}'")
                        }
                        _channels.value = channels
                        configDao.updateRefreshTimestamp(System.currentTimeMillis())
                        // Cache locally
                        cacheFile.writeText(body)
                        loaded = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("IptvRepo", "network refresh failed, trying local cache", e)
            }
        }

        if (!loaded && cacheFile.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val body = cacheFile.readText()
                    val channels = M3uParser.parse(body)
                    android.util.Log.d("IptvRepo", "parsed ${channels.size} channels from local cache")
                    _channels.value = channels
                } catch (e: Exception) {
                    android.util.Log.e("IptvRepo", "local cache parse failed", e)
                }
            }
        }
    }

    /** Follow redirects from an m3u4u.com URL to resolve the final Dropbox CDN URL. */
    private suspend fun resolveM3u4uRedirect(originalUrl: String): String? {
        // Actually follow the redirect chain to get the fresh Dropbox CDN URL
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(originalUrl)
                    .head()
                    .build()
                // Use a custom client that doesn't auto-follow redirects
                val client = okHttpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                val response = client.newCall(request).execute()
                response.use { resp ->
                    val location = resp.header("Location")
                    android.util.Log.d("IptvRepo", "resolveM3u4uRedirect: status=${resp.code}, location=$location")
                    location
                }
            } catch (e: Exception) {
                android.util.Log.e("IptvRepo", "resolveM3u4uRedirect failed", e)
                null
            }
        }
    }

    override suspend fun refreshEpg() {
        val epgUrl = configDao.getEpgUrl()
        // Try local epg-bridge server first (127.0.0.1 with ADB forward, or 10.0.2.2 emulator host)
        val localCandidates = listOf(
            "http://127.0.0.1:8099/merged-epg.xml",
            "http://10.0.2.2:8099/merged-epg.xml"
        )
        val urlsToTry = if (epgUrl.isNullOrBlank()) {
            localCandidates
        } else {
            listOf(epgUrl) + localCandidates
        }

        for (url in urlsToTry) {
            android.util.Log.d("IptvRepo", "refreshEpg: trying $url")
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    response.use { resp ->
                        val body = resp.body ?: return@withContext

                    // Parse XMLTV em streaming (handle gzip if URL ends with .gz)
                    val rawStream = body.byteStream()
                    val inputStream = if (url.endsWith(".gz", ignoreCase = true)) {
                        java.util.zip.GZIPInputStream(rawStream)
                    } else {
                        rawStream
                    }
                    val epgData = EpgParser.parse(inputStream)

                    if (!epgData.isParseSuccess || epgData.programs.isEmpty()) {
                        android.util.Log.w("IptvRepo", "  parse returned ${epgData.programs.size} progs, trying next URL")
                        return@withContext
                    }

                    _channelNameMap = epgData.channelNames
                    _nameToTvgId = epgData.channelNames.entries.associate { (k, v) -> v to k }

                    android.util.Log.d("IptvRepo", "refreshEpg: parsed ${epgData.programs.size} programs, ${epgData.channelNames.size} channel names from $url")

                    // Diagnostic: time range of parsed programs
                    val minStart = epgData.programs.minOf { it.startTime }
                    val maxEnd = epgData.programs.maxOf { it.endTime }
                    android.util.Log.d("IptvRepo", "  program time range: $minStart to $maxEnd")
                    val now = System.currentTimeMillis()
                    val overlapping = epgData.programs.count { it.startTime <= now && it.endTime >= now }
                    android.util.Log.d("IptvRepo", "  programs overlapping now ($now): $overlapping")
                    epgData.programs.take(3).forEach { p ->
                        android.util.Log.d("IptvRepo", "  sample: ch='${p.channelTvgId}' start=${p.startTime} end=${p.endTime} title='${p.title}'")
                    }

                    // DB operations in NonCancellable to survive screen transitions
                    withContext(kotlinx.coroutines.NonCancellable) {
                        epgDao.clearAll()
                        epgData.programs.chunked(500).forEach { batch ->
                            epgDao.insertAll(batch.map { EpgProgramEntity.fromDomain(it) })
                        }
                        val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                        epgDao.deleteOlderThan(threshold)
                        android.util.Log.d("IptvRepo", "refreshEpg: SUCCESS from $url")
                    }
                } // response.use
            }
            return // success — parsed OK and inserted
            } catch (e: Exception) {
                android.util.Log.d("IptvRepo", "refreshEpg: failed for $url: ${e.message}")
            }
        }
        android.util.Log.w("IptvRepo", "refreshEpg: all URLs failed")
    }

    override suspend fun getCurrentProgram(tvgId: String): EpgProgram? {
        // Try exact match first
        epgDao.getCurrentProgram(tvgId)?.let { return it.toDomain() }
        // Fallback via name bridge
        if (_nameToTvgId.isNotEmpty()) {
            val m3uChannel = _channels.value.find { it.id == tvgId } ?: return null
            val normalizedName = EpgParser.normalizeChannelName(m3uChannel.name)
            val epgId = _nameToTvgId[normalizedName] ?: return null
            return epgDao.getCurrentProgram(epgId)?.toDomain()
        }
        return null
    }

    override fun getProgramsByChannel(tvgId: String): Flow<List<EpgProgram>> {
        return epgDao.getProgramsByChannel(tvgId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getProgramsForChannels(
        tvgIds: List<String>,
        windowStart: Long,
        windowEnd: Long
    ): List<EpgProgram> = withContext(Dispatchers.IO) {
        if (tvgIds.isEmpty()) return@withContext emptyList()

        // 1. Try exact match by ID first
        val exact = epgDao.getProgramsForChannels(tvgIds, windowStart, windowEnd).map { it.toDomain() }
        val matchedIds = exact.map { it.channelTvgId }.distinct().toSet()
        val unmatched = tvgIds.count { it !in matchedIds }

        // 2. If some unmatched, try fallback via name bridge
        if (unmatched > 0 && _nameToTvgId.isNotEmpty()) {
            val channels = _channels.value
            // m3uId → epgId (so we can rewrite channelTvgId back to m3uId)
            val bridgeByEpgId = mutableMapOf<String, String>() // epgId → m3uId

            for (m3uId in tvgIds) {
                if (m3uId in matchedIds) continue
                val m3uChannel = channels.find { it.id == m3uId } ?: continue
                val normalizedName = EpgParser.normalizeChannelName(m3uChannel.name)

                // 2a. Exact name match (existing)
                var epgId = _nameToTvgId[normalizedName]
                if (epgId != null && epgId !in matchedIds) {
                    bridgeByEpgId[epgId] = m3uId
                    continue
                }

                // 2b. Substring fallback: M3U name contains EPG name (or vice versa)
                // Pick the longest match to avoid false positives ("Gloob" vs "Globo")
                var bestEpgId: String? = null
                var bestLen = 0
                for ((epgNormalName, candidateId) in _nameToTvgId) {
                    if (candidateId in matchedIds) continue
                    if (epgNormalName.length < 3 || normalizedName.length < 3) continue
                    if (normalizedName.contains(epgNormalName) || epgNormalName.contains(normalizedName)) {
                        val matchLen = minOf(normalizedName.length, epgNormalName.length)
                        if (matchLen > bestLen) {
                            bestLen = matchLen
                            bestEpgId = candidateId
                        }
                    }
                }
                if (bestEpgId != null && bestEpgId !in matchedIds) {
                    bridgeByEpgId[bestEpgId] = m3uId
                }
            }

            if (bridgeByEpgId.isNotEmpty()) {
                android.util.Log.d("IptvRepo", "name fallback: ${bridgeByEpgId.size} channels bridged via name")
                val nameMatched = epgDao.getProgramsForChannels(
                    bridgeByEpgId.keys.toList(), windowStart, windowEnd
                ).map { it.toDomain() }
                // Rewrite channelTvgId back to M3U id so groupBy matches grid lookups
                val rewritten = nameMatched.map { prog ->
                    val m3uId = bridgeByEpgId[prog.channelTvgId]
                    if (m3uId != null) prog.copy(channelTvgId = m3uId) else prog
                }
                return@withContext exact + rewritten
            }
        }

        exact
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
