package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text

/**
 * Player de TV ao vivo usando ExoPlayer.
 * Rota: iptv_player/{channelUrl}/{channelName}?channelLogo={channelLogo}
 */
object IptvPlayerScreen {
    const val ROUTE = "iptv_player/{channelUrl}/{channelName}?channelLogo={channelLogo}"

    fun createRoute(
        channelUrl: String,
        channelName: String,
        channelLogo: String? = null
    ): String {
        val encodedUrl = java.net.URLEncoder.encode(channelUrl, "UTF-8")
        val encodedName = java.net.URLEncoder.encode(channelName, "UTF-8")
        val encodedLogo = channelLogo?.let {
            java.net.URLEncoder.encode(it, "UTF-8")
        } ?: ""
        return "iptv_player/$encodedUrl/$encodedName?channelLogo=$encodedLogo"
    }
}

@Composable
fun IptvPlayerScreen(
    channelUrl: String,
    channelName: String,
    channelLogo: String?,
    onBackPress: () -> Unit,
    onNavigateToIptv: () -> Unit
) {
    val context = LocalContext.current

    // Remember ExoPlayer instance (recreate only if URL changes)
    val player = remember(channelUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(channelUrl)
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            player.run {
                playWhenReady = false
                stop()
                release()
            }
        }
    }

    // Back button handling
    BackHandler(onBack = onBackPress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setKeepScreenOn(true)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay info no topo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = channelName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
