package com.presyohan.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * High-speed product image resolver that fetches authentic product photos
 * from Open Food Facts & Wikimedia Commons APIs when Gemini search returns missing/invalid image URLs.
 */
object ProductImageResolver {

    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    suspend fun resolveProductImage(itemName: String, sourceName: String? = null): String? = withContext(Dispatchers.IO) {
        val cleanName = itemName.trim()
        if (cleanName.isEmpty()) return@withContext null

        // 1. Try Open Food Facts API (ideal for bottled water, drinks, groceries, brands)
        val offResult = fetchFromOpenFoodFacts(cleanName)
        if (!offResult.isNullOrBlank()) {
            return@withContext offResult
        }

        // If itemName contains brand/size (e.g. "Nature's Spring Bottle water"), try short search
        val firstTwoWords = cleanName.split(" ").take(2).joinToString(" ")
        if (firstTwoWords != cleanName && firstTwoWords.length >= 4) {
            val offShortResult = fetchFromOpenFoodFacts(firstTwoWords)
            if (!offShortResult.isNullOrBlank()) {
                return@withContext offShortResult
            }
        }

        // 2. Try Wikimedia Commons API as secondary fallback
        val wikiResult = fetchFromWikimedia(cleanName)
        if (!wikiResult.isNullOrBlank()) {
            return@withContext wikiResult
        }

        null
    }

    private fun fetchFromOpenFoodFacts(query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encodedQuery&search_simple=1&action=process&json=1"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "PresyohanMobileApp/1.0 (android)")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = jsonDecoder.parseToJsonElement(responseText).jsonObject
                val products = root["products"]?.jsonArray
                if (products != null && products.isNotEmpty()) {
                    for (item in products) {
                        val productObj = item.jsonObject
                        val imageUrl = productObj["image_url"]?.jsonPrimitive?.content
                            ?: productObj["image_front_url"]?.jsonPrimitive?.content
                            ?: productObj["image_small_url"]?.jsonPrimitive?.content
                        if (!imageUrl.isNullOrBlank() && isValidImageUrl(imageUrl)) {
                            return imageUrl
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("ProductImageResolver", "OpenFoodFacts query failed: ${e.message}")
            null
        }
    }

    private fun fetchFromWikimedia(query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&prop=pageimages&piprop=thumbnail&pithumbsize=400&gsrsearch=$encodedQuery&format=json"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "PresyohanMobileApp/1.0 (android)")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = jsonDecoder.parseToJsonElement(responseText).jsonObject
                val queryObj = root["query"]?.jsonObject
                val pages = queryObj?.get("pages")?.jsonObject
                if (pages != null) {
                    for ((_, pageElement) in pages) {
                        val pageObj = pageElement.jsonObject
                        val thumbnail = pageObj["thumbnail"]?.jsonObject
                        val sourceUrl = thumbnail?.get("source")?.jsonPrimitive?.content
                        if (!sourceUrl.isNullOrBlank() && isValidImageUrl(sourceUrl)) {
                            return sourceUrl
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("ProductImageResolver", "Wikimedia query failed: ${e.message}")
            null
        }
    }

    fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        
        // Reject web pages / html links
        if (lower.contains(".html") || lower.contains(".htm") || lower.contains("/products/") || lower.contains("/p/")) {
            if (!lower.endsWith(".jpg") && !lower.endsWith(".png") && !lower.endsWith(".jpeg") && !lower.endsWith(".webp")) {
                return false
            }
        }
        return true
    }
}
