package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Footer de navegação D-pad para telas IPTV.
 * Mostra dicas de navegação semi-transparentes fixas na parte inferior.
 */
@Composable
fun IptvFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.5f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(
                R.string.iptv_footer_text,
                stringResource(R.string.iptv_navigate),
                stringResource(R.string.iptv_select),
                stringResource(R.string.iptv_hold_to_favorite),
                stringResource(R.string.iptv_menu_navigation)
            ),
            color = NuvioTheme.colors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
