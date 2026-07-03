package com.nuvio.tv.ui.screens.iptv

/**
 * Zonas de foco D-pad mapeadas às 3 colunas físicas:
 *
 * ┌─Col 1────┐  ┌──Col 2──────────┐  ┌─Col 3──────┐
 * │          │  │  SEARCH (topo)   │  │            │
 * │ SIDEBAR  │←│→  LIST (canais) │←│→  EPG       │
 * │          │  │                  │  │            │
 * └──────────┘  └─────────────────┘  └────────────┘
 *
 * LEFT/RIGHT = entre as 3 colunas físicas
 * UP/DOWN    = vertical dentro da zona
 * SEARCH e LIST estão na MESMA coluna: DOWN de SEARCH → LIST, UP de LIST index 0 → SEARCH
 */
enum class FocusZone { SIDEBAR, SEARCH, LIST, EPG }

data class FocusState(
    val zone: FocusZone = FocusZone.LIST,
    val index: Int = 0
) {
    companion object {
        private val zoneOrder = listOf(
            FocusZone.SIDEBAR,
            FocusZone.SEARCH,
            FocusZone.LIST,
            FocusZone.EPG
        )

        /** Próxima zona à direita (sidebar → list → epg). SEARCH pula para EPG. */
        fun nextZone(current: FocusZone): FocusZone = when (current) {
            FocusZone.SIDEBAR -> FocusZone.LIST
            FocusZone.SEARCH -> FocusZone.EPG
            FocusZone.LIST -> FocusZone.EPG
            FocusZone.EPG -> FocusZone.EPG  // já está na última
        }

        /** Zona anterior à esquerda (epg → list → sidebar). SEARCH volta para sidebar. */
        fun prevZone(current: FocusZone, midColumnInSearchMode: Boolean = false): FocusZone = when (current) {
            FocusZone.SIDEBAR -> FocusZone.SIDEBAR  // já está na primeira
            FocusZone.SEARCH -> FocusZone.SIDEBAR
            FocusZone.LIST -> FocusZone.SIDEBAR
            FocusZone.EPG -> if (midColumnInSearchMode) FocusZone.SEARCH else FocusZone.LIST
        }

        /** Retorna o índice máximo válido para a zona, dado o tamanho da lista. */
        fun maxIndex(zone: FocusZone, listSize: Int, epgSize: Int): Int = when (zone) {
            FocusZone.SIDEBAR -> listSize.coerceAtLeast(1) - 1
            FocusZone.SEARCH -> 0  // search é único
            FocusZone.LIST -> listSize.coerceAtLeast(1) - 1
            FocusZone.EPG -> epgSize.coerceAtLeast(1) - 1
        }
    }
}
