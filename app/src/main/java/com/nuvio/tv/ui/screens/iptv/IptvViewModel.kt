package com.nuvio.tv.ui.screens.iptv

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.server.IptvConfigServer
import com.nuvio.tv.core.server.IptvConfigState
import com.nuvio.tv.core.server.PendingIptvChange
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.domain.repository.IptvRepository
import com.nuvio.tv.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IptvUiState(
    val isLoading: Boolean = true,
    val channels: List<TvChannel> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val favorites: Set<String> = emptySet(),
    val error: String? = null,
    val isConfigured: Boolean = false,
    val currentPrograms: Map<String, EpgProgram> = emptyMap(),
    val gridPrograms: Map<String, List<EpgProgram>> = emptyMap(),
    val selectedPrograms: List<EpgProgram> = emptyList(),
    val focusedChannelId: String? = null,
    // Focus state (D-pad navigation)
    val focusZone: FocusZone = FocusZone.LIST,
    val focusIndex: Int = 0,
    val lastZoneIndex: Map<FocusZone, Int> = mapOf(
        FocusZone.RAIL to 0,
        FocusZone.SEARCH to 0,
        FocusZone.LIST to 0,
        FocusZone.EPG to 0,
        FocusZone.GRID to 0
    ),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    // QR server state
    val qrServerActive: Boolean = false,
    val qrServerUrl: String? = null,
    val qrServerPort: Int = 8082
)

sealed class IptvEvent {
    data class SelectGroup(val group: String) : IptvEvent()
    data object ClearGroup : IptvEvent()
    data object Refresh : IptvEvent()
    data object Retry : IptvEvent()
    data class ToggleFavorite(val channelId: String) : IptvEvent()
    data class FocusChannel(val channelId: String?) : IptvEvent()
    // Focus & search events
    data class MoveFocus(val zone: FocusZone, val index: Int) : IptvEvent()
    data class SetSearchQuery(val query: String) : IptvEvent()
    data object ToggleSearch : IptvEvent()
}

@HiltViewModel
class IptvViewModel @Inject constructor(
    private val repository: IptvRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val selectedGroup = MutableStateFlow<String?>(null)
    private val _events = MutableStateFlow<IptvEvent?>(null)
    private val _currentPrograms = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    private val _gridPrograms = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    private val _updateTick = MutableStateFlow(0L)
    private val _focusedChannelId = MutableStateFlow<String?>(null)
    private val _selectedPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    private val _qrServerActive = MutableStateFlow(false)
    private val _qrServerUrl = MutableStateFlow<String?>(null)
    private val _qrServerPort = MutableStateFlow(8082)
    private val _focusZone = MutableStateFlow(FocusZone.LIST)
    private val _focusIndex = MutableStateFlow(0)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _m3uConfigured = MutableStateFlow(false)
    private var _programsJob: kotlinx.coroutines.Job? = null
    private val _lastZoneIndex = MutableStateFlow<Map<FocusZone, Int>>(
        mapOf(
            FocusZone.RAIL to 0,
            FocusZone.SEARCH to 0,
            FocusZone.LIST to 0,
            FocusZone.EPG to 0,
            FocusZone.GRID to 0
        )
    )

    private var configServer: IptvConfigServer? = null

    /** EPG auto-refresh interval: 4h stale threshold, 6h periodic check */
    private companion object {
        private const val EPG_STALE_MS = 4 * 60 * 60 * 1000L
        private const val EPG_PERIODIC_MS = 6 * 60 * 60 * 1000L
    }

    private val epgPrefs get() = appContext.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)

    private fun isEpgStale(): Boolean {
        val last = epgPrefs.getLong("last_epg_refresh", 0L)
        return (System.currentTimeMillis() - last) > EPG_STALE_MS
    }

    /** Exposed "now" tick for the UI — DO NOT include in combine. UI collects via collectAsState(). */
    private val _nowTick = MutableStateFlow(System.currentTimeMillis())
    val nowTick: StateFlow<Long> = _nowTick

    val uiState: StateFlow<IptvUiState> = combine(
        combine(
            repository.getChannels(),
            repository.getGroups(),
            selectedGroup,
            _events,
            _updateTick
        ) { channels: List<TvChannel>, groups: List<String>, group: String?, _: IptvEvent?, _: Long ->
            val programs = _currentPrograms.value
            val favs = _favorites.value
            val favoritesGroup = appContext.getString(R.string.iptv_category_favorites)

            val allGroups = buildList {
                if (favs.isNotEmpty()) add(favoritesGroup)
                addAll(groups)
            }

            val filtered = when {
                group == null -> channels
                group == favoritesGroup -> channels.filter { it.favoriteKey in favs }
                else -> channels.filter { it.group == group }
            }

            Triple(filtered, allGroups, group)
        },
        _selectedPrograms,
        _qrServerActive,
        _qrServerUrl,
        _qrServerPort,
        _lastZoneIndex,
        _gridPrograms
    ) { args: Array<Any?> ->
        val (channels, allGroups, group) = args[0] as Triple<List<TvChannel>, List<String>, String?>
        val epg = args[1] as List<EpgProgram>
        val qrActive = args[2] as Boolean
        val qrUrl = args[3] as String?
        val qrPort = args[4] as Int
        val lastIdx = args[5] as Map<FocusZone, Int>
        val gridProgs = args[6] as Map<String, List<EpgProgram>>
        IptvUiState(
            isLoading = false,
            channels = channels,
            groups = allGroups,
            selectedGroup = group,
            favorites = _favorites.value,
            isConfigured = _m3uConfigured.value,
            currentPrograms = _currentPrograms.value,
            gridPrograms = gridProgs,
            selectedPrograms = epg,
            focusedChannelId = _focusedChannelId.value,
            focusZone = _focusZone.value,
            focusIndex = _focusIndex.value,
            lastZoneIndex = lastIdx,
            searchQuery = _searchQuery.value,
            isSearchActive = _isSearchActive.value,
            qrServerActive = qrActive,
            qrServerUrl = qrUrl,
            qrServerPort = qrPort
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IptvUiState())

    init {
        viewModelScope.launch {
            val m3uUrl = repository.getM3uUrl()
            android.util.Log.d("IptvDiag", "init: m3uUrl=${m3uUrl ?: "NULL"}")
            _m3uConfigured.value = m3uUrl != null
            if (m3uUrl != null) {
                refresh()
            }
        }
        startProgramWatcher()
        startFavoriteWatcher()

        // Watch for channels to load, then load grid programs
        viewModelScope.launch {
            repository.getChannels().collect { channels ->
                if (channels.isNotEmpty() && _gridPrograms.value.isEmpty()) {
                    android.util.Log.d("IptvDiag", "channels loaded (${channels.size}), triggering loadGridPrograms")
                    loadGridPrograms()
                }
            }
        }

        // Start now tick (60s cadence, independent of combine)
        viewModelScope.launch {
            while (true) {
                _nowTick.value = System.currentTimeMillis()
                delay(60_000)
            }
        }

        // Periodic EPG auto-refresh (6h, checks staleness)
        viewModelScope.launch {
            while (true) {
                delay(EPG_PERIODIC_MS)
                refreshEpgIfStale()
            }
        }
    }

    private fun startFavoriteWatcher() {
        viewModelScope.launch {
            while (true) {
                _favorites.value = repository.getFavorites().toSet()
                _updateTick.value = System.currentTimeMillis()
                delay(5_000)
            }
        }
    }

    private fun startProgramWatcher() {
        viewModelScope.launch {
            while (true) {
                loadGridPrograms()
                _updateTick.value = System.currentTimeMillis()
                delay(120_000) // refresh grid every 2 minutes
            }
        }
    }

    suspend fun loadGridPrograms() {
        try {
            val channels = repository.getChannels().first()
            val group = selectedGroup.value
            val filtered = when {
                group == null -> channels
                else -> channels.filter { it.group == group }
            }
            if (filtered.isEmpty()) {
                _gridPrograms.value = emptyMap()
                return
            }
            val tvgIds = filtered.map { it.id }
            val now = _nowTick.value
            val hourMs = 60 * 60 * 1000L
            val windowStart = now - (now % hourMs)
            val windowEnd = windowStart + 8 * hourMs // 8-hour window

            val allPrograms = repository.getProgramsForChannels(tvgIds, windowStart, windowEnd)
            android.util.Log.d("IptvDiag", "loadGridPrograms: got ${allPrograms.size} progs for ${tvgIds.size} ids")

            val grouped = allPrograms.groupBy { it.channelTvgId }
            val mapped = filtered.associate { ch ->
                ch.id to (grouped[ch.id] ?: emptyList())
            }
            val withData = mapped.count { it.value.isNotEmpty() }
            android.util.Log.d("IptvDiag", "gridPrograms: ${allPrograms.size} progs → $withData channels mapped")
            _gridPrograms.value = mapped
        } catch (e: Exception) {
            android.util.Log.e("IptvDiag", "loadGridPrograms failed", e)
        }
    }

    fun onEvent(event: IptvEvent) {
        when (event) {
            is IptvEvent.SelectGroup -> {
                selectedGroup.value = event.group
                viewModelScope.launch { loadGridPrograms() }
            }
            is IptvEvent.ClearGroup -> {
                selectedGroup.value = null
                viewModelScope.launch { loadGridPrograms() }
            }
            is IptvEvent.Refresh -> refresh()
            is IptvEvent.Retry -> refresh()
            is IptvEvent.ToggleFavorite -> viewModelScope.launch {
                repository.toggleFavorite(event.channelId)
                _favorites.value = repository.getFavorites().toSet()
                _updateTick.value = System.currentTimeMillis()
            }
            is IptvEvent.FocusChannel -> viewModelScope.launch {
                _focusedChannelId.value = event.channelId
                _programsJob?.cancel()
                if (event.channelId != null) {
                    _programsJob = viewModelScope.launch {
                        val programs = repository.getProgramsByChannel(event.channelId).first()
                        android.util.Log.d("IptvDiag", "programs loaded: ${programs.size} for channel ${event.channelId}")
                        // Log titles for marquee debugging
                        programs.take(5).forEachIndexed { i, p ->
                            android.util.Log.d("IptvDiag", "  epg[$i]: title='${p.title}' len=${p.title.length}")
                        }
                        _selectedPrograms.value = programs
                    }
                } else {
                    _selectedPrograms.value = emptyList()
                }
            }
            is IptvEvent.MoveFocus -> {
                _focusZone.value = event.zone
                _focusIndex.value = event.index
                // Save last index for this zone
                _lastZoneIndex.value = _lastZoneIndex.value + (event.zone to event.index)
                _updateTick.value = System.currentTimeMillis()
                if (event.zone != FocusZone.SEARCH) {
                    _isSearchActive.value = false
                }
            }
            is IptvEvent.SetSearchQuery -> {
                _searchQuery.value = event.query
                _updateTick.value = System.currentTimeMillis()
            }
            is IptvEvent.ToggleSearch -> {
                _isSearchActive.value = !_isSearchActive.value
                if (!_isSearchActive.value) {
                    _searchQuery.value = ""
                    _focusZone.value = FocusZone.LIST
                    _focusIndex.value = 0
                }
                _updateTick.value = System.currentTimeMillis()
            }
        }
    }

    fun saveUrls(m3uUrl: String, epgUrl: String) {
        viewModelScope.launch {
            repository.saveUrls(m3uUrl, epgUrl)
            refresh()
        }
    }

    fun startQrMode(ip: String) {
        stopQrMode()
        val port = 8082
        configServer = IptvConfigServer.startOnAvailablePort(
            context = appContext,
            currentConfigProvider = {
                IptvConfigState(
                    m3uUrl = runCatching { kotlinx.coroutines.runBlocking { repository.getM3uUrl() } }.getOrNull().orEmpty(),
                    epgUrl = runCatching { kotlinx.coroutines.runBlocking { repository.getEpgUrl() } }.getOrNull().orEmpty()
                )
            },
            onChangeProposed = { change ->
                viewModelScope.launch {
                    repository.saveUrls(change.m3uUrl, change.epgUrl)
                    configServer?.confirmChange(change.id)
                    refresh()
                }
            }
        )
        if (configServer != null) {
            _qrServerActive.value = true
            _qrServerUrl.value = "http://$ip:$port"
            _qrServerPort.value = port
        }
    }

    fun stopQrMode() {
        configServer?.stop()
        configServer = null
        _qrServerActive.value = false
        _qrServerUrl.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopQrMode()
    }

    private fun refresh() {
        // Run playlist and EPG refresh independently — one cancellation doesn't block the other
        viewModelScope.launch {
            try {
                repository.refreshPlaylist()
            } catch (e: Exception) {
                android.util.Log.e("IptvVM", "playlist refresh failed", e)
            }
        }
        refreshEpgTask()
    }

    private fun refreshEpgTask() {
        viewModelScope.launch {
            try {
                repository.refreshEpg()
                epgPrefs.edit().putLong("last_epg_refresh", System.currentTimeMillis()).apply()
                loadGridPrograms()
                _favorites.value = repository.getFavorites().toSet()
                android.util.Log.d("IptvVM", "refresh done, favs=${_favorites.value.size}")
                _updateTick.value = System.currentTimeMillis()
            } catch (e: Exception) {
                android.util.Log.e("IptvVM", "epg refresh failed", e)
            }
        }
    }

    /** Refresh EPG only if > 4h since last refresh (auto-refresh path). */
    private suspend fun refreshEpgIfStale() {
        if (!isEpgStale()) return
        android.util.Log.d("IptvVM", "auto epg refresh: stale, downloading...")
        try {
            repository.refreshEpg()
            epgPrefs.edit().putLong("last_epg_refresh", System.currentTimeMillis()).apply()
            loadGridPrograms()
            _updateTick.value = System.currentTimeMillis()
        } catch (e: Exception) {
            android.util.Log.e("IptvVM", "auto epg refresh failed", e)
        }
    }

    /** Force EPG refresh regardless of staleness (manual button / save). */
    fun forceRefreshEpg() {
        refreshEpgTask()
    }
}
