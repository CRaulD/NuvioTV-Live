@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.dpadRepeatThrottle
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

// ─── Category colors — one per group for visual distinction ──────────────────

private val CATEGORY_COLORS = listOf(
    Color(0xFF3B82F6), // blue — News
    Color(0xFF22C55E), // green — Sports
    Color(0xFFA855F7), // purple — Variety
    Color(0xFFEF4444), // red — Movies
    Color(0xFFF97316), // orange — Series
    Color(0xFFEC4899), // pink — Kids
    Color(0xFF14B8A6), // teal — Documentaries
    Color(0xFFEAB308), // yellow — Music
    Color(0xFF6366F1), // indigo — Religion
    Color(0xFF84CC16), // lime — Cooking
    Color(0xFF06B6D4), // cyan — Travel
    Color(0xFF8B5CF6), // violet — Tech
)

/** Map group name to a stable color based on index. */
private fun categoryColor(index: Int): Color =
    CATEGORY_COLORS[index % CATEGORY_COLORS.size]

/**
 * Premium horizontal rail of category cards for IPTV.
 *
 * Visual: colorful icon per category, gradient card background,
 * symmetric padding, auto-select on focus.
 */
@Composable
fun CategoryRailPremium(
    groups: List<String>,
    channels: List<TvChannel>,
    selectedGroup: String?,
    onGroupClick: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val favoriteLabel = stringResource(R.string.iptv_category_favorites)
    val counts = remember(groups, channels, favoriteLabel) {
        groups.associateWith { g ->
            channels.count { it.group == g }
        } + (favoriteLabel to channels.size)
    }

    // Debounced auto-select: when focus stops on a card for 400ms, select that group
    var hoveredGroup by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hoveredGroup, isFocused) {
        if (hoveredGroup != null && isFocused) {
            delay(400)
            onGroupClick(hoveredGroup!!)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .focusRequester(focusRequester)
            .background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .dpadRepeatThrottle(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Todos" — first item with special visual
            item(key = "__all__") {
                RailCardPremium(
                    label = stringResource(R.string.iptv_category_all),
                    channelCount = channels.size,
                    isSelectedGroup = selectedGroup == null,
                    colorIndex = -1, // special
                    onClick = { onGroupClick("") },
                    isSectionActive = isFocused,
                    onCardFocused = { hoveredGroup = "" }
                )
            }

            itemsIndexed(
                items = groups,
                key = { _, g -> g }
            ) { index, group ->
                val realIndex = index + 1 // offset for "Todos" above
                // Show channel count: for favorites, it's the number of favorited channels
                val chCount = if (group == stringResource(R.string.iptv_category_favorites))
                    channels.size
                else counts[group] ?: 0

                RailCardPremium(
                    label = group,
                    channelCount = chCount,
                    isSelectedGroup = group == selectedGroup,
                    colorIndex = realIndex,
                    onClick = { onGroupClick(group) },
                    isSectionActive = isFocused,
                    onCardFocused = { hoveredGroup = group }
                )
            }
        }
    }
}

// ─── RailCardPremium ─────────────────────────────────────────────────────────

/**
 * Premium category card with color identity, icon, and gradient.
 *
 * Layout:
 * ┌──────────────┐
 * │   ●  icon    │  ← color dot + emoji icon
 * │              │
 * │  Categoria   │  ← label 14sp SemiBold
 * │  23 canais   │  ← count 11sp
 * └──────────────┘
 *
 * Focused: shadow + scale 1.03 + bright border
 *
 * @param isSectionActive when false, renders as non-focusable Box (no Card focus visuals)
 *        to avoid dual-highlight during focusSection transitions.
 */
@Composable
private fun RailCardPremium(
    label: String,
    channelCount: Int,
    isSelectedGroup: Boolean,
    colorIndex: Int,
    onClick: () -> Unit,
    isSectionActive: Boolean = true,
    onCardFocused: () -> Unit = {}
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (colorIndex >= 0) categoryColor(colorIndex)
        else NuvioTheme.colors.Secondary // "Todos" uses theme accent

    var hasFocus by remember { mutableStateOf(false) }

    if (isSectionActive) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(150.dp)
                .height(84.dp)
                .onFocusChanged { focusState ->
                    val wasFocused = hasFocus
                    hasFocus = focusState.hasFocus
                    if (focusState.hasFocus && !wasFocused) onCardFocused()
                },
            shape = CardDefaults.shape(shape = shape),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.BackgroundCard
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = accent.copy(alpha = 0.8f)
                    ),
                    shape = shape
                )
            ),
            scale = CardDefaults.scale(focusedScale = 1.03f)
        ) {
            RailCardContent(hasFocus, isSelectedGroup, accent, colorIndex, label, channelCount)
        }
    } else {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(84.dp)
                .clip(shape)
                .background(NuvioTheme.colors.BackgroundCard, shape)
        ) {
            RailCardContent(hasFocus = false, isSelectedGroup, accent, colorIndex, label, channelCount)
        }
    }
}

@Composable
private fun RailCardContent(
    hasFocus: Boolean,
    isSelectedGroup: Boolean,
    accent: Color,
    colorIndex: Int,
    label: String,
    channelCount: Int
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Gradient overlay with accent tint
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = if (hasFocus) 0.15f else 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Color dot icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .shadow(
                        elevation = if (hasFocus) 4.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = accent.copy(alpha = 0.3f),
                        spotColor = accent.copy(alpha = 0.3f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.9f),
                                accent.copy(alpha = 0.4f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (colorIndex >= 0) "\u25CF" else "\u25C9",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Category name
            Text(
                text = label,
                color = if (hasFocus || isSelectedGroup) Color.White
                else NuvioTheme.colors.TextPrimary.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = if (hasFocus) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Channel count
            Text(
                text = stringResource(R.string.iptv_channel_count, channelCount),
                color = if (hasFocus || isSelectedGroup)
                    accent.copy(alpha = 0.8f)
                else NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Bottom accent line when selected
        if (isSelectedGroup && !hasFocus) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accent.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
