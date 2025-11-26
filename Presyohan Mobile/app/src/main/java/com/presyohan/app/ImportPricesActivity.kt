package com.presyohan.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class ImportPricesActivity : AppCompatActivity() {
    private lateinit var loadingOverlay: View

    private var storeId: String = ""
    private var storeName: String = ""
    private var storeBranch: String = ""

    private enum class Step { UPLOAD, VALIDATE, REVIEW, DONE }
    private var step: Step = Step.UPLOAD

    // Parsed data
    @Serializable
    data class ParsedRow(
        val rowIndex: Int,
        val category: String,
        val name: String,
        val description: String?,
        val unit: String?,
        val price: Double
    )

    data class Issue(val rowIndex: Int, val message: String)
    data class Warn(val rowIndex: Int, val message: String)

    private val rows = mutableListOf<ParsedRow>()
    private val issues = mutableListOf<Issue>()
    private val warnings = mutableListOf<Warn>()

    // Preview
    data class CreatePreview(
        val name: String,
        val category: String?,
        val unit: String?,
        val price: Double,
        val description: String?
    )
    data class UpdatePreview(
        val productId: String,
        val name: String,
        val prevCategory: String?,
        val prevUnit: String?,
        val prevPrice: Double,
        val prevDescription: String?,
        val nextCategory: String?,
        val nextUnit: String?,
        val nextPrice: Double,
        val nextDescription: String?
    )

    private val previewCreates = mutableListOf<CreatePreview>()
    private val previewUpdates = mutableListOf<UpdatePreview>()

    // Views
    private lateinit var headerStoreText: TextView
    private lateinit var headerBranchText: TextView
    private lateinit var inputText: EditText
    private lateinit var charCountText: TextView
    private lateinit var btnParseText: Button

    private lateinit var cardValidate: View
    private lateinit var parsedCountText: TextView
    private lateinit var warnCountText: TextView
    private lateinit var issuesCountText: TextView
    private lateinit var btnComputeDryRun: Button

    private lateinit var cardReview: View
    private lateinit var createsCountText: TextView
    private lateinit var updatesCountText: TextView
    private lateinit var btnApplyImport: Button

    private lateinit var cardDone: View
    private lateinit var doneSummaryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_prices)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId") ?: ""
        storeName = intent.getStringExtra("storeName") ?: "Store"
        storeBranch = intent.getStringExtra("storeBranch") ?: ""

        setupHeader()
        setupUpload()
        setupValidate()
        setupReview()
        setupDone()
        setStep(Step.UPLOAD)

        // Load branch if absent
        if (storeBranch.isEmpty() && storeId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    @Serializable
                    data class StoreRow(val branch: String?)
                    val rows = SupabaseProvider.client.postgrest["stores"].select {
                        filter { eq("id", storeId) }
                        limit(1)
                    }.decodeList<StoreRow>()
                    storeBranch = rows.firstOrNull()?.branch ?: ""
                    headerBranchText.text = storeBranch
                } catch (_: Exception) {}
            }
        }
    }

    private fun setupHeader() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        headerStoreText = findViewById(R.id.storeText)
        headerBranchText = findViewById(R.id.storeBranchText)
        headerStoreText.text = storeName
        headerBranchText.text = storeBranch
    }

    private fun setupUpload() {
        inputText = findViewById(R.id.inputText)
        charCountText = findViewById(R.id.charCountText)
        btnParseText = findViewById(R.id.btnParseText)

        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                charCountText.text = (s?.length ?: 0).toString() + " characters"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnParseText.setOnClickListener {
            parseRawText(inputText.text?.toString() ?: "")
        }
    }

    private fun setupValidate() {
        cardValidate = findViewById(R.id.cardValidate)
        parsedCountText = findViewById(R.id.parsedCountText)
        warnCountText = findViewById(R.id.warnCountText)
        issuesCountText = findViewById(R.id.issuesCountText)
        btnComputeDryRun = findViewById(R.id.btnComputeDryRun)

        btnComputeDryRun.setOnClickListener {
            computeDryRun()
        }
    }

    private fun setupReview() {
        cardReview = findViewById(R.id.cardReview)
        createsCountText = findViewById(R.id.createsCountText)
        updatesCountText = findViewById(R.id.updatesCountText)
        btnApplyImport = findViewById(R.id.btnApplyImport)
        btnApplyImport.setOnClickListener { applyImport() }
    }

    private fun setupDone() {
        cardDone = findViewById(R.id.cardDone)
        doneSummaryText = findViewById(R.id.doneSummaryText)
    }

    private fun setStep(next: Step) {
        step = next
        findViewById<View>(R.id.cardUpload).visibility = if (step == Step.UPLOAD) View.VISIBLE else View.GONE
        cardValidate.visibility = if (step == Step.VALIDATE) View.VISIBLE else View.GONE
        cardReview.visibility = if (step == Step.REVIEW) View.VISIBLE else View.GONE
        cardDone.visibility = if (step == Step.DONE) View.VISIBLE else View.GONE
    }

    // ===== RAW TEXT PARSER (Updated to match web logic) =====
    private fun parseRawText(textRaw: String) {
        try {
            warnings.clear(); issues.clear(); rows.clear()

            val lines = textRaw.split("\r?\n".toRegex())
            val cleaned = mutableListOf<String>()
            for (ln0 in lines) {
                var ln = ln0 ?: ""
                if (ln.isEmpty()) { cleaned.add(""); continue }
                ln = ln.replace('\u00A0', ' ')
                val ignorePatterns = listOf(
                    """^\n?\s*PRICELIST:?\s*$""".toRegex(RegexOption.IGNORE_CASE),
                    """Shared via Presyohan""".toRegex(RegexOption.IGNORE_CASE),
                    """^\s*\d{1,2}\s*/\s*\d{1,2}\s*/\s*\d{2,4}\s*$""".toRegex()
                )
                if (ignorePatterns.any { it.containsMatchIn(ln) }) continue
                val looksLikeHeader = """^\s*[^-•*].*—.*$""".toRegex().matches(ln) && !"""[₱0-9]""".toRegex().containsMatchIn(ln) && !"""[\[\]]""".toRegex().containsMatchIn(ln)
                if (looksLikeHeader) continue
                cleaned.add(ln)
            }

            val categoryRegex = """^\s*\[([^\]]+)]\s*$""".toRegex()
            val itemBulletRegex = """^\s*[-•*]\s*""".toRegex()

            // Updated to allow optional pipe or space for unit
            // Group 1: Price number
            // Group 2: Unit (optional)
            val priceLineRegex = "[:=\\-—>]?\\s*₱?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:(?:\\|)?\\s*(.+))?\\s*$".toRegex()

            // Updated inline regex
            // Group 1: Name
            // Group 2: Description (optional)
            // Group 3: Price
            // Group 4: Unit (optional, captures rest of line allowing space or pipe)
            val inlineItemRegex = Regex(
                pattern = """^\n?\s*[-•*]\s*([^(|\n]+?)(?:\s*\(([^)]*)\))?\s*(?:[—\-=>:]?\s*)?₱?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?:\s*(?:\|)?\s*(.+))?\s*$""",
                option = RegexOption.IGNORE_CASE
            )

            val parsed = mutableListOf<ParsedRow>()
            var currentCategory = ""

            for ((idx, rawLine) in cleaned.withIndex()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                val mCat = categoryRegex.find(line)
                if (mCat != null) {
                    currentCategory = mCat.groupValues[1].trim().uppercase()
                    continue
                }

                if (!itemBulletRegex.containsMatchIn(line)) {
                    val hasPriceNumber = "[0-9]+(?:\\.[0-9]{1,2})?".toRegex().containsMatchIn(line)
                    val looksLikeCategory = !hasPriceNumber && !"[:=\\-—>|]".toRegex().containsMatchIn(line)
                    if (looksLikeCategory) {
                        currentCategory = line.trim().uppercase()
                        continue
                    }
                }

                val inline = inlineItemRegex.find(line)
                if (inline != null) {
                    if (currentCategory.isEmpty()) {
                        warnings.add(Warn(idx + 1, "Line skipped — item has no category context."))
                        continue
                    }
                    val name = inline.groupValues[1].trim()
                    val desc = inline.groupValues[2].trim().ifEmpty { null }
                    val priceStr = inline.groupValues[3].trim()
                    val unitRaw = inline.groupValues.getOrNull(4)?.trim().orEmpty()

                    val priceClean = priceStr.replace(",", "")
                    if (!"^[0-9]+(\\.[0-9]{1,2})?$".toRegex().matches(priceClean)) {
                        warnings.add(Warn(idx + 1, "Line skipped — price is not a valid number."))
                        continue
                    }
                    val priceNum = priceClean.toDouble()
                    parsed.add(ParsedRow(idx + 1, currentCategory, name, desc, if (unitRaw.isEmpty()) "1pc" else unitRaw, priceNum))
                    continue
                }

                if (itemBulletRegex.containsMatchIn(line)) {
                    val stripped = line.replace(itemBulletRegex, "").trim()
                    val nameRegex = """^([^(|]+?)(?:\s*\(([^)]*)\))?\s*$""".toRegex()
                    val nm = nameRegex.find(stripped)
                    val name = (nm?.groupValues?.getOrNull(1) ?: stripped).trim()
                    val desc = nm?.groupValues?.getOrNull(2)?.trim().orEmpty()
                    val nextLine = cleaned.getOrNull(idx + 1)?.trim() ?: ""
                    val priceMatch = priceLineRegex.find(nextLine)
                    if (priceMatch != null) {
                        if (currentCategory.isEmpty()) {
                            warnings.add(Warn(idx + 1, "Line skipped — item has no category context."))
                            continue
                        }
                        val priceNum = priceMatch.groupValues[1].replace(",", "").toDouble()
                        val unitRaw = priceMatch.groupValues.getOrNull(2)?.trim().orEmpty()
                        parsed.add(ParsedRow(idx + 1, currentCategory, name, desc.ifEmpty { null }, if (unitRaw.isEmpty()) "1pc" else unitRaw, priceNum))
                        continue
                    }
                }

                val priceOnly = priceLineRegex.find(line)
                if (priceOnly != null && parsed.isNotEmpty()) {
                    warnings.add(Warn(idx + 1, "Line skipped — ambiguous price without item name."))
                    continue
                }

                warnings.add(Warn(idx + 1, "Line skipped — unrecognized format."))
            }

            // Deduplicate by category+name+description+unit (case-insensitive). Keep latest.
            val seen = mutableMapOf<String, Int>()
            val deduped = mutableListOf<ParsedRow>()
            for (p in parsed) {
                val key = "${p.category.uppercase()}::${p.name.uppercase()}::${(p.description ?: "").uppercase()}::${(p.unit ?: "").uppercase()}"
                val prev = seen[key]
                if (prev != null) {
                    val labelDesc = if (!p.description.isNullOrEmpty()) " (${p.description})" else ""
                    val labelUnit = if (!p.unit.isNullOrEmpty()) " / ${p.unit}" else ""
                    warnings.add(Warn(p.rowIndex, "Duplicate item — kept latest for \"${p.name}${labelDesc}${labelUnit}\"."))
                    deduped[prev] = p
                } else {
                    seen[key] = deduped.size
                    deduped.add(p)
                }
            }

            rows.clear(); rows.addAll(deduped)
            issues.clear() // errors are enforced during parse

            parsedCountText.text = "${rows.size} items parsed"
            warnCountText.text = "${warnings.size} warnings"
            issuesCountText.text = "${issues.size} issues"

            setStep(Step.VALIDATE)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Failed to parse text", Toast.LENGTH_LONG).show()
        }
    }

    private fun computeDryRun() {
        if (storeId.isEmpty()) {
            Toast.makeText(this, "Missing store context.", Toast.LENGTH_SHORT).show()
            return
        }
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @Serializable
                data class ProductRow(
                    val id: String,
                    val store_id: String,
                    val name: String,
                    val description: String? = null,
                    val price: Double = 0.0,
                    val units: String? = null,
                    val category: String? = null
                )

                val data = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject { put("p_store_id", storeId) }
                ).decodeList<ProductRow>()

                val bySignature = mutableMapOf<String, ProductRow>()
                for (e in data) {
                    val sig = "${e.name.trim().uppercase()}::${(e.description ?: "").trim().uppercase()}::${(e.units ?: "").trim().uppercase()}"
                    if (!bySignature.containsKey(sig)) bySignature[sig] = e
                }

                previewCreates.clear(); previewUpdates.clear()
                for (r in rows) {
                    val sig = "${r.name.trim().uppercase()}::${(r.description ?: "").trim().uppercase()}::${(r.unit ?: "").trim().uppercase()}"
                    val match = bySignature[sig]
                    if (match == null) {
                        previewCreates.add(
                            CreatePreview(
                                name = r.name,
                                category = r.category,
                                unit = r.unit,
                                price = r.price,
                                description = r.description
                            )
                        )
                    } else {
                        val willUpdate = ("${match.category ?: ""}" != "${r.category}") ||
                                ("${match.units ?: ""}" != "${r.unit ?: ""}") ||
                                (match.price != r.price) ||
                                ("${match.description ?: ""}" != "${r.description ?: ""}")
                        if (willUpdate) {
                            previewUpdates.add(
                                UpdatePreview(
                                    productId = match.id,
                                    name = r.name,
                                    prevCategory = match.category,
                                    prevUnit = match.units,
                                    prevPrice = match.price,
                                    prevDescription = match.description,
                                    nextCategory = r.category,
                                    nextUnit = r.unit,
                                    nextPrice = r.price,
                                    nextDescription = r.description
                                )
                            )
                        }
                    }
                }

                createsCountText.text = "${previewCreates.size} new items"
                updatesCountText.text = "${previewUpdates.size} updates"
                setStep(Step.REVIEW)
            } catch (e: Exception) {
                Toast.makeText(this@ImportPricesActivity, e.message ?: "Failed to compute dry-run", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun applyImport() {
        if (storeId.isEmpty()) return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            var created = 0
            var updated = 0
            try {
                // Load categories and cache
                @Serializable
                data class CatRow(val id: String, val name: String)
                val cats = SupabaseProvider.client.postgrest["categories"].select {
                    filter { eq("store_id", storeId) }
                }.decodeList<CatRow>()
                val catMap = mutableMapOf<String, String>() // NAME_UPPER -> id
                cats.forEach { catMap[it.name.uppercase()] = it.id }

                suspend fun ensureCategory(name: String?): String? {
                    val key = (name ?: "").trim().uppercase()
                    if (key.isEmpty()) return null
                    val cached = catMap[key]; if (cached != null) return cached
                    @Serializable
                    data class AddCatReturn(val category_id: String, val name: String)
                    val res = SupabaseProvider.client.postgrest.rpc(
                        "add_category",
                        buildJsonObject {
                            put("p_store_id", JsonPrimitive(storeId))
                            put("p_name", JsonPrimitive(key))
                        }
                    ).decodeList<AddCatReturn>()
                    val newId = res.firstOrNull()?.category_id
                    val normalized = res.firstOrNull()?.name ?: key
                    if (newId != null) {
                        catMap[normalized.uppercase()] = newId
                    }
                    return newId
                }

                // Creates
                for (c in previewCreates) {
                    val categoryId = ensureCategory(c.category)
                    val payload = buildJsonObject {
                        put("p_store_id", JsonPrimitive(storeId))
                        put("p_category_id", categoryId?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("p_name", JsonPrimitive(c.name))
                        put("p_description", c.description?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("p_price", JsonPrimitive(c.price))
                        put("p_unit", JsonPrimitive(c.unit ?: "1pc"))
                    }
                    SupabaseProvider.client.postgrest.rpc("add_product", payload)
                    created += 1
                }

                // Updates
                for (u in previewUpdates) {
                    val categoryId = if ((u.nextCategory ?: "") != (u.prevCategory ?: "")) {
                        ensureCategory(u.nextCategory)
                    } else {
                        val key = (u.nextCategory ?: "").trim().uppercase()
                        catMap[key]
                    }
                    val updatePayload = buildJsonObject {
                        put("name", JsonPrimitive(u.name))
                        put("description", u.nextDescription?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("price", JsonPrimitive(u.nextPrice))
                        put("unit", JsonPrimitive(u.nextUnit ?: "1pc"))
                        if (categoryId != null) put("category_id", JsonPrimitive(categoryId))
                    }
                    SupabaseProvider.client.postgrest["products"].update(updatePayload) {
                        filter { eq("store_id", storeId); eq("id", u.productId) }
                    }
                    updated += 1
                }

                doneSummaryText.text = "Imported successfully. Created: ${created}, Updated: ${updated}."
                setStep(Step.DONE)
            } catch (e: Exception) {
                Toast.makeText(this@ImportPricesActivity, e.message ?: "Import failed", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
}