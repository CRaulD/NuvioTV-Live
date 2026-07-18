package com.nuvio.tv.core.iptv

import com.nuvio.tv.domain.model.EpgProgram
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Streaming SAX EPG XMLTV parser (does not load the full XML into RAM).
 *
 * XMLTV format:
 * ```xml
 * <?xml version="1.0" encoding="UTF-8"?>
 * <tv>
 *   <channel id="HBO.br">
 *     <display-name>HBO</display-name>
 *   </channel>
 *   <programme start="20260101000000 +0000" stop="20260101013000 +0000" channel="HBO.br">
 *     <title>Jornal Nacional</title>
 *     <desc>Telejornal...</desc>
 *   </programme>
 * </tv>
 * ```
 *
 * Returns EpgData containing both programs and channel name mappings.
 */
data class EpgData(
    val programs: List<EpgProgram>,
    /** channelTvgId → normalized display name (lowercase, stripped) */
    val channelNames: Map<String, String>,
    /** true when SAX parse completed without throwing */
    val isParseSuccess: Boolean = false
)

object EpgParser {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Single compiled regex to strip resolution suffixes from channel names. Order matters: longer patterns first. */
    private val RESOLUTION_SUFFIX = Regex("fhd|uhd|4k|hd|sd", RegexOption.IGNORE_CASE)

    /** Strip location prefixes like "São Paulo/SP " from EPG channel names. */
    private val LOCATION_PREFIX = Regex("^[a-zà-ÿ\\s]+/[a-z]{2}\\s+", RegexOption.IGNORE_CASE)

    /**
     * Parse XMLTV from an InputStream (streaming SAX).
     * Returns EpgData with programs and channel name mappings, or empty on error.
     *
     * The [inputStream] is closed by this method. The caller remains owner of the
     * original Response/ResponseBody lifecycle. Double-close is a no-op.
     */
    fun parse(inputStream: InputStream): EpgData {
        val programs = mutableListOf<EpgProgram>()
        val channelNames = mutableMapOf<String, String>()

        try {
            val factory = SAXParserFactory.newInstance()
            val saxParser = factory.newSAXParser()
            val handler = object : DefaultHandler() {
                private var currentProgram: ProgrammeBuilder? = null
                private var currentElement: StringBuilder? = null
                private var currentChannelId: String? = null
                private var currentChannelDisplayName: StringBuilder? = null

                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes?
                ) {
                    val tag = qName?.lowercase() ?: return
                    when (tag) {
                        "channel" -> {
                            currentChannelId = attributes?.getValue("id")
                            currentChannelDisplayName = null // will be set when display-name starts
                        }
                        "programme" -> {
                            currentChannelId = null
                            currentChannelDisplayName = null
                            currentProgram = ProgrammeBuilder(
                                start = attributes?.getValue("start") ?: "",
                                stop = attributes?.getValue("stop") ?: "",
                                channel = attributes?.getValue("channel") ?: ""
                            )
                        }
                        "display-name" -> {
                            if (currentChannelId != null && currentChannelDisplayName == null) {
                                currentChannelDisplayName = StringBuilder()
                            }
                            currentElement = StringBuilder()
                        }
                        "title", "desc" -> {
                            currentElement = StringBuilder()
                        }
                        else -> {
                            currentElement = null
                        }
                    }
                }

                override fun characters(ch: CharArray?, start: Int, length: Int) {
                    currentElement?.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val tag = qName?.lowercase() ?: return
                    when (tag) {
                        "channel" -> {
                            val id = currentChannelId
                            val name = currentChannelDisplayName?.toString()?.trim()
                            if (id != null && !name.isNullOrBlank()) {
                                channelNames[id] = normalizeChannelName(name)
                            }
                            currentChannelId = null
                            currentChannelDisplayName = null
                        }
                        "display-name" -> {
                            // Append the display-name content to channel name
                            val text = currentElement?.toString()?.trim()
                            if (text != null && currentChannelDisplayName != null && text.isNotBlank()) {
                                currentChannelDisplayName?.append(text)
                                currentChannelDisplayName?.append(" ")
                            }
                            currentElement = null
                        }
                        "title" -> {
                            currentProgram?.title = currentElement?.toString()?.trim() ?: ""
                        }
                        "desc" -> {
                            currentProgram?.description = currentElement?.toString()?.trim()
                        }
                        "programme" -> {
                            currentProgram?.let { builder ->
                                val startMs = parseTimestamp(builder.start)
                                val endMs = parseTimestamp(builder.stop)
                                if (startMs != null && endMs != null) {
                                    programs.add(
                                        EpgProgram(
                                            channelTvgId = builder.channel,
                                            title = builder.title,
                                            description = builder.description,
                                            startTime = startMs,
                                            endTime = endMs
                                        )
                                    )
                                }
                            }
                            currentProgram = null
                        }
                    }
                }
            }

            inputStream.use { stream ->
                saxParser.parse(stream, handler)
            }
            return EpgData(programs, channelNames, isParseSuccess = true)
        } catch (e: Exception) {
            android.util.Log.e("EpgParser", "parse failed", e)
            return EpgData(programs, channelNames, isParseSuccess = false)
        }
    }

    /**
     * Parse XMLTV timestamp in "YYYYMMDDHHMMSS ±0000" format.
     * Returns epoch millis UTC, or null if parsing fails.
     */
    private fun parseTimestamp(timestamp: String): Long? {
        if (timestamp.isBlank()) return null
        val clean = if (timestamp.length > 14) {
            timestamp.substring(0, 14) + " " + timestamp.substring(14).trim()
        } else {
            timestamp + " +0000"
        }
        return try {
            dateFormat.parse(clean)?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalize a channel name for fuzzy matching:
     * lowercase, remove HD/FHD/SD/4K suffixes, trim.
     */
    fun normalizeChannelName(name: String): String {
        return name.lowercase()
            .replace(LOCATION_PREFIX, "")
            .replace(RESOLUTION_SUFFIX, "")
            .trim()
    }

    private data class ProgrammeBuilder(
        val start: String,
        val stop: String,
        val channel: String,
        var title: String = "",
        var description: String? = null
    )
}
