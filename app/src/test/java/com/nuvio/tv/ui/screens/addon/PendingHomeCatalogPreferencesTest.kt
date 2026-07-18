package com.nuvio.tv.ui.screens.addon

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingHomeCatalogPreferencesTest {

    @Test
    fun `resolver keeps addon catalog and collection disabled keys together`() {
        val resolved = resolvePendingHomeCatalogPreferences(
            proposedCatalogOrderKeys = listOf(
                "catalog_movie_featured",
                "collection_favorites",
                "missing_key",
                "catalog_movie_featured"
            ),
            proposedDisabledCatalogKeys = listOf(
                "catalog_movie_featured",
                "missing_catalog",
                "catalog_movie_featured"
            ),
            proposedDisabledCollectionKeys = listOf(
                "collection_favorites",
                "missing_collection",
                "collection_favorites"
            ),
            availableCatalogKeys = setOf("catalog_movie_featured"),
            availableDisableKeys = setOf("catalog_movie_featured"),
            collectionKeys = setOf("collection_favorites")
        )

        assertEquals(
            listOf("catalog_movie_featured", "collection_favorites"),
            resolved.orderKeys
        )
        assertEquals(
            listOf("catalog_movie_featured", "collection_favorites"),
            resolved.disabledKeys
        )
    }

    @Test
    fun `resolver can clear disabled collections while preserving disabled catalogs`() {
        val resolved = resolvePendingHomeCatalogPreferences(
            proposedCatalogOrderKeys = listOf("catalog_movie_featured", "collection_favorites"),
            proposedDisabledCatalogKeys = listOf("catalog_movie_featured"),
            proposedDisabledCollectionKeys = emptyList(),
            availableCatalogKeys = setOf("catalog_movie_featured"),
            availableDisableKeys = setOf("catalog_movie_featured"),
            collectionKeys = setOf("collection_favorites")
        )

        assertEquals(
            listOf("catalog_movie_featured", "collection_favorites"),
            resolved.orderKeys
        )
        assertEquals(listOf("catalog_movie_featured"), resolved.disabledKeys)
    }
}
