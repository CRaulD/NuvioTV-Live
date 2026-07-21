@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Canal Rail melhorado (Opção B) — horizontal rail no expanded mode (②).
 *
 * Mostra TODOS os canais (não só da categoria atual), com:
 * - Nome do canal
 * - Programa atual (título + horário)
 * - Próximo programa
 * - Live badge para canais AO VIVO
 *
 * Primeiro item é sempre o "Program Guide" que abre o EPG overlay (③).
 *
 * O foco é SINGLE — apenas um card destacado por vez.
 *
 * Layout:
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ [📋 Guide] [Globo RJ          ] [Record TV         ] [SBT    ...│
 * │              Jornal Nacional    Balanço Geral       Prog. Ratinho│
 * │              20:30-21:15       20:00-21:00         20:00-21:30  │
 * │              ▲ Travessia       ▲ A Hora Venenosa   ▲ Masked S.  │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Composable
fun PlayerChannelRail(
    channels: List<TvChannel>,
    gridPrograms: Map<String, List<EpgProgram>>,
    now: Long,
    selectedIndex: Int,
    onGuideClick: () -> Unit,
    onChannelClick: (TvChannel) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val railListState = rememberLazyListState()

    // Auto-scroll to selected index
    LaunchedEffect(selectedIndex) {
        railListState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
    }

    // Request focus when rail becomes visible or selection changes
    LaunchedEffect(selectedIndex) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        LazyRow(
            state = railListState,
            modifier = Modifier
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            // First item: Program Guide
            item(key = "__guide__") {
                GuideCard(
                    isSelected = selectedIndex == 0,
                    focusRequester = if (selectedIndex == 0) focusRequester else remember { FocusRequester() },
                    onClick = onGuideClick
                )
            }

            // Channel cards
            items(channels, key = { "${it.id}_${it.url.hashCode()}" }) { channel ->
                val chProgs = gridPrograms[channel.id].orEmpty()
                val currentProg = chProgs.firstOrNull { now >= it.startTime && now < it.endTime }
                val nextProg = currentProg?.let { cur ->
                    chProgs.firstOrNull { it.startTime >= cur.endTime }
                }
                val idx = channels.indexOf(channel)
                ChannelRailCard(
                    channel = channel,
                    currentProgram = currentProg,
                    nextProgram = nextProg,
                    isSelected = idx + 1 == selectedIndex,
                    focusRequester = if (idx + 1 == selectedIndex) focusRequester else remember { FocusRequester() },
                    onClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun GuideCard(
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .then(if (isSelected) Modifier.focusRequester(focusRequester) else Modifier),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                focusedContainerColor = NuvioTheme.colors.FocusBackground.copy(alpha = 0.7f)
            ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📋",
                color = Color.White,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Guide",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChannelRailCard(
    channel: TvChannel,
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .fillMaxHeight()
            .then(if (isSelected) Modifier.focusRequester(focusRequester) else Modifier),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                focusedContainerColor = NuvioTheme.colors.FocusBackground.copy(alpha = 0.7f)
            ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Top row: channel name + live badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (currentProgram != null) {
                    // Live badge
                    Text(
                        text = "AO VIVO",
                        color = Color(0xFFE53935),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x30E53935))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Current program info
            if (currentProgram != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentProgram.title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatTime(currentProgram.startTime)} — ${formatTime(currentProgram.endTime)}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "No programming",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            // Next program
            if (nextProgram != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "▲ ${nextProgram.title}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
    return sdf.format(java.util.Date(epochMillis))
}
