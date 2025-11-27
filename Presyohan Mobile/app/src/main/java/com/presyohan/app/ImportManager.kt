package com.presyohan.app

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ImportResult(
    val savedCount: Int,
    val attemptedCount: Int,
    val categoryCount: Int,
    val failures: List<Pair<ParsedItem, String>> = emptyList()
)

class ImportManager(private val repo: ImportRepository) {
    suspend fun performImport(
        storeId: String,
        categories: List<ParsedCategory>,
        categoryIdByName: MutableMap<String, String>
    ): ImportResult {
        var saved = 0
        var attempted = 0
        val failures = mutableListOf<Pair<ParsedItem, String>>()

        for (cat in categories) {
            val validItems = cat.items.filter { it.status == ItemStatus.NEW || it.status == ItemStatus.UPDATE }
            if (validItems.isEmpty()) continue

            val catId = repo.ensureCategory(storeId, cat.name, categoryIdByName)

            for (item in validItems) {
                attempted++
                val ok = repo.addOrUpdateProduct(storeId, catId, item)
                if (ok) saved++ else failures.add(item to "save_failed")
            }
        }
        val validCategoryCount = categories.count { it.items.any { i -> i.status == ItemStatus.NEW || i.status == ItemStatus.UPDATE } }
        return ImportResult(saved, attempted, validCategoryCount, failures)
    }
}

interface ImportRepository {
    suspend fun ensureCategory(storeId: String, name: String, cache: MutableMap<String, String>): String
    suspend fun addOrUpdateProduct(storeId: String, categoryId: String, item: ParsedItem): Boolean
}

class SupabaseImportRepository : ImportRepository {
    override suspend fun ensureCategory(storeId: String, name: String, cache: MutableMap<String, String>): String {
        val cached = cache[name]
        if (!cached.isNullOrBlank()) return cached

        // Try RPC to add category (handles dedup/normalize server-side)
        @Serializable data class RpcRow(val category_id: String, val name: String)
        return try {
            val rows = SupabaseProvider.client.postgrest.rpc(
                "add_category",
                buildJsonObject {
                    put("p_store_id", kotlinx.serialization.json.JsonPrimitive(storeId))
                    put("p_name", kotlinx.serialization.json.JsonPrimitive(name))
                }
            ).decodeList<RpcRow>()
            val id = rows.firstOrNull()?.category_id
            val normalizedName = rows.firstOrNull()?.name ?: name
            if (!id.isNullOrBlank()) {
                cache[normalizedName] = id
                id
            } else {
                // Fallback: lookup existing
                lookupCategoryId(storeId, name, cache)
            }
        } catch (_: Exception) {
            lookupCategoryId(storeId, name, cache)
        }
    }

    private suspend fun lookupCategoryId(storeId: String, name: String, cache: MutableMap<String, String>): String {
        @Serializable data class CatRow(val category_id: String, val name: String)
        val rows = try {
            SupabaseProvider.client.postgrest.rpc(
                "get_user_categories",
                buildJsonObject { put("p_store_id", kotlinx.serialization.json.JsonPrimitive(storeId)) }
            ).decodeList<CatRow>()
        } catch (_: Exception) { emptyList() }
        rows.forEach { cache[it.name] = it.category_id }
        val id = cache[name]
        if (!id.isNullOrBlank()) return id

        // As last resort, upsert directly
        @Serializable data class Inserted(val id: String)
        try {
            SupabaseProvider.client.postgrest["categories"].insert(
                mapOf("store_id" to storeId, "name" to name)
            )
            val r = SupabaseProvider.client.postgrest["categories"].select(Columns.list("id")) {
                filter { eq("store_id", storeId); eq("name", name) }
                limit(1)
            }.decodeList<Inserted>()
            val newId = r.firstOrNull()?.id ?: throw RuntimeException("No category id")
            cache[name] = newId
            return newId
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun addOrUpdateProduct(storeId: String, categoryId: String, item: ParsedItem): Boolean {
        @Serializable data class ProdId(val id: String)
        val existing = try {
            SupabaseProvider.client.postgrest["products"].select(Columns.list("id")) {
                filter { eq("store_id", storeId); eq("name", item.name) }
                limit(1)
            }.decodeList<ProdId>().firstOrNull()?.id
        } catch (_: Exception) { null }

        return try {
            if (existing != null) {
                val payload = buildJsonObject {
                    put("name", item.name)
                    if (item.description != null) put("description", item.description) else put("description", kotlinx.serialization.json.JsonNull)
                    put("price", item.price ?: 0.0)
                    put("unit", item.unit)
                    put("category_id", categoryId)
                }
                SupabaseProvider.client.postgrest["products"].update(payload) {
                    filter { eq("id", existing); eq("store_id", storeId) }
                }
            } else {
                // Use RPC to insert new product to avoid RLS issues
                SupabaseProvider.client.postgrest.rpc(
                    "add_product",
                    buildJsonObject {
                        put("p_store_id", kotlinx.serialization.json.JsonPrimitive(storeId))
                        put("p_category_id", kotlinx.serialization.json.JsonPrimitive(categoryId))
                        put("p_name", kotlinx.serialization.json.JsonPrimitive(item.name))
                        if (item.description != null) {
                            put("p_description", item.description)
                        } else {
                            put("p_description", kotlinx.serialization.json.JsonNull)
                        }
                        put("p_price", kotlinx.serialization.json.JsonPrimitive(item.price ?: 0.0))
                        put("p_unit", kotlinx.serialization.json.JsonPrimitive(item.unit))
                    }
                )
            }
            true
        } catch (_: Exception) { false }
    }
}
