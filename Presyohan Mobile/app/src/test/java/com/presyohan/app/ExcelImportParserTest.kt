package com.presyohan.app

import org.dhatim.fastexcel.Workbook
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelImportParserTest {

    @Test
    fun test_parseXlsx_correctly_maps_headers_and_rows() {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "TestApp", "1.0")
        val ws = wb.newWorksheet("Sheet1")

        // Headers
        ws.value(0, 0, "Category")
        ws.value(0, 1, "Product Name")
        ws.value(0, 2, "Price")
        ws.value(0, 3, "Unit")
        ws.value(0, 4, "Description")

        // Data 1
        ws.value(1, 0, "FRUITS")
        ws.value(1, 1, "Apple")
        ws.value(1, 2, "15.50")
        ws.value(1, 3, "piece")
        ws.value(1, 4, "Red apple")

        // Data 2 (Invalid Price)
        ws.value(2, 0, "FRUITS")
        ws.value(2, 1, "Banana")
        ws.value(2, 2, "invalid-price")
        ws.value(2, 3, "bunch")
        ws.value(2, 4, "Yellow banana")

        // Data 3 (Negative Price)
        ws.value(3, 0, "DRINKS")
        ws.value(3, 1, "Soda")
        ws.value(3, 2, "-25.00")
        ws.value(3, 3, "can")
        ws.value(3, 4, "Cola drink")

        wb.finish()

        val bytes = out.toByteArray()
        val inputStream = ByteArrayInputStream(bytes)

        val categories = ExcelImportParser.parseXlsx(inputStream)

        // We expect FRUITS and DRINKS categories
        assertEquals(2, categories.size)

        val fruitsCat = categories.find { it.name == "FRUITS" }
        assertNotNull(fruitsCat)
        assertEquals(2, fruitsCat!!.items.size)

        val apple = fruitsCat.items.find { it.productName == "Apple" }
        assertNotNull(apple)
        assertEquals(15.50, apple!!.price ?: 0.0, 0.001)
        assertEquals("piece", apple.unit)
        assertEquals("Red apple", apple.description)
        assertEquals(ValidationStatus.NEW, apple.validationStatus)

        val banana = fruitsCat.items.find { it.productName == "Banana" }
        assertNotNull(banana)
        assertNull(banana!!.price)
        assertEquals(ValidationStatus.INVALID, banana.validationStatus)
        assertTrue(banana.validationErrors.contains(ValidationError.INVALID_PRICE))

        val drinksCat = categories.find { it.name == "DRINKS" }
        assertNotNull(drinksCat)
        assertEquals(1, drinksCat!!.items.size)

        val soda = drinksCat.items.find { it.productName == "Soda" }
        assertNotNull(soda)
        assertEquals(-25.00, soda!!.price ?: 0.0, 0.001)
        assertEquals(ValidationStatus.INVALID, soda.validationStatus)
        assertTrue(soda.validationErrors.contains(ValidationError.NEGATIVE_PRICE))
    }
}
