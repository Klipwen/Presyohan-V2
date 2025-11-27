package com.presyohan.app

/**
 * Robust parser for Add Multiple Items that supports:
 * - Category headers (bracketed, dashed, plain line, heuristic: no digits)
 * - Single-line items with multiple separators (— - = > : or none)
 * - Bullet types (-, •, *) and multiline items (name line + price line)
 * - Optional description (parentheses) and optional unit (defaults to 1pc)
 * - Price with or without ₱ sign; validates decimals (0, 1, or 2 places)
 * - Ignores Presyohan export headers (PRICELIST, Shared via Presyohan, dates)
 * - Flags items without active category as ERROR_NO_CATEGORY for preview
 */
object AddMultipleItemsParser {

    private val bracketHeader = Regex("^\\s*\\[\\s*(.+?)\\s*]\\s*$")
    private val dashedOrPlainHeader = Regex("^\\s*([^\\d]+?)\\s*(?:[—\\-:]\\s*)?$")

    // Find prices; prefer the FIRST price after a separator (—, -, :, >) to
    // correctly parse Presyohan exports that may include a second trailing price
    // (e.g., "— ₱100.00 | — ₱1.00 | pc"). Fallback to LAST token to avoid digits
    // inside names like "Triangle (180/100pcs) — ₱2.00 | pc".
    private val priceToken = Regex("(?:₱\\s*)?([0-9]+(?:[.,][0-9]{1,2})?)")
    private val dateLine = Regex("^\\s*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\s*$")
    private val exportHeaderKeywords = listOf("PRICELIST", "SHARED VIA PRESYOHAN")
    private val bulletChars = setOf('-', '•', '*')

    private val knownUnits = setOf(
        "pc", "1pc", "pcs", "piece", "pieces", "pack", "cup", "stick", "box",
        "bottle", "can", "kg", "g", "lb", "l", "ml"
    )

    fun parseRawToCategories(raw: String, existingProductNames: Set<String>): List<ParsedCategory> {
        val categories = mutableListOf<ParsedCategory>()
        var currentCategory: ParsedCategory? = null
        val issuesCategory = ParsedCategory("INVALID ITEMS")

        val seenNames = mutableSetOf<String>()
        var pendingName: String? = null
        var pendingDesc: String? = null

        raw.lines().forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.isBlank()) return@forEach

            // Ignore export headers
            val upper = line.uppercase()
            if (exportHeaderKeywords.any { upper.contains(it) } || dateLine.matches(line)) return@forEach
            if (upper.contains("STORE") && upper.contains("BRANCH") && currentCategory == null) return@forEach

            // Category detection
            val catName = when {
                bracketHeader.matches(line) -> bracketHeader.matchEntire(line)!!.groupValues[1].trim().uppercase()
                dashedOrPlainHeader.matches(line) && !startsWithBullet(line) -> dashedOrPlainHeader.matchEntire(line)!!.groupValues[1].trim().uppercase()
                else -> null
            }
            if (!catName.isNullOrBlank()) {
                pendingName = null; pendingDesc = null
                currentCategory = ParsedCategory(catName)
                categories.add(currentCategory!!)
                return@forEach
            }

            // Multi-line item price continuation (e.g., "₱5 | stick")
            if (pendingName != null) {
                val price = extractPrice(line)
                if (price != null) {
                    val unit = extractUnitAfterPrice(line, priceMatchIndex(line))
                    val status = when {
                        price == null -> ItemStatus.ERROR_NO_PRICE
                        seenNames.contains(pendingName!!.lowercase()) -> ItemStatus.DUPLICATE
                        existingProductNames.contains(pendingName!!.lowercase()) -> ItemStatus.UPDATE
                        currentCategory == null -> ItemStatus.ERROR_NO_CATEGORY
                        else -> ItemStatus.NEW
                    }
                    val item = ParsedItem(
                        name = pendingName!!,
                        description = pendingDesc,
                        unit = unit ?: "1pc",
                        price = price,
                        status = status
                    )
                    if (currentCategory != null) {
                        currentCategory!!.items.add(item)
                    } else {
                        issuesCategory.items.add(item)
                    }
                    seenNames.add(pendingName!!.lowercase())
                    pendingName = null; pendingDesc = null
                    return@forEach
                }
                // If not a price line, fall through and treat as new parsing
            }

            // Single-line item
            val price = extractPrice(line)
            val priceIdx = priceMatchIndex(line)
            if (price != null && priceIdx != null) {
                val before = line.substring(0, priceIdx.first).trim()
                val nameDescRaw = stripLeadingBulletAndSeparators(before)
                val nameDesc = stripTrailingSeparators(nameDescRaw)
                val name = extractName(nameDesc)
                val desc = extractDescription(nameDesc)
                val unit = extractUnitAfterPrice(line, priceIdx) ?: findUnitBeforePrice(nameDesc) ?: "1pc"

                val normalizedName = name.lowercase()
                val status = when {
                    price == null -> ItemStatus.ERROR_NO_PRICE
                    seenNames.contains(normalizedName) -> ItemStatus.DUPLICATE
                    existingProductNames.contains(normalizedName) -> ItemStatus.UPDATE
                    currentCategory == null -> ItemStatus.ERROR_NO_CATEGORY
                    else -> ItemStatus.NEW
                }
                val item = ParsedItem(name, desc, unit, price, status)
                if (currentCategory != null) {
                    currentCategory!!.items.add(item)
                } else {
                    issuesCategory.items.add(item)
                }
                seenNames.add(normalizedName)
                return@forEach
            }

            // Possibly a multiline item start (bullet + name without price)
            if (startsWithBullet(line)) {
                val nameDescRaw = stripLeadingBulletAndSeparators(line)
                val nameDesc = stripTrailingSeparators(nameDescRaw)
                val name = extractName(nameDesc)
                val desc = extractDescription(nameDesc)
                // hold pending; next line should be price
                pendingName = name
                pendingDesc = desc
                return@forEach
            }

            // If line looks like a store header (e.g., "Store — Branch" without prices), ignore
            if (looksLikeStoreHeader(line)) {
                return@forEach
            }

            // If reached here, invalid/unknown format -> show in preview
            val nameGuess = line
            val status = if (currentCategory == null) ItemStatus.ERROR_NO_CATEGORY else ItemStatus.ERROR_INVALID_FORMAT
            val item = ParsedItem(
                name = nameGuess,
                description = null,
                unit = "1pc",
                price = null,
                status = status
            )
            if (currentCategory != null) {
                currentCategory!!.items.add(item)
            } else {
                issuesCategory.items.add(item)
            }
            seenNames.add(nameGuess.lowercase())
        }

        // Finalize: include issues category if it has items
        if (issuesCategory.items.isNotEmpty()) categories.add(issuesCategory)
        // Keep only categories that have items
        return categories.filter { it.items.isNotEmpty() }
    }

    private fun startsWithBullet(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && bulletChars.contains(t[0])
    }

    private fun stripLeadingBulletAndSeparators(text: String): String {
        var t = text.trim()
        if (t.isNotEmpty() && bulletChars.contains(t[0])) t = t.substring(1).trim()
        // Remove leading "-", "•", "*" and extra separators like ":", "-", "—"
        t = t.trim().trimStart('-', '•', '*', ':', '—').trim()
        return t
    }

    private fun extractDescription(nameDesc: String): String? {
        val m = Regex("\\((.*?)\\)").find(nameDesc)
        return m?.groupValues?.get(1)?.trim()?.ifBlank { null }
    }

    private fun extractName(nameDesc: String): String {
        return nameDesc.replace(Regex("\\(.*?\\)"), "").trim()
    }

    private fun extractPrice(line: String): Double? {
        val sep = firstSeparatorIndex(line)
        val matches = priceToken.findAll(line).toList()
        val target = if (sep != null) {
            matches.firstOrNull { it.range.first > sep }
        } else {
            matches.lastOrNull()
        } ?: return null
        val raw = target.groupValues[1]
        val normalized = raw.replace(",", "")
        return normalized.toDoubleOrNull()
    }

    private fun priceMatchIndex(line: String): IntRange? {
        val sep = firstSeparatorIndex(line)
        val matches = priceToken.findAll(line).toList()
        val target = if (sep != null) {
            matches.firstOrNull { it.range.first > sep }
        } else {
            matches.lastOrNull()
        }
        return target?.range
    }

    private fun extractUnitAfterPrice(line: String, priceRange: IntRange?): String? {
        if (priceRange == null) return null
        val after = line.substring(priceRange.last + 1).trim()
        if (after.isBlank()) return null
        // Prefer last pipe-separated unit to handle formats with two prices
        val lastPipeIdx = after.lastIndexOf('|')
        if (lastPipeIdx >= 0) {
            return after.substring(lastPipeIdx + 1).trim().ifBlank { null }
        }
        // Otherwise, capture patterns like "1 pc", "pc", "3pcs"
        val m = Regex("^([0-9]+\\s*[a-zA-Z]+(?:\\s*[a-zA-Z]+)*)").find(after)
        if (m != null) return m.groupValues[1].trim().lowercase()
        val token = after.split(Regex("\\s+")).firstOrNull()?.lowercase()
        return if (token != null && token.any { it.isLetter() } && token.isNotBlank()) token else null
    }

    private fun findUnitBeforePrice(before: String): String? {
        val tokens = before.trim().split(Regex("\\s+"))
        val candidate = tokens.lastOrNull()?.lowercase()
        return if (candidate != null && knownUnits.contains(candidate)) candidate else null
    }

    private fun stripTrailingSeparators(text: String): String {
        var t = text.trim()
        t = t.trimEnd('—', '-', '=', '>', ':')
        return t.trim()
    }

    private fun firstSeparatorIndex(line: String): Int? {
        val idxs = listOf(
            line.indexOf('—'),
            line.indexOf('-'),
            line.indexOf(':'),
            line.indexOf('>')
        ).filter { it >= 0 }
        return idxs.minOrNull()
    }

    private fun looksLikeStoreHeader(line: String): Boolean {
        // Heuristic: contains a long dash, no price token, not a bullet, and not a bracket header
        val hasDash = line.contains('—')
        val hasPrice = priceToken.containsMatchIn(line)
        val isBullet = startsWithBullet(line)
        val isBracket = bracketHeader.matches(line)
        return hasDash && !hasPrice && !isBullet && !isBracket
    }
}
