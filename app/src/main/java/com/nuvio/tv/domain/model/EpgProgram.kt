package com.nuvio.tv.domain.model

data class EpgProgram(
    val channelTvgId: String,       // Reference to the channel's tvg-id
    val title: String,
    val description: String? = null,
    val startTime: Long,            // epoch millis UTC
    val endTime: Long               // epoch millis UTC
) {
    val isNow: Boolean
        get() {
            val now = System.currentTimeMillis()
            return startTime <= now && endTime > now
        }
}
