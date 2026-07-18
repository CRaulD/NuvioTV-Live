@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// ─── Sidebar state ──────────────────────────────────────────────────────────

private enum class Col { CATEGORY, CHANNEL }

// ─── Sidebar action model ───────────────────────────────────────────────────

private sealed class SidebarAction {
    data class MoveCol(val col: Col) : SidebarAction()
    data class MoveCat(val index: Int) : SidebarAction()
    data class MoveCh(val index: Int) : SidebarAction()
    data object SelectCategory : SidebarAction()
    data object SelectChannel : SidebarAction()
    data object Dismiss : SidebarAction()
}

private fun handleSidebarKey(
    key: Key,
    activeCol: Col,
    catFocus: Int,
    chFocus: Int,
    catSize: Int,
    chSize: Int
): SidebarAction? = when {
    key == Key.DirectionRight && activeCol == Col.CHANNEL -> SidebarAction.Dismiss
    key == Key.Back -> SidebarAction.Dismiss
    key == Key.DirectionLeft && activeCol == Col.CHANNEL && catSize > 0 ->
        SidebarAction.MoveCol(Col.CATEGORY)
    key == Key.DirectionRight && activeCol == Col.CATEGORY ->
        SidebarAction.MoveCol(Col.CHANNEL)

    activeCol == Col.CATEGORY -> when {
        key == Key.DirectionDown ->
            SidebarAction.MoveCat((catFocus + 1).coerceAtMost(catSize - 1))
        key == Key.DirectionUp ->
            SidebarAction.MoveCat((catFocus - 1).coerceAtLeast(0))
        key == Key.Enter -> SidebarAction.SelectCategory
        else -> null
    }

    activeCol == Col.CHANNEL -> when {
        key == Key.DirectionDown ->
            SidebarAction.MoveCh((chFocus + 1).coerceAtMost(chSize - 1))
        key == Key.DirectionUp ->
            SidebarAction.MoveCh((chFocus - 1).coerceAtLeast(0))
        key == Key.Enter -> SidebarAction.SelectChannel
        else -> null
    }

    else -> null
}

// ─── Left accent border helper ──────────────────────────────────────────────

private fun Modifier.leftAccentBorder(color: Color): Modifier = this.then(
    Modifier.drawBehind {
        if (color != Color.Transparent) {
            drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(2.dp.toPx(), size.height)
            )
        }
    }
)

// ─── Main sidebar composable ────────────────────────────────────────────────

/**
 * Player sidebar with category + channel columns (50/50 split).
 * Slides in from the left over the player video.
 */
@Composable
fun PlayerChannelSidebar(
    isOpen: Boolean,
    channels: List<TvChannel>,
    groups: List<String>,
    currentChannel: TvChannel?,
    currentPrograms: Map<String, EpgProgram>,
    favorites: Set<String>,
    onChannelSelect: (TvChannel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen && groups.isEmpty()) return

    var activeCol by remember { mutableStateOf(Col.CHANNEL) }
    var catFocusIndex by remember { mutableIntStateOf(0) }
    var chFocusIndex by remember { mutableIntStateOf(0) }
    var activeCategory by remember { mutableStateOf<String?>(null) }

    // Reset when opening
    LaunchedEffect(isOpen) {
        if (isOpen) {
            activeCol = Col.CHANNEL
            catFocusIndex = 0
            chFocusIndex = 0
            activeCategory = null
        }
    }

    // Filter channels by active category
    val filteredChannels = remember(activeCategory, channels) {
        if (activeCategory == null) channels
        else channels.filter { it.group == activeCategory }
    }

    // ── Dark overlay ──────────────────────────────────────────────────────
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        endX = 600f
                    )
                )
        )
    }

    // ── Sidebar panel ─────────────────────────────────────────────────────
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(animationSpec = tween(280)) { -it },
        exit = slideOutHorizontally(animationSpec = tween(200)) { -it }
    ) {
        Row(
            modifier = modifier
                .fillMaxHeight()
                .width(360.dp)
                .then(
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown)
                            return@onPreviewKeyEvent false
                        handleSidebarKey(
                            event.key, activeCol, catFocusIndex, chFocusIndex,
                            groups.size, filteredChannels.size
                        )?.let { action ->
                            when (action) {
                                is SidebarAction.MoveCol -> activeCol = action.col
                                is SidebarAction.MoveCat -> catFocusIndex = action.index
                                is SidebarAction.MoveCh -> chFocusIndex = action.index
                                is SidebarAction.SelectCategory -> {
                                    activeCategory = groups.getOrNull(catFocusIndex)
                                    activeCol = Col.CHANNEL
                                    chFocusIndex = 0
                                }
                                is SidebarAction.SelectChannel -> {
                                    filteredChannels.getOrNull(chFocusIndex)?.let(onChannelSelect)
                                    onDismiss()
                                }
                                is SidebarAction.Dismiss -> onDismiss()
                            }
                            true
                        } ?: false
                    }
                )
        ) {
            // Left 50% — Categories
            CategoryColumn(
                categories = groups,
                activeCategory = activeCategory,
                isFocused = activeCol == Col.CATEGORY,
                focusedIndex = catFocusIndex,
                onCategorySelect = { idx ->
                    activeCategory = groups.getOrNull(idx)
                    activeCol = Col.CHANNEL
                    chFocusIndex = 0
                },
                modifier = Modifier.weight(1f)
            )

            // Right 50% — Channels
            ChannelColumn(
                channels = filteredChannels,
                currentChannel = currentChannel,
                currentPrograms = currentPrograms,
                favorites = favorites,
                isFocused = activeCol == Col.CHANNEL,
                focusedIndex = chFocusIndex,
                onChannelSelect = { ch ->
                    onChannelSelect(ch)
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Category column ────────────────────────────────────────────────────────

@Composable
private fun CategoryColumn(
    categories: List<String>,
    activeCategory: String?,
    isFocused: Boolean,
    focusedIndex: Int,
    onCategorySelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0a0a0a))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF080808))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Categorias",
                color = Color(0xFF1e1e1e),
                fontSize = 9.sp,
                letterSpacing = 0.1.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // List
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = rememberLazyListState()
        ) {
            itemsIndexed(categories, key = { _, cat -> cat }) { idx, category ->
                val isActive = category == activeCategory
                val isFocusedItem = isFocused && idx == focusedIndex
                val accentColor = if (isActive) NuvioTheme.colors.Secondary else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            when {
                                isActive -> NuvioTheme.colors.Secondary.copy(alpha = 0.06f)
                                isFocusedItem -> Color(0xFF131313)
                                else -> Color.Transparent
                            }
                        )
                        .leftAccentBorder(accentColor)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category,
                        color = when {
                            isActive -> NuvioTheme.colors.Secondary
                            isFocusedItem -> Color(0xFF666666)
                            else -> Color(0xFF2a2a2a)
                        },
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Channel column ─────────────────────────────────────────────────────────

@Composable
private fun ChannelColumn(
    channels: List<TvChannel>,
    currentChannel: TvChannel?,
    currentPrograms: Map<String, EpgProgram>,
    favorites: Set<String>,
    isFocused: Boolean,
    focusedIndex: Int,
    onChannelSelect: (TvChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0d0d0d))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0a0a0a))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Channels",
                color = Color(0xFF1e1e1e),
                fontSize = 9.sp,
                letterSpacing = 0.1.sp,
                fontWeight = FontWeight.Medium
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = rememberLazyListState()
        ) {
            itemsIndexed(channels, key = { idx, ch -> "${ch.id}_${idx}" }) { idx, channel ->
                val isPlaying = channel.id == currentChannel?.id
                val isFocusedItem = isFocused && idx == focusedIndex
                val program = currentPrograms[channel.id]
                val accentColor = if (isPlaying) NuvioTheme.colors.Secondary else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(
                            when {
                                isPlaying -> NuvioTheme.colors.Secondary.copy(alpha = 0.05f)
                                isFocusedItem -> Color(0xFF141414)
                                else -> Color.Transparent
                            }
                        )
                        .leftAccentBorder(accentColor)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo
                    if (channel.logo != null) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF161616)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF161616))
                                .border(1.dp, Color(0xFF1c1c1c), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = channel.name.take(1).uppercase(),
                                color = Color(0xFF2a2a2a),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Info column
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = when {
                                isPlaying -> NuvioTheme.colors.Secondary
                                isFocusedItem -> Color(0xFF777777)
                                else -> Color(0xFF3a3a3a)
                            },
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Current program
                        if (program != null) {
                            Text(
                                text = "${program.startTime.toTimeString()} ${program.title}",
                                color = Color(0xFF1e1e1e),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp)
                            )

                            // Progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(top = 3.dp)
                                    .background(Color(0xFF1a1a1a))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(program.progress())
                                        .fillMaxHeight()
                                        .background(
                                            if (isPlaying) NuvioTheme.colors.Secondary
                                            else NuvioTheme.colors.Secondary.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }

                        // Favorite indicator
                        if (favorites.contains(channel.id)) {
                            Text(
                                text = "★ Favorite",
                                color = NuvioTheme.colors.Secondary.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }

                    // Playing indicator dot
                    if (isPlaying) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NuvioTheme.colors.Secondary)
                        )
                    }
                }
            }
        }
    }
}
