@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Icons (Unicode for TV-safe display) ────────────────────────────────────
private val STAR_FILLED = "\u2605" // ★
private val STAR_EMPTY = "\u2606"  // ☆

// ─── Format helpers ─────────────────────────────────────────────────────────

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun Long.toTimeString(): String = timeFormat.format(Date(this))

private fun EpgProgram.progress(): Float {
    val now = System.currentTimeMillis()
    val total = endTime - startTime
    if (total <= 0) return 0f
    val elapsed = now - startTime
    return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun EpgProgram.remainingSec(): Int {
    val now = System.currentTimeMillis()
    val remaining = endTime - now
    return (remaining / 1_000).toInt().coerceAtLeast(0)
}

// ─── IptvScreen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IptvScreen(
    onChannelClick: (TvChannel) -> Unit,
    onSetupClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    viewModel: IptvViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // ── Diagnóstico + auto-select ────────────────────────────────────────
    LaunchedEffect(Unit) {
        Log.d("IptvDiag", "isConfigured=${state.isConfigured} " +
                "channels=${state.channels.size} " +
                "groups=${state.groups.size} " +
                "selectedGroup=${state.selectedGroup} " +
                "focusedChannelId=${state.focusedChannelId}")
    }
    // Auto-select primeiro canal quando não há foco e há canais disponíveis
    LaunchedEffect(state.channels.size, state.focusedChannelId) {
        if (state.channels.isNotEmpty() && state.focusedChannelId == null) {
            viewModel.onEvent(IptvEvent.FocusChannel(state.channels.first().id))
        }
    }

    if (!state.isConfigured && !state.isLoading && state.channels.isEmpty()) {
        IptvEmptyState(onSetupClick = onSetupClick)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .onPreviewKeyEvent { event ->
                Log.d("IptvKey", "key=${event.key} type=${event.type}")
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Menu -> {
                        onMenuClick()
                        true
                    }
                    Key.DirectionDown -> {
                        when (state.focusZone) {
                            FocusZone.SIDEBAR -> {
                                val totalItems = 1 + state.groups.size
                                val next = (state.focusIndex + 1).coerceAtMost(totalItems - 1)
                                if (next != state.focusIndex) {
                                    itHandleSidebarFocus(state, viewModel, next)
                                }
                            }
                            FocusZone.SEARCH -> {
                                // DOWN in search → first channel
                                if (state.channels.isNotEmpty()) {
                                    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.LIST, 0))
                                    viewModel.onEvent(IptvEvent.FocusChannel(state.channels[0].id))
                                }
                            }
                            FocusZone.LIST -> {
                                val maxIdx = state.channels.size - 1
                                val next = (state.focusIndex + 1).coerceAtMost(maxIdx)
                                if (next != state.focusIndex) {
                                    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.LIST, next))
                                    viewModel.onEvent(IptvEvent.FocusChannel(state.channels[next].id))
                                }
                            }
                            FocusZone.EPG -> {
                                val maxIdx = state.selectedPrograms.size - 1
                                val next = (state.focusIndex + 1).coerceAtMost(maxIdx)
                                if (next != state.focusIndex) {
                                    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.EPG, next))
                                }
                            }
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        when (state.focusZone) {
                            FocusZone.SIDEBAR -> {
                                val prev = (state.focusIndex - 1).coerceAtLeast(0)
                                if (prev != state.focusIndex) {
                                    itHandleSidebarFocus(state, viewModel, prev)
                                }
                            }
                            FocusZone.SEARCH -> {
                                // UP in search → stays (already at top)
                            }
                            FocusZone.LIST -> {
                                if (state.focusIndex == 0) {
                                    // UP at first channel → search bar
                                    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.SEARCH, 0))
                                    viewModel.onEvent(IptvEvent.FocusChannel(null))
                                } else {
                                    val prev = (state.focusIndex - 1).coerceAtLeast(0)
                                    if (prev != state.focusIndex) {
                                        viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.LIST, prev))
                                        viewModel.onEvent(IptvEvent.FocusChannel(state.channels[prev].id))
                                    }
                                }
                            }
                            FocusZone.EPG -> {
                                val prev = (state.focusIndex - 1).coerceAtLeast(0)
                                if (prev != state.focusIndex) {
                                    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.EPG, prev))
                                }
                            }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        val prev = FocusState.prevZone(state.focusZone, state.isSearchActive)
                        if (prev != state.focusZone) {
                            val savedIndex = state.lastZoneIndex[prev] ?: 0
                            viewModel.onEvent(IptvEvent.MoveFocus(prev, savedIndex))
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        val next = FocusState.nextZone(state.focusZone)
                        if (next != state.focusZone) {
                            val savedIndex = state.lastZoneIndex[next] ?: 0
                            viewModel.onEvent(IptvEvent.MoveFocus(next, savedIndex))
                        }
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        itHandleEnter(state, viewModel, onChannelClick)
                        true
                    }
                    else -> false
                }
            }
    ) {
        // ── Split screen: Sidebar + Channels + Preview ───────────────────
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

            // ── Left Sidebar: Categories ─────────────────────────────────
            CategorySidebar(
                groups = state.groups,
                selectedGroup = state.selectedGroup,
                focusZone = state.focusZone,
                focusIndex = state.focusIndex,
                onGroupClick = { group ->
                    viewModel.onEvent(
                        if (group == state.selectedGroup) IptvEvent.ClearGroup
                        else IptvEvent.SelectGroup(group)
                    )
                    // Keep focus on the selected category, not "Todos"
                    val newIndex = if (group.isEmpty()) 0
                                   else state.groups.indexOf(group) + 1
                    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.SIDEBAR, newIndex))
                },
                onFocusChange = { idx ->
                    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.SIDEBAR, idx))
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(180.dp)
            )

            // ── Channel list + Search ────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .fillMaxHeight()
                    .background(NuvioTheme.colors.Background)
            ) {
                // Search bar at top
                IptvSearchBar(
                    searchQuery = state.searchQuery,
                    isSearchActive = state.isSearchActive,
                    isFocused = state.focusZone == FocusZone.SEARCH,
                    onSearchQueryChange = { query -> viewModel.onEvent(IptvEvent.SetSearchQuery(query)) },
                    onSearchFocusChange = { focused: Boolean ->
                        if (focused != state.isSearchActive) {
                            viewModel.onEvent(IptvEvent.ToggleSearch)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 12.dp, top = 6.dp)
                )

                // Channel list
                ChannelList(
                    channels = state.channels,
                    currentPrograms = state.currentPrograms,
                    favorites = state.favorites,
                    focusZone = state.focusZone,
                    focusIndex = state.focusIndex,
                    onChannelClick = onChannelClick,
                    onToggleFavorite = { channelId ->
                        viewModel.onEvent(IptvEvent.ToggleFavorite(channelId))
                    },
                    onFocusChannel = { channelId ->
                        viewModel.onEvent(IptvEvent.FocusChannel(channelId))
                    },
                    onFocusChange = { idx ->
                        viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.LIST, idx))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            // ── Detail Card: Player + EPG ────────────────────────────────
            val focusedChannel = state.focusedChannelId?.let { id ->
                state.channels.find { it.id == id }
            }
            val currentProgram = focusedChannel?.let { state.currentPrograms[it.id] }
            val nextProgram = currentProgram?.let { curr ->
                val idx = state.selectedPrograms.indexOfFirst { it.startTime == curr.startTime }
                if (idx >= 0 && idx + 1 < state.selectedPrograms.size) state.selectedPrograms[idx + 1] else null
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(NuvioTheme.colors.Background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Player
                    IptvPlayerPane(
                        channel = focusedChannel,
                        currentProgram = currentProgram,
                        nextProgram = nextProgram,
                        isLive = focusedChannel?.let { it.id in state.currentPrograms } == true,
                        isEpgFocused = state.focusZone == FocusZone.EPG,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // EPG Schedule
                    EpgSchedulePane(
                        programs = state.selectedPrograms,
                        nextProgram = nextProgram,
                        focusZone = state.focusZone,
                        focusIndex = state.focusIndex,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }

        // ── Footer ───────────────────────────────────────────────────────
        IptvFooter()
    }
}

// ─── Key handler helpers ─────────────────────────────────────────────────────

private fun itHandleSidebarFocus(
    state: IptvUiState,
    viewModel: IptvViewModel,
    newIndex: Int
) {
    val group = if (newIndex == 0) "" else state.groups[newIndex - 1]
    viewModel.onEvent(
        if (group.isEmpty()) IptvEvent.ClearGroup
        else IptvEvent.SelectGroup(group)
    )
    viewModel.onEvent(IptvEvent.MoveFocus(FocusZone.SIDEBAR, newIndex))
}

private fun itHandleEnter(
    state: IptvUiState,
    viewModel: IptvViewModel,
    onChannelClick: (TvChannel) -> Unit
) {
    when (state.focusZone) {
        FocusZone.SIDEBAR -> {
            if (state.focusIndex < 1 + state.groups.size) {
                val group = if (state.focusIndex == 0) "" else state.groups[state.focusIndex - 1]
                viewModel.onEvent(
                    if (group.isEmpty()) IptvEvent.ClearGroup
                    else IptvEvent.SelectGroup(group)
                )
            }
        }
        FocusZone.LIST -> {
            val idx = state.focusIndex
            if (idx >= 0 && idx < state.channels.size) {
                onChannelClick(state.channels[idx])
            }
        }
        FocusZone.SEARCH -> {
            viewModel.onEvent(IptvEvent.ToggleSearch)
        }
        else -> {}
    }
}

// ─── CategorySidebar ────────────────────────────────────────────────────────

@Composable
private fun CategorySidebar(
    groups: List<String>,
    selectedGroup: String?,
    focusZone: FocusZone,
    focusIndex: Int,
    onGroupClick: (String) -> Unit,
    onFocusChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalItems = listOf(null) + groups // null = "Todos"
    val isInZone = focusZone == FocusZone.SIDEBAR
    val listState = rememberLazyListState()

    // Auto-scroll para a categoria focada (dentro da LazyColumn)
    LaunchedEffect(if (isInZone) focusIndex else -1) {
        if (isInZone && focusIndex > 0 && focusIndex - 1 < groups.size) {
            listState.animateScrollToItem(focusIndex - 1)
        }
    }

    Column(
        modifier = modifier
            .background(NuvioTheme.colors.Background)
            .padding(vertical = 4.dp)
    ) {
        // "Todos"
        CategoryItem(
            label = "Todos",
            isFocused = isInZone && focusIndex == 0,
            isSelectedGroup = selectedGroup == null,
            onClick = { onGroupClick("") },
            onFocusChange = { focused ->
                if (focused) onGroupClick("")
                onFocusChange(0)
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(groups, key = { _, g -> g }) { dataIdx, group ->
                CategoryItem(
                    label = group,
                    isFocused = isInZone && focusIndex == dataIdx + 1,
                    isSelectedGroup = group == selectedGroup,
                    onClick = { onGroupClick(group) },
                    onFocusChange = { focused ->
                        if (focused) onGroupClick(group)
                        onFocusChange(dataIdx + 1)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    label: String,
    isFocused: Boolean,
    isSelectedGroup: Boolean,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    val textColor = when {
        isSelectedGroup -> NuvioTheme.colors.TextPrimary
        else -> NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f)
    }
    val textWeight = if (isSelectedGroup) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFocused) Modifier.border(
                    1.dp, NuvioTheme.colors.FocusRing, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = null
            )
            .onFocusChanged { onFocusChange(it.isFocused) }
            .padding(start = if (isSelectedGroup) 8.dp else 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ── Left accent bar para categoria selecionada ────────────────
            if (isSelectedGroup) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NuvioTheme.colors.FocusRing)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = textWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── ChannelList ────────────────────────────────────────────────────────────

@Composable
private fun ChannelList(
    channels: List<TvChannel>,
    currentPrograms: Map<String, EpgProgram>,
    favorites: Set<String>,
    focusZone: FocusZone,
    focusIndex: Int,
    onChannelClick: (TvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onFocusChannel: (String?) -> Unit,
    onFocusChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isInZone = focusZone == FocusZone.LIST
    val listState = rememberLazyListState()

    // Auto-scroll para o item focado
    LaunchedEffect(if (isInZone) focusIndex else -1) {
        if (isInZone && focusIndex >= 0 && focusIndex < channels.size) {
            listState.animateScrollToItem(focusIndex)
        }
    }

    // Notify ViewModel when focus changes within LIST zone only
    // Use just focusIndex as key (no conditional -1) so leaving LIST zone
    // does NOT clear EPG data — the programs persist when user navigates RIGHT to EPG
    LaunchedEffect(focusIndex) {
        if (isInZone && focusIndex >= 0 && focusIndex < channels.size) {
            onFocusChannel(channels[focusIndex].id)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(NuvioTheme.colors.Background)
            .padding(start = 8.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(channels, key = { index, c -> "${index}_${c.id}" }) { index, channel ->
            val program = currentPrograms[channel.id]
            val isFavorite = channel.id in favorites
            val isFocused = isInZone && index == focusIndex

            ChannelCard(
                channel = channel,
                index = index + 1,
                isFocused = isFocused,
                isFavorite = isFavorite,
                currentProgram = program,
                onClick = { onChannelClick(channel) },
                onToggleFavorite = { onToggleFavorite(channel.id) },
                onFocusChange = { if (it) onFocusChange(index) }
            )
        }
    }
}

// ─── ChannelCard ────────────────────────────────────────────────────────────

@Composable
private fun ChannelCard(
    channel: TvChannel,
    index: Int,
    isFocused: Boolean,
    isFavorite: Boolean,
    currentProgram: EpgProgram?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    var longPressTriggered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isFocused) Modifier.border(
                    1.dp, NuvioTheme.colors.FocusRing, RoundedCornerShape(6.dp)
                ) else Modifier
            )
            .combinedClickable(
                onClick = {
                    if (!longPressTriggered) onClick()
                    longPressTriggered = false
                },
                onLongClick = {
                    longPressTriggered = true
                    onToggleFavorite()
                }
            )
            .onFocusChanged { onFocusChange(it.isFocused) }
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Channel number ────────────────────────────────────────────
            Text(
                text = index.toString().padStart(2, '0'),
                color = NuvioTheme.colors.TextSecondary.copy(alpha = if (isFocused) 0.6f else 0.25f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(22.dp),
                textAlign = TextAlign.Center
            )

            // ── Logo (32dp) ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo != null) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(2.dp)
                            .alpha(if (isFocused) 1f else 0.5f),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = if (isFocused) 0.7f else 0.4f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // ── Name + program ────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = if (isFocused) NuvioTheme.colors.TextPrimary
                    else NuvioTheme.colors.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentProgram != null) {
                    Text(
                        text = currentProgram.title,
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // ── Program time ─────────────────────────────────────────────
            if (currentProgram != null) {
                Text(
                    text = currentProgram.startTime.toTimeString(),
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = if (isFocused) 0.5f else 0.25f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // ── Favorite star (rightmost) ────────────────────────────────
            Text(
                text = if (isFavorite) STAR_FILLED else STAR_EMPTY,
                color = if (isFavorite) NuvioTheme.colors.FocusRing
                else NuvioTheme.colors.TextSecondary.copy(alpha = 0.2f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Empty state ────────────────────────────────────────────────────────────

@Composable
private fun IptvEmptyState(onSetupClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TV ao Vivo",
                color = NuvioTheme.colors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Nenhuma playlist configurada",
                color = NuvioTheme.colors.TextSecondary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onSetupClick) {
                Text("Configurar playlist", color = NuvioTheme.colors.TextPrimary)
            }
        }
    }
}
