package com.presyohan.app

import kotlinx.serialization.Serializable

@Serializable
enum class ItemStatus {
    NEW,
    UPDATE,
    DUPLICATE,
    ERROR_NO_PRICE,
    ERROR_INVALID_FORMAT,
    ERROR_NO_CATEGORY
}

@Serializable
data class ParsedItem(
    val name: String,
    val description: String?,
    val unit: String,
    val price: Double?,
    var status: ItemStatus
)

@Serializable
data class ParsedCategory(
    val name: String,
    val items: MutableList<ParsedItem> = mutableListOf()
)

@Serializable
enum class EntryMode {
    SIMPLE,
    FAST
}

@Serializable
enum class ImportSource {
    SIMPLE_MANUAL,
    FAST_TEXT,
    EXCEL_FILE,
    RAW_TEXT_IMPORT
}

@Serializable
enum class ValidationStatus {
    NEW,
    UPDATE,
    DUPLICATE,
    INVALID
}

@Serializable
enum class ValidationError {
    EMPTY_PRODUCT_NAME,
    MISSING_CATEGORY,
    INVALID_PRICE,
    NEGATIVE_PRICE,
    INVALID_FORMAT,
    MISSING_REQUIRED_FIELD,
    DUPLICATE_IN_IMPORT,
    DUPLICATE_IN_DATABASE_AMBIGUOUS
}

@Serializable
data class ImportMetadata(
    val fileName: String? = null,
    val mimeType: String? = null,
    val rawTextPreview: String? = null,
    val rowCount: Int = 0,
    val parsedAtMillis: Long? = null,
    val parseWarnings: List<String> = emptyList(),
    val sourceLabel: String = ""
)

@Serializable
data class DraftImportSession(
    val sessionId: String,
    val storeId: String,
    val storeName: String? = null,
    val source: ImportSource = ImportSource.SIMPLE_MANUAL,
    val currentMode: EntryMode = EntryMode.SIMPLE,
    val categories: MutableList<DraftCategory> = mutableListOf(),
    val metadata: ImportMetadata = ImportMetadata(),
    val isDirty: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

@Serializable
data class DraftCategory(
    val draftCategoryId: String,
    val categoryId: String? = null,
    val name: String,
    val items: MutableList<DraftItem> = mutableListOf()
)

@Serializable
data class DraftItem(
    val draftItemId: String,
    val categoryId: String? = null,
    val categoryName: String,
    val productId: String? = null,
    val productName: String,
    val description: String? = null,
    val unit: String,
    val priceText: String,
    val price: Double? = null,
    val source: ImportSource,
    val sourceRowNumber: Int? = null,
    val originalLine: String? = null,
    val validationStatus: ValidationStatus = ValidationStatus.INVALID,
    val validationErrors: List<ValidationError> = emptyList(),
    val duplicateKey: String = ImportDraftKeys.productKey(productName, description)
)

object ImportDraftKeys {
    fun normalizeText(value: String?): String {
        return value
            .orEmpty()
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    fun productKey(name: String?, description: String?): String {
        return listOf(normalizeText(name), normalizeText(description)).joinToString("|")
    }
}

fun ParsedItem.toDraftItem(
    categoryName: String,
    categoryId: String? = null,
    source: ImportSource = ImportSource.FAST_TEXT,
    sourceRowNumber: Int? = null,
    originalLine: String? = null
): DraftItem {
    val mappedStatus = when (status) {
        ItemStatus.NEW -> ValidationStatus.NEW
        ItemStatus.UPDATE -> ValidationStatus.UPDATE
        ItemStatus.DUPLICATE -> ValidationStatus.DUPLICATE
        ItemStatus.ERROR_NO_PRICE,
        ItemStatus.ERROR_INVALID_FORMAT,
        ItemStatus.ERROR_NO_CATEGORY -> ValidationStatus.INVALID
    }
    val mappedErrors = when (status) {
        ItemStatus.ERROR_NO_PRICE -> listOf(ValidationError.INVALID_PRICE)
        ItemStatus.ERROR_INVALID_FORMAT -> listOf(ValidationError.INVALID_FORMAT)
        ItemStatus.ERROR_NO_CATEGORY -> listOf(ValidationError.MISSING_CATEGORY)
        else -> emptyList()
    }

    return DraftItem(
        draftItemId = DraftIdGenerator.next("item"),
        categoryId = categoryId,
        categoryName = categoryName,
        productName = name,
        description = description,
        unit = unit,
        priceText = price?.toString().orEmpty(),
        price = price,
        source = source,
        sourceRowNumber = sourceRowNumber,
        originalLine = originalLine,
        validationStatus = mappedStatus,
        validationErrors = mappedErrors
    )
}

fun ParsedCategory.toDraftCategory(
    categoryId: String? = null,
    source: ImportSource = ImportSource.FAST_TEXT
): DraftCategory {
    return DraftCategory(
        draftCategoryId = DraftIdGenerator.next("category"),
        categoryId = categoryId,
        name = name,
        items = items.map { it.toDraftItem(name, categoryId, source) }.toMutableList()
    )
}

fun List<ParsedCategory>.toDraftCategories(source: ImportSource = ImportSource.FAST_TEXT): MutableList<DraftCategory> {
    return map { it.toDraftCategory(source = source) }.toMutableList()
}

fun DraftItem.toParsedItem(): ParsedItem {
    val mappedStatus = when (validationStatus) {
        ValidationStatus.NEW -> ItemStatus.NEW
        ValidationStatus.UPDATE -> ItemStatus.UPDATE
        ValidationStatus.DUPLICATE -> ItemStatus.DUPLICATE
        ValidationStatus.INVALID -> when {
            validationErrors.contains(ValidationError.MISSING_CATEGORY) -> ItemStatus.ERROR_NO_CATEGORY
            validationErrors.contains(ValidationError.INVALID_FORMAT) -> ItemStatus.ERROR_INVALID_FORMAT
            else -> ItemStatus.ERROR_NO_PRICE
        }
    }

    return ParsedItem(
        name = productName,
        description = description,
        unit = unit,
        price = price,
        status = mappedStatus
    )
}

fun DraftCategory.toParsedCategory(): ParsedCategory {
    return ParsedCategory(
        name = name,
        items = items.map { it.toParsedItem() }.toMutableList()
    )
}

@Serializable
data class ParseResult(
    val categories: List<DraftCategory>,
    val invalidLines: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

