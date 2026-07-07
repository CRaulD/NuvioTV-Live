package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

// 45.dp/s ≈ 5.3 chars/s — noticeably faster than Compose's 30.dp/s default while staying well below
// the ~8.5 cps comprehension threshold. Matches FocusMarqueeText/StreamBadgeChips/PlaybackSettings.
private val MarqueeVelocity = 45.dp

/**
 * Texto com letreiro horizontal (marquee TV-style).
 *
 * Usa [Modifier.basicMarquee] — a API pública do Compose — que mede a largura real do texto
 * via o layout system. Diferente de uma animação manual, não há divergência entre a largura
 * medida e a largura renderizada, então o texto completo aparece antes do loop reiniciar.
 *
 * [isActive] ativa o marquee; quando inativo, o texto é truncado com reticências.
 * O [basicMarquee] é no-op quando o texto já cabe no container, então texto curto não rola.
 */
@Composable
fun MarqueeText(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NuvioTheme.colors.TextPrimary,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        modifier = if (isActive) {
            modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = MarqueeVelocity)
        } else {
            modifier
        },
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = if (isActive) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}
