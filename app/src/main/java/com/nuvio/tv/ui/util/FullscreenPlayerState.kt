package com.nuvio.tv.ui.util

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * CompositionLocal para comunicar modo fullscreen do player IPTV
 * ao MainActivity, que precisa esconder o drawer lateral.
 */
val LocalIsPlayerFullscreen = compositionLocalOf { mutableStateOf(false) }
