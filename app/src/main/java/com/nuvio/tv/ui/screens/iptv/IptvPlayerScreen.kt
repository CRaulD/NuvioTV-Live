@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text

/**
 * Fullscreen IPTV player with sidebar overlay.
 * Route: iptv_player/{channelUrl}/{channelName}?channelLogo={channelLogo}
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
    onNavigateToIptv: () -> Unit,
    viewModel: IptvViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // ── Which channel is currently playing ─────────────────────────────────
    val initialChannel = remember(channelUrl, channelName, state.channels) {
        state.channels.find { it.url == channelUrl || it.name == channelName }
    }
    var currentChannel by remember(initialChannel) {
        mutableStateOf(initialChannel)
    }

    val focusRequester = remember { FocusRequester() }

    // Request Compose focus so onPreviewKeyEvent fires (not stolen by AndroidView)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // ── ExoPlayer ──────────────────────────────────────────────────────────
    val player = remember(currentChannel?.url ?: channelUrl) {
        val url = currentChannel?.url ?: channelUrl
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            player.run {
                playWhenReady = false
                stop()
                release()
            }
        }
    }

    // Rebuild player when channel switches via sidebar
    LaunchedEffect(currentChannel?.url) {
        currentChannel?.let { ch ->
            val mediaItem = MediaItem.Builder()
                .setUri(ch.url)
                .build()
            player.run {
                stop()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }
    }

    // ── Back: exit player ──────────────────────────────────
    BackHandler(enabled = true) {
        onBackPress()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .focusRequester(focusRequester)
    ) {
        // 1. ExoPlayer View (no built-in controller — we handle keys ourselves)
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setKeepScreenOn(true)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. OSD overlay (always visible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                val ch = currentChannel
                Text(
                    text = ch?.name ?: channelName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ch != null && state.currentPrograms[ch.id] != null) {
                    val prog = state.currentPrograms[ch.id]!!
                    Text(
                        text = prog.title,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
