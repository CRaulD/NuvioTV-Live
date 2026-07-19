package com.nuvio.tv.ui.screens.iptv

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.TvChannel
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
    timeZone = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
}
private fun Long.toHourMin(): String = timeFormat.format(Date(this))

private fun EpgProgram.progressValue(now: Long): Float {
    val total = endTime - startTime
    if (total <= 0) return 0f
    val elapsed = now - startTime
    return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/**
 * Premium preview pane — video full-width com info sobreposta.
 *
 * Aceita [player] por parâmetro (hoisted no pai) para que o mesmo
 * ExoPlayer seja compartilhado entre preview (collapsed) e fullscreen (expanded).
 *
 * Layout:
 * ┌──────────────────────────────────────────────┐
 * │  ● AO VIVO                                   │  MiniVideoPlayer (fill)
 * │                                               │
 * │  Globo News                                   │  info overlay (bottom)
 * │  Jornal das Dez                               │
 * │  19:00 ████████░░░ 73% · 12min  20:00        │
 * └──────────────────────────────────────────────┘
 */
@Composable
fun IptvPreviewPanePremium(
    channel: TvChannel?,
    currentProgram: EpgProgram?,
    now: Long,
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .shadow(8.dp, shape)
    ) {
        if (channel == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NuvioTheme.colors.BackgroundCard),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.iptv_browse_categories),
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.4f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.iptv_select_channel_hint),
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.25f),
                        fontSize = 12.sp
                    )
                }
            }
            return
        }

        // ── Full-width video player (shared ExoPlayer) ──────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        isFocusable = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Gradient overlay (bottom fade) ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        ),
                        startY = 600f
                    )
                )
        )

        // ── ● AO VIVO badge ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(NuvioTheme.colors.Primary.copy(alpha = dotAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.iptv_live_badge),
                color = NuvioTheme.colors.Primary,
                style = NuvioTheme.textStyles.badge,
                letterSpacing = 0.15.sp
            )
        }

        // ── Info overlay (bottom) ────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Text(
                    text = channel.name,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (currentProgram != null) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = currentProgram.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val progress = currentProgram.progressValue(now)
                    val remainingMin = ((currentProgram.endTime - now) / 60_000).toInt().coerceAtLeast(0)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentProgram.startTime.toHourMin(),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(NuvioTheme.colors.Primary)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(progress * 100).toInt()}% · ${remainingMin}min",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentProgram.endTime.toHourMin(),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Text(
                        text = "Sem programação no momento",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
