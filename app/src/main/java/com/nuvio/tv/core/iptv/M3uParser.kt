package com.nuvio.tv.core.iptv

import com.nuvio.tv.domain.model.TvChannel

/**
 * Streaming M3U/M3U+ playlist parser (line by line).
 * Supports:
 * - #EXTM3U header
 * - #EXTINF with attributes: tvg-id, tvg-name, tvg-logo, group-title
 * - Extended M3U with quoted attributes
 * - Optional BOM at file start
 * - Comments (#EXT-X-*, #KODIPROP, etc.)
 */
object M3uParser {

    fun parse(content: String): List<TvChannel> {
        val channels = mutableListOf<TvChannel>()
        val lines = content.lines()

        var currentExtInf: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty() || trimmed.startsWith("#EXTM3U")) {
                continue
            }

            if (trimmed.startsWith("#EXTINF:")) {
                currentExtInf = trimmed
                continue
            }

            if (trimmed.startsWith("#")) {
                // Other comment/tags — skip
                continue
            }

            // URL line — pairs with the last #EXTINF
            val url = trimmed
            val extInf = currentExtInf
            if (url.isNotEmpty() && extInf != null) {
                val channel = parseChannel(extInf, url)
                channels.add(channel)
                currentExtInf = null
            }
        }

        return channels
    }

    private fun parseChannel(extInf: String, url: String): TvChannel {
        // Extract name (after the last comma)
        val name = extractName(extInf)

        // Extract attributes tvg-id, tvg-name, tvg-logo, group-title
        val tvgId = extractAttribute(extInf, "tvg-id") ?: name
        val tvgName = extractAttribute(extInf, "tvg-name")
        val tvgLogo = extractAttribute(extInf, "tvg-logo")
        val groupTitle = extractAttribute(extInf, "group-title")

        return TvChannel(
            id = tvgId,
            name = name,
            url = url,
            logo = tvgLogo,
            group = groupTitle,
            tvgName = tvgName,
            tvgLogo = tvgLogo
        )
    }

    /**
     * Extract channel name from #EXTINF.
     * Format: #EXTINF:-1 attributes,Channel Name
     */
    private fun extractName(extInf: String): String {
        val commaIndex = extInf.lastIndexOf(',')
        if (commaIndex < 0) return "Unknown"
        return extInf.substring(commaIndex + 1).trim()
    }

    /**
     * Extract an attribute from #EXTINF by name.
     * Supports quoted and unquoted values.
     * Ex: tvg-id="globo" or tvg-id=globo
     */
    private fun extractAttribute(extInf: String, attrName: String): String? {
        val regex = Regex("""$attrName\s*=\s*"([^"]*)"|$attrName\s*=\s*(\S+)""")
        val match = regex.find(extInf) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
            .ifBlank { null }
    }
}
