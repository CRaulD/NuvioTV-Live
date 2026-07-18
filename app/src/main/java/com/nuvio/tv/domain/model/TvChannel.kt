package com.nuvio.tv.domain.model

data class TvChannel(
    val id: String,              // tvg-id do M3U
    val name: String,            // Nome do canal
    val url: String,             // URL do stream
    val logo: String?,           // URL do logo
    val group: String?,          // group-title (categoria)
    val tvgName: String?,        // tvg-name
    val tvgLogo: String?         // tvg-logo (pode diferir do logo)
) {
    /** Unique key for favorites — uses URL (unique per M3U line), falls back to tvg-id. */
    val favoriteKey: String get() = if (url.isNotBlank()) url else id
}
