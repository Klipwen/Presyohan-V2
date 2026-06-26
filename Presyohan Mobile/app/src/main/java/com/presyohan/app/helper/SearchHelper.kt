package com.presyohan.app.helper

import java.util.Locale

object SearchHelper {

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    fun isFuzzyMatch(token: String, text: String): Boolean {
        val cleanText = text.lowercase(Locale.getDefault())
        val cleanToken = token.lowercase(Locale.getDefault())

        if (cleanText.contains(cleanToken)) return true

        val words = cleanText.split(Regex("[\\s,\\.\\-\\(\\)\\[\\]/]+"))
        for (word in words) {
            if (word.isEmpty()) continue
            if (word.contains(cleanToken)) return true

            if (cleanToken.length >= 3) {
                val maxAllowedDistance = if (cleanToken.length <= 4) 1 else 2
                if (levenshteinDistance(cleanToken, word) <= maxAllowedDistance) {
                    return true
                }
            }
        }
        return false
    }

    fun matchPrice(token: String, price: Double): Boolean {
        val cleanToken = token.lowercase(Locale.getDefault()).replace("₱", "").replace(",", "").trim()
        if (cleanToken.isEmpty()) return false

        val priceStr1 = String.format(Locale.getDefault(), "%.2f", price)
        val priceStr2 = price.toInt().toString()

        return priceStr1.contains(cleanToken) || priceStr2.contains(cleanToken)
    }

    fun calculateProductScore(
        query: String,
        name: String,
        description: String? = null,
        categoryName: String? = null,
        storeName: String? = null
    ): Double {
        val q = query.lowercase(Locale.getDefault()).trim()
        if (q.isEmpty()) return 0.0

        val pName = name.lowercase(Locale.getDefault()).trim()
        val pDesc = (description ?: "").lowercase(Locale.getDefault()).trim()
        val pCat = (categoryName ?: "").lowercase(Locale.getDefault()).trim()
        val pStore = (storeName ?: "").lowercase(Locale.getDefault()).trim()

        var score = 0.0

        // 1. Exact name match
        if (pName == q) {
            score += 10000.0
        }

        // 2. Name starts with query
        if (pName.startsWith(q)) {
            score += 5000.0
        }

        // 3. Name contains query
        if (pName.contains(q)) {
            score += 2000.0
        }

        // Token based checks
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val nameWords = pName.split(Regex("[\\s,\\.\\-\\(\\)\\[\\]/]+")).filter { it.isNotEmpty() }
        val descWords = pDesc.split(Regex("[\\s,\\.\\-\\(\\)\\[\\]/]+")).filter { it.isNotEmpty() }

        for (token in tokens) {
            // Word exact match in name
            if (nameWords.contains(token)) {
                score += 1000.0
            }

            // Contains token in name
            if (pName.contains(token)) {
                score += 500.0
            }

            // Fuzzy match in name words
            for (word in nameWords) {
                if (word.contains(token)) {
                    score += 200.0 * (token.length.toDouble() / word.length.toDouble())
                } else if (token.length >= 3) {
                    val maxDist = if (token.length <= 4) 1 else 2
                    val dist = levenshteinDistance(token, word)
                    if (dist <= maxDist) {
                        score += 100.0 * (1.0 - dist.toDouble() / token.length.toDouble())
                    }
                }
            }

            // Matches in description
            if (pDesc.contains(token)) {
                score += 50.0
            }
            for (word in descWords) {
                if (word.contains(token)) {
                    score += 20.0 * (token.length.toDouble() / word.length.toDouble())
                } else if (token.length >= 3) {
                    val maxDist = if (token.length <= 4) 1 else 2
                    val dist = levenshteinDistance(token, word)
                    if (dist <= maxDist) {
                        score += 10.0 * (1.0 - dist.toDouble() / token.length.toDouble())
                    }
                }
            }

            // Matches in Category Name
            if (pCat.contains(token)) {
                score += 30.0
            }

            // Matches in Store Name
            if (pStore.contains(token)) {
                score += 10.0
            }
        }

        return score
    }
}
