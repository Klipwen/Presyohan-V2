package com.presyohan.app

import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.io.InputStream

object ExcelImportParser {

    fun parseXlsx(inputStream: InputStream): List<DraftCategory> {
        val flatItems = mutableListOf<DraftItem>()

        try {
            ReadableWorkbook(inputStream).use { workbook ->
                val sheet = workbook.firstSheet
                sheet.openStream().use { stream ->
                    var colCategory = -1
                    var colName = -1
                    var colPrice = -1
                    var colUnit = -1
                    var colDescription = -1

                    var isFirstRow = true

                    stream.forEach { row ->
                        val cells = (0 until row.cellCount).map { row.getCell(it)?.rawValue?.trim() ?: "" }

                        if (cells.all { it.isEmpty() }) return@forEach

                        if (isFirstRow) {
                            cells.forEachIndexed { idx, text ->
                                val t = text.lowercase()
                                if (t.contains("category")) colCategory = idx
                                else if (t.contains("name") || t.contains("product")) colName = idx
                                else if (t.contains("price") || t.contains("amount")) colPrice = idx
                                else if (t.contains("unit") || t.contains("size")) colUnit = idx
                                else if (t.contains("description") || t.contains("desc")) colDescription = idx
                            }

                            if (colCategory == -1 && colName == -1) {
                                colCategory = 0
                                colName = 1
                                colPrice = 2
                                colUnit = 3
                                colDescription = 4
                                parseRow(cells, colCategory, colName, colPrice, colUnit, colDescription, row.rowNum, flatItems)
                            }
                            isFirstRow = false
                        } else {
                            parseRow(cells, colCategory, colName, colPrice, colUnit, colDescription, row.rowNum, flatItems)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExcelImportParser", "Failed to parse excel", e)
        }

        val categoriesMap = mutableMapOf<String, MutableList<DraftItem>>()
        for (item in flatItems) {
            val catName = item.categoryName.trim().uppercase().ifBlank { "UNCATEGORIZED" }
            val list = categoriesMap.getOrPut(catName) { mutableListOf() }
            list.add(item.copy(categoryName = catName))
        }

        return categoriesMap.map { (catName, items) ->
            DraftCategory(
                draftCategoryId = "category-${java.util.UUID.randomUUID()}",
                name = catName,
                items = items
            )
        }
    }

    private fun parseRow(
        cells: List<String>,
        colCategory: Int,
        colName: Int,
        colPrice: Int,
        colUnit: Int,
        colDescription: Int,
        rowNum: Int,
        outList: MutableList<DraftItem>
    ) {
        val category = cells.getOrNull(colCategory)?.ifBlank { "UNCATEGORIZED" } ?: "UNCATEGORIZED"
        val name = cells.getOrNull(colName) ?: ""
        val priceText = cells.getOrNull(colPrice) ?: ""
        val unit = cells.getOrNull(colUnit)?.ifBlank { "1pc" } ?: "1pc"
        val description = cells.getOrNull(colDescription)?.ifBlank { null }

        if (name.isBlank() && priceText.isBlank()) return

        val priceVal = priceText.replace("₱", "").replace("PHP", "", true).trim().toDoubleOrNull()

        val errors = mutableListOf<ValidationError>()
        var status = ValidationStatus.NEW

        if (name.isBlank()) {
            errors.add(ValidationError.EMPTY_PRODUCT_NAME)
            status = ValidationStatus.INVALID
        }
        if (priceVal == null) {
            errors.add(ValidationError.INVALID_PRICE)
            status = ValidationStatus.INVALID
        } else if (priceVal < 0) {
            errors.add(ValidationError.NEGATIVE_PRICE)
            status = ValidationStatus.INVALID
        }

        outList.add(
            DraftItem(
                draftItemId = "item-${java.util.UUID.randomUUID()}",
                categoryName = category,
                productName = name,
                description = description,
                unit = unit,
                priceText = priceText,
                price = priceVal,
                source = ImportSource.EXCEL_FILE,
                sourceRowNumber = rowNum,
                originalLine = "Row $rowNum: ${cells.joinToString(" | ")}",
                validationStatus = status,
                validationErrors = errors
            )
        )
    }
}
