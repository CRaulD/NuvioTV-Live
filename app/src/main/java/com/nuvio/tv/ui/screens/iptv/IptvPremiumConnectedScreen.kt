@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Premium IPTV screen conectada ao ViewModel real (Hilt).
 *
 * Layout (mesmo do IptvRailPreviewScreenPremium):
 * ┌──────────────────────────────────────────────────┐
 * │  ▶ Video full-width                     [🔍][⚙] │  IptvPreviewPanePremium (210dp)
 * │     info sobreposta                              │  action buttons overlay
 * ├──────────────────────────────────────────────────┤
 * │  ● Todos  ● Notícias  ● Esportes  ...           │  CategoryRailPremium (100dp)
 * ├──────────────────────────────────────────────────┤
 * │  │ 08:00  09:00  10:00  11:00                   │  EpgGridCompact
 * │  │ 1│CH1│ Café │ Jornal │ Novela │ ...          │
 * ├──────────────────────────────────────────────────┤
 * │  ↑↓ Navigate · OK Select · Menu navigation      │  Footer
 * └──────────────────────────────────────────────────┘
 *
 * Diferenças do demo:
 * - Dados reais do IptvRepository via IptvViewModel
 * - Canais com URLs reais (stream M3U)
 * - Programação real do EPG
 * - Navegação para IptvPlayerScreen ao pressionar OK
 */

/** Seção de foco única — navegação vertical simples entre Rail e Grid. */
private enum class FocusSection { BUTTONS, RAIL, GRID }

@Composable
fun IptvPremiumConnectedScreen(
    onChannelPlay: (channelUrl: String, channelName: String, channelLogo: String?) -> Unit,
    onSetupClick: () -> Unit,
    viewModel: IptvViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val now by viewModel.nowTick.collectAsState()

    // ── Auto-select first channel ────────────────────────────────────────
    LaunchedEffect(state.channels.size, state.focusedChannelId) {
        if (state.channels.isNotEmpty() && state.focusedChannelId == null) {
            viewModel.onEvent(IptvEvent.FocusChannel(state.channels.first().id))
        }
    }

    // ── Empty / not configured ──────────────────────────────────────────
    if (!state.isConfigured && !state.isLoading && state.channels.isEmpty()) {
        IptvPremiumEmptyState(onSetupClick = onSetupClick)
        return
    }

    val hourMs = 60 * 60 * 1000L

    // ── State local (foco Rail ↔ Grid) ──────────────────────────────────
    val railFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    var focusSection by remember { mutableStateOf(FocusSection.RAIL) }
    var focusedChannelId by remember { mutableStateOf(state.focusedChannelId) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    // Sync focusedChannelId from ViewModel
    LaunchedEffect(state.focusedChannelId) {
        if (state.focusedChannelId != null) {
            focusedChannelId = state.focusedChannelId
        }
    }

    // Request rail focus when switching to RAIL section
    LaunchedEffect(focusSection) {
        when (focusSection) {
            FocusSection.RAIL -> railFocusRequester.requestFocus()
            else -> {} // grid e buttons têm foco próprio
        }
    }

    // ── Filter channels by search ──────────────────────────────────────
    val filteredChannels = remember(state.channels, searchQuery) {
        val base = state.channels
        if (searchQuery.isBlank()) base
        else base.filter { ch ->
            ch.name.contains(searchQuery, ignoreCase = true) ||
            state.gridPrograms[ch.id]?.any { prog -> (prog.title ?: "").contains(searchQuery, ignoreCase = true) } == true
        }
    }

    val focusedChannel = filteredChannels.firstOrNull { it.id == focusedChannelId }

    // Current program (now-playing) from grid programs
    val currentProgram = focusedChannel?.let { ch ->
        state.gridPrograms[ch.id]
            ?.firstOrNull { prog -> now >= prog.startTime && now < prog.endTime }
    }

    // ── EPG grid data from ViewModel ────────────────────────────────────
    val windowStart = remember(now) { now - (now % hourMs) }
    val windowEnd = windowStart + 12 * hourMs

    // ── Screen ──────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NuvioTheme.colors.Background,
                        NuvioTheme.colors.BackgroundElevated.copy(alpha = 0.3f),
                        NuvioTheme.colors.Background
                    )
                )
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (focusSection) {
                    FocusSection.BUTTONS -> when (event.key) {
                        Key.DirectionDown -> {
                            focusSection = FocusSection.RAIL
                            railFocusRequester.requestFocus()
                            true
                        }
                        Key.DirectionUp -> true // absorve pra não abrir drawer
                        else -> false
                    }
                    FocusSection.RAIL -> when (event.key) {
                        Key.DirectionUp -> {
                            focusSection = FocusSection.BUTTONS
                            true
                        }
                        Key.DirectionDown -> {
                            focusSection = FocusSection.GRID
                            gridFocusRequester.requestFocus()
                            true
                        }
                        else -> false
                    }
                    FocusSection.GRID -> when (event.key) {
                        Key.DirectionUp -> {
                            // grid gerencia UP interno + onExitUp no primeiro canal
                            false
                        }
                        else -> false
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top zone: preview + rail ────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NuvioTheme.colors.Surface.copy(alpha = 0.5f),
                                NuvioTheme.colors.BackgroundCard.copy(alpha = 0.3f),
                                NuvioTheme.colors.Background
                            )
                        )
                    )
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ─── Preview pane with action button overlay ────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(horizontal = 24.dp, vertical = 0.dp)
                ) {
                    IptvPreviewPanePremium(
                        channel = focusedChannel,
                        currentProgram = currentProgram,
                        now = now,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top-right: search + settings buttons
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionIconButton(
                            icon = Icons.Default.Search,
                            contentDescription = stringResource(R.string.iptv_search_btn),
                            onClick = { showSearch = true },
                            focusable = focusSection == FocusSection.RAIL || focusSection == FocusSection.BUTTONS
                        )
                        ActionIconButton(
                            icon = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.iptv_settings_btn),
                            onClick = { onSetupClick() },
                            focusable = focusSection == FocusSection.RAIL || focusSection == FocusSection.BUTTONS
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category rail (dados reais)
                CategoryRailPremium(
                    groups = state.groups,
                    channels = state.channels,
                    selectedGroup = state.selectedGroup,
                    onGroupClick = { group ->
                        if (group.isEmpty()) viewModel.onEvent(IptvEvent.ClearGroup)
                        else viewModel.onEvent(IptvEvent.SelectGroup(group))
                        focusedChannelId = null
                    },
                    isFocused = !showSearch && focusSection == FocusSection.RAIL,
                    focusRequester = railFocusRequester
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── EPG Grid Compact ────────────────────────────────────────
            EpgGridCompact(
                channels = filteredChannels,
                gridPrograms = state.gridPrograms,
                windowStart = windowStart,
                windowEnd = windowEnd,
                now = now,
                focusedChannelId = focusedChannelId,
                onChannelPlay = { channel ->
                    onChannelPlay(
                        channel.url,
                        channel.name,
                        channel.logo
                    )
                },
                onShiftWindow = { delta ->
                    android.util.Log.d("IptvPremiumConnected", "Shift: $delta")
                },
                onToggleFavorite = { channelId ->
                    viewModel.onEvent(IptvEvent.ToggleFavorite(channelId))
                },
                favorites = state.favorites,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                enabled = !showSearch && focusSection == FocusSection.GRID,
                onExitLeft = { focusSection = FocusSection.RAIL; railFocusRequester.requestFocus() },
                onExitUp = { focusSection = FocusSection.RAIL; railFocusRequester.requestFocus() },
                onChannelFocused = { channelId ->
                    focusedChannelId = channelId
                },
                focusRequester = gridFocusRequester
            )

            // ── Footer ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.5f)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(
                        R.string.iptv_footer_text,
                        stringResource(R.string.iptv_navigate),
                        stringResource(R.string.iptv_select),
                        stringResource(R.string.iptv_hold_to_favorite),
                        stringResource(R.string.iptv_menu_navigation)
                    ),
                    color = NuvioTheme.colors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        // ── Search overlay ──────────────────────────────────────────────
        if (showSearch) {
            val searchResults = filteredChannels
                .map { ch ->
                    ConnectedSearchResult(
                        channelName = ch.name,
                        group = ch.group ?: "",
                        currentProgram = state.gridPrograms[ch.id]
                            ?.firstOrNull { prog -> now >= prog.startTime && now < prog.endTime }
                            ?.title
                    )
                }
            SearchOverlay(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it; focusedChannelId = null },
                results = searchResults,
                onDismiss = { showSearch = false; searchQuery = "" },
                onSelect = { channelName ->
                    focusedChannelId = filteredChannels.firstOrNull { it.name == channelName }?.id
                    showSearch = false
                    searchQuery = ""
                }
            )
        }
    }
}

// ─── Empty state (quando não há playlist configurada) ─────────────────────────

@Composable
private fun IptvPremiumEmptyState(onSetupClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.iptv_empty_state_title),
                color = NuvioTheme.colors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.iptv_empty_state_subtitle),
                color = NuvioTheme.colors.TextSecondary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.tv.material3.Button(onClick = onSetupClick) {
                Text(stringResource(R.string.iptv_empty_state_btn), color = NuvioTheme.colors.TextPrimary)
            }
        }
    }
}

// ─── Data class auxiliar ──────────────────────────────────────────────────────

private data class ConnectedSearchResult(
    val channelName: String,
    val group: String,
    val currentProgram: String?
)

// ─── Search Overlay (c/ resultados) ─────────────────────────────────────────

@Composable
private fun SearchOverlay(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    results: List<ConnectedSearchResult>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    event.key == Key.Back
                ) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(NuvioTheme.colors.BackgroundElevated, RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .width(420.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.iptv_search_btn),
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NuvioTheme.colors.FocusRing),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.iptv_search_placeholder),
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 18.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Results list
                if (results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(results) { result ->
                            ConnectedSearchResultItem(
                                result = result,
                                onSelect = { onSelect(result.channelName) }
                            )
                        }
                    }
                } else if (searchQuery.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.iptv_no_channels_found),
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (results.isNotEmpty()) stringResource(R.string.iptv_channels_found, results.size)
                           else stringResource(R.string.iptv_type_to_search),
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "↑↓ Navegar · OK Selecionar · BACK Voltar",
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.3f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ConnectedSearchResultItem(
    result: ConnectedSearchResult,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f),
            focusedContainerColor = NuvioTheme.colors.FocusRing.copy(alpha = 0.15f)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.dp, NuvioTheme.colors.FocusRing.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.channelName,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.currentProgram != null) {
                    Text(
                        text = result.currentProgram,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = result.group,
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

// ─── Action icon button (ícones vetoriais no overlay do video) ────────────────

@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusable: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = if (!focusable) Modifier.focusProperties { canFocus = false } else Modifier,
        shape = CardDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.12f)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(6.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(6.dp),
            tint = Color.White.copy(alpha = 0.7f)
        )
    }
}
