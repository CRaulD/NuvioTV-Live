package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel

import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Compact layout constants — grid as subtle supporting info ───────────────

private val HOUR_WIDTH = 100.dp          // narrower hour blocks
private val ROW_HEIGHT = 38.dp           // compact rows
private val CHANNEL_INFO_WIDTH = 200.dp  // wider channel column for names
private val HEADER_HEIGHT = 20.dp        // compact timeline header
private val EPG_SHAPE = RoundedCornerShape(4.dp)
private val VISIBLE_ROWS = 3
private val ROW_GAP = 3.dp
private val GRID_ROWS_HEIGHT = 120.dp  // 3×38 + 2×3

private const val HOUR_MS = 60 * 60 * 1000L
private const val HALF_HOUR_MS = 30 * 60 * 1000L
private const val GRID_WINDOW_HOURS = 12  // 12-hour window for scrollable grid

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
    timeZone = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
}
private fun Long.toHourMin(): String = timeFormat.format(Date(this))

/**
 * Compact EPG grid — subtle supporting info, not the visual focus.
 *
 * KEY FIX: ChannelInfo cells are FIXED (outside horizontal scroll).
 * Only the program blocks area scrolls horizontally, so channel names
 * remain visible while scrolling through the schedule.
 *
 * 3 rows, smaller blocks, more transparent defaults, minimal footprint.
 */
@Composable
fun EpgGridCompact(
    channels: List<TvChannel>,
    gridPrograms: Map<String, List<EpgProgram>>,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    focusedChannelId: String?,
    onChannelPlay: (TvChannel) -> Unit,
    onShiftWindow: (Long) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    favorites: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onExitLeft: () -> Unit = {},
    onExitUp: () -> Unit = {},
    onChannelFocused: (String) -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    if (channels.isEmpty()) return

    val density = LocalDensity.current
    val hourWidthPx = with(density) { HOUR_WIDTH.toPx() }

    val effectiveStart = if (windowStart > 0) windowStart else {
        now - (now % HOUR_MS)
    }
    val effectiveEnd = if (windowEnd > effectiveStart) windowEnd else effectiveStart + 4 * HOUR_MS
    val windowSpan = effectiveEnd - effectiveStart
    val programsAreaWidthPx = remember(effectiveStart, effectiveEnd) {
        (windowSpan.toFloat() / HOUR_MS.toFloat()) * hourWidthPx
    }
    val programsAreaWidthDp: Dp = with(density) { programsAreaWidthPx.toDp() }

    var focusChannel by remember { mutableIntStateOf(0) }
    var focusProgram by remember { mutableIntStateOf(0) }
    val verticalListState = rememberLazyListState()
    val gridFocusRequester = remember { focusRequester ?: FocusRequester() }
    var longPressFired by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(enabled) {
        if (enabled) gridFocusRequester.requestFocus()
    }

    LaunchedEffect(focusedChannelId, channels) {
        if (focusedChannelId != null) {
            val idx = channels.indexOfFirst { it.id == focusedChannelId }
            if (idx in channels.indices && idx != focusChannel) focusChannel = idx
        }
    }

    LaunchedEffect(focusChannel) {
        if (focusChannel in channels.indices) verticalListState.animateScrollToItem(focusChannel)
    }

    LaunchedEffect(focusProgram, focusChannel, effectiveStart, effectiveEnd) {
        val ch = channels.getOrNull(focusChannel) ?: return@LaunchedEffect
        val progs = gridPrograms[ch.id].orEmpty()
        val prog = progs.getOrNull(focusProgram) ?: return@LaunchedEffect

        val edgeThreshold = 30 * 60 * 1000L
        if (prog.endTime > effectiveEnd - edgeThreshold) {
            onShiftWindow(HOUR_MS)
            kotlinx.coroutines.delay(200)
            return@LaunchedEffect
        }

        // Auto-scroll horizontally — keep focused program in view
        val hourPx = with(density) { HOUR_WIDTH.toPx() }
        val progStartX = ((prog.startTime - effectiveStart).toFloat() / HOUR_MS) * hourPx
        val progWidth = ((prog.endTime - prog.startTime).coerceAtLeast(HALF_HOUR_MS).toFloat() / HOUR_MS) * hourPx
        val viewWidthPx = 800f // estimated viewport width (~800dp at 1x density)
        val margin = hourPx * 0.3f
        val scrollX = horizontalScrollState.value.toFloat()
        if (progStartX + progWidth > scrollX + viewWidthPx - margin) {
            val target = (progStartX + progWidth - viewWidthPx + margin).toInt().coerceAtLeast(0)
            horizontalScrollState.animateScrollTo(target)
        } else if (progStartX < scrollX + margin) {
            val target = (progStartX - margin).toInt().coerceAtLeast(0)
            horizontalScrollState.animateScrollTo(target)
        }
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

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (!enabled) return false
        // Reset focusChannel if out of bounds (channels list may have changed)
        if (focusChannel !in channels.indices) {
            if (channels.isEmpty()) return false
            focusChannel = 0
        }
        val ch = channels[focusChannel]

        // Timer + flag for long-press:
        // KeyDown → start 500ms timer (with flag guard to prevent double-fire)
        // Timer completes → set flag, toggle favorite
        // KeyUp → if flag set, just consume; else cancel timer + play channel
        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
            if (event.type == KeyEventType.KeyDown) {
                // Ignore auto-repeat (key held down generates multiple KeyDown)
                if (event.nativeKeyEvent!!.repeatCount > 0) return true
                longPressFired = false
                longPressJob?.cancel()
                val favKey = ch.favoriteKey
                longPressJob = scope.launch {
                    delay(500)
                    if (!longPressFired) {
                        longPressFired = true
                        onToggleFavorite(favKey)
                    }
                }
                return true
            }
            if (event.type == KeyEventType.KeyUp) {
                if (longPressFired) {
                    // Long-press already handled — just consume, don't play
                    return true
                } else {
                    // Short press — play channel
                    longPressJob?.cancel()
                    onChannelPlay(ch)
                    return true
                }
            }
            return false
        }
        if (event.type != KeyEventType.KeyDown) return false
        val progs = gridPrograms[ch.id].orEmpty()
        val prog = progs.getOrNull(focusProgram)

        return when (event.key) {
            Key.DirectionUp -> {
                if (focusChannel > 0) {
                    focusChannel--
                    val targetTime = prog?.startTime ?: now
                    val targetProgs = gridPrograms[channels[focusChannel].id].orEmpty()
                    focusProgram = findProgramAtTime(targetProgs, targetTime)
                    onChannelFocused(channels[focusChannel].id)
                } else {
                    onExitUp()
                }
                true
            }
            Key.DirectionDown -> {
                if (focusChannel < channels.lastIndex) {
                    focusChannel++
                    val targetTime = prog?.startTime ?: now
                    val targetProgs = gridPrograms[channels[focusChannel].id].orEmpty()
                    focusProgram = findProgramAtTime(targetProgs, targetTime)
                    onChannelFocused(channels[focusChannel].id)
                }
                true
            }
            Key.DirectionLeft -> {
                if (focusProgram > 0) focusProgram--
                else onExitLeft()
                true
            }
            Key.DirectionRight -> {
                if (focusProgram < progs.lastIndex) focusProgram++
                true
            }
            else -> false
        }
    }

    // ── Outer container — transparent, minimal ────────────────────────────
    Box(
        modifier = modifier
            .focusable()
            .focusRequester(gridFocusRequester)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NuvioTheme.colors.BackgroundCard.copy(alpha = 0.20f),
                        NuvioTheme.colors.Background.copy(alpha = 0.80f)
                    )
                )
            )
            .onPreviewKeyEvent(::handleKey)
    ) {
        Column {
            // ── Timeline header (CHANNEL_INFO_WIDTH spacer + scrollable hours) ─
            Row(Modifier.height(HEADER_HEIGHT)) {
                // Fixed spacer matching channel column width
                Box(
                    modifier = Modifier
                        .width(CHANNEL_INFO_WIDTH)
                        .height(HEADER_HEIGHT)
                        .background(Color.Transparent)
                )
                // Scrollable hour labels (shared horizontalScrollState)
                Row(
                    modifier = Modifier
                        .horizontalScroll(horizontalScrollState)
                        .requiredWidth(programsAreaWidthDp)
                        .height(HEADER_HEIGHT)
                        .clip(EPG_SHAPE)
                        .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.25f), EPG_SHAPE)
                ) {
                    var t = effectiveStart
                    while (t < effectiveEnd) {
                        Box(
                            modifier = Modifier
                                .width(HOUR_WIDTH)
                                .height(HEADER_HEIGHT)
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = t.toHourMin(),
                                color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.45f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        t += HOUR_MS
                    }
                    // Absorb remaining space
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // ── Channel rows (3 visible): FIXED channel info + scrollable programs ─
            key(gridPrograms) {
                LazyColumn(
                    state = verticalListState,
                    modifier = Modifier.fillMaxWidth().height(GRID_ROWS_HEIGHT),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(ROW_GAP)
                ) {
                    items(items = channels, key = { "${it.id}_${it.url.hashCode()}" }) { channel ->
                        val index = channels.indexOf(channel)
                        val isFocusedRow = enabled && index == focusChannel
                        val progs = gridPrograms[channel.id].orEmpty()

                        // ── Single row: fixed ChannelInfo + scrollable Programs ─
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // FIXED channel info (never scrolls horizontally)
                            ChannelInfoCellCompact(
                                channel = channel,
                                index = index,
                                isFocused = isFocusedRow,
                                isFavorite = favorites.contains(channel.favoriteKey),
                                modifier = Modifier
                                    .width(CHANNEL_INFO_WIDTH)
                                    .fillMaxHeight()
                            )

                            // Scrollable programs area (shared horizontalScrollState)
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(horizontalScrollState)
                                    .requiredWidth(programsAreaWidthDp)
                                    .fillMaxHeight()
                            ) {
                                if (progs.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .width(programsAreaWidthDp)
                                            .fillMaxHeight()
                                            .clip(EPG_SHAPE)
                                            .background(
                                                NuvioTheme.colors.BackgroundCard.copy(alpha = 0.12f),
                                                EPG_SHAPE
                                            )
                                    )
                                } else {
                                    var cursor = effectiveStart
                                    progs.forEachIndexed { i, prog ->
                                        if (prog.startTime > cursor) {
                                            val gapMs = prog.startTime - cursor
                                            val gapFraction = gapMs.toFloat() / HOUR_MS
                                            Spacer(
                                                Modifier
                                                    .width(HOUR_WIDTH * gapFraction)
                                                    .fillMaxHeight()
                                            )
                                        }
                                        EpgProgramBlockCompact(
                                            program = prog,
                                            isCurrent = prog.isNow,
                                            isFocused = isFocusedRow && i == focusProgram,
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                        cursor = prog.endTime
                                    }
                                    // Absorb remaining space
                                    if (progs.isNotEmpty()) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Channel info cell compact ───────────────────────────────────────────────

@Composable
private fun ChannelInfoCellCompact(
    channel: TvChannel,
    index: Int,
    isFocused: Boolean,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(EPG_SHAPE)
            .background(
                if (isFocused) NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f)
                else Color.Transparent,
                EPG_SHAPE
            )
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar on focus
        if (isFocused) {
            Box(
                modifier = Modifier
                    .width(2.dp).height(24.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(NuvioTheme.colors.Secondary)
            )
            Spacer(Modifier.width(8.dp))
        }
        // Channel number
        Text(
            text = "${index + 1}",
            color = if (isFocused) NuvioTheme.colors.TextPrimary.copy(alpha = 0.9f)
            else NuvioTheme.colors.TextSecondary.copy(alpha = 0.35f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp)
        )
        // Logo (small)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isFocused) NuvioTheme.colors.FocusBackground.copy(alpha = 0.5f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier.size(26.dp).padding(2.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = channel.name.take(1).uppercase(),
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = if (isFocused) 0.7f else 0.3f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // Channel name + star icon row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isFavorite) "\u2605" else "\u2606",
                color = if (isFavorite) NuvioTheme.colors.Secondary.copy(alpha = 0.9f)
                else NuvioTheme.colors.TextSecondary.copy(alpha = 0.25f),
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = channel.name,
                color = if (isFocused) NuvioTheme.colors.TextPrimary.copy(alpha = 0.9f)
                else NuvioTheme.colors.TextSecondary.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Program block compact ───────────────────────────────────────────────────

@Composable
private fun EpgProgramBlockCompact(
    program: EpgProgram,
    isCurrent: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hourWidthPx = with(density) { HOUR_WIDTH.toPx() }

    // Width = duration-based. Text truncates if longer (grid stays aligned).
    val durationWidthPx = (((program.endTime - program.startTime).coerceAtLeast(HALF_HOUR_MS)).toFloat() / HOUR_MS) * hourWidthPx
    val widthPx = durationWidthPx

    // Very subtle default — grid is supporting info, not the focus
    val bgBrush = when {
        isFocused -> Brush.verticalGradient(
            colors = listOf(NuvioTheme.colors.BackgroundCard, NuvioTheme.colors.BackgroundElevated.copy(alpha = 0.8f))
        )
        isCurrent -> Brush.verticalGradient(
            colors = listOf(
                NuvioTheme.colors.Secondary.copy(alpha = 0.08f),
                NuvioTheme.colors.Secondary.copy(alpha = 0.03f)
            )
        )
        else -> Brush.verticalGradient(
            colors = listOf(
                NuvioTheme.colors.BackgroundCard.copy(alpha = 0.18f),
                NuvioTheme.colors.BackgroundElevated.copy(alpha = 0.10f)
            )
        )
    }
    val borderColor = if (isFocused) NuvioTheme.colors.FocusRing.copy(alpha = 0.7f) else Color.Transparent
    val borderWidth = if (isFocused) 1.5.dp else 0.dp

    Box(
        modifier = modifier
            .width(with(density) { widthPx.toDp() })
            .clip(EPG_SHAPE)
            .shadow(if (isFocused) 3.dp else 0.dp, EPG_SHAPE, ambientColor = NuvioTheme.colors.Secondary.copy(alpha = 0.12f))
            .background(bgBrush, EPG_SHAPE)
            .border(width = borderWidth, color = borderColor, shape = EPG_SHAPE)
    ) {
        if (isCurrent && !isFocused) {
            Box(
                modifier = Modifier
                    .width(2.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .background(NuvioTheme.colors.Secondary.copy(alpha = 0.6f))
                    .align(Alignment.CenterStart)
            )
        }
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = program.title,
                color = when {
                    isFocused -> NuvioTheme.colors.TextPrimary
                    isCurrent -> NuvioTheme.colors.Secondary.copy(alpha = 0.7f)
                    else -> NuvioTheme.colors.TextSecondary.copy(alpha = 0.55f)
                },
                fontSize = 10.sp,
                fontWeight = if (isFocused || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
        }
    }
}
