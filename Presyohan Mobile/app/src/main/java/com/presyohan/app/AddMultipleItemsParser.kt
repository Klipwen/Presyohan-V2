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
    private val dashedOrPlainHeader = Regex("^\\s*([^\\d\\-\\—\\:\\>\\=\\[\\]]+?)\\s*(?:[—\\-:]\\s*)?$")

    // Find prices; prefer the FIRST price after a separator (—, -, :, >)
    private val priceToken = Regex("(?:₱|PHP|Php|php|P|p)?\\s*([0-9]+(?:[.,][0-9]{1,2})?)")
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
        val issuesCategory = ParsedCategory("UNCATEGORIZED")

        val seenNames = mutableSetOf<String>()
        var pendingName: String? = null
        var pendingDesc: String? = null

        raw.lines().forEach { lineRaw ->
            val lineNormalized = lineRaw
                .replace('\u2014', '-') // em-dash
                .replace('\u2013', '-') // en-dash
                .replace('\u2012', '-') // figure-dash
                .replace('\u00A0', ' ') // non-breaking space
            val line = lineNormalized.trim()
            if (line.isBlank()) return@forEach

            // Ignore export headers
            val upper = line.uppercase()
            if (exportHeaderKeywords.any { upper.contains(it) } || dateLine.matches(line)) return@forEach
            if (upper.contains("STORE") && upper.contains("BRANCH") && currentCategory == null) return@forEach

            // Check if this is a comma-separated list of items
            val commaSplitResult = splitCommaSeparatedLine(line)
            if (commaSplitResult != null) {
                val catOverride = commaSplitResult.first
                val itemLines = commaSplitResult.second

                if (!catOverride.isNullOrBlank()) {
                    currentCategory = ParsedCategory(catOverride.trim().uppercase())
                    categories.add(currentCategory!!)
                }

                itemLines.forEach { itemLine ->
                    val item = parsePartToParsedItem(
                        part = itemLine,
                        seenNames = seenNames,
                        existingProductNames = existingProductNames,
                        hasCategory = (currentCategory != null)
                    )
                    if (currentCategory != null) {
                        currentCategory!!.items.add(item)
                    } else {
                        issuesCategory.items.add(item)
                    }
                    seenNames.add(item.name.lowercase())
                }
                return@forEach
            }

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
                pendingName = name
                pendingDesc = desc
                return@forEach
            }

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

        if (issuesCategory.items.isNotEmpty()) categories.add(issuesCategory)
        return categories.filter { it.items.isNotEmpty() }
    }

    fun parseTextToResult(raw: String, existingProductNames: Set<String>): ParseResult {
        val categories = mutableListOf<DraftCategory>()
        val invalidLines = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        var currentCategory: DraftCategory? = null
        val uncategorizedCategory = DraftCategory(
            draftCategoryId = DraftIdGenerator.next("category"),
            name = "UNCATEGORIZED",
            items = mutableListOf()
        )

        val seenNames = mutableSetOf<String>()
        var pendingName: String? = null
        var pendingDesc: String? = null
        var pendingOriginalLine: String? = null
        var pendingRowNumber: Int? = null

        var rowCounter = 0

        raw.lines().forEach { lineRaw ->
            rowCounter++
            val lineNormalized = lineRaw
                .replace('\u2014', '-') // em-dash
                .replace('\u2013', '-') // en-dash
                .replace('\u2012', '-') // figure-dash
                .replace('\u00A0', ' ') // non-breaking space
            val line = lineNormalized.trim()
            if (line.isBlank()) return@forEach

            val upper = line.uppercase()
            if (exportHeaderKeywords.any { upper.contains(it) } || dateLine.matches(line)) {
                return@forEach
            }
            if (upper.contains("STORE") && upper.contains("BRANCH") && currentCategory == null) {
                return@forEach
            }

            // Check if this is a comma-separated list of items
            val commaSplitResult = splitCommaSeparatedLine(line)
            if (commaSplitResult != null) {
                if (pendingName != null) {
                    val draftItem = DraftItem(
                        draftItemId = DraftIdGenerator.next("item"),
                        categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                        categoryId = currentCategory?.categoryId,
                        productName = pendingName!!,
                        description = pendingDesc,
                        unit = "1pc",
                        priceText = "",
                        price = null,
                        source = ImportSource.FAST_TEXT,
                        sourceRowNumber = pendingRowNumber,
                        originalLine = pendingOriginalLine,
                        validationStatus = ValidationStatus.INVALID,
                        validationErrors = listOf(ValidationError.INVALID_PRICE)
                    )
                    if (currentCategory != null) {
                        currentCategory!!.items.add(draftItem)
                    } else {
                        uncategorizedCategory.items.add(draftItem)
                    }
                    seenNames.add(pendingName!!.lowercase())
                    pendingName = null; pendingDesc = null; pendingOriginalLine = null; pendingRowNumber = null
                }

                val catOverride = commaSplitResult.first
                val itemLines = commaSplitResult.second

                if (!catOverride.isNullOrBlank()) {
                    val newCat = DraftCategory(
                        draftCategoryId = DraftIdGenerator.next("category"),
                        name = catOverride.trim().uppercase(),
                        items = mutableListOf()
                    )
                    currentCategory = newCat
                    categories.add(newCat)
                }

                itemLines.forEach { itemLine ->
                    val draftItem = parsePartToDraftItem(
                        part = itemLine,
                        originalLine = lineRaw,
                        rowCounter = rowCounter,
                        categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                        categoryId = currentCategory?.categoryId,
                        seenNames = seenNames,
                        existingProductNames = existingProductNames
                    )
                    if (draftItem.validationStatus == ValidationStatus.INVALID && draftItem.validationErrors.contains(ValidationError.INVALID_FORMAT)) {
                        invalidLines.add(itemLine)
                    }
                    if (currentCategory != null) {
                        currentCategory!!.items.add(draftItem)
                    } else {
                        uncategorizedCategory.items.add(draftItem)
                    }
                    seenNames.add(draftItem.productName.lowercase())
                }
                return@forEach
            }

            val bracketHeaderRegex = Regex("^\\s*\\[\\s*(.+?)\\s*]\\s*$")
            val dashedOrPlainHeaderRegex = Regex("^\\s*([^\\d\\-\\:\\>\\=\\[\\]]+?)\\s*(?:[\\-:]\\s*)?$")

            val catName = when {
                bracketHeaderRegex.matches(line) -> bracketHeaderRegex.matchEntire(line)!!.groupValues[1].trim().uppercase()
                dashedOrPlainHeaderRegex.matches(line) && !startsWithBullet(line) -> dashedOrPlainHeaderRegex.matchEntire(line)!!.groupValues[1].trim().uppercase()
                else -> null
            }

            if (!catName.isNullOrBlank()) {
                if (pendingName != null) {
                    val draftItem = DraftItem(
                        draftItemId = DraftIdGenerator.next("item"),
                        categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                        categoryId = currentCategory?.categoryId,
                        productName = pendingName!!,
                        description = pendingDesc,
                        unit = "1pc",
                        priceText = "",
                        price = null,
                        source = ImportSource.FAST_TEXT,
                        sourceRowNumber = pendingRowNumber,
                        originalLine = pendingOriginalLine,
                        validationStatus = ValidationStatus.INVALID,
                        validationErrors = listOf(ValidationError.INVALID_PRICE)
                    )
                    if (currentCategory != null) {
                        currentCategory!!.items.add(draftItem)
                    } else {
                        uncategorizedCategory.items.add(draftItem)
                    }
                    seenNames.add(pendingName!!.lowercase())
                    pendingName = null; pendingDesc = null; pendingOriginalLine = null; pendingRowNumber = null
                }

                val newCat = DraftCategory(
                    draftCategoryId = DraftIdGenerator.next("category"),
                    name = catName,
                    items = mutableListOf()
                )
                currentCategory = newCat
                categories.add(newCat)
                return@forEach
            }

            val priceTokenRegex = Regex("(-\\s*)?(?:₱|PHP|Php|php|P|p)?\\s*([0-9]+(?:[.,][0-9]{1,2})?)")

            fun findPriceMatchIndex(l: String): IntRange? {
                val sep = firstSeparatorIndex(l)
                val matches = priceTokenRegex.findAll(l).toList()
                val target = if (sep != null) {
                    matches.firstOrNull { it.range.first >= sep }
                } else {
                    matches.lastOrNull()
                }
                return target?.range
            }

            if (pendingName != null) {
                val priceRange = findPriceMatchIndex(line)
                if (priceRange != null) {
                    val matchResult = priceTokenRegex.find(line, priceRange.first)
                    val isNegative = determineIsNegative(matchResult, priceRange.first, line)
                    val priceVal = matchResult?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()

                    val unit = extractUnitAfterPrice(line, priceRange) ?: "1pc"
                    val normalizedName = pendingName!!.lowercase()

                    val validationErrors = mutableListOf<ValidationError>()
                    var status = ValidationStatus.NEW

                    if (priceVal == null) {
                        validationErrors.add(ValidationError.INVALID_PRICE)
                        status = ValidationStatus.INVALID
                    } else if (isNegative) {
                        validationErrors.add(ValidationError.NEGATIVE_PRICE)
                        status = ValidationStatus.INVALID
                    }

                    if (status != ValidationStatus.INVALID) {
                        if (seenNames.contains(normalizedName)) {
                            status = ValidationStatus.DUPLICATE
                        } else if (existingProductNames.contains(normalizedName)) {
                            status = ValidationStatus.UPDATE
                        }
                    }

                    val item = DraftItem(
                        draftItemId = DraftIdGenerator.next("item"),
                        categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                        categoryId = currentCategory?.categoryId,
                        productName = pendingName!!,
                        description = pendingDesc,
                        unit = unit,
                        priceText = priceVal?.toString() ?: "",
                        price = priceVal,
                        source = ImportSource.FAST_TEXT,
                        sourceRowNumber = pendingRowNumber,
                        originalLine = "$pendingOriginalLine\n$lineRaw",
                        validationStatus = status,
                        validationErrors = validationErrors
                    )

                    if (currentCategory != null) {
                        currentCategory.items.add(item)
                    } else {
                        uncategorizedCategory.items.add(item)
                    }

                    seenNames.add(normalizedName)
                    pendingName = null; pendingDesc = null; pendingOriginalLine = null; pendingRowNumber = null
                    return@forEach
                }
                val draftItem = DraftItem(
                    draftItemId = DraftIdGenerator.next("item"),
                    categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                    categoryId = currentCategory?.categoryId,
                    productName = pendingName!!,
                    description = pendingDesc,
                    unit = "1pc",
                    priceText = "",
                    price = null,
                    source = ImportSource.FAST_TEXT,
                    sourceRowNumber = pendingRowNumber,
                    originalLine = pendingOriginalLine,
                    validationStatus = ValidationStatus.INVALID,
                    validationErrors = listOf(ValidationError.INVALID_PRICE)
                )
                if (currentCategory != null) {
                    currentCategory.items.add(draftItem)
                } else {
                    uncategorizedCategory.items.add(draftItem)
                }
                seenNames.add(pendingName!!.lowercase())
                pendingName = null; pendingDesc = null; pendingOriginalLine = null; pendingRowNumber = null
            }

            val priceRange = findPriceMatchIndex(line)
            if (priceRange != null) {
                val matchResult = priceTokenRegex.find(line, priceRange.first)
                val isNegative = determineIsNegative(matchResult, priceRange.first, line)
                val priceVal = matchResult?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()

                val before = line.substring(0, priceRange.first).trim()
                val nameDescRaw = stripLeadingBulletAndSeparators(before)
                val nameDesc = stripTrailingSeparators(nameDescRaw)
                val name = extractName(nameDesc)
                val desc = extractDescription(nameDesc)
                val unit = extractUnitAfterPrice(line, priceRange) ?: findUnitBeforePrice(nameDesc) ?: "1pc"

                val normalizedName = name.lowercase()
                val validationErrors = mutableListOf<ValidationError>()
                var status = ValidationStatus.NEW

                if (name.isBlank()) {
                    validationErrors.add(ValidationError.EMPTY_PRODUCT_NAME)
                    status = ValidationStatus.INVALID
                }
                if (priceVal == null) {
                    validationErrors.add(ValidationError.INVALID_PRICE)
                    status = ValidationStatus.INVALID
                } else if (isNegative) {
                    validationErrors.add(ValidationError.NEGATIVE_PRICE)
                    status = ValidationStatus.INVALID
                }

                if (status != ValidationStatus.INVALID) {
                    if (seenNames.contains(normalizedName)) {
                        status = ValidationStatus.DUPLICATE
                    } else if (existingProductNames.contains(normalizedName)) {
                        status = ValidationStatus.UPDATE
                    }
                }

                val item = DraftItem(
                    draftItemId = DraftIdGenerator.next("item"),
                    categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                    categoryId = currentCategory?.categoryId,
                    productName = name,
                    description = desc,
                    unit = unit,
                    priceText = priceVal?.toString() ?: "",
                    price = priceVal,
                    source = ImportSource.FAST_TEXT,
                    sourceRowNumber = rowCounter,
                    originalLine = lineRaw,
                    validationStatus = status,
                    validationErrors = validationErrors
                )

                if (currentCategory != null) {
                    currentCategory.items.add(item)
                } else {
                    uncategorizedCategory.items.add(item)
                }
                seenNames.add(normalizedName)
                return@forEach
            }

            if (startsWithBullet(line)) {
                val nameDescRaw = stripLeadingBulletAndSeparators(line)
                val nameDesc = stripTrailingSeparators(nameDescRaw)
                val name = extractName(nameDesc)
                val desc = extractDescription(nameDesc)

                pendingName = name
                pendingDesc = desc
                pendingOriginalLine = lineRaw
                pendingRowNumber = rowCounter
                return@forEach
            }

            if (looksLikeStoreHeader(line)) {
                return@forEach
            }

            invalidLines.add(lineRaw)
            val nameGuess = line
            val validationErrors = mutableListOf<ValidationError>(ValidationError.INVALID_FORMAT)
            val item = DraftItem(
                draftItemId = DraftIdGenerator.next("item"),
                categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                categoryId = currentCategory?.categoryId,
                productName = nameGuess,
                description = null,
                unit = "1pc",
                priceText = "",
                price = null,
                source = ImportSource.FAST_TEXT,
                sourceRowNumber = rowCounter,
                originalLine = lineRaw,
                validationStatus = ValidationStatus.INVALID,
                validationErrors = validationErrors
            )

            if (currentCategory != null) {
                currentCategory.items.add(item)
            } else {
                uncategorizedCategory.items.add(item)
            }
            seenNames.add(nameGuess.lowercase())
        }

        if (pendingName != null) {
            val draftItem = DraftItem(
                draftItemId = DraftIdGenerator.next("item"),
                categoryName = currentCategory?.name ?: "UNCATEGORIZED",
                categoryId = currentCategory?.categoryId,
                productName = pendingName!!,
                description = pendingDesc,
                unit = "1pc",
                priceText = "",
                price = null,
                source = ImportSource.FAST_TEXT,
                sourceRowNumber = pendingRowNumber,
                originalLine = pendingOriginalLine,
                validationStatus = ValidationStatus.INVALID,
                validationErrors = listOf(ValidationError.INVALID_PRICE)
            )
            if (currentCategory != null) {
                currentCategory.items.add(draftItem)
            } else {
                uncategorizedCategory.items.add(draftItem)
            }
            seenNames.add(pendingName!!.lowercase())
        }

        if (uncategorizedCategory.items.isNotEmpty()) {
            categories.add(0, uncategorizedCategory)
            warnings.add("Some items were imported without category context and grouped under UNCATEGORIZED.")
        }

        return ParseResult(
            categories = categories,
            invalidLines = invalidLines,
            warnings = warnings
        )
    }

    private fun startsWithBullet(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && bulletChars.contains(t[0])
    }

    private fun stripLeadingBulletAndSeparators(text: String): String {
        var t = text.trim()
        if (t.isNotEmpty() && bulletChars.contains(t[0])) t = t.substring(1).trim()
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
            matches.firstOrNull { it.range.first >= sep }
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
            matches.firstOrNull { it.range.first >= sep }
        } else {
            matches.lastOrNull()
        }
        return target?.range
    }

    private fun extractUnitAfterPrice(line: String, priceRange: IntRange?): String? {
        if (priceRange == null) return null
        val after = line.substring(priceRange.last + 1).trim()
        if (after.isBlank()) return null
        val lastPipeIdx = after.lastIndexOf('|')
        if (lastPipeIdx >= 0) {
            return after.substring(lastPipeIdx + 1).trim().ifBlank { null }
        }
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

    private fun determineIsNegative(matchResult: MatchResult?, matchStartIdx: Int, line: String): Boolean {
        if (matchResult == null) return false
        val sepIdx = firstSeparatorIndex(line)
        if (sepIdx != null && matchStartIdx == sepIdx) {
            val matchText = matchResult.value
            if (matchText.isNotEmpty()) {
                val rest = matchText.substring(1)
                return rest.contains('-')
            }
        }
        return matchResult.groupValues[1].isNotEmpty()
    }

    private fun looksLikeStoreHeader(line: String): Boolean {
        val sepIdx = firstSeparatorIndex(line) ?: return false
        val isBullet = startsWithBullet(line)
        val isBracket = bracketHeader.matches(line)
        if (isBullet || isBracket) return false

        val afterSeparator = line.substring(sepIdx + 1)
        val hasPriceAfterSeparator = priceToken.containsMatchIn(afterSeparator)
        return !hasPriceAfterSeparator
    }

    private fun splitByCommaOutsideParentheses(input: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0
        var bracketDepth = 0
        for (char in input) {
            when (char) {
                '(' -> {
                    parenDepth++
                    current.append(char)
                }
                ')' -> {
                    if (parenDepth > 0) parenDepth--
                    current.append(char)
                }
                '[' -> {
                    bracketDepth++
                    current.append(char)
                }
                ']' -> {
                    if (bracketDepth > 0) bracketDepth--
                    current.append(char)
                }
                ',' -> {
                    if (parenDepth == 0 && bracketDepth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun splitCommaSeparatedLine(line: String): Pair<String?, List<String>>? {
        val parts = splitByCommaOutsideParentheses(line)
        if (parts.size < 2) return null

        var priceCount = 0
        for (part in parts) {
            if (extractPrice(part) != null) {
                priceCount++
            }
        }

        if (priceCount < 2) return null

        val firstPart = parts[0].trim()

        val bracketPrefixRegex = Regex("^\\s*\\[\\s*(.+?)\\s*\\]\\s*(.+)$")
        val bracketMatch = bracketPrefixRegex.matchEntire(firstPart)
        if (bracketMatch != null) {
            val catName = bracketMatch.groupValues[1].trim()
            val remainingItem = bracketMatch.groupValues[2].trim()
            val remainingParts = listOf(remainingItem) + parts.drop(1).map { it.trim() }
            return Pair(catName, remainingParts)
        }

        val sepIdx = firstSeparatorIndex(firstPart)
        if (sepIdx != null) {
            val left = firstPart.substring(0, sepIdx).trim()
            val right = firstPart.substring(sepIdx + 1).trim()

            val leftHasDigitsOrBrackets = left.any { it.isDigit() || it == '[' || it == ']' }
            if (!leftHasDigitsOrBrackets && left.isNotEmpty() && left.length < 40) {
                val rightPrice = extractPrice(right)
                val rightPriceIdx = priceMatchIndex(right)
                if (rightPrice != null && rightPriceIdx != null) {
                    val before = right.substring(0, rightPriceIdx.first).trim()
                    val nameDescRaw = stripLeadingBulletAndSeparators(before)
                    val nameDesc = stripTrailingSeparators(nameDescRaw)
                    val name = extractName(nameDesc)
                    if (name.isNotEmpty()) {
                        val remainingParts = listOf(right) + parts.drop(1).map { it.trim() }
                        return Pair(left, remainingParts)
                    }
                }
            }
        }

        return Pair(null, parts.map { it.trim() })
    }

    private fun parsePartToParsedItem(
        part: String,
        seenNames: MutableSet<String>,
        existingProductNames: Set<String>,
        hasCategory: Boolean
    ): ParsedItem {
        val price = extractPrice(part)
        val priceIdx = priceMatchIndex(part)
        if (price != null && priceIdx != null) {
            val before = part.substring(0, priceIdx.first).trim()
            val nameDescRaw = stripLeadingBulletAndSeparators(before)
            val nameDesc = stripTrailingSeparators(nameDescRaw)
            val name = extractName(nameDesc)
            val desc = extractDescription(nameDesc)
            val unit = extractUnitAfterPrice(part, priceIdx) ?: findUnitBeforePrice(nameDesc) ?: "1pc"

            val normalizedName = name.lowercase()
            val status = when {
                seenNames.contains(normalizedName) -> ItemStatus.DUPLICATE
                existingProductNames.contains(normalizedName) -> ItemStatus.UPDATE
                !hasCategory -> ItemStatus.ERROR_NO_CATEGORY
                else -> ItemStatus.NEW
            }
            return ParsedItem(name, desc, unit, price, status)
        } else {
            val nameGuess = stripLeadingBulletAndSeparators(part)
            val status = if (!hasCategory) ItemStatus.ERROR_NO_CATEGORY else ItemStatus.ERROR_INVALID_FORMAT
            return ParsedItem(
                name = nameGuess,
                description = null,
                unit = "1pc",
                price = null,
                status = status
            )
        }
    }

    private fun parsePartToDraftItem(
        part: String,
        originalLine: String,
        rowCounter: Int,
        categoryName: String,
        categoryId: String?,
        seenNames: MutableSet<String>,
        existingProductNames: Set<String>
    ): DraftItem {
        val priceRange = priceMatchIndex(part)
        if (priceRange != null) {
            val priceTokenRegex = Regex("(-\\s*)?(?:₱|PHP|Php|php|P|p)?\\s*([0-9]+(?:[.,][0-9]{1,2})?)")
            val matchResult = priceTokenRegex.find(part, priceRange.first)
            val isNegative = determineIsNegative(matchResult, priceRange.first, part)
            val priceVal = matchResult?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()

            val before = part.substring(0, priceRange.first).trim()
            val nameDescRaw = stripLeadingBulletAndSeparators(before)
            val nameDesc = stripTrailingSeparators(nameDescRaw)
            val name = extractName(nameDesc)
            val desc = extractDescription(nameDesc)
            val unit = extractUnitAfterPrice(part, priceRange) ?: findUnitBeforePrice(nameDesc) ?: "1pc"

            val normalizedName = name.lowercase()
            val validationErrors = mutableListOf<ValidationError>()
            var status = ValidationStatus.NEW

            if (name.isBlank()) {
                validationErrors.add(ValidationError.EMPTY_PRODUCT_NAME)
                status = ValidationStatus.INVALID
            }
            if (priceVal == null) {
                validationErrors.add(ValidationError.INVALID_PRICE)
                status = ValidationStatus.INVALID
            } else if (isNegative) {
                validationErrors.add(ValidationError.NEGATIVE_PRICE)
                status = ValidationStatus.INVALID
            }

            if (status != ValidationStatus.INVALID) {
                if (seenNames.contains(normalizedName)) {
                    status = ValidationStatus.DUPLICATE
                } else if (existingProductNames.contains(normalizedName)) {
                    status = ValidationStatus.UPDATE
                }
            }

            return DraftItem(
                draftItemId = DraftIdGenerator.next("item"),
                categoryName = categoryName,
                categoryId = categoryId,
                productName = name,
                description = desc,
                unit = unit,
                priceText = priceVal?.toString() ?: "",
                price = priceVal,
                source = ImportSource.FAST_TEXT,
                sourceRowNumber = rowCounter,
                originalLine = part,
                validationStatus = status,
                validationErrors = validationErrors
            )
        } else {
            val validationErrors = mutableListOf<ValidationError>(ValidationError.INVALID_FORMAT)
            val nameGuess = stripLeadingBulletAndSeparators(part)
            return DraftItem(
                draftItemId = DraftIdGenerator.next("item"),
                categoryName = categoryName,
                categoryId = categoryId,
                productName = nameGuess,
                description = null,
                unit = "1pc",
                priceText = "",
                price = null,
                source = ImportSource.FAST_TEXT,
                sourceRowNumber = rowCounter,
                originalLine = part,
                validationStatus = ValidationStatus.INVALID,
                validationErrors = validationErrors
            )
        }
    }
}
