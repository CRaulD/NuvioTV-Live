package com.nuvio.tv.ui.screens.addon

internal data class ResolvedHomeCatalogPreferences(
    val orderKeys: List<String>,
    val disabledKeys: List<String>
)

internal fun resolvePendingHomeCatalogPreferences(
    proposedCatalogOrderKeys: List<String>,
    proposedDisabledCatalogKeys: List<String>,
    proposedDisabledCollectionKeys: List<String>,
    availableCatalogKeys: Set<String>,
    availableDisableKeys: Set<String>,
    collectionKeys: Set<String>
): ResolvedHomeCatalogPreferences {
    val allValidOrderKeys = availableCatalogKeys + collectionKeys
    val validCatalogOrder = proposedCatalogOrderKeys
        .asSequence()
        .filter { it in allValidOrderKeys }
        .distinct()
        .toList()
    val validDisabledCatalogs = proposedDisabledCatalogKeys
        .asSequence()
        .filter { it in availableDisableKeys }
        .distinct()
        .toList()
    val validDisabledCollections = proposedDisabledCollectionKeys
        .asSequence()
        .filter { it in collectionKeys }
        .distinct()
        .toList()

    return ResolvedHomeCatalogPreferences(
        orderKeys = validCatalogOrder,
        disabledKeys = (validDisabledCatalogs + validDisabledCollections).distinct()
    )
}
