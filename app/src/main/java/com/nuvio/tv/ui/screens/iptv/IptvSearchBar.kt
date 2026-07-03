package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun IptvSearchBar(
    searchQuery: String,
    isSearchActive: Boolean,
    isFocused: Boolean = false,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                NuvioTheme.colors.BackgroundCard,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isSearchActive || isFocused) Modifier.border(
                    1.dp, NuvioTheme.colors.FocusRing, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .onFocusChanged { onSearchFocusChange(it.isFocused) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search icon
            androidx.compose.material3.Text(
                text = "\u2315", // ⌕
                color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.4f),
                fontSize = 18.sp
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(start = 8.dp)
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                readOnly = !isSearchActive,
                singleLine = true,
                textStyle = TextStyle(
                    color = if (isSearchActive) NuvioTheme.colors.TextPrimary
                    else NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f),
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty() && !isSearchActive) {
                            androidx.compose.material3.Text(
                                text = stringResource(R.string.iptv_search_placeholder),
                                color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.3f),
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}
