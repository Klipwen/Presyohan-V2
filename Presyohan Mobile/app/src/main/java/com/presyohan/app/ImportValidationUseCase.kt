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
data class ReviewSummary(
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

    fun validate(session: DraftImportSession, existingProds: List<DbProduct>): DraftImportSession {
        // Group existing DB products by normalized key
        val dbProductMap = existingProds.groupBy { ImportDraftKeys.productKey(it.name, it.description) }

        val validatedCategories = mutableListOf<DraftCategory>()
        val seenImportKeys = mutableSetOf<String>()

        categoriesLoop@ for (cat in session.categories) {
            // Skip empty categories in validation unless they contain items (review blocks done if invalid items exist anyway)
            val validatedItems = mutableListOf<DraftItem>()

            for (item in cat.items) {
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

                if (cat.name.trim().isEmpty() || cat.name == "UNCATEGORIZED") {
                    if (item.source == ImportSource.SIMPLE_MANUAL && !validationErrors.contains(ValidationError.MISSING_CATEGORY)) {
                        validationErrors.add(ValidationError.MISSING_CATEGORY)
                    }
                    // For parser context, UNCATEGORIZED items from raw text also get validation status / errors
                    if (cat.name == "UNCATEGORIZED" && !validationErrors.contains(ValidationError.MISSING_CATEGORY)) {
                        validationErrors.add(ValidationError.MISSING_CATEGORY)
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

                // If the item is still considered valid, perform duplicate checks
                if (status != ValidationStatus.INVALID) {
                    val key = ImportDraftKeys.productKey(item.productName, item.description)

                    if (seenImportKeys.contains(key)) {
                        status = ValidationStatus.DUPLICATE
                        if (!validationErrors.contains(ValidationError.DUPLICATE_IN_IMPORT)) {
                            validationErrors.add(ValidationError.DUPLICATE_IN_IMPORT)
                        }
                    } else {
                        // Check matches in DB
                        val dbMatches = dbProductMap[key]
                        if (dbMatches != null) {
                            if (dbMatches.size == 1) {
                                status = ValidationStatus.UPDATE
                                matchedProductId = dbMatches.first().id
                            } else if (dbMatches.size > 1) {
                                status = ValidationStatus.INVALID
                                if (!validationErrors.contains(ValidationError.DUPLICATE_IN_DATABASE_AMBIGUOUS)) {
                                    validationErrors.add(ValidationError.DUPLICATE_IN_DATABASE_AMBIGUOUS)
                                }
                            }
                        } else {
                            status = ValidationStatus.NEW
                        }
                    }
                    seenImportKeys.add(key)
                }

                validatedItems.add(
                    item.copy(
                        productId = matchedProductId,
                        validationStatus = status,
                        validationErrors = validationErrors
                    )
                )
            }

            validatedCategories.add(
                cat.copy(items = validatedItems)
            )
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
            newItemsCount = newCount,
            updateItemsCount = updateCount,
            duplicateItemsCount = duplicateCount,
            invalidItemsCount = invalidCount,
            totalCategories = categoriesWithItems.size,
            totalItems = totalItems
        )
    }
}
