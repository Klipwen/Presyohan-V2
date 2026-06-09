package com.presyohan.app

import org.junit.Assert.*
import org.junit.Test

class AddMultipleItemsParserTest {
    @Test
    fun parses_multiple_categories_and_items() {
        val raw = """
            [Beverages]
            Coffee - hot, cup ₱120
            Tea ₱80

            Snacks:
            Chips ₱50
            Tea ₱80
            Cookies - pack ₱100
            Invalid Item no price
        """.trimIndent()

        val existing = setOf("coffee") // Coffee exists → UPDATE
        val categories = AddMultipleItemsParser.parseRawToCategories(raw, existing)

        // Expect 2 categories (Beverages, Snacks) with items
        assertEquals(2, categories.size)
        assertEquals("BEVERAGES", categories[0].name)
        assertEquals("SNACKS", categories[1].name)

        val bev = categories[0].items
        val sna = categories[1].items
        assertEquals(2, bev.size)
        assertEquals(4, sna.size) // includes duplicate Tea and invalid line

        // Coffee update
        assertEquals("Coffee", bev[0].name)
        assertEquals(ItemStatus.UPDATE, bev[0].status)
        assertEquals(120.0, bev[0].price!!, 0.001)

        // Tea new
        assertEquals(ItemStatus.NEW, bev[1].status)

        // Snacks: first Chips NEW, Tea DUPLICATE (in same list), Cookies NEW, Invalid ERROR_NO_PRICE
        assertEquals(ItemStatus.NEW, sna[0].status)
        assertEquals(ItemStatus.DUPLICATE, sna[1].status)
        assertEquals(ItemStatus.NEW, sna[2].status)
        assertEquals(ItemStatus.ERROR_NO_PRICE, sna[3].status)
    }

    @Test
    fun supports_bullets_separators_no_peso_and_default_unit() {
        val raw = """
            ALCOHOL/LIQUOR
            - Red Horse (beer) — 145.00 | bottle
            • Alfonso Light — 375.00 | 1pc
            * Gin — 120
        """.trimIndent()

        val categories = AddMultipleItemsParser.parseRawToCategories(raw, emptySet())
        assertEquals(1, categories.size)
        val items = categories[0].items
        assertEquals(3, items.size)
        assertEquals("Red Horse", items[0].name)
        assertEquals("bottle", items[0].unit)
        assertEquals(145.0, items[0].price!!, 0.001)
        assertEquals("Alfonso Light", items[1].name)
        assertEquals("1pc", items[1].unit)
        assertEquals(375.0, items[1].price!!, 0.001)
        assertEquals("Gin", items[2].name)
        assertEquals("1pc", items[2].unit) // default unit
        assertEquals(120.0, items[2].price!!, 0.001)
    }

    @Test
    fun detects_multiline_items_and_ignores_export_headers() {
        val raw = """
            PRICELIST:
            My Store — Branch
            11/26/2025
            Shared via Presyohan

            [Snacks]
            • Fishball (Sauce)
            ₱5 | stick
        """.trimIndent()

        val categories = AddMultipleItemsParser.parseRawToCategories(raw, emptySet())
        assertEquals(1, categories.size)
        val items = categories[0].items
        assertEquals(1, items.size)
        assertEquals("Fishball", items[0].name)
        assertEquals("Sauce", items[0].description)
        assertEquals(5.0, items[0].price!!, 0.001)
        assertEquals("stick", items[0].unit)
    }

    @Test
    fun flags_items_without_category_context() {
        val raw = """
            - Item A — 10
            - Item B — 12 | pcs
        """.trimIndent()

        val categories = AddMultipleItemsParser.parseRawToCategories(raw, emptySet())
        // Items should be placed under UNCATEGORIZED with error status
        val uncategorized = categories.first { it.name == "UNCATEGORIZED" }
        assertEquals(2, uncategorized.items.size)
        assertEquals(ItemStatus.ERROR_NO_CATEGORY, uncategorized.items[0].status)
        assertEquals(ItemStatus.ERROR_NO_CATEGORY, uncategorized.items[1].status)
    }

    @Test
    fun test_parseTextToResult_fruits_drinks_example() {
        val raw = """
            [FRUITS]
            Apple (Red) - PHP 120.00 | Kilo
            Banana - PHP 90.00 | Dozen

            [DRINKS]
            Coke 1.5L - PHP 95.00 | Bottle
            Water - PHP 20.00 | Bottle
        """.trimIndent()

        val result = AddMultipleItemsParser.parseTextToResult(raw, emptySet())

        assertEquals(2, result.categories.size)
        assertEquals("FRUITS", result.categories[0].name)
        assertEquals("DRINKS", result.categories[1].name)

        val fruits = result.categories[0].items
        assertEquals(2, fruits.size)
        assertEquals("Apple", fruits[0].productName)
        assertEquals("Red", fruits[0].description)
        assertEquals(120.0, fruits[0].price!!, 0.001)
        assertEquals("kilo", fruits[0].unit)
        assertEquals(ValidationStatus.NEW, fruits[0].validationStatus)

        assertEquals("Banana", fruits[1].productName)
        assertNull(fruits[1].description)
        assertEquals(90.0, fruits[1].price!!, 0.001)
        assertEquals("dozen", fruits[1].unit)

        val drinks = result.categories[1].items
        assertEquals(2, drinks.size)
        assertEquals("Coke 1.5L", drinks[0].productName)
        assertEquals(95.0, drinks[0].price!!, 0.001)
        assertEquals("bottle", drinks[0].unit)
    }

    @Test
    fun test_parseTextToResult_negative_price_and_invalid_formats() {
        val raw = """
            [TEST]
            Negative Item - PHP -50.00 | pc
            Malformed Item - 
            Valid Item - PHP 10.00
        """.trimIndent()

        val result = AddMultipleItemsParser.parseTextToResult(raw, emptySet())
        assertEquals(1, result.categories.size)
        val items = result.categories[0].items
        assertEquals(3, items.size)

        // Negative price
        assertEquals("Negative Item", items[0].productName)
        assertEquals(ValidationStatus.INVALID, items[0].validationStatus)
        assertTrue(items[0].validationErrors.contains(ValidationError.NEGATIVE_PRICE))

        // Malformed/empty price
        assertEquals("Malformed Item", items[1].productName)
        assertEquals(ValidationStatus.INVALID, items[1].validationStatus)
        assertTrue(items[1].validationErrors.contains(ValidationError.INVALID_PRICE))

        // Valid Item
        assertEquals("Valid Item", items[2].productName)
        assertEquals(ValidationStatus.NEW, items[2].validationStatus)
        assertEquals(10.0, items[2].price!!, 0.001)
    }

    @Test
    fun test_comma_separated_parsing() {
        val raw = """
            groceries - eggs 10 pc, kape 15 pack, pancit canton 15 pack
            Snacks: chips 12 pc, popcorn 25 g
            Fresh Milk (1L, chocolate flavor) - 150
        """.trimIndent()

        val result = AddMultipleItemsParser.parseTextToResult(raw, emptySet())
        
        // We expect 3 categories: GROCERIES, SNACKS, and UNCATEGORIZED (since Fresh Milk is uncategorized)
        assertEquals(3, result.categories.size)
        
        val groceries = result.categories.first { it.name == "GROCERIES" }
        assertEquals(3, groceries.items.size)
        assertEquals("eggs", groceries.items[0].productName)
        assertEquals(10.0, groceries.items[0].price!!, 0.001)
        assertEquals("pc", groceries.items[0].unit)

        assertEquals("kape", groceries.items[1].productName)
        assertEquals(15.0, groceries.items[1].price!!, 0.001)
        assertEquals("pack", groceries.items[1].unit)

        assertEquals("pancit canton", groceries.items[2].productName)
        assertEquals(15.0, groceries.items[2].price!!, 0.001)
        assertEquals("pack", groceries.items[2].unit)

        val snacks = result.categories.first { it.name == "SNACKS" }
        assertEquals(2, snacks.items.size)
        assertEquals("chips", snacks.items[0].productName)
        assertEquals(12.0, snacks.items[0].price!!, 0.001)
        assertEquals("pc", snacks.items[0].unit)

        assertEquals("popcorn", snacks.items[1].productName)
        assertEquals(25.0, snacks.items[1].price!!, 0.001)
        assertEquals("g", snacks.items[1].unit)

        val uncategorized = result.categories.first { it.name == "UNCATEGORIZED" }
        // Fresh Milk should NOT be split because the comma is inside parenthesis and there is only 1 price
        assertEquals(1, uncategorized.items.size)
        assertEquals("Fresh Milk", uncategorized.items[0].productName)
        assertEquals("1L, chocolate flavor", uncategorized.items[0].description)
        assertEquals(150.0, uncategorized.items[0].price!!, 0.001)
    }

    @Test
    fun test_presyohan_import_parsing() {
        val raw = """
            PRICELIST:
            QSOS 2 — Curva Medellin, Cebu
            06/07/2026

            [ALCOHOL/LIQUOR]
            • Alfonso Light (alfonso) — ₱375.00 | 1L
            • Club Mix (lime juice) — ₱80.00 | Small
            • Emperador Light (Emperador) — ₱155.00 | 750ml

            [CIGARETTES]
            • Marlboro (Marlboro red/puwa, Marlboro Ice blast) — ₱10.00 | Stick

            Shared via Presyohan
        """.trimIndent()

        val result = AddMultipleItemsParser.parseTextToResult(raw, emptySet())

        // Validate store headers/dates/footers were completely ignored
        assertFalse(result.categories.any { it.name == "UNCATEGORIZED" })
        assertEquals(2, result.categories.size)

        val alcohol = result.categories.first { it.name == "ALCOHOL/LIQUOR" }
        assertEquals(3, alcohol.items.size)

        assertEquals("Alfonso Light", alcohol.items[0].productName)
        assertEquals("alfonso", alcohol.items[0].description)
        assertEquals(375.0, alcohol.items[0].price!!, 0.001)
        assertEquals("1l", alcohol.items[0].unit)

        assertEquals("Club Mix", alcohol.items[1].productName)
        assertEquals("lime juice", alcohol.items[1].description)
        assertEquals(80.0, alcohol.items[1].price!!, 0.001)
        assertEquals("small", alcohol.items[1].unit)

        assertEquals("Emperador Light", alcohol.items[2].productName)
        assertEquals("Emperador", alcohol.items[2].description)
        assertEquals(155.0, alcohol.items[2].price!!, 0.001)
        assertEquals("750ml", alcohol.items[2].unit)

        val cigarettes = result.categories.first { it.name == "CIGARETTES" }
        assertEquals(1, cigarettes.items.size)
        assertEquals("Marlboro", cigarettes.items[0].productName)
        assertEquals("Marlboro red/puwa, Marlboro Ice blast", cigarettes.items[0].description)
        assertEquals(10.0, cigarettes.items[0].price!!, 0.001)
        assertEquals("stick", cigarettes.items[0].unit)
    }
}
