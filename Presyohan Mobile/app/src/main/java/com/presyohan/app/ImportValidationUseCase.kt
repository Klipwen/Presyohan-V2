package com.presyohan.app

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class DbProduct(
    val id: String,
    val category_id: String,
    val name: String,
    val description: String? = null,
    val price: Double = 0.0,
    val unit: String = ""
)

@Serializable
data class DbCategory(
    val id: String,
    val name: String
)

@Serializable
data class ReviewSummary(
    val newCategoriesCount: Int,
    val newItemsCount: Int,
    val updateItemsCount: Int,
    val duplicateItemsCount: Int,
    val invalidItemsCount: Int,
    val totalCategories: Int,
    val totalItems: Int
)

class ImportValidationUseCase {

    suspend fun fetchExistingProducts(storeId: String): List<DbProduct> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.postgrest["products"]
                .select(Columns.list("id, category_id, name, description, price, unit")) {
                    filter { eq("store_id", storeId) }
                }
                .decodeList<DbProduct>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchExistingCategories(storeId: String): List<DbCategory> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.postgrest["categories"]
                .select(Columns.list("id, name")) {
                    filter { eq("store_id", storeId) }
                }
                .decodeList<DbCategory>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun validate(
        session: DraftImportSession,
        existingProds: List<DbProduct>,
        existingCats: List<DbCategory> = emptyList()
    ): DraftImportSession {
        // Group existing DB products by normalized key (Name + Description + Unit)
        val dbProductMap = existingProds.groupBy { ImportDraftKeys.productKey(it.name, it.description, it.unit) }
        
        // Map category names case-insensitively to their IDs, and IDs to names
        val dbCatMapByName = existingCats.associate { ImportDraftKeys.normalizeText(it.name) to it.id }
        val dbCatNameById = existingCats.associate { it.id to it.name }

        // Flatten all items from the session's categories
        val flatItems = mutableListOf<Pair<DraftCategory, DraftItem>>()
        for (cat in session.categories) {
            for (item in cat.items) {
                flatItems.add(Pair(cat, item))
            }
        }

        val validatedItems = mutableListOf<DraftItem>()
        val seenImportKeys = mutableSetOf<String>()

        for ((parsedCat, item) in flatItems) {
            // 1. Deduplicate within the imported list:
            val normName = ImportDraftKeys.normalizeText(item.productName)
            val normDesc = ImportDraftKeys.normalizeText(item.description)
            val normCat = ImportDraftKeys.normalizeText(parsedCat.name)
            val normUnit = ImportDraftKeys.normalizeText(item.unit)
            val priceStr = item.price?.toString() ?: "null"
            val importListKey = "$normName|$normDesc|$normCat|$normUnit|$priceStr"

            if (seenImportKeys.contains(importListKey)) {
                // Duplicate in the import list -> merge as one (skip it)
                continue
            }
            seenImportKeys.add(importListKey)

            // 2. Check if there is an exact match in the database (same name, description, unit):
            val key = ImportDraftKeys.productKey(item.productName, item.description, item.unit)
            val dbMatches = dbProductMap[key]

            var resolvedCategoryId = parsedCat.categoryId ?: dbCatMapByName[ImportDraftKeys.normalizeText(parsedCat.name)]
            var resolvedCategoryName = parsedCat.name

            val isExactDuplicateInDb = dbMatches?.any { dbProd ->
                val samePrice = dbProd.price == item.price
                val sameUnit = ImportDraftKeys.normalizeText(dbProd.unit) == ImportDraftKeys.normalizeText(item.unit)
                samePrice && sameUnit
            } ?: false

            val validationErrors = mutableListOf<ValidationError>()
            validationErrors.addAll(item.validationErrors) // Preserve any parser-detected errors

            var status = if (validationErrors.isNotEmpty()) ValidationStatus.INVALID else ValidationStatus.NEW
            var matchedProductId: String? = item.productId

            // Perform client-side field validation rules
            if (item.productName.trim().isEmpty()) {
                if (!validationErrors.contains(ValidationError.EMPTY_PRODUCT_NAME)) {
                    validationErrors.add(ValidationError.EMPTY_PRODUCT_NAME)
                }
                status = ValidationStatus.INVALID
            }

            val priceVal = item.price
            if (priceVal == null) {
                if (!validationErrors.contains(ValidationError.INVALID_PRICE)) {
                    validationErrors.add(ValidationError.INVALID_PRICE)
                }
                status = ValidationStatus.INVALID
            } else if (priceVal < 0) {
                if (!validationErrors.contains(ValidationError.NEGATIVE_PRICE)) {
                    validationErrors.add(ValidationError.NEGATIVE_PRICE)
                }
                status = ValidationStatus.INVALID
            }

            // If valid, check duplicates and resolve matched product
            if (status != ValidationStatus.INVALID) {
                if (isExactDuplicateInDb) {
                    status = ValidationStatus.DUPLICATE
                    val dbProd = dbMatches!!.first { dbProd ->
                        val samePrice = dbProd.price == item.price
                        val sameUnit = ImportDraftKeys.normalizeText(dbProd.unit) == ImportDraftKeys.normalizeText(item.unit)
                        samePrice && sameUnit
                    }
                    matchedProductId = dbProd.id
                    resolvedCategoryId = dbProd.category_id
                    resolvedCategoryName = dbCatNameById[resolvedCategoryId] ?: resolvedCategoryName
                } else if (dbMatches != null) {
                    if (dbMatches.size == 1) {
                        val dbProd = dbMatches.first()
                        status = ValidationStatus.UPDATE
                        matchedProductId = dbProd.id
                        // Match category to existing product's category
                        resolvedCategoryId = dbProd.category_id
                        resolvedCategoryName = dbCatNameById[resolvedCategoryId] ?: resolvedCategoryName
                    } else if (dbMatches.size > 1) {
                        // Ambiguous match across categories -> find the one in the same parsed category
                        val catDbMatches = dbMatches.filter { resolvedCategoryId != null && it.category_id == resolvedCategoryId }
                        if (catDbMatches.size == 1) {
                            status = ValidationStatus.UPDATE
                            matchedProductId = catDbMatches.first().id
                        } else {
                            status = ValidationStatus.INVALID
                            if (!validationErrors.contains(ValidationError.DUPLICATE_IN_DATABASE_AMBIGUOUS)) {
                                validationErrors.add(ValidationError.DUPLICATE_IN_DATABASE_AMBIGUOUS)
                            }
                        }
                    }
                }
            }

            // Verify category requirements
            if (resolvedCategoryName.trim().isEmpty() || resolvedCategoryName == "UNCATEGORIZED") {
                if (item.source == ImportSource.SIMPLE_MANUAL && !validationErrors.contains(ValidationError.MISSING_CATEGORY)) {
                    validationErrors.add(ValidationError.MISSING_CATEGORY)
                }
                if (resolvedCategoryName == "UNCATEGORIZED" && !validationErrors.contains(ValidationError.MISSING_CATEGORY)) {
                    validationErrors.add(ValidationError.MISSING_CATEGORY)
                }
                status = ValidationStatus.INVALID
            }

            validatedItems.add(
                item.copy(
                    categoryId = resolvedCategoryId,
                    categoryName = resolvedCategoryName,
                    productId = matchedProductId,
                    validationStatus = status,
                    validationErrors = validationErrors
                )
            )
        }

        // Group validated items back into categories by name and ID
        val categoryGroups = validatedItems.groupBy { Pair(it.categoryName.trim().uppercase(), it.categoryId) }
        val validatedCategories = categoryGroups.map { (key, items) ->
            DraftCategory(
                draftCategoryId = DraftIdGenerator.next("category"),
                categoryId = key.second,
                name = key.first,
                items = items.toMutableList()
            )
        }.toMutableList()

        // Move UNCATEGORIZED category to the top if present
        val uncategorizedIndex = validatedCategories.indexOfFirst { it.name == "UNCATEGORIZED" }
        if (uncategorizedIndex > 0) {
            val uncategorized = validatedCategories.removeAt(uncategorizedIndex)
            validatedCategories.add(0, uncategorized)
        }

        return session.copy(
            categories = validatedCategories,
            isDirty = session.isDirty
        )
    }

    fun produceSummary(session: DraftImportSession): ReviewSummary {
        var newCount = 0
        var updateCount = 0
        var duplicateCount = 0
        var invalidCount = 0
        var totalItems = 0
        val categoriesWithItems = session.categories.filter { it.items.isNotEmpty() }

        // A category is considered new if it has items and categoryId is null
        val newCategoriesCount = categoriesWithItems.count { it.categoryId == null }

        for (cat in session.categories) {
            for (item in cat.items) {
                totalItems++
                when (item.validationStatus) {
                    ValidationStatus.NEW -> newCount++
                    ValidationStatus.UPDATE -> updateCount++
                    ValidationStatus.DUPLICATE -> duplicateCount++
                    ValidationStatus.INVALID -> invalidCount++
                }
            }
        }

        return ReviewSummary(
            newCategoriesCount = newCategoriesCount,
            newItemsCount = newCount,
            updateItemsCount = updateCount,
            duplicateItemsCount = duplicateCount,
            invalidItemsCount = invalidCount,
            totalCategories = categoriesWithItems.size,
            totalItems = totalItems
        )
    }
}
