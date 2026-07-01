package com.nuvio.tv.ui.screens.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.domain.repository.IptvRepository
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
    val selectedPrograms: List<EpgProgram> = emptyList(), // EPG do canal focado
    val focusedChannelId: String? = null
)

sealed class IptvEvent {
    data class SelectGroup(val group: String) : IptvEvent()
    data object ClearGroup : IptvEvent()
    data object Refresh : IptvEvent()
    data object Retry : IptvEvent()
    data class ToggleFavorite(val channelId: String) : IptvEvent()
    data class FocusChannel(val channelId: String?) : IptvEvent()
}

private const val FAVORITES_GROUP = "⭐ Favoritos"

@HiltViewModel
class IptvViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    private val selectedGroup = MutableStateFlow<String?>(null)
    private val _events = MutableStateFlow<IptvEvent?>(null)
    private val _currentPrograms = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    private val _updateTick = MutableStateFlow(0L)
    private val _focusedChannelId = MutableStateFlow<String?>(null)
    private val _selectedPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())

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

            val allGroups = buildList {
                if (favs.isNotEmpty()) add(FAVORITES_GROUP)
                addAll(groups)
            }

            val filtered = when {
                group == null -> channels
                group == FAVORITES_GROUP -> channels.filter { it.id in favs }
                else -> channels.filter { it.group == group }
            }

            Triple(filtered, allGroups, group)
        },
        _selectedPrograms
    ) { (channels, allGroups, group), epg ->
        IptvUiState(
            isLoading = false,
            channels = channels,
            groups = allGroups,
            selectedGroup = group,
            favorites = _favorites.value,
            isConfigured = true, // channels list non-empty implies configured
            currentPrograms = _currentPrograms.value,
            selectedPrograms = epg,
            focusedChannelId = _focusedChannelId.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IptvUiState())

    init {
        viewModelScope.launch {
            val m3uUrl = repository.getM3uUrl()
            if (m3uUrl != null) {
                refresh()
                startProgramWatcher()
                startFavoriteWatcher()
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
                refreshCurrentPrograms()
                _updateTick.value = System.currentTimeMillis()
                delay(30_000)
            }
        }
    }

    private suspend fun refreshCurrentPrograms() {
        try {
            val channels = repository.getChannels().first()
            val programs = mutableMapOf<String, EpgProgram>()
            for (channel in channels) {
                val prog = repository.getCurrentProgram(channel.id)
                if (prog != null) {
                    programs[channel.id] = prog
                }
            }
            _currentPrograms.value = programs
        } catch (_: Exception) { }
    }

    fun onEvent(event: IptvEvent) {
        when (event) {
            is IptvEvent.SelectGroup -> selectedGroup.value = event.group
            is IptvEvent.ClearGroup -> selectedGroup.value = null
            is IptvEvent.Refresh -> refresh()
            is IptvEvent.Retry -> refresh()
            is IptvEvent.ToggleFavorite -> viewModelScope.launch {
                repository.toggleFavorite(event.channelId)
                _favorites.value = repository.getFavorites().toSet()
                _updateTick.value = System.currentTimeMillis()
            }
            is IptvEvent.FocusChannel -> viewModelScope.launch {
                _focusedChannelId.value = event.channelId
                if (event.channelId != null) {
                    _selectedPrograms.value = repository.getProgramsByChannel(event.channelId).first()
                } else {
                    _selectedPrograms.value = emptyList()
                }
            }
        }
    }

    fun saveUrls(m3uUrl: String, epgUrl: String) {
        viewModelScope.launch {
            repository.saveUrls(m3uUrl, epgUrl)
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                repository.refreshPlaylist()
                repository.refreshEpg()
                refreshCurrentPrograms()
                _favorites.value = repository.getFavorites().toSet()
                android.util.Log.d("IptvVM", "refresh done, favs=${_favorites.value.size}")
                _updateTick.value = System.currentTimeMillis()
            } catch (e: Exception) {
                android.util.Log.e("IptvVM", "refresh failed", e)
            }
        }
    }
}
