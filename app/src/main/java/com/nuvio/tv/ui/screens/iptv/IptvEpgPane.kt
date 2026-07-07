@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
// NOTE: designed to be placed inside a detail-card Column.
// The parent card provides outer border, corner radius, and background.

@Composable
fun EpgSchedulePane(
    programs: List<EpgProgram>,
    focusZone: FocusZone = FocusZone.EPG,
    focusIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    val isInZone = focusZone == FocusZone.EPG
    val listState = rememberLazyListState()

    // Log para diagnóstico
    LaunchedEffect(isInZone, focusIndex, programs.size) {
        Log.d("IptvEpg", "render: isInZone=$isInZone focusIndex=$focusIndex programs=${programs.size}")
    }

    // Auto-scroll silencioso para o programa foco
    LaunchedEffect(if (isInZone) focusIndex else -1) {
        if (isInZone && focusIndex > 0 && focusIndex < programs.size) {
            listState.scrollToItem(focusIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (programs.isEmpty()) {
            // Espaço vazio — sem textos de "nenhuma programação"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(programs, key = { it.startTime }) { program ->
                    val isCurrent = program.isNow
                    val dataIdx = programs.indexOf(program)
                    val isFocused = isInZone && dataIdx == focusIndex
                    EpgScheduleRow(
                        name = program.title,
                        time = program.startTime.toTimeString(),
                        duration = "${(program.endTime - program.startTime) / 60_000}min",
                        isCurrent = isCurrent,
                        isFocused = isFocused,
                        progress = if (isCurrent) program.progress() else 0f
                    )
                }
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
    isFocused: Boolean = false,
    progress: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) Modifier.border(
                    1.dp, NuvioTheme.colors.FocusRing.copy(alpha = 0.6f), RoundedCornerShape(4.dp)
                ) else Modifier
            )
            .background(
                if (isCurrent) NuvioTheme.colors.Primary.copy(alpha = 0.06f)
                else if (isFocused) NuvioTheme.colors.FocusBackground.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Horário + indicador AO VIVO, sem duração (mais espaço pro nome)
        Text(
            text = time,
            color = if (isCurrent) NuvioTheme.colors.TextPrimary
            else NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f),
            style = NuvioTheme.textStyles.metadata,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
        )
        if (isCurrent) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "●",
                color = NuvioTheme.colors.Primary,
                fontSize = 8.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Nome do programa — com marquee quando focado (sem uppercase)
        MarqueeText(
            text = name,
            isActive = isFocused,
            color = if (isCurrent) NuvioTheme.colors.TextPrimary
            else NuvioTheme.colors.TextSecondary.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
        )
    }

    // Progress bar for current program
    if (isCurrent && progress > 0f) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(1.dp)
                    .background(NuvioTheme.colors.Primary)
            )
        }
    }
}
