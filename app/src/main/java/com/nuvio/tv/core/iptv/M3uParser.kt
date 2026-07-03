package com.nuvio.tv.core.iptv

import com.nuvio.tv.domain.model.TvChannel

/**
 * Parser de playlist M3U/M3U+ em modo streaming (linha a linha).
 * Suporta:
 * - #EXTM3U header
 * - #EXTINF com atributos: tvg-id, tvg-name, tvg-logo, group-title
 * - Extended M3U com atributos entre aspas
 * - BOM opcional no início do arquivo
 * - Comentários (#EXT-X-*, #KODIPROP, etc.)
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
                // Outros comentários/tags — ignorar
                continue
            }

            // Linha com URL — pareia com o último #EXTINF
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
        // Extrai o nome (depois do último vírgula)
        val name = extractName(extInf)

        // Extrai atributos tvg-id, tvg-name, tvg-logo, group-title
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
     * Extrai o nome do canal do #EXTINF.
     * O formato é: #EXTINF:-1 atributos,Nome do Canal
     */
    private fun extractName(extInf: String): String {
        val commaIndex = extInf.lastIndexOf(',')
        if (commaIndex < 0) return "Unknown"
        return extInf.substring(commaIndex + 1).trim()
    }

    /**
     * Extrai um atributo do #EXTINF pelo nome.
     * Suporta valores com e sem aspas.
     * Ex: tvg-id="globo" ou tvg-id=globo
     */
    private fun extractAttribute(extInf: String, attrName: String): String? {
        val regex = Regex("""$attrName\s*=\s*"([^"]*)"|$attrName\s*=\s*(\S+)""")
        val match = regex.find(extInf) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
            .ifBlank { null }
    }
}
