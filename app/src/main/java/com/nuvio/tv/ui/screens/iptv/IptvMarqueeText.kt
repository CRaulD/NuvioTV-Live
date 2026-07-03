package com.nuvio.tv.ui.screens.iptv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Texto com letreiro horizontal (marquee TV-style).
 *
 * A Row mantém SEMPRE a mesma estrutura (texto + gap + duplicata)
 * para evitar relayout ao ativar/desativar. A animação controla
 * apenas o translationX via graphicsLayer.
 *
 * Velocidade constante: ~35 px/s.
 */
@Composable
fun MarqueeText(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NuvioTheme.colors.TextPrimary,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    gap: Dp = 40.dp
) {
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }

    // Velocidade constante ~35 px/s para leitura confortável em TV
    val speedPxPerSec = 35f

    LaunchedEffect(isActive, text) {
        if (!isActive) {
            offsetX.snapTo(0f)
        } else {
            val gapPx = with(density) { gap.toPx() }
            val approxCharWidth = with(density) { fontSize.toPx() * 0.55f }
            val textWidthPx = text.length * approxCharWidth
            val distance = textWidthPx + gapPx

            if (distance <= 0f) return@LaunchedEffect

            val durationMs = (distance / speedPxPerSec * 1000).toInt().coerceIn(2000, 12000)

            while (true) {
                offsetX.snapTo(0f)
                offsetX.animateTo(
                    targetValue = -distance,
                    animationSpec = tween(
                        durationMillis = durationMs,
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    val currentOffsetX by offsetX.asState()

    Box(
        modifier = modifier
            .clipToBounds()
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    translationX = if (isActive) currentOffsetX else 0f
                }
        ) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Visible
            )

            Spacer(modifier = Modifier.width(gap))
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
        }
    }
}
