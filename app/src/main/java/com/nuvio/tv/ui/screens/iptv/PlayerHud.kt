@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * HUD overlay for the IPTV player.
 *
 * Top bar: channel name + group, AO VIVO badge, clock.
 * Bottom bar: current program title, time range, progress bar.
 * Hint bar: shortcuts (↓ Rail, ☰ Guide, ↑↓ Channels).
 *
 * All sub-elements use the same semi-transparent black background.
 */
@Composable
fun PlayerHud(
    channelName: String,
    channelGroup: String?,
    currentProgram: EpgProgram?,
    nowTick: Long,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        }
    }

    Box(modifier.fillMaxSize()) {
        // ── Top bar ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channelName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channelGroup != null) {
                        Text(
                            text = channelGroup,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
                // AO VIVO badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE53935), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AO VIVO",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Clock
                Text(
                    text = timeFormat.format(Date(nowTick)),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }

        // ── Bottom bar (program info + progress + hints) ────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.BottomCenter)
        ) {
            if (currentProgram != null) {
                Text(
                    text = currentProgram.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                // Time range
                Text(
                    text = "${timeFormat.format(Date(currentProgram.startTime))} - ${timeFormat.format(Date(currentProgram.endTime))}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                // Progress bar
                ProgressBar(
                    currentProgram = currentProgram,
                    nowTick = nowTick
                )
            } else {
                Text(
                    text = "No program info",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
            // Hint chips — abaixo da info do programa
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HintChip(key = "↓", label = "Rail")
                HintChip(key = "☰", label = "Guide")
                HintChip(key = "←→", label = "Channels")
            }
        }
    }
}

@Composable
private fun ProgressBar(
    currentProgram: EpgProgram,
    nowTick: Long,
    modifier: Modifier = Modifier
) {
    val duration = currentProgram.endTime - currentProgram.startTime
    val progress = if (duration > 0) {
        ((nowTick - currentProgram.startTime).toFloat() / duration.toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE53935))
        )
    }
}

@Composable
private fun HintChip(key: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = key,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}
