@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.iptv

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Fullscreen IPTV player with auto-hide HUD overlay.
 *
 * Route: iptv_player/{channelUrl}/{channelName}?channelLogo={channelLogo}
 *
 * HUD behavior:
 * - HUD appears on any key press (except BACK)
 * - Auto-hides after 4 seconds of inactivity
 * - BACK hides HUD first, then exits on second press
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

    // ── Which channel is currently playing ─────────────────
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

    // ── ExoPlayer ──────────────────────────────────────────
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

    // Rebuild player when channel switches
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

    // ── HUD visibility -------------------------------------------------------
    var isHudVisible by remember { mutableStateOf(true) }
    var hudTimerReset by remember { mutableStateOf(0L) }

    // Auto-hide after 4s of idle
    LaunchedEffect(isHudVisible, hudTimerReset) {
        if (isHudVisible) {
            delay(4000)
            isHudVisible = false
        }
    }

    // Local now-tick for progress bar smoothness (1s granularity)
    var localNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            localNow = System.currentTimeMillis()
            delay(1000)
        }
    }

    // ── Key handling ───────────────────────────────────────
    fun showHud() {
        isHudVisible = true
        hudTimerReset = System.nanoTime() // bump to restart the auto-hide timer
    }

    val previewKeyHandler: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionUp, Key.DirectionDown,
                Key.DirectionLeft, Key.DirectionRight,
                Key.Enter, Key.NumPadEnter, Key.Menu -> {
                    showHud()
                    false // don't consume — let other handlers process
                }
                else -> false
            }
        } else false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent(previewKeyHandler)
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

        // 2. HUD overlay — auto-hides after 4s idle
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            PlayerHud(
                channelName = currentChannel?.name ?: channelName,
                channelGroup = currentChannel?.group,
                currentProgram = currentChannel?.let { ch ->
                    state.currentPrograms[ch.id]
                },
                nowTick = localNow
            )
        }
    }

    // ── Back: hide HUD first, then exit ────────────────────
    BackHandler(enabled = true) {
        if (isHudVisible) {
            isHudVisible = false
            // DON'T bump hudTimerReset — we want to hide, not restart the timer
        } else {
            onBackPress()
        }
    }
}
