package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun IptvSetupScreen(
    onSave: () -> Unit,
    viewModel: IptvViewModel = hiltViewModel()
) {
    var m3uUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Configurar TV ao Vivo",
                color = NuvioTheme.colors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "URL da Playlist M3U",
                color = NuvioTheme.colors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            BasicTextField(
                value = m3uUrl,
                onValueChange = { m3uUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusTarget()
                    .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                textStyle = TextStyle(
                    color = NuvioTheme.colors.TextPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (m3uUrl.isEmpty()) {
                        Text(
                            text = "https://m3u4u.com/m3u/...",
                            color = NuvioTheme.colors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "URL do EPG (opcional)",
                color = NuvioTheme.colors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            BasicTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusTarget()
                    .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                textStyle = TextStyle(
                    color = NuvioTheme.colors.TextPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (epgUrl.isEmpty()) {
                        Text(
                            text = "https://m3u4u.com/xml/...",
                            color = NuvioTheme.colors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveUrls(m3uUrl, epgUrl)
                    onSave()
                },
                enabled = m3uUrl.isNotBlank()
            ) {
                Text("Salvar", color = NuvioTheme.colors.TextPrimary)
            }
        }
    }
}
