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
 * Parser EPG XMLTV em streaming via SAX (não carrega o XML inteiro na RAM).
 *
 * Formato do XMLTV:
 * ```xml
 * <?xml version="1.0" encoding="UTF-8"?>
 * <tv>
 *   <programme start="20260101000000 +0000" stop="20260101013000 +0000" channel="globo">
 *     <title>Jornal Nacional</title>
 *     <desc>Telejornal...</desc>
 *   </programme>
 * </tv>
 * ```
 */
object EpgParser {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Parseia XMLTV de um InputStream (streaming SAX).
     * Retorna lista de EpgProgram, ou lista vazia em caso de erro.
     */
    fun parse(inputStream: InputStream): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()

        try {
            val factory = SAXParserFactory.newInstance()
            val saxParser = factory.newSAXParser()
            val handler = object : DefaultHandler() {
                private var currentProgram: ProgrammeBuilder? = null
                private var currentElement: StringBuilder? = null

                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes?
                ) {
                    val tag = qName?.lowercase() ?: return
                    when (tag) {
                        "programme" -> {
                            currentProgram = ProgrammeBuilder(
                                start = attributes?.getValue("start") ?: "",
                                stop = attributes?.getValue("stop") ?: "",
                                channel = attributes?.getValue("channel") ?: ""
                            )
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

            saxParser.parse(inputStream, handler)
        } catch (e: Exception) {
            // Log would go here — return partial results
        }

        return programs
    }

    /**
     * Parseia timestamp XMLTV no formato "YYYYMMDDHHMMSS ±0000".
     * Retorna epoch millis UTC, ou null se não conseguir parsear.
     */
    private fun parseTimestamp(timestamp: String): Long? {
        if (timestamp.isBlank()) return null
        // Aceita tanto "20260101000000 +0000" quanto "20260101000000"
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

    private data class ProgrammeBuilder(
        val start: String,
        val stop: String,
        val channel: String,
        var title: String = "",
        var description: String? = null
    )
}
