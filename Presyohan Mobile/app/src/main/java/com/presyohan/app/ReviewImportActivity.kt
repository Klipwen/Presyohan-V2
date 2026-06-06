package com.presyohan.app

import android.app.Dialog
import android.content.Intent
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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewImportActivity : AppCompatActivity() {

    private lateinit var tvDuplicateAlert: TextView
    private lateinit var tvInvalidAlert: TextView
    private lateinit var btnEditItems: MaterialButton
    private lateinit var reviewRecyclerView: RecyclerView
    private lateinit var warningBanner: CardView
    private lateinit var tvWarningText: TextView
    private lateinit var tvNewItemsSummary: TextView
    private lateinit var tvUpdateItemsSummary: TextView
    private lateinit var tvGroupSummaryText: TextView
    private lateinit var btnConfirmImport: MaterialButton
    private lateinit var btnBack: ImageView
    private lateinit var loadingOverlay: View

    private var storeId: String? = null
    private var storeName: String? = null
    private var draftSessionId: String? = null
    private var session: DraftImportSession? = null

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
        tvDuplicateAlert = findViewById(R.id.tvDuplicateAlert)
        tvInvalidAlert = findViewById(R.id.tvInvalidAlert)
        btnEditItems = findViewById(R.id.btnEditItems)
        reviewRecyclerView = findViewById(R.id.reviewRecyclerView)
        warningBanner = findViewById(R.id.warningBanner)
        tvWarningText = findViewById(R.id.tvWarningText)
        tvNewItemsSummary = findViewById(R.id.tvNewItemsSummary)
        tvUpdateItemsSummary = findViewById(R.id.tvUpdateItemsSummary)
        tvGroupSummaryText = findViewById(R.id.tvGroupSummaryText)
        btnConfirmImport = findViewById(R.id.btnConfirmImport)
        btnBack = findViewById(R.id.btnBack)

        reviewRecyclerView.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { onBackPressed() }

        btnEditItems.setOnClickListener {
            // Route back to AddMultipleItemsActivity in Simple Mode with the current session
            val intent = Intent(this, AddMultipleItemsActivity::class.java).apply {
                putExtra("storeId", storeId)
                putExtra("storeName", storeName)
                putExtra("draftSessionId", draftSessionId)
            }
            startActivity(intent)
            finish()
        }

        btnConfirmImport.setOnClickListener {
            performConfirmImport()
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
                    setupRecyclerView(loadedSession)
                } else {
                    Toast.makeText(this@ReviewImportActivity, "Failed to load session details.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displaySessionSummary(session: DraftImportSession) {
        var total = 0
        var newCount = 0
        var updateCount = 0
        var invalidCount = 0
        var duplicateCount = 0

        session.categories.forEach { cat ->
            cat.items.forEach { item ->
                total++
                when (item.validationStatus) {
                    ValidationStatus.NEW -> newCount++
                    ValidationStatus.UPDATE -> updateCount++
                    ValidationStatus.INVALID -> invalidCount++
                    ValidationStatus.DUPLICATE -> duplicateCount++
                }
            }
        }

        tvDuplicateAlert.text = "Found $duplicateCount Duplicates !"
        tvInvalidAlert.text = "Found $invalidCount Invalid items !"
        tvNewItemsSummary.text = newCount.toString()
        tvUpdateItemsSummary.text = updateCount.toString()
        tvGroupSummaryText.text = "${session.categories.size} Categories and $total total items"

        if (invalidCount > 0) {
            warningBanner.visibility = View.VISIBLE
            tvWarningText.text = "$invalidCount invalid items require attention before saving."
            btnConfirmImport.isEnabled = false
            btnConfirmImport.alpha = 0.5f
        } else {
            warningBanner.visibility = View.GONE
            btnConfirmImport.isEnabled = true
            btnConfirmImport.alpha = 1.0f
        }
    }

    private fun setupRecyclerView(session: DraftImportSession) {
        val listItems = mutableListOf<ReviewListItem>()
        session.categories.forEach { cat ->
            if (cat.items.isNotEmpty()) {
                listItems.add(ReviewListItem.Header(cat.name))
                cat.items.forEach { item ->
                    listItems.add(ReviewListItem.Item(item))
                }
            }
        }
        reviewRecyclerView.adapter = ReviewImportAdapter(listItems)
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

    // --- RECYCLER LIST MODEL ---
    sealed class ReviewListItem {
        data class Header(val categoryName: String) : ReviewListItem()
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
                holder.tvCategoryHeader.text = listItem.categoryName
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
                    ValidationStatus.INVALID -> Color.parseColor("#C62828") // Red
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
                    holder.itemPrice.text = String.format("₱%.2f", priceVal)
                    holder.itemPrice.setTextColor(Color.parseColor("#219EBC"))
                } else {
                    holder.itemPrice.text = item.priceText.ifBlank { "₱0.00" }
                    holder.itemPrice.setTextColor(Color.parseColor("#C62828"))
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
