@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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

// ─── IptvPlayerPane ─────────────────────────────────────────────────────────

@Composable
fun IptvPlayerPane(
    channel: TvChannel?,
    currentProgram: EpgProgram?,
    isLive: Boolean,
    isEpgFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (channel == null) {
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(158.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NuvioTheme.colors.Background)
            )
        }
        return
    }

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Release player on dispose
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // Set media when channel changes
    LaunchedEffect(channel.id) {
        val mediaItem = MediaItem.fromUri(Uri.parse(channel.url))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Video player (158dp fixo) ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(
                    1.5.dp,
                    NuvioTheme.colors.Secondary.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp)
                )
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = NuvioTheme.colors.Secondary.copy(alpha = 0.08f),
                    spotColor = NuvioTheme.colors.Secondary.copy(alpha = 0.04f)
                )
        ) {
            // ── ExoPlayer ───────────────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        isFocusable = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Gradient overlay ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // ── ● AO VIVO badge (top-left) ──────────────────────────────────
            if (isLive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(50.dp)
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing dot
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
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
            }

            // ── Info overlay (bottom-left) ──────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    // Channel name
                    Text(
                        text = channel.name,
                        color = Color.White,
                        style = NuvioTheme.textStyles.cardTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Current program + progress (se houver)
                    if (currentProgram != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        MarqueeText(
                            text = currentProgram.title.toTitleCase(),
                            isActive = isEpgFocused,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentProgram.startTime.toTimeString(),
                                color = Color.White.copy(alpha = 0.6f),
                                style = NuvioTheme.textStyles.metadata
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(currentProgram.progress())
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(NuvioTheme.colors.Primary)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentProgram.endTime.toTimeString(),
                                color = Color.White.copy(alpha = 0.6f),
                                style = NuvioTheme.textStyles.metadata
                            )
                        }

                        // Percentage + remaining
                        val progress = currentProgram.progress()
                        val remainingMs = currentProgram.endTime - System.currentTimeMillis()
                        val remainingMin = (remainingMs / 60_000).toInt().coerceAtLeast(0)
                        Text(
                            text = "${(progress * 100).toInt()}% · ${remainingMin}min",
                            color = Color.White.copy(alpha = 0.5f),
                            style = NuvioTheme.textStyles.metadata,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun String.toTitleCase(): String =
    split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
