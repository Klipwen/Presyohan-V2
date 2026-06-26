package com.presyohan.app

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import android.util.TypedValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import android.widget.Button

class ReviewImportActivity : AppCompatActivity() {

    private lateinit var tvAlertTitle: TextView
    private lateinit var tvAlertSubtitle: TextView
    private lateinit var btnEditItems: MaterialButton
    private lateinit var reviewRecyclerView: RecyclerView
    private lateinit var tvNewCategoriesSummary: TextView
    private lateinit var tvNewItemsSummary: TextView
    private lateinit var tvUpdateItemsSummary: TextView
    private lateinit var tvGroupSummaryText: TextView
    private lateinit var btnConfirmImport: AppCompatButton
    private lateinit var btnBack: ImageView
    private lateinit var loadingOverlay: View

    private lateinit var btnFloatingErrors: View
    private lateinit var tvFloatingErrorCount: TextView
    private lateinit var cardFloatingErrorCircle: CardView

    private var storeId: String? = null
    private var storeName: String? = null
    private var draftSessionId: String? = null
    private var session: DraftImportSession? = null

    private lateinit var layoutNewCategoryClick: View
    private lateinit var layoutNewItemsClick: View
    private lateinit var layoutUpdateItemsClick: View

    enum class ReviewFilterMode {
        ALL,
        NEW_CATEGORIES,
        NEW_ITEMS,
        UPDATE_ITEMS,
        INVALID_ONLY
    }

    private var currentFilterMode = ReviewFilterMode.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_import)

        loadingOverlay = LoadingOverlayHelper.attach(this)
        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        draftSessionId = intent.getStringExtra("draftSessionId")

        initViews()
        loadSessionData()
    }

    private fun initViews() {
        tvAlertTitle = findViewById(R.id.tvAlertTitle)
        tvAlertSubtitle = findViewById(R.id.tvAlertSubtitle)
        btnEditItems = findViewById<MaterialButton>(R.id.btnEditItems)
        reviewRecyclerView = findViewById(R.id.reviewRecyclerView)
        tvNewCategoriesSummary = findViewById(R.id.tvNewCategoriesSummary)
        tvNewItemsSummary = findViewById(R.id.tvNewItemsSummary)
        tvUpdateItemsSummary = findViewById(R.id.tvUpdateItemsSummary)
        tvGroupSummaryText = findViewById(R.id.tvGroupSummaryText)
        btnConfirmImport = findViewById<AppCompatButton>(R.id.btnConfirmImport)
        btnBack = findViewById(R.id.btnBack)
        btnFloatingErrors = findViewById(R.id.btnFloatingErrors)
        tvFloatingErrorCount = findViewById(R.id.tvFloatingErrorCount)
        cardFloatingErrorCircle = findViewById(R.id.cardFloatingErrorCircle)

        layoutNewCategoryClick = findViewById(R.id.layoutNewCategoryClick)
        layoutNewItemsClick = findViewById(R.id.layoutNewItemsClick)
        layoutUpdateItemsClick = findViewById(R.id.layoutUpdateItemsClick)

        reviewRecyclerView.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { onBackPressed() }

        val isCopyPrices = intent.getBooleanExtra("isCopyPrices", false)
        if (isCopyPrices) {
            btnEditItems.visibility = View.GONE
            btnConfirmImport.text = "Confirm & Copy"
        } else {
            btnEditItems.visibility = View.VISIBLE
            btnConfirmImport.text = "DONE"
            btnEditItems.setOnClickListener {
                // Route back to AddMultipleItemsActivity in Simple Mode with the current session
                val intent = Intent(this, AddMultipleItemsActivity::class.java).apply {
                    putExtra("storeId", storeId)
                    putExtra("storeName", storeName)
                    putExtra("draftSessionId", draftSessionId)
                    putExtra("isFromReview", true)
                }
                startActivity(intent)
                finish()
            }
        }

        btnConfirmImport.setOnClickListener {
            performConfirmImport()
        }

        layoutNewCategoryClick.setOnClickListener {
            val s = session ?: return@setOnClickListener
            currentFilterMode = if (currentFilterMode == ReviewFilterMode.NEW_CATEGORIES) {
                ReviewFilterMode.ALL
            } else {
                ReviewFilterMode.NEW_CATEGORIES
            }
            applyFilterAndVisualStates(s)
        }

        layoutNewItemsClick.setOnClickListener {
            val s = session ?: return@setOnClickListener
            currentFilterMode = if (currentFilterMode == ReviewFilterMode.NEW_ITEMS) {
                ReviewFilterMode.ALL
            } else {
                ReviewFilterMode.NEW_ITEMS
            }
            applyFilterAndVisualStates(s)
        }

        layoutUpdateItemsClick.setOnClickListener {
            val s = session ?: return@setOnClickListener
            currentFilterMode = if (currentFilterMode == ReviewFilterMode.UPDATE_ITEMS) {
                ReviewFilterMode.ALL
            } else {
                ReviewFilterMode.UPDATE_ITEMS
            }
            applyFilterAndVisualStates(s)
        }

        btnFloatingErrors.setOnClickListener {
            val s = session ?: return@setOnClickListener
            currentFilterMode = if (currentFilterMode == ReviewFilterMode.INVALID_ONLY) {
                ReviewFilterMode.ALL
            } else {
                ReviewFilterMode.INVALID_ONLY
            }
            applyFilterAndVisualStates(s)
        }
    }

    private fun loadSessionData() {
        val sId = draftSessionId ?: return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            val loadedSession = withContext(Dispatchers.IO) {
                ImportDraftStore(application).loadSession(sId)
            }
            session = loadedSession
            withContext(Dispatchers.Main) {
                LoadingOverlayHelper.hide(loadingOverlay)
                if (loadedSession != null) {
                    displaySessionSummary(loadedSession)
                    applyFilterAndVisualStates(loadedSession)
                } else {
                    Toast.makeText(this@ReviewImportActivity, "Failed to load session details.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displaySessionSummary(session: DraftImportSession) {
        val summary = ImportValidationUseCase().produceSummary(session)

        tvNewCategoriesSummary.text = summary.newCategoriesCount.toString()
        tvNewItemsSummary.text = summary.newItemsCount.toString()
        tvUpdateItemsSummary.text = summary.updateItemsCount.toString()
        tvGroupSummaryText.text = "There are ${summary.totalCategories} Categories and ${summary.totalItems} total items"

        // Warning state
        if (summary.invalidItemsCount > 0 || summary.duplicateItemsCount > 0) {
            tvAlertTitle.text = "Found ${summary.duplicateItemsCount} Duplicates and ${summary.invalidItemsCount} invalid items!"
            tvAlertTitle.setTextColor(Color.parseColor("#FB8500"))

            val subtitleText = when {
                summary.invalidItemsCount > 0 && summary.duplicateItemsCount > 0 ->
                    "Invalid items require attention. Duplicate items are skipped."
                summary.invalidItemsCount > 0 ->
                    "Invalid items require attention before saving."
                else ->
                    "Duplicate items are skipped. Ready to save."
            }
            tvAlertSubtitle.text = subtitleText
            tvAlertSubtitle.setTextColor(Color.parseColor("#FB8500"))

            // Show floating error badge
            if (summary.invalidItemsCount > 0) {
                btnFloatingErrors.visibility = View.VISIBLE
                tvFloatingErrorCount.text = summary.invalidItemsCount.toString()

                // Disable confirm
                btnConfirmImport.isEnabled = false
                btnConfirmImport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFCC80"))
            } else {
                btnFloatingErrors.visibility = View.GONE

                // Enable confirm (only duplicates present, duplicates are skipped)
                btnConfirmImport.isEnabled = true
                btnConfirmImport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FB8500")) // Orange
            }
        } else {
            // Success state
            tvAlertTitle.text = "No duplicates or invalid items found!"
            tvAlertTitle.setTextColor(Color.parseColor("#757575"))

            tvAlertSubtitle.text = "Ready to save."
            tvAlertSubtitle.setTextColor(Color.parseColor("#757575"))

            // Hide floating error badge
            btnFloatingErrors.visibility = View.GONE

            // Enable confirm
            btnConfirmImport.isEnabled = true
            btnConfirmImport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FB8500")) // Orange
        }
    }

    private fun setupRecyclerView(session: DraftImportSession) {
        val listItems = mutableListOf<ReviewListItem>()
        session.categories.forEach { cat ->
            val isNewCategory = cat.categoryId == null

            val filteredItems = cat.items.filter { item ->
                when (currentFilterMode) {
                    ReviewFilterMode.ALL -> true
                    ReviewFilterMode.INVALID_ONLY -> item.validationStatus == ValidationStatus.INVALID
                    ReviewFilterMode.NEW_ITEMS -> item.validationStatus == ValidationStatus.NEW
                    ReviewFilterMode.UPDATE_ITEMS -> item.validationStatus == ValidationStatus.UPDATE
                    ReviewFilterMode.NEW_CATEGORIES -> isNewCategory
                }
            }

            if (filteredItems.isNotEmpty()) {
                listItems.add(ReviewListItem.Header(cat.name, isNewCategory))
                filteredItems.forEach { item ->
                    listItems.add(ReviewListItem.Item(item))
                }
            }
        }
        reviewRecyclerView.adapter = ReviewImportAdapter(listItems)
    }

    private fun applyFilterAndVisualStates(s: DraftImportSession) {
        // Ensure all layouts keep their standard touch ripple background
        layoutNewCategoryClick.setBackgroundResource(getSelectableItemBackgroundResourceId())
        layoutNewItemsClick.setBackgroundResource(getSelectableItemBackgroundResourceId())
        layoutUpdateItemsClick.setBackgroundResource(getSelectableItemBackgroundResourceId())
        cardFloatingErrorCircle.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FB8500")))

        // Adjust opacities (alpha) based on active filter mode for premium minimalism
        when (currentFilterMode) {
            ReviewFilterMode.ALL -> {
                layoutNewCategoryClick.alpha = 1.0f
                layoutNewItemsClick.alpha = 1.0f
                layoutUpdateItemsClick.alpha = 1.0f
            }
            ReviewFilterMode.NEW_CATEGORIES -> {
                layoutNewCategoryClick.alpha = 1.0f
                layoutNewItemsClick.alpha = 0.4f
                layoutUpdateItemsClick.alpha = 0.4f
            }
            ReviewFilterMode.NEW_ITEMS -> {
                layoutNewCategoryClick.alpha = 0.4f
                layoutNewItemsClick.alpha = 1.0f
                layoutUpdateItemsClick.alpha = 0.4f
            }
            ReviewFilterMode.UPDATE_ITEMS -> {
                layoutNewCategoryClick.alpha = 0.4f
                layoutNewItemsClick.alpha = 0.4f
                layoutUpdateItemsClick.alpha = 1.0f
            }
            ReviewFilterMode.INVALID_ONLY -> {
                cardFloatingErrorCircle.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E65100"))) // Dark orange when active
                layoutNewCategoryClick.alpha = 0.4f
                layoutNewItemsClick.alpha = 0.4f
                layoutUpdateItemsClick.alpha = 0.4f
            }
        }

        setupRecyclerView(s)
    }

    private fun getSelectableItemBackgroundResourceId(): Int {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return outValue.resourceId
    }

    private fun performConfirmImport() {
        val currentSession = session ?: return
        var invalidCount = 0
        currentSession.categories.forEach { cat ->
            invalidCount += cat.items.count { it.validationStatus == ValidationStatus.INVALID }
        }

        if (invalidCount > 0) {
            Toast.makeText(this, "Cannot import. Please resolve the $invalidCount invalid items first.", Toast.LENGTH_LONG).show()
            return
        }

        val isCopyPrices = intent.getBooleanExtra("isCopyPrices", false)
        if (isCopyPrices) {
            val destPasteCode = intent.getStringExtra("destPasteCode") ?: ""
            val srcStoreId = intent.getStringExtra("sourceStoreId") ?: ""
            val selectedIds = intent.getStringArrayListExtra("selectedProductIds") ?: emptyList()

            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        SupabaseProvider.client.postgrest.rpc(
                            "copy_prices",
                            buildJsonObject {
                                put("p_source_store_id", srcStoreId)
                                put("p_dest_paste_code", destPasteCode)
                                put("p_items", buildJsonArray {
                                    selectedIds.forEach { add(JsonPrimitive(it) as kotlinx.serialization.json.JsonElement) }
                                })
                                put("p_dry_run", false)
                            }
                        )
                    }

                    // Delete the draft session
                    withContext(Dispatchers.IO) {
                        ImportDraftStore(application).deleteSession(currentSession.sessionId)
                    }

                    withContext(Dispatchers.Main) {
                        LoadingOverlayHelper.hide(loadingOverlay)
                        showExportCompleteDialog()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        LoadingOverlayHelper.hide(loadingOverlay)
                        Toast.makeText(this@ReviewImportActivity, "Copy failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val repo = SupabaseImportRepository()
                val manager = ImportManager(repo)
                val categoryMap = mutableMapOf<String, String>()

                val result = withContext(Dispatchers.IO) {
                    manager.performDraftImport(currentSession, categoryMap)
                }

                // Delete session from store on success
                withContext(Dispatchers.IO) {
                    ImportDraftStore(application).deleteSession(currentSession.sessionId)
                }

                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    Toast.makeText(
                        this@ReviewImportActivity,
                        "Successfully imported ${result.savedCount} items!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Return to store page/dashboard
                    val intent = Intent(this@ReviewImportActivity, HomeActivity::class.java).apply {
                        putExtra("storeId", storeId)
                        putExtra("storeName", storeName)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    Toast.makeText(this@ReviewImportActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showExportCompleteDialog() {
        CopyPricesDialogHelper.showCopyCompleteDialog(this) {
            finish()
        }
    }

    // --- RECYCLER LIST MODEL ---
    sealed class ReviewListItem {
        data class Header(val categoryName: String, val isNewCategory: Boolean = false) : ReviewListItem()
        data class Item(val item: DraftItem) : ReviewListItem()
    }

    // --- ADAPTER ---
    class ReviewImportAdapter(private val items: List<ReviewListItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_ITEM = 1
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is ReviewListItem.Header -> TYPE_HEADER
                is ReviewListItem.Item -> TYPE_ITEM
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val v = inflater.inflate(R.layout.item_review_category_header, parent, false)
                HeaderViewHolder(v)
            } else {
                val v = inflater.inflate(R.layout.item_review_import_row, parent, false)
                ItemViewHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val listItem = items[position]
            if (holder is HeaderViewHolder && listItem is ReviewListItem.Header) {
                if (listItem.isNewCategory) {
                    val builder = SpannableStringBuilder(listItem.categoryName)
                    builder.append("  ") // padding space
                    val startPos = builder.length
                    builder.append("new")
                    val endPos = builder.length

                    val density = holder.itemView.resources.displayMetrics.density
                    builder.setSpan(
                        RoundedBackgroundSpan(
                            backgroundColor = Color.parseColor("#FB8500"),
                            textColor = Color.WHITE,
                            cornerRadius = 4f * density,
                            paddingHorizontal = 6f * density,
                            paddingVertical = 2f * density
                        ),
                        startPos,
                        endPos,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    holder.tvCategoryHeader.text = builder
                } else {
                    holder.tvCategoryHeader.text = listItem.categoryName
                }
            } else if (holder is ItemViewHolder && listItem is ReviewListItem.Item) {
                val item = listItem.item

                // Render name with inline custom badge
                val builder = SpannableStringBuilder(item.productName)
                val badgeText = when (item.validationStatus) {
                    ValidationStatus.NEW -> "new"
                    ValidationStatus.UPDATE -> "update"
                    ValidationStatus.INVALID -> "invalid"
                    ValidationStatus.DUPLICATE -> "duplicate"
                }

                val badgeBgColor = when (item.validationStatus) {
                    ValidationStatus.NEW -> Color.parseColor("#FB8500") // Orange
                    ValidationStatus.UPDATE -> Color.parseColor("#219EBC") // Teal/Blue
                    ValidationStatus.INVALID -> Color.parseColor("#E65100") // Dark Orange (avoid red)
                    ValidationStatus.DUPLICATE -> Color.parseColor("#757575") // Grey
                }

                builder.append("  ") // padding space
                val startPos = builder.length
                builder.append(badgeText)
                val endPos = builder.length

                val density = holder.itemView.resources.displayMetrics.density
                builder.setSpan(
                    RoundedBackgroundSpan(
                        backgroundColor = badgeBgColor,
                        textColor = Color.WHITE,
                        cornerRadius = 4f * density,
                        paddingHorizontal = 6f * density,
                        paddingVertical = 2f * density
                    ),
                    startPos,
                    endPos,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                holder.itemName.text = builder

                // Details Text (descriptions)
                val descText = item.description
                if (!descText.isNullOrBlank()) {
                    holder.itemDetails.text = descText
                    holder.itemDetails.visibility = View.VISIBLE
                } else {
                    holder.itemDetails.text = ""
                    holder.itemDetails.visibility = View.GONE
                }

                // Price display
                val priceVal = item.price
                if (priceVal != null) {
                    holder.itemPrice.text = "₱%,.2f".format(java.util.Locale.US, priceVal)
                    holder.itemPrice.setTextColor(Color.parseColor("#219EBC"))
                } else {
                    val rawPriceText = item.priceText.trim()
                    val numericOnly = rawPriceText.replace("₱", "").replace(",", "").trim()
                    val parsed = numericOnly.toDoubleOrNull()
                    if (parsed != null) {
                        holder.itemPrice.text = "₱%,.2f".format(java.util.Locale.US, parsed)
                    } else {
                        holder.itemPrice.text = if (rawPriceText.isBlank()) "₱0.00" else rawPriceText
                    }
                    holder.itemPrice.setTextColor(Color.parseColor("#FB8500"))
                }

                // Unit display
                holder.itemUnit.text = item.unit.ifBlank { "1pc" }

                // Error text inline
                if (item.validationErrors.isNotEmpty()) {
                    holder.tvErrorText.visibility = View.VISIBLE
                    val errs = item.validationErrors.map {
                        when (it) {
                            ValidationError.EMPTY_PRODUCT_NAME -> "Product name is required"
                            ValidationError.INVALID_PRICE -> "Valid price is required"
                            ValidationError.NEGATIVE_PRICE -> "Price cannot be negative"
                            ValidationError.MISSING_CATEGORY -> "Category is required"
                            ValidationError.DUPLICATE_IN_IMPORT -> "Duplicate item in list"
                            ValidationError.DUPLICATE_IN_DATABASE_AMBIGUOUS -> "Ambiguous duplicates in store"
                            ValidationError.INVALID_FORMAT -> "Invalid format"
                            ValidationError.MISSING_REQUIRED_FIELD -> "Required field is missing"
                            else -> "Unknown error"
                        }
                    }
                    holder.tvErrorText.text = errs.joinToString(", ")
                } else {
                    holder.tvErrorText.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvCategoryHeader: TextView = v.findViewById(R.id.tvCategoryHeader)
        }

        class ItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val itemName: TextView = v.findViewById(R.id.itemName)
            val itemDetails: TextView = v.findViewById(R.id.itemDetails)
            val itemPrice: TextView = v.findViewById(R.id.itemPrice)
            val itemUnit: TextView = v.findViewById(R.id.itemUnit)
            val tvErrorText: TextView = v.findViewById(R.id.tvErrorText)
        }
    }
}

// Custom inline badge drawing span
class RoundedBackgroundSpan(
    private val backgroundColor: Int,
    private val textColor: Int,
    private val cornerRadius: Float,
    private val paddingHorizontal: Float,
    private val paddingVertical: Float
) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val originalTextSize = paint.textSize
        paint.textSize = originalTextSize * 0.8f // Slightly smaller font for badge
        val width = (paint.measureText(text, start, end) + paddingHorizontal * 2).toInt()
        paint.textSize = originalTextSize
        return width
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val originalColor = paint.color
        val originalTextSize = paint.textSize
        
        paint.textSize = originalTextSize * 0.8f // Slightly smaller font for badge
        
        val width = paint.measureText(text, start, end)
        val fontMetrics = paint.fontMetrics
        
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val rectTop = y + fontMetrics.ascent - paddingVertical
        val rectBottom = y + fontMetrics.descent + paddingVertical
        
        // Draw background
        paint.color = backgroundColor
        val rect = RectF(x, rectTop, x + width + paddingHorizontal * 2, rectBottom)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        // Draw text
        paint.color = textColor
        canvas.drawText(text!!, start, end, x + paddingHorizontal, y.toFloat(), paint)
        
        // Restore paint
        paint.color = originalColor
        paint.textSize = originalTextSize
    }
}
