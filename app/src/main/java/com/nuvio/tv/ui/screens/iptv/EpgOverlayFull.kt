package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Constants ─────────────────────────────────────────────────────────────

private val SIDEBAR_WIDTH = 220.dp
private val HOUR_WIDTH = 140.dp           // wider for fullscreen
private val ROW_HEIGHT = 56.dp            // taller rows for text
private val HEADER_HEIGHT = 28.dp
private val DETAIL_PANE_HEIGHT = 100.dp
private val CHANNEL_INFO_WIDTH = 200.dp
private val VISIBLE_ROWS = 6
private val ROW_GAP = 4.dp
private val EPG_SHAPE = RoundedCornerShape(4.dp)
private val GRID_WINDOW_HOURS = 10          // 10 hours visible

private const val HOUR_MS = 60 * 60 * 1000L
private const val HALF_HOUR_MS = 30 * 60 * 1000L

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
    timeZone = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
}
private fun Long.toHourMin(): String = timeFormat.format(Date(this))

// ─── Theme helpers ─────────────────────────────────────────────────────────

private val EPG_BG_ALPHA = 0.7f
private val EPG_CELL_ALPHA = 0.18f

/**
 * Full-screen EPG Overlay — state ③ of the 3-state player.
 *
 * ┌─────────────┬──────────────────────────────────────────────┐
 * │ Sidebar     │  EPG Grid (channels × programs)              │
 * │ categories  │  [Timeline header ── scrolls horizontally]   │
 * │             │  [Channel 1] [prog A] [prog B] ...           │
 * │             │  [Channel 2] [prog C] [prog D] ...           │
 * │             │  ...                                          │
 * ├─────────────┴──────────────────────────────────────────────┤
 * │  Detail pane — focused program info + [▶ Assistir]         │
 * └────────────────────────────────────────────────────────────┘
 */
@Composable
fun EpgOverlayFull(
    channels: List<TvChannel>,
    groups: List<String>,
    selectedGroup: String?,
    gridPrograms: Map<String, List<EpgProgram>>,
    now: Long,
    onGroupSelected: (String?) -> Unit,
    onChannelSelect: (TvChannel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val viewportWidthPx = with(density) { (screenWidthDp - SIDEBAR_WIDTH.value).dp.toPx() }
    val hourWidthPx = with(density) { HOUR_WIDTH.toPx() }

    // Window
    val windowStart = remember(now) { now - (now % HOUR_MS) }
    val windowEnd = windowStart + GRID_WINDOW_HOURS * HOUR_MS
    val windowSpan = windowEnd - windowStart
    val programsAreaWidthPx = (windowSpan.toFloat() / HOUR_MS) * hourWidthPx
    val programsAreaWidthDp: Dp = with(density) { programsAreaWidthPx.toDp() }

    // Filtered channels by group
    val favoritesGroup = remember { "⭐ Favoritos" }
    val filteredChannels = remember(channels, selectedGroup) {
        when (selectedGroup) {
            null -> channels
            favoritesGroup -> channels.filter { it.favoriteKey != null } // simplified
            else -> channels.filter { it.group == selectedGroup }
        }
    }

    // Focus state
    var sidebarHasFocus by remember { mutableStateOf(false) }
    var focusChannelIdx by remember { mutableIntStateOf(0) }
    var focusProgramIdx by remember { mutableIntStateOf(0) }
    var sidebarSelectedIdx by remember { mutableIntStateOf(0) }
    val sidebarFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    val verticalListState = rememberLazyListState()
    val sidebarListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    // Init focus to grid
    LaunchedEffect(Unit) {
        android.util.Log.d("EpgOverlayFull", "Init: groups=${groups.size}, channels=${channels.size}, gridProgramsKeys=${gridPrograms.size}, selectedGroup=$selectedGroup")
        gridFocusRequester.requestFocus()
    }

    // Auto-scroll vertical to focused channel
    LaunchedEffect(focusChannelIdx) {
        if (focusChannelIdx in filteredChannels.indices) {
            verticalListState.animateScrollToItem(focusChannelIdx)
        }
    }

    // Auto-scroll horizontal to focused program
    LaunchedEffect(focusProgramIdx, focusChannelIdx, windowStart) {
        val ch = filteredChannels.getOrNull(focusChannelIdx) ?: return@LaunchedEffect
        val progs = gridPrograms[ch.id].orEmpty()
        val prog = progs.getOrNull(focusProgramIdx) ?: return@LaunchedEffect

        val hourPx = with(density) { HOUR_WIDTH.toPx() }
        val progStartX = ((prog.startTime - windowStart).toFloat() / HOUR_MS) * hourPx

        // Always scroll to keep focused program in the first 25% of viewport
        val targetX = (progStartX - viewportWidthPx * 0.25f).toInt().coerceAtLeast(0)
        horizontalScrollState.animateScrollTo(targetX)
    }

    fun findProgramAtTime(programs: List<EpgProgram>, targetTime: Long): Int {
        if (programs.isEmpty()) return 0
        for (i in programs.indices) {
            val p = programs[i]
            if (p.startTime <= targetTime && p.endTime > targetTime) return i
        }
        var best = 0
        for (i in programs.indices) {
            if (programs[i].startTime <= targetTime) best = i
        }
        return best.coerceIn(0, programs.lastIndex)
    }

    // Focused program detail
    val focusedChannel = filteredChannels.getOrNull(focusChannelIdx)
    val focusedProgram = focusedChannel?.let { ch ->
        gridPrograms[ch.id]?.getOrNull(focusProgramIdx)
    }

    // Build sidebar items
    val sidebarItems = remember(groups) {
        buildList {
            add("Todos")
            addAll(groups)
        }
    }

    // Auto-scroll sidebar to selected category
    LaunchedEffect(sidebarSelectedIdx, sidebarItems.size) {
        if (sidebarSelectedIdx in sidebarItems.indices) {
            sidebarListState.animateScrollToItem(sidebarSelectedIdx)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ═══════════════════════════════════════════════════════════════
            // LEFT SIDEBAR — Categories
            // ═══════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .fillMaxHeight()
                    .background(NuvioTheme.colors.surfaces.panel.copy(alpha = EPG_BG_ALPHA))
                    .onPreviewKeyEvent { event ->
                        if (!sidebarHasFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                sidebarSelectedIdx = (sidebarSelectedIdx - 1).coerceAtLeast(0)
                                true
                            }
                            Key.DirectionDown -> {
                                sidebarSelectedIdx = (sidebarSelectedIdx + 1).coerceAtMost(sidebarItems.lastIndex)
                                true
                            }
                            Key.DirectionRight -> {
                                sidebarHasFocus = false
                                gridFocusRequester.requestFocus()
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                val selected = sidebarItems[sidebarSelectedIdx]
                                val group = if (selected == "Todos") null else selected
                                onGroupSelected(group)
                                focusChannelIdx = 0
                                focusProgramIdx = 0
                                // Update grid programs via callback
                                true
                            }
                            else -> false
                        }
                    }
                    .focusRequester(sidebarFocusRequester)
                    .focusable()
                    .onFocusChanged { sidebarHasFocus = it.isFocused }
            ) {
                // Title
                Text(
                    text = "Categorias",
                    color = NuvioTheme.colors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = sidebarListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(sidebarItems) { item ->
                        val isSelected = when {
                            selectedGroup == null && item == "Todos" -> true
                            selectedGroup == item -> true
                            else -> false
                        }
                        val isFocused = sidebarHasFocus && sidebarItems[sidebarSelectedIdx] == item

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isFocused -> NuvioTheme.colors.FocusBackground.copy(alpha = 0.5f)
                                        isSelected -> NuvioTheme.colors.FocusBackground.copy(alpha = 0.25f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isFocused) Modifier.border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                                        shape = RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = item,
                                color = if (isSelected || isFocused) NuvioTheme.colors.TextPrimary
                                        else NuvioTheme.colors.TextSecondary,
                                fontWeight = if (isSelected || isFocused) FontWeight.Medium else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // MAIN AREA — EPG Grid
            // ═══════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(NuvioTheme.colors.surfaces.background.copy(alpha = EPG_BG_ALPHA))
            ) {
                // Timeline header
                TimelineHeader(
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    scrollState = horizontalScrollState,
                    now = now,
                    modifier = Modifier.fillMaxWidth()
                )

                // Grid rows
                LazyColumn(
                    state = verticalListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (sidebarHasFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> {
                                    if (focusChannelIdx == 0 && filteredChannels.isNotEmpty()) {
                                        // At first channel → move to previous category
                                        val prevIdx = sidebarSelectedIdx - 1
                                        if (prevIdx >= 0) {
                                            sidebarSelectedIdx = prevIdx
                                            val newGroup = sidebarItems[prevIdx]
                                            onGroupSelected(if (newGroup == "Todos") null else newGroup)
                                            focusChannelIdx = 0
                                            focusProgramIdx = 0
                                        }
                                        true
                                    } else {
                                        val currentProgs = filteredChannels.getOrNull(focusChannelIdx)
                                            ?.let { gridPrograms[it.id].orEmpty() } ?: emptyList()
                                        val currentTime = currentProgs.getOrNull(focusProgramIdx)?.startTime ?: now
                                        focusChannelIdx = (focusChannelIdx - 1).coerceAtLeast(0)
                                        val ch = filteredChannels.getOrNull(focusChannelIdx)
                                        if (ch != null) {
                                            val progs = gridPrograms[ch.id].orEmpty()
                                            focusProgramIdx = findProgramAtTime(progs, currentTime)
                                        }
                                        true
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (focusChannelIdx >= filteredChannels.lastIndex.coerceAtLeast(0) && filteredChannels.isNotEmpty()) {
                                        // At last channel → move to next category
                                        val nextIdx = sidebarSelectedIdx + 1
                                        if (nextIdx < sidebarItems.size) {
                                            sidebarSelectedIdx = nextIdx
                                            val newGroup = sidebarItems[nextIdx]
                                            onGroupSelected(if (newGroup == "Todos") null else newGroup)
                                            focusChannelIdx = 0
                                            focusProgramIdx = 0
                                        }
                                        true
                                    } else {
                                        val currentProgs = filteredChannels.getOrNull(focusChannelIdx)
                                            ?.let { gridPrograms[it.id].orEmpty() } ?: emptyList()
                                        val currentTime = currentProgs.getOrNull(focusProgramIdx)?.startTime ?: now
                                        android.util.Log.d("EpgOverlayFull", "Down: from chIdx=$focusChannelIdx progIdx=$focusProgramIdx time=${currentTime.toHourMin()}")
                                        focusChannelIdx = (focusChannelIdx + 1).coerceAtMost(filteredChannels.lastIndex.coerceAtLeast(0))
                                        val ch = filteredChannels.getOrNull(focusChannelIdx)
                                        if (ch != null) {
                                            val progs = gridPrograms[ch.id].orEmpty()
                                            focusProgramIdx = findProgramAtTime(progs, currentTime)
                                        }
                                        true
                                    }
                                }
                                Key.DirectionLeft -> {
                                    // Previous program or move to sidebar
                                    val ch = filteredChannels.getOrNull(focusChannelIdx)
                                    val progs = ch?.let { gridPrograms[it.id].orEmpty() } ?: emptyList()
                                    if (focusProgramIdx > 0 && progs.isNotEmpty()) {
                                        focusProgramIdx = (focusProgramIdx - 1).coerceAtLeast(0)
                                    } else {
                                        sidebarHasFocus = true
                                        sidebarFocusRequester.requestFocus()
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    val ch = filteredChannels.getOrNull(focusChannelIdx)
                                    val progs = ch?.let { gridPrograms[it.id].orEmpty() } ?: emptyList()
                                    if (progs.isNotEmpty()) {
                                        focusProgramIdx = (focusProgramIdx + 1).coerceAtMost(progs.lastIndex)
                                    }
                                    true
                                }
                                Key.DirectionCenter, Key.Enter -> {
                                    // Watch channel
                                    val ch = filteredChannels.getOrNull(focusChannelIdx)
                                    if (ch != null) onChannelSelect(ch)
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusRequester(gridFocusRequester)
                        .focusable()
                        .onFocusChanged { sidebarHasFocus = false },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(ROW_GAP)
                ) {
                    items(filteredChannels, key = { "${it.id}_${it.url.hashCode()}" }) { channel ->
                        val progs = gridPrograms[channel.id].orEmpty()
                        val isFocused = !sidebarHasFocus && filteredChannels.indexOf(channel) == focusChannelIdx
                        val focusedProg = if (isFocused && progs.isNotEmpty()) {
                            progs.getOrNull(focusProgramIdx)
                        } else {
                            null
                        }
                        val nowProgIdx = findProgramAtTime(progs, now)

                        EpgGridRow(
                            channel = channel,
                            programs = progs,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            hourWidth = HOUR_WIDTH,
                            channelInfoWidth = CHANNEL_INFO_WIDTH,
                            isFocused = isFocused,
                            focusedProgram = focusedProg,
                            nowProgIdx = nowProgIdx,
                            now = now,
                            horizontalScrollState = horizontalScrollState,
                            programsAreaWidth = programsAreaWidthDp
                        )
                    }

                    if (filteredChannels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhum canal encontrado",
                                    color = NuvioTheme.colors.TextSecondary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // DETAIL PANE (bottom)
                // ═══════════════════════════════════════════════════════════
                if (focusedProgram != null) {
                    DetailPane(
                        channelName = focusedChannel?.name ?: "",
                        program = focusedProgram,
                        onWatch = {
                            focusedChannel?.let { onChannelSelect(it) }
                        }
                    )
                }
            }
        }
    }
}

// ─── Timeline Header ───────────────────────────────────────────────────────

@Composable
private fun TimelineHeader(
    windowStart: Long,
    windowEnd: Long,
    scrollState: androidx.compose.foundation.ScrollState,
    now: Long,
    modifier: Modifier = Modifier
) {
    val hourSlots = (windowEnd - windowStart) / HOUR_MS
    val dayFormatter = java.text.SimpleDateFormat("EEE dd", java.util.Locale("pt", "BR"))
    val dayLabel = dayFormatter.format(java.util.Date(now))

    Row(
        modifier = modifier
            .background(NuvioTheme.colors.surfaces.variant.copy(alpha = 0.3f))
            .height(HEADER_HEIGHT)
    ) {
        // Day label (fixed, not scrolling)
        Box(
            modifier = Modifier
                .width(CHANNEL_INFO_WIDTH)
                .fillMaxHeight()
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = dayLabel,
                color = NuvioTheme.colors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        // Hour slots (scrollable)
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .weight(1f)
        ) {
            for (i in 0 until hourSlots.toInt()) {
                val hour = windowStart + i * HOUR_MS
                Box(
                    modifier = Modifier
                        .width(HOUR_WIDTH)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = hour.toHourMin(),
                        color = NuvioTheme.colors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

// ─── EPG Grid Row (single channel) ────────────────────────────────────────

@Composable
private fun EpgGridRow(
    channel: TvChannel,
    programs: List<EpgProgram>,
    windowStart: Long,
    windowEnd: Long,
    hourWidth: Dp,
    channelInfoWidth: Dp,
    isFocused: Boolean,
    focusedProgram: EpgProgram?,
    nowProgIdx: Int,
    now: Long,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    programsAreaWidth: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .then(
                if (isFocused) Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                        shape = RoundedCornerShape(8.dp)
                    )
                else Modifier
            )
    ) {
        // Channel info column (fixed, not scrolling)
        Box(
            modifier = Modifier
                .width(channelInfoWidth)
                .fillMaxHeight()
                .background(
                    if (isFocused) NuvioTheme.colors.FocusBackground.copy(alpha = 0.7f)
                    else Color.Transparent
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Accent bar on the left when focused
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .background(NuvioTheme.colors.FocusRing)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = channel.name,
                    color = if (isFocused) Color.White else NuvioTheme.colors.TextSecondary,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Programs area (horizontally scrollable) with gridlines + timeline overlay
        val gridlineColor = NuvioTheme.colors.surfaces.variant.copy(alpha = 0.25f)
        val timelineColor = NuvioTheme.colors.FocusRing.copy(alpha = 0.8f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState)
                    .width(programsAreaWidth)
                    .drawBehind {
                        // Gridlines — vertical lines at each hour
                        val hourPx = HOUR_WIDTH.toPx()
                        val totalHours = (size.width / hourPx).toInt()
                        for (i in 0..totalHours) {
                            val x = i * hourPx
                            drawLine(
                                color = gridlineColor,
                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                end = androidx.compose.ui.geometry.Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
            ) {
                if (programs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Sem programação",
                            color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    programs.forEachIndexed { idx, prog ->
                    val progStart = ((prog.startTime - windowStart).toFloat() / HOUR_MS)
                    val progEnd = ((prog.endTime - windowStart).toFloat() / HOUR_MS)
                    val widthFraction = ((prog.endTime - prog.startTime).coerceAtLeast(HALF_HOUR_MS).toFloat() / HOUR_MS)
                    val isNow = now in prog.startTime until prog.endTime
                    val isFocusedProg = focusedProgram?.let { prog == it } == true || (isFocused && idx == 0 && focusedProgram == null)

                    Box(
                        modifier = Modifier
                            .width(hourWidth * widthFraction)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .clip(EPG_SHAPE)
                            .background(
                                when {
                                    isFocusedProg -> NuvioTheme.colors.FocusBackground.copy(alpha = 0.7f)
                                    isNow -> NuvioTheme.colors.Secondary.copy(alpha = 0.25f)
                                    else -> NuvioTheme.colors.surfaces.card.copy(alpha = EPG_CELL_ALPHA)
                                }
                            )
                            .then(
                                if (isFocusedProg) Modifier.background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            NuvioTheme.colors.FocusBackground.copy(alpha = 0.45f),
                                            Color.Transparent
                                        )
                                    )
                                ) else Modifier
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Text(
                                text = prog.title ?: "Sem título",
                                color = if (isFocusedProg) Color.White else NuvioTheme.colors.TextSecondary,
                                fontSize = if (isFocusedProg) 13.sp else 12.sp,
                                fontWeight = if (isFocusedProg) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isFocusedProg) {
                                Text(
                                    text = "${prog.startTime.toHourMin()} - ${prog.endTime.toHourMin()}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                            // AO VIVO badge on current program
                            if (isNow && !isFocusedProg) {
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(NuvioTheme.colors.Secondary, androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "AO VIVO",
                                        color = NuvioTheme.colors.Secondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
            // Timeline overlay — vertical line at "now" position
            if (now in windowStart..windowEnd) {
                val hourPx = with(LocalDensity.current) { HOUR_WIDTH.toPx() }
                val scrollPx = horizontalScrollState.value.toFloat()
                val nowOffset = ((now - windowStart).toFloat() / HOUR_MS) * hourPx - scrollPx
                if (nowOffset > 0f) {
                    val density = LocalDensity.current
                    val offDp = with(density) { nowOffset.toDp() }
                    Box(
                        modifier = Modifier
                            .offset(x = offDp)
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(timelineColor)
                    )
                }
            }
        }
    }
}

// ─── Detail Pane (bottom) ─────────────────────────────────────────────────

@Composable
private fun DetailPane(
    channelName: String,
    program: EpgProgram,
    onWatch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DETAIL_PANE_HEIGHT)
            .background(NuvioTheme.colors.surfaces.panel.copy(alpha = EPG_BG_ALPHA))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.title ?: "",
                color = NuvioTheme.colors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${channelName} · ${program.startTime.toHourMin()} - ${program.endTime.toHourMin()}",
                color = NuvioTheme.colors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )
            if (!program.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = program.description,
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // [Assistir] button
        Card(
            onClick = onWatch,
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                    shape = RoundedCornerShape(8.dp)
                )
            ),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.Primary.copy(alpha = 0.8f),
                focusedContainerColor = NuvioTheme.colors.Primary
            ),
            scale = CardDefaults.scale(focusedScale = 1.05f)
        ) {
            Text(
                text = "▶ Assistir",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}
