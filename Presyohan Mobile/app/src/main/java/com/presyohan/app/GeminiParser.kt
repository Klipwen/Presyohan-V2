package com.presyohan.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.URL
import java.net.HttpURLConnection

@Serializable
data class GeminiParsedItem(
    val productName: String,
    val description: String? = null,
    val unit: String = "1pc",
    val price: Double? = null,
    val priceText: String = "",
    val isValid: Boolean = true,
    val originalLine: String? = null
)

@Serializable
data class GeminiParsedCategory(
    val name: String,
    val items: List<GeminiParsedItem>
)

@Serializable
data class GeminiParseResponse(
    val categories: List<GeminiParsedCategory>
)

object GeminiParser {

    private val jsonDecoder = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }


    suspend fun parseText(
        rawText: String,
        existingCategoryIds: Map<String, String>, // Category name to ID map
        existingProductNames: Set<String>
    ): ParseResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            throw IllegalStateException("API key not configured")
        }

        val existingCategoriesList = existingCategoryIds.keys.joinToString(", ") { "\"$it\"" }

        val systemInstruction = """
            You are an expert data parsing assistant for "Presyohan", a price tracking app.
            Your task is to parse a raw text pricelist or supplier message and convert it into a structured JSON object.

            List of Existing Categories in this Store:
            [$existingCategoriesList]

            CRITICAL DISAMBIGUATION RULES FOR NUMBERS:
            - You must distinguish between Model Numbers/Sizes, Quantities/Units, and Prices.
            - STANDALONE NUMBERS AS PRICES: If a line contains a product name followed by a standalone number without any trailing unit name (e.g., "Egg 15" or "item 1 15" or "item 2 18"), that standalone number MUST be parsed as the price (e.g., price: 15.0, price: 18.0).
            - SPACE BETWEEN NUMBER AND UNIT (PRICE & UNIT): If a number is separated from the unit name by a space (e.g., "15 pc", "10 stick", "25 box"), the number is the PRICE and the unit is the selling unit.
              - Example: "Egg 15 pc" -> price: 15.0, unit: "pc" (automatically normalized to "1pc").
            - JOINED NUMBER AND UNIT (QUANTITY/PACK SIZE): If a number and a unit name are joined without a space (e.g., "15pc", "15pcs", "10stick", "1.5L", "500g", "1kg"), this represents a quantity or size, NOT the price. The price is null.
              - Example: "Egg 15pc" or "Egg 15pcs" -> price: null, unit: "15pcs".
              - PLURAL CORRECTION: If a joined quantity/pack unit uses a number greater than 1 joined with "pc" (e.g., "15pc"), auto-correct the unit to plural "pcs" (e.g., "15pcs").
            - MULTIPLE NUMBERS: If a line contains both a joined unit/quantity and a separate number (e.g., "Egg 15pcs 7" or "Coke 1.5L 80"), the joined one is the quantity/unit, and the other number ("7", "80") is the price.

            CRITICAL PRICELIST IMPORT RULES:
            - Ignore metadata headers and footers from exported pricelists:
              - Ignore lines starting with or containing "PRICELIST:", store info (e.g., "QSOS 2 — Curva Medellin, Cebu"), or dates (e.g., "06/07/2026").
              - Ignore the footer "Shared via Presyohan".
              - Do NOT extract these lines as items.
            - Explicit Category Headers: Lines like "[ALCOHOL/LIQUOR]", "[CIGARETTES]" are category names. All following items belong to that category until a new header is encountered.
            - Bulleted Items: Lines starting with "•" or "-" contain items. Parse them accurately:
              - Example: "• Alfonso Light (alfonso) — ₱375.00 | 1L" -> productName: "Alfonso Light", description: "alfonso", price: 375.0, unit: "1L".
              - DO NOT treat the size/unit at the end of the line (e.g., "1L", "750ml", "Small", "Stick") as the price. The price is the value preceded by the peso symbol (e.g. ₱375.00).

            Instructions:
            1. Parse the input text line-by-line or section-by-section to extract items, supporting both line-based entries and inline comma-separated lists of items.
            2. For each item, extract:
               - productName: The core name of the product (e.g., "Fuji Apple", "Fresh Milk", "Safeguard"). Exclude sizes or prices from this field.
               - description: Extra details like brand, packaging type, flavor, size, weight, or model numbers (e.g., "1.5L", "10W-40", "Spicy", "500g bag"). Set to null if none.
               - unit: The selling unit (e.g., "pc", "kg", "bottle", "pack", "can", "box"). Default to "1pc" if not explicitly specified. Do NOT confuse size with the unit (e.g., for "500ml Coke", description is "500ml", unit is "bottle" or "pc").
               - price: The actual numeric cost (e.g., 150.00). Must be a clean number. Set to null if no price is specified.
               - priceText: The exact raw string representing the price found in the text (e.g., "₱150", "99.50", "P150.00").
               - isValid: Set to true for valid items; set to false if the entry is gibberish, unrecognizable random words/jumbled letters, incomplete, or not a product entry. Any unrecognizable non-words or meaningless letters like 'asdfasdf' must be considered invalid.
               - originalLine: The exact text segment this item was parsed from.
            3. Determine Categories for each item:
               - Check the "List of Existing Categories" first to see where the item is related.
               - If the input text contains an explicit category header (e.g., "[Beverages]", "Snacks:") or an inline category prefix (e.g., "groceries - eggs 10 pc"), map the item to that category name (preferably matching or normalizing to one of the existing categories, e.g., "groceries" -> "GROCERIES").
               - If there is NO category header or category prefix specified (e.g. just a list of items like "eggs 10 pc, kape 15 pack, pancit canton 15 pack" or "Fresh Milk 1L 150"):
                 - Semantically map each individual item to the best-fitting category from the "List of Existing Categories" (e.g., "eggs" -> "DAIRY", "kape" -> "BEVERAGES").
                 - If an item does not map to any existing category, use general knowledge/common sense to group it under a logical, standard high-level category name based on its type (e.g., "pancit canton" -> "CANNED GOODS & NOODLES" or "INSTANT NOODLES", "Tuna (canned)" -> "CANNED GOODS", "Fuji Apple" -> "FRUITS & VEGETABLES"). DO NOT simply copy the item's name as the category name (e.g., do not name the category "TUNA" or "APPLE").
               - If the item is invalid (isValid is false), group it under a category named "UNCATEGORIZED".
            4. Support Inline/Comma-Separated Items:
               - If a line contains a comma-separated list of items (e.g., "groceries - eggs 10 pc, kape 15 pack, pancit canton 15 pack" or just "eggs 10 pc, kape 15 pack"), split them and parse each item separately.
               - If the list starts with a category prefix followed by a separator (like "groceries -" or "Snacks:"), group all the comma-separated items in that line under that category.

            Return ONLY a JSON object matching this schema:
            {
              "categories": [
                {
                  "name": "string (uppercase category name)",
                  "items": [
                    {
                      "productName": "string",
                      "description": "string or null",
                      "unit": "string",
                      "price": number or null,
                      "priceText": "string",
                      "isValid": boolean,
                      "originalLine": "string"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val prompt = """
            Input Text to parse:
            ""${'"'}
            $rawText
            ""${'"'}
        """.trimIndent()

        val requestObj = GeminiRestRequest(
            systemInstruction = GeminiRestSystemInstruction(
                parts = listOf(GeminiRestPart(systemInstruction))
            ),
            contents = listOf(
                GeminiRestContent(
                    role = "user",
                    parts = listOf(GeminiRestPart(prompt))
                )
            ),
            generationConfig = GeminiRestGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1
            )
        )
        val requestBody = jsonDecoder.encodeToString(requestObj)

        var responseText: String? = null
        var lastError: Exception? = null

        val modelsToTry = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-flash-latest", "gemini-2.5-flash-lite")
        for (modelName in modelsToTry) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 30000
                conn.readTimeout = 30000

                conn.outputStream.use { os ->
                    val input = requestBody.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    break
                } else {
                    val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    throw IllegalStateException("API call to $modelName failed with code $responseCode: $errorText")
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("GeminiParser", "Failed to call model $modelName: ${e.message}")
            }
        }

        if (responseText == null) {
            throw lastError ?: IllegalStateException("All Gemini models failed to respond")
        }

        val restResponse = jsonDecoder.decodeFromString<GeminiRestResponse>(responseText)
        val rawJson = restResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Gemini")

        val cleanedJson = cleanJson(rawJson)
        val apiResponse = jsonDecoder.decodeFromString<GeminiParseResponse>(cleanedJson)

        mapToParseResult(apiResponse, existingCategoryIds, existingProductNames)
    }

    private fun cleanJson(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        return cleaned.trim()
    }

    private fun mapToParseResult(
        response: GeminiParseResponse,
        existingCategoryIds: Map<String, String>,
        existingProductNames: Set<String>
    ): ParseResult {
        val categories = mutableListOf<DraftCategory>()
        val invalidLines = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        var rowCounter = 0

        for (apiCat in response.categories) {
            val normalizedCatName = apiCat.name.trim().uppercase()
            val categoryId = existingCategoryIds.entries
                .firstOrNull { it.key.equals(normalizedCatName, ignoreCase = true) }
                ?.value

            val itemsList = mutableListOf<DraftItem>()

            for (apiItem in apiCat.items) {
                rowCounter++
                val validationErrors = mutableListOf<ValidationError>()
                var status = ValidationStatus.NEW

                val productName = apiItem.productName.trim()
                if (productName.isEmpty()) {
                    validationErrors.add(ValidationError.EMPTY_PRODUCT_NAME)
                    status = ValidationStatus.INVALID
                }

                if (!apiItem.isValid) {
                    validationErrors.add(ValidationError.INVALID_FORMAT)
                    status = ValidationStatus.INVALID
                    if (!apiItem.originalLine.isNullOrBlank()) {
                        invalidLines.add(apiItem.originalLine)
                    }
                }

                val priceVal = apiItem.price
                if (priceVal == null) {
                    validationErrors.add(ValidationError.INVALID_PRICE)
                    status = ValidationStatus.INVALID
                } else if (priceVal < 0) {
                    validationErrors.add(ValidationError.NEGATIVE_PRICE)
                    status = ValidationStatus.INVALID
                }

                if (normalizedCatName == "UNCATEGORIZED") {
                    validationErrors.add(ValidationError.MISSING_CATEGORY)
                    status = ValidationStatus.INVALID
                }

                if (status != ValidationStatus.INVALID) {
                    val normalizedName = productName.lowercase()
                    if (existingProductNames.contains(normalizedName)) {
                        status = ValidationStatus.UPDATE
                    }
                }

                val draftItem = DraftItem(
                    draftItemId = DraftIdGenerator.next("item"),
                    categoryId = categoryId,
                    categoryName = normalizedCatName,
                    productName = productName,
                    description = apiItem.description?.trim(),
                    unit = apiItem.unit.trim().lowercase(),
                    priceText = apiItem.priceText.ifBlank { priceVal?.toString().orEmpty() },
                    price = priceVal,
                    source = ImportSource.FAST_TEXT,
                    sourceRowNumber = rowCounter,
                    originalLine = apiItem.originalLine ?: productName,
                    validationStatus = status,
                    validationErrors = validationErrors
                )
                itemsList.add(draftItem)
            }

            if (itemsList.isNotEmpty()) {
                val draftCat = DraftCategory(
                    draftCategoryId = DraftIdGenerator.next("category"),
                    categoryId = categoryId,
                    name = normalizedCatName,
                    items = itemsList
                )
                categories.add(draftCat)
            }
        }

        // Check if there are any uncategorized warnings
        if (categories.any { it.name == "UNCATEGORIZED" }) {
            warnings.add("Some items were imported without category context and grouped under UNCATEGORIZED.")
        }

        return ParseResult(
            categories = categories,
            invalidLines = invalidLines,
            warnings = warnings
        )
    }
}

@Serializable
data class GeminiRestPart(val text: String)

@Serializable
data class GeminiRestContent(val parts: List<GeminiRestPart>, val role: String? = null)

@Serializable
data class GeminiRestSystemInstruction(val parts: List<GeminiRestPart>)

@Serializable
data class GeminiRestGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Double? = null
)

@Serializable
data class GeminiRestRequest(
    @SerialName("system_instruction")
    val systemInstruction: GeminiRestSystemInstruction,
    val contents: List<GeminiRestContent>,
    val generationConfig: GeminiRestGenerationConfig
)

@Serializable
data class GeminiRestCandidate(val content: GeminiRestContent, val finishReason: String? = null)

@Serializable
data class GeminiRestResponse(val candidates: List<GeminiRestCandidate>)

