package com.nuvio.tv.ui.screens.iptv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Locale

@Composable
fun IptvSetupScreen(
    onSave: () -> Unit,
    viewModel: IptvViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var m3uUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        if (uiState.qrServerActive) {
            // QR mode overlay
            QrModePanel(
                url = uiState.qrServerUrl ?: "",
                onClose = { viewModel.stopQrMode() }
            )
        } else {
            // Normal setup form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.iptv_setup_title),
                    color = NuvioTheme.colors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = stringResource(R.string.iptv_m3u_url_label),
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
                                text = stringResource(R.string.iptv_m3u_url_placeholder),
                                color = NuvioTheme.colors.TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.iptv_epg_url_label),
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
                                text = stringResource(R.string.iptv_epg_url_placeholder),
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
                    Text(stringResource(R.string.iptv_save_btn), color = NuvioTheme.colors.TextPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // QR mode button
                Button(
                    onClick = {
                        val ip = DeviceIpAddress.get(context)
                        if (ip != null) {
                            viewModel.startQrMode(ip)
                        }
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Primary.copy(alpha = 0.2f),
                        focusedContainerColor = NuvioTheme.colors.Primary.copy(alpha = 0.4f),
                        contentColor = NuvioTheme.colors.TextPrimary
                    )
                ) {
                    Text(stringResource(R.string.iptv_qr_btn))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // EPG refresh button
                Button(
                    onClick = { viewModel.forceRefreshEpg() },
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Primary.copy(alpha = 0.2f),
                        focusedContainerColor = NuvioTheme.colors.Primary.copy(alpha = 0.4f),
                        contentColor = NuvioTheme.colors.TextPrimary
                    )
                ) {
                    Text(stringResource(R.string.iptv_epg_refresh_btn))
                }
            }
        }
    }
}

@Composable
private fun QrModePanel(
    url: String,
    onClose: () -> Unit
) {
    val qrBitmap = remember(url) {
        runCatching { QrCodeGenerator.generate(url, 400, margin = 2) }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.iptv_qr_title),
            color = NuvioTheme.colors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.iptv_qr_instruction),
            color = NuvioTheme.colors.TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // QR code
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier
                    .size(250.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.iptv_qr_error),
                    color = NuvioTheme.colors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // URL text
        Text(
            text = url,
            color = NuvioTheme.colors.Primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onClose,
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                contentColor = NuvioTheme.colors.TextSecondary
            )
        ) {
            Text(stringResource(R.string.iptv_qr_back_btn))
        }
    }
}
