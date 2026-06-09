package com.presyohan.app

import org.junit.Assert.*
import org.junit.Test

class ImportManagerTest {

    private class FakeRepo : ImportRepository {
        val categories = mutableMapOf<String, String>()
        val saved = mutableListOf<Triple<String, String, ParsedItem>>() // storeId, categoryId, item
        val savedDraft = mutableListOf<Triple<String, String, DraftItem>>() // storeId, categoryId, item

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

        override suspend fun addOrUpdateDraftProduct(storeId: String, categoryId: String, item: DraftItem): Boolean {
            savedDraft.add(Triple(storeId, categoryId, item))
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

    @Test
    fun test_performDraftImport_saves_only_new_and_update_items() {
        val repo = FakeRepo()
        val manager = ImportManager(repo)

        val itemNew = DraftItem(
            draftItemId = "id-1",
            categoryName = "DRINKS",
            productName = "Cola",
            unit = "can",
            price = 25.0,
            validationStatus = ValidationStatus.NEW
        )
        val itemUpdate = DraftItem(
            draftItemId = "id-2",
            categoryName = "DRINKS",
            productName = "Pepsi",
            unit = "can",
            price = 24.0,
            validationStatus = ValidationStatus.UPDATE,
            productId = "db-pepsi-id"
        )
        val itemDuplicate = DraftItem(
            draftItemId = "id-3",
            categoryName = "DRINKS",
            productName = "Cola",
            unit = "can",
            price = 25.0,
            validationStatus = ValidationStatus.DUPLICATE
        )
        val itemInvalid = DraftItem(
            draftItemId = "id-4",
            categoryName = "DRINKS",
            productName = "Bad Item",
            unit = "can",
            price = -5.0,
            validationStatus = ValidationStatus.INVALID
        )

        val cat = DraftCategory(
            draftCategoryId = "cat-id",
            name = "DRINKS",
            items = mutableListOf(itemNew, itemUpdate, itemDuplicate, itemInvalid)
        )

        val session = DraftImportSession(
            sessionId = "session-1",
            storeId = "store-1",
            categories = mutableListOf(cat)
        )

        val result = kotlinx.coroutines.runBlocking {
            manager.performDraftImport(session, mutableMapOf())
        }

        // Only NEW and UPDATE should be saved (2 items)
        assertEquals(2, result.attemptedCount)
        assertEquals(2, result.savedCount)
        assertEquals(1, result.categoryCount)

        assertEquals(2, repo.savedDraft.size)
        assertTrue(repo.savedDraft.any { it.third.productName == "Cola" && it.third.validationStatus == ValidationStatus.NEW })
        assertTrue(repo.savedDraft.any { it.third.productName == "Pepsi" && it.third.validationStatus == ValidationStatus.UPDATE })
        assertFalse(repo.savedDraft.any { it.third.productName == "Bad Item" })
    }
}

