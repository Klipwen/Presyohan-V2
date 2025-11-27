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
}
