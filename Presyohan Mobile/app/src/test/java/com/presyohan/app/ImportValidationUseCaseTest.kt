package com.presyohan.app

import org.junit.Assert.*
import org.junit.Test

class ImportValidationUseCaseTest {

    @Test
    fun test_validation_assigns_new_update_and_duplicates() {
        val useCase = ImportValidationUseCase()

        // Existing DB product
        val dbProds = listOf(
            DbProduct(
                id = "db-apple-id",
                category_id = "cat-id",
                name = "Apple",
                description = "Red apple",
                price = 100.0,
                unit = "kilo"
            )
        )

        // Session draft items
        val item1 = DraftItem(
            draftItemId = "item1",
            categoryName = "Fruits",
            productName = "Apple",
            description = "Red apple",
            unit = "kilo",
            priceText = "120.0",
            price = 120.0,
            source = ImportSource.SIMPLE_MANUAL
        )
        val item2 = DraftItem(
            draftItemId = "item2",
            categoryName = "Fruits",
            productName = "Banana",
            description = null,
            unit = "piece",
            priceText = "15.0",
            price = 15.0,
            source = ImportSource.SIMPLE_MANUAL
        )
        val item3 = DraftItem(
            draftItemId = "item3",
            categoryName = "Fruits",
            productName = "Banana", // Duplicate in same import list
            description = null,
            unit = "piece",
            priceText = "15.0",
            price = 15.0,
            source = ImportSource.SIMPLE_MANUAL
        )

        val cat = DraftCategory(
            draftCategoryId = "cat1",
            name = "Fruits",
            items = mutableListOf(item1, item2, item3)
        )

        val session = DraftImportSession(
            sessionId = "session-1",
            storeId = "store-1",
            categories = mutableListOf(cat)
        )

        val validated = useCase.validate(session, dbProds)
        val items = validated.categories[0].items

        // Apple matches DB → UPDATE
        assertEquals(ValidationStatus.UPDATE, items[0].validationStatus)
        assertEquals("db-apple-id", items[0].productId)
        assertTrue(items[0].validationErrors.isEmpty())

        // First Banana → NEW
        assertEquals(ValidationStatus.NEW, items[1].validationStatus)
        assertNull(items[1].productId)
        assertTrue(items[1].validationErrors.isEmpty())

        // Second Banana → DUPLICATE (in import list)
        assertEquals(ValidationStatus.DUPLICATE, items[2].validationStatus)
        assertTrue(items[2].validationErrors.contains(ValidationError.DUPLICATE_IN_IMPORT))
    }

    @Test
    fun test_validation_negative_price_and_empty_name() {
        val useCase = ImportValidationUseCase()

        val item1 = DraftItem(
            draftItemId = "item1",
            categoryName = "Fruits",
            productName = "", // Empty name
            unit = "pc",
            priceText = "10",
            price = 10.0,
            source = ImportSource.SIMPLE_MANUAL
        )
        val item2 = DraftItem(
            draftItemId = "item2",
            categoryName = "Fruits",
            productName = "Apple",
            unit = "pc",
            priceText = "-5",
            price = -5.0, // Negative price
            source = ImportSource.SIMPLE_MANUAL
        )

        val cat = DraftCategory(
            draftCategoryId = "cat1",
            name = "Fruits",
            items = mutableListOf(item1, item2)
        )

        val session = DraftImportSession(
            sessionId = "session-1",
            storeId = "store-1",
            categories = mutableListOf(cat)
        )

        val validated = useCase.validate(session, emptyList())
        val items = validated.categories[0].items

        assertEquals(ValidationStatus.INVALID, items[0].validationStatus)
        assertTrue(items[0].validationErrors.contains(ValidationError.EMPTY_PRODUCT_NAME))

        assertEquals(ValidationStatus.INVALID, items[1].validationStatus)
        assertTrue(items[1].validationErrors.contains(ValidationError.NEGATIVE_PRICE))
    }

    @Test
    fun test_validation_ambiguous_db_duplicates() {
        val useCase = ImportValidationUseCase()

        // Database has duplicate entries with same name/description
        val dbProds = listOf(
            DbProduct(id = "id1", category_id = "c", name = "Orange", description = null),
            DbProduct(id = "id2", category_id = "c", name = "Orange", description = null)
        )

        val item = DraftItem(
            draftItemId = "item1",
            categoryName = "Fruits",
            productName = "Orange",
            unit = "pc",
            priceText = "10.0",
            price = 10.0,
            source = ImportSource.SIMPLE_MANUAL
        )

        val cat = DraftCategory(
            draftCategoryId = "cat1",
            name = "Fruits",
            items = mutableListOf(item)
        )

        val session = DraftImportSession(
            sessionId = "session-1",
            storeId = "store-1",
            categories = mutableListOf(cat)
        )

        val validated = useCase.validate(session, dbProds)
        val items = validated.categories[0].items

        assertEquals(ValidationStatus.INVALID, items[0].validationStatus)
        assertTrue(items[0].validationErrors.contains(ValidationError.DUPLICATE_IN_DATABASE_AMBIGUOUS))
    }
}
