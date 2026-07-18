package com.nuvio.tv.core.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgParserTest {

    @Test
    fun `normalizeChannelName strips FHD correctly`() {
        assertEquals("globo", EpgParser.normalizeChannelName("Globo FHD"))
    }

    @Test
    fun `normalizeChannelName strips UHD correctly`() {
        assertEquals("canal", EpgParser.normalizeChannelName("Canal UHD"))
    }

    @Test
    fun `normalizeChannelName strips HD correctly`() {
        assertEquals("band", EpgParser.normalizeChannelName("Band HD"))
    }

    @Test
    fun `normalizeChannelName strips 4K correctly`() {
        assertEquals("esporte", EpgParser.normalizeChannelName("Esporte 4K"))
    }

    @Test
    fun `normalizeChannelName strips SD correctly`() {
        assertEquals("globo", EpgParser.normalizeChannelName("GLOBO SD"))
    }

    @Test
    fun `normalizeChannelName handles FHD before HD partial match`() {
        // If FHD is stripped before HD, "Globo FHD" -> "globo" not "globo f"
        assertEquals("globo", EpgParser.normalizeChannelName("Globo FHD"))
    }

    @Test
    fun `normalizeChannelName handles multiple suffixes`() {
        assertEquals("canal", EpgParser.normalizeChannelName("Canal HD FHD"))
    }

    @Test
    fun `normalizeChannelName does not alter name without suffix`() {
        assertEquals("globo news", EpgParser.normalizeChannelName("Globo News"))
    }

    @Test
    fun `normalizeChannelName lowercases correctly`() {
        assertEquals("record tv", EpgParser.normalizeChannelName("Record TV"))
    }
}
