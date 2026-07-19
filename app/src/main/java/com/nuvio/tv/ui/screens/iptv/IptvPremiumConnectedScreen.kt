@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.iptv

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.LocalIsPlayerFullscreen
import kotlinx.coroutines.delay

/**
 * Premium IPTV screen conectada ao ViewModel real (Hilt).
 *
 * Modo collapsed: preview + rail + EPG grid (navegação vertical).
 * Modo expanded: fullscreen video + HUD + ←→ troca canal.
 *
 * O ExoPlayer é instância ÚNICA, criada aqui e compartilhada
 * entre o preview pane (collapsed) e o fullscreen (expanded).
 */
private enum class FocusSection { BUTTONS, RAIL, GRID }

@Composable
fun IptvPremiumConnectedScreen(
    onChannelPlay: (channelUrl: String, channelName: String, channelLogo: String?) -> Unit,
    onSetupClick: () -> Unit,
    viewModel: IptvViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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

    // ── State local ─────────────────────────────────────────────────────
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
            else -> {}
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

    val currentProgram = focusedChannel?.let { ch ->
        state.gridPrograms[ch.id]
            ?.firstOrNull { prog -> now >= prog.startTime && now < prog.endTime }
    }

    // ── EPG grid data ──────────────────────────────────────────────────
    val windowStart = remember(now) { now - (now % hourMs) }
    val windowEnd = windowStart + 12 * hourMs

    // ══════════════════════════════════════════════════════════════════════
    //  SHARED ExoPlayer — instância única, hoisted do preview pane
    // ══════════════════════════════════════════════════════════════════════

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.run {
                playWhenReady = false
                stop()
                release()
            }
        }
    }

    // Load channel media when focused channel changes
    LaunchedEffect(focusedChannel?.id) {
        val ch = focusedChannel ?: return@LaunchedEffect
        if (ch.url.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(ch.url))
            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPANDED MODE STATE
    // ══════════════════════════════════════════════════════════════════════

    var isExpanded by remember { mutableStateOf(false) }
    val expandedFocusRequester = remember { FocusRequester() }

    // Notify MainActivity to hide sidebar drawer
    val isPlayerFullscreen = LocalIsPlayerFullscreen.current
    LaunchedEffect(isExpanded) {
        isPlayerFullscreen.value = isExpanded
    }

    // Track current channel index for ←→ navigation
    var currentChannelIdx by remember { mutableIntStateOf(0) }

    LaunchedEffect(isExpanded, focusedChannelId, filteredChannels.size) {
        if (isExpanded && focusedChannelId != null) {
            currentChannelIdx = filteredChannels.indexOfFirst { it.id == focusedChannelId }
                .coerceAtLeast(0)
            expandedFocusRequester.requestFocus()
        }
    }

    // ── HUD state (auto-hide) ──────────────────────────────────────────
    var isHudVisible by remember { mutableStateOf(true) }
    var hudTimerReset by remember { mutableStateOf(0L) }

    LaunchedEffect(isHudVisible, hudTimerReset) {
        if (isHudVisible) {
            delay(4000)
            isHudVisible = false
        }
    }

    // Local now-tick (1s granularity)
    var localNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isExpanded) {
        while (isExpanded) {
            localNow = System.currentTimeMillis()
            delay(1000)
        }
    }

    // ── Switch channel helper ──────────────────────────────────────────
    fun switchChannel(delta: Int) {
        val idx = (currentChannelIdx + delta).coerceIn(0, (filteredChannels.size - 1).coerceAtLeast(0))
        if (idx != currentChannelIdx && filteredChannels.isNotEmpty()) {
            currentChannelIdx = idx
            val newCh = filteredChannels[idx]
            focusedChannelId = newCh.id
            viewModel.onEvent(IptvEvent.FocusChannel(newCh.id))
            // Show HUD + reset timer
            isHudVisible = true
            hudTimerReset = System.nanoTime()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LAYOUT
    // ══════════════════════════════════════════════════════════════════════

    Box(modifier = Modifier.fillMaxSize()) {
        if (isExpanded) {
            // ─────────────────────────────────────────────────────────────
            // EXPANDED — fullscreen player
            // ─────────────────────────────────────────────────────────────

            val expandedChannel = filteredChannels.getOrNull(currentChannelIdx)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusable()
                    .focusRequester(expandedFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                switchChannel(-1)
                                true
                            }
                            Key.DirectionRight -> {
                                switchChannel(1)
                                true
                            }
                            Key.DirectionDown -> {
                                // Future: open rail
                                true
                            }
                            Key.Menu -> {
                                // Future: open EPG overlay
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                // Fullscreen video (same ExoPlayer)
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = false
                            setKeepScreenOn(true)
                            isFocusable = false
                            isFocusableInTouchMode = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // HUD overlay
                AnimatedVisibility(
                    visible = isHudVisible,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    val hudProgram = expandedChannel?.let { ch ->
                        state.gridPrograms[ch.id]
                            ?.firstOrNull { prog -> localNow >= prog.startTime && localNow < prog.endTime }
                    }
                    PlayerHud(
                        channelName = expandedChannel?.name ?: "",
                        channelGroup = expandedChannel?.group,
                        currentProgram = hudProgram,
                        nowTick = localNow
                    )
                }

                // Mini card (1s on channel switch)
                var showMiniCard by remember { mutableStateOf(false) }
                LaunchedEffect(currentChannelIdx) {
                    if (isExpanded) {
                        showMiniCard = true
                        delay(1000)
                        showMiniCard = false
                    }
                }
                if (showMiniCard && expandedChannel != null) {
                    MiniCardSwitch(
                        channel = expandedChannel,
                        currentProgram = expandedChannel?.let { ch ->
                            state.currentPrograms[ch.id]
                        }
                    )
                }
            }

            // BACK to collapse
            androidx.activity.compose.BackHandler(enabled = isExpanded) {
                isExpanded = false
            }

        } else {
            // ─────────────────────────────────────────────────────────────
            // COLLAPSED — preview + rail + grid
            // ─────────────────────────────────────────────────────────────

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
                                Key.DirectionUp -> true
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
                                Key.DirectionUp -> false
                                else -> false
                            }
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top zone: preview + rail
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

                        // Preview pane with action buttons overlay
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
                                player = exoPlayer,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top-right: search + settings
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

                    // EPG Grid Compact
                    EpgGridCompact(
                        channels = filteredChannels,
                        gridPrograms = state.gridPrograms,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        now = now,
                        focusedChannelId = focusedChannelId,
                        onChannelPlay = { channel ->
                            // Expand instead of navigating to separate player
                            focusedChannelId = channel.id
                            currentChannelIdx = filteredChannels.indexOfFirst { it.id == channel.id }
                                .coerceAtLeast(0)
                            isExpanded = true
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

                    // Footer
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

                // Search overlay
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
    }
}

// ─── Mini card for channel switching ───────────────────────────────────────

@Composable
private fun MiniCardSwitch(
    channel: TvChannel,
    currentProgram: EpgProgram?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Channel number placeholder
                Box(
                    modifier = Modifier
                        .background(NuvioTheme.colors.Primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = channel.id,
                        color = NuvioTheme.colors.Primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentProgram != null) {
                        Text(
                            text = currentProgram.title,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty state ───────────────────────────────────────────────────────────

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

// ─── Data class auxiliar ──────────────────────────────────────────────────

private data class ConnectedSearchResult(
    val channelName: String,
    val group: String,
    val currentProgram: String?
)

// ─── Search Overlay ──────────────────────────────────────────────────────

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
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
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NuvioTheme.colors.BackgroundCard)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = result.channelName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (result.currentProgram != null) {
                Text(
                    text = result.currentProgram,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ─── ActionIconButton ──────────────────────────────────────────────────────

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusable: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .then(
                if (focusable) Modifier.focusRequester(focusRequester).focusable()
                else Modifier
            )
    ) {
        Card(
            onClick = onClick,
            border = CardDefaults.border(
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                    shape = RoundedCornerShape(8.dp)
                )
            ),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            colors = CardDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.4f),
                focusedContainerColor = NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (focusable) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}
