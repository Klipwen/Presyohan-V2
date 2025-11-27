package com.presyohan.app

import org.junit.Assert.*
import org.junit.Test

class ImportManagerTest {

    private class FakeRepo : ImportRepository {
        val categories = mutableMapOf<String, String>()
        val saved = mutableListOf<Triple<String, String, ParsedItem>>() // storeId, categoryId, item

        override suspend fun ensureCategory(storeId: String, name: String, cache: MutableMap<String, String>): String {
            val existing = cache[name] ?: categories[name]
            if (existing != null) return existing
            val id = "c_${name.lowercase()}"
            categories[name] = id
            cache[name] = id
            return id
        }

        override suspend fun addOrUpdateProduct(storeId: String, categoryId: String, item: ParsedItem): Boolean {
            saved.add(Triple(storeId, categoryId, item))
            return true
        }
    }

    @Test
    fun imports_only_valid_items_and_keeps_referential_integrity() {
        val repo = FakeRepo()
        val manager = ImportManager(repo)

        val categories = listOf(
            ParsedCategory("DRINKS", mutableListOf(
                ParsedItem("Cola", null, "can", 25.0, ItemStatus.NEW),
                ParsedItem("Tea", null, "cup", null, ItemStatus.ERROR_NO_PRICE)
            )),
            ParsedCategory("FOOD", mutableListOf(
                ParsedItem("Burger", null, "pcs", 85.0, ItemStatus.UPDATE)
            ))
        )

        val result = kotlinx.coroutines.runBlocking {
            manager.performImport("store-1", categories, mutableMapOf())
        }

        // Attempted = 2 (NEW + UPDATE), saved = 2
        assertEquals(2, result.attemptedCount)
        assertEquals(2, result.savedCount)
        assertEquals(2, result.categoryCount)

        // Check referential integrity: items saved with correct category ids
        val drinksId = repo.categories["DRINKS"]
        val foodId = repo.categories["FOOD"]
        assertNotNull(drinksId)
        assertNotNull(foodId)
        assertTrue(repo.saved.any { it.second == drinksId && it.third.name == "Cola" })
        assertTrue(repo.saved.any { it.second == foodId && it.third.name == "Burger" })
        // Invalid item not saved
        assertFalse(repo.saved.any { it.third.name == "Tea" && it.third.price == null })
    }
}

