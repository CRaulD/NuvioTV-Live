@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun Long.toTimeString(): String = timeFormat.format(Date(this))

private fun EpgProgram.progress(): Float {
    val now = System.currentTimeMillis()
    val total = endTime - startTime
    if (total <= 0) return 0f
    val elapsed = now - startTime
    return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

// ─── EpgSchedulePane ─────────────────────────────────────────────────────────

@Composable
fun EpgSchedulePane(
    programs: List<EpgProgram>,
    currentPrograms: Map<String, EpgProgram>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.25f))
            .border(0.5.dp, NuvioTheme.colors.BackgroundCard.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Text(
            text = "PROGRAMAÇÃO",
            color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // ── List ─────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(programs, key = { it.startTime }) { program ->
                val isCurrent = program.isNow
                EpgScheduleRow(
                    name = program.title,
                    time = program.startTime.toTimeString(),
                    duration = "${(program.endTime - program.startTime) / 60_000}min",
                    isCurrent = isCurrent,
                    progress = if (isCurrent) program.progress() else 0f
                )
            }
        }

        // ── Footer ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (programs.isNotEmpty()) NuvioTheme.colors.BackgroundCard.copy(alpha = 0.1f)
                    else Color.Transparent
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "\uD83D\uDCC5", // 📅
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ver programação completa",
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "\u276F", // ❯
                    color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.3f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun EpgScheduleRow(
    name: String,
    time: String,
    duration: String,
    isCurrent: Boolean,
    progress: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrent) NuvioTheme.colors.Primary.copy(alpha = 0.06f)
                else Color.Transparent
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            color = if (isCurrent) NuvioTheme.colors.TextPrimary
            else NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(44.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name.uppercase(),
            color = if (isCurrent) NuvioTheme.colors.TextPrimary
            else NuvioTheme.colors.TextSecondary.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isCurrent) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AO VIVO",
                color = NuvioTheme.colors.Primary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                modifier = Modifier
                    .border(0.5.dp, NuvioTheme.colors.Primary.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = duration,
            color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.35f),
            fontSize = 11.sp
        )
    }

    // Progress bar for current program
    if (isCurrent && progress > 0f) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(NuvioTheme.colors.Primary)
            )
        }
    }
}
