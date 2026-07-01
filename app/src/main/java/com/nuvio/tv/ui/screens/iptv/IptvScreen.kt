@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
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
private val STAR_EMPTY = "\u2606" // ☆
private val LIVE_DOT = "\u25CF" // ●

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

private fun EpgProgram.remainingMinutes(): Int {
    val now = System.currentTimeMillis()
    val remaining = endTime - now
    return (remaining / 60_000).toInt().coerceAtLeast(0)
}

// ─── IptvScreen ─────────────────────────────────────────────────────────────

@Composable
fun IptvScreen(
    onChannelClick: (TvChannel) -> Unit,
    onSetupClick: () -> Unit,
    viewModel: IptvViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (!state.isConfigured && !state.isLoading && state.channels.isEmpty()) {
        IptvEmptyState(onSetupClick = onSetupClick)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        IptvHeader(
            onRefresh = { viewModel.onEvent(IptvEvent.Refresh) },
            onSetup = onSetupClick
        )

        // ── Split screen: Sidebar + Channels + Preview ───────────────────
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

            // ── Left Sidebar: Categories ─────────────────────────────────
            CategorySidebar(
                groups = state.groups,
                selectedGroup = state.selectedGroup,
                onGroupClick = { group ->
                    viewModel.onEvent(
                        if (group == state.selectedGroup) IptvEvent.ClearGroup
                        else IptvEvent.SelectGroup(group)
                    )
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(220.dp)
            )

            // Vertical divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f))
            )

            // ── Channel list ─────────────────────────────────────────────
            ChannelList(
                channels = state.channels,
                currentPrograms = state.currentPrograms,
                favorites = state.favorites,
                onChannelClick = onChannelClick,
                onToggleFavorite = { channelId ->
                    viewModel.onEvent(IptvEvent.ToggleFavorite(channelId))
                },
                onFocusChannel = { channelId ->
                    viewModel.onEvent(IptvEvent.FocusChannel(channelId))
                },
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            )

            // Panel divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f))
            )

            // ── Mini ExoPlayer + EPG ─────────────────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Player — usa o canal focado
                val focusedChannel = state.focusedChannelId?.let { id ->
                    state.channels.find { it.id == id }
                }
                IptvPlayerPane(
                    channel = focusedChannel,
                    currentProgram = focusedChannel?.let { state.currentPrograms[it.id] },
                    isLive = focusedChannel?.let { it.id in state.currentPrograms } == true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                // EPG Schedule
                EpgSchedulePane(
                    programs = state.selectedPrograms,
                    currentPrograms = state.currentPrograms,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─── IptvHeader ─────────────────────────────────────────────────────────────

@Composable
private fun IptvHeader(
    onRefresh: () -> Unit,
    onSetup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TV ao Vivo",
            color = NuvioTheme.colors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh) {
                Text("Atualizar", color = NuvioTheme.colors.TextPrimary)
            }
            Button(onClick = onSetup) {
                Text("Configurar", color = NuvioTheme.colors.TextPrimary)
            }
        }
    }
}

// ─── CategorySidebar ────────────────────────────────────────────────────────

@Composable
private fun CategorySidebar(
    groups: List<String>,
    selectedGroup: String?,
    onGroupClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(NuvioTheme.colors.Background)
            .padding(vertical = 8.dp)
    ) {
        // ── Title "CANAIS" ──────────────────────────────────────────────
        Text(
            text = "CANAIS",
            color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.3f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.25.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        // "Todos" sempre no topo
        CategoryItem(
            label = "Todos",
            isSelected = selectedGroup == null,
            onClick = { onGroupClick("") }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(groups, key = { it }) { group ->
                CategoryItem(
                    label = group,
                    isSelected = group == selectedGroup,
                    onClick = { onGroupClick(group) }
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected)
        NuvioTheme.colors.BackgroundCard.copy(alpha = 0.3f)
    else
        Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 0.dp else 0.dp,
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { /* hover state not needed for TV click */ },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selected indicator bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(
                        if (isSelected) NuvioTheme.colors.Primary.copy(alpha = 0.8f)
                        else Color.Transparent,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isSelected) NuvioTheme.colors.TextPrimary
                else NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        // Invisible clickable overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { onClick() }
        )
    }
}

// ─── ChannelList ────────────────────────────────────────────────────────────

@Composable
private fun ChannelList(
    channels: List<TvChannel>,
    currentPrograms: Map<String, EpgProgram>,
    favorites: Set<String>,
    onChannelClick: (TvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onFocusChannel: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var focusedIndex by remember { mutableIntStateOf(-1) }

    LazyColumn(
        modifier = modifier
            .background(NuvioTheme.colors.Background)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(channels, key = { index, c -> "${index}_${c.id}" }) { index, channel ->
            val program = currentPrograms[channel.id]
            val isFavorite = channel.id in favorites

            // Notify ViewModel when focus changes
            LaunchedEffect(focusedIndex) {
                onFocusChannel(if (focusedIndex >= 0 && focusedIndex < channels.size) channels[focusedIndex].id else null)
            }

            ChannelCard(
                channel = channel,
                index = index + 1,
                isFocused = index == focusedIndex,
                isFavorite = isFavorite,
                currentProgram = program,
                onClick = { onChannelClick(channel) },
                onToggleFavorite = { onToggleFavorite(channel.id) },
                onFocusChange = { isFocused ->
                    if (isFocused) focusedIndex = index
                }
            )
        }
    }
}

// ─── ChannelCard (Demo style) ───────────────────────────────────────────────

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
    val bgColor = when {
        isFocused -> NuvioTheme.colors.BackgroundCard.copy(alpha = 0.8f)
        else -> Color.Transparent
    }

    val borderColor = if (isFocused)
        NuvioTheme.colors.Primary.copy(alpha = 0.4f)
    else
        Color.Transparent

    val focusAlpha = if (isFocused) 1f else 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { onFocusChange(it.isFocused) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Channel number ────────────────────────────────────────────
            Text(
                text = index.toString().padStart(2, '0'),
                color = NuvioTheme.colors.TextSecondary.copy(alpha = if (isFocused) 0.6f else 0.25f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(22.dp),
                textAlign = TextAlign.Center
            )

            // ── Star (favorite toggle) ─────────────────────────────────────
            Card(
                onClick = onToggleFavorite,
                colors = CardDefaults.colors(
                    containerColor = Color.Transparent
                ),
                shape = CardDefaults.shape(shape = RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = if (isFavorite) STAR_FILLED else STAR_EMPTY,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = if (isFavorite) NuvioTheme.colors.FocusRing
                    else NuvioTheme.colors.TextSecondary.copy(alpha = 0.2f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ── Logo (56dp como no mockup) ────────────────────────────────
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo != null) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(2.dp)
                            .alpha(focusAlpha),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = focusAlpha * 0.7f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Name + Progress + Program ────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = if (isFocused) NuvioTheme.colors.TextPrimary
                    else NuvioTheme.colors.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Current program + progress inline
                if (currentProgram != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentProgram.title,
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(currentProgram.progress())
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isFocused) NuvioTheme.colors.Primary
                                    else NuvioTheme.colors.FocusRing.copy(alpha = 0.6f)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ── Program time ─────────────────────────────────────────────
            if (currentProgram != null) {
                Text(
                    text = currentProgram.startTime.toTimeString(),
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = if (isFocused) 0.5f else 0.25f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Invisible clickable overlay for play
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { onClick() }
        )
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
