package com.presyohan.app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup

class CopyPricesActivity : AppCompatActivity() {
    private lateinit var btnBack: ImageView
    private lateinit var headerLabel: TextView
    private lateinit var stepIndicator1: TextView
    private lateinit var stepIndicator2: TextView
    private lateinit var stepIndicator3: TextView
    private lateinit var stepLabel1: TextView
    private lateinit var stepLabel2: TextView
    private lateinit var stepLabel3: TextView
    private lateinit var btnNext: Button
    private lateinit var btnBackStep: Button
    private lateinit var btnConfirm: Button
    
    // Step 1: Select Items
    private lateinit var searchEditText: EditText
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var productsRecyclerView: RecyclerView
    private lateinit var selectedCountText: TextView
    
    // Step 2: Enter Code
    private lateinit var codeEditText: EditText
    private lateinit var codeValidationText: TextView
    
    // Step 3: Review
    private lateinit var reviewRecyclerView: RecyclerView
    private lateinit var reviewSummaryText: TextView
    
    private var currentStep = 1 // 1: select, 2: code, 3: review
    private var sourceStoreId: String? = null
    private var sourceStoreName: String? = null
    private var sourceProducts = mutableListOf<SourceProduct>()
    private var selectedProductIds = mutableSetOf<String>()
    private var pasteCode: String = ""
    private var validDestination: DestinationStore? = null
    private var previewRows = mutableListOf<PreviewRow>()
    
    @Serializable
    data class SourceProduct(
        val product_id: String,
        val name: String? = null,
        val category: String? = null,
        val price: Double? = null,
        val units: String? = null,
        val unit: String? = null
    )
    
    @Serializable
    data class DestinationStore(
        val store_id: String,
        val store_name: String
    )
    
    @Serializable
    data class PreviewRow(
        val product_id: String,
        val name: String? = null,
        val source_price: Double? = null,
        val dest_price: Double? = null,
        val action: String? = null // "create" or "update"
    )
    
    @Serializable
    data class ValidateCodeResult(
        val store_id: String,
        val store_name: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_copy_prices)

        sourceStoreId = intent.getStringExtra("storeId")
        sourceStoreName = intent.getStringExtra("storeName")
        
        if (sourceStoreId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initViews()
        setupClickListeners()
        loadSourceProducts()
        showStep(1)
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, sourceStoreId, sourceStoreName)
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        headerLabel = findViewById(R.id.headerLabel)
        stepIndicator1 = findViewById(R.id.stepIndicator1)
        stepIndicator2 = findViewById(R.id.stepIndicator2)
        stepIndicator3 = findViewById(R.id.stepIndicator3)
        stepLabel1 = findViewById(R.id.stepLabel1)
        stepLabel2 = findViewById(R.id.stepLabel2)
        stepLabel3 = findViewById(R.id.stepLabel3)
        btnNext = findViewById(R.id.btnNext)
        btnBackStep = findViewById(R.id.btnBackStep)
        btnConfirm = findViewById(R.id.btnConfirm)
        
        searchEditText = findViewById(R.id.searchEditText)
        selectAllCheckbox = findViewById(R.id.selectAllCheckbox)
        productsRecyclerView = findViewById(R.id.productsRecyclerView)
        selectedCountText = findViewById(R.id.selectedCountText)
        
        codeEditText = findViewById(R.id.codeEditText)
        codeValidationText = findViewById(R.id.codeValidationText)
        
        reviewRecyclerView = findViewById(R.id.reviewRecyclerView)
        reviewSummaryText = findViewById(R.id.reviewSummaryText)
        
        headerLabel.text = "Copy Prices"
        
        productsRecyclerView.layoutManager = LinearLayoutManager(this)
        reviewRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        
        btnNext.setOnClickListener {
            when (currentStep) {
                1 -> {
                    if (selectedProductIds.isEmpty()) {
                        Toast.makeText(this, "Select at least one item.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    showStep(2)
                }
                2 -> {
                    if (pasteCode.length != 6 || validDestination == null) {
                        Toast.makeText(this, "Enter a valid 6-digit paste-code.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    runDryRun()
                }
            }
        }
        
        btnBackStep.setOnClickListener {
            when (currentStep) {
                2 -> showStep(1)
                3 -> showStep(2)
            }
        }
        
        btnConfirm.setOnClickListener {
            applyCopy()
        }
        
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedProductIds.clear()
                selectedProductIds.addAll(sourceProducts.map { it.product_id })
            } else {
                selectedProductIds.clear()
            }
            updateProductAdapter()
            updateSelectedCount()
        }
        
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateProductAdapter()
            }
        })
        
        codeEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.replace(Regex("[^0-9]"), "")?.take(6) ?: ""
                if (text != codeEditText.text.toString()) {
                    codeEditText.setText(text)
                    codeEditText.setSelection(text.length)
                }
                pasteCode = text
                if (text.length == 6) {
                    validatePasteCode(text)
                } else {
                    validDestination = null
                    codeValidationText.text = ""
                    codeValidationText.visibility = View.GONE
                }
            }
        })
    }

    private fun showStep(step: Int) {
        currentStep = step
        updateStepIndicators()
        
        // Hide all step content
        findViewById<View>(R.id.step1Content).visibility = View.GONE
        findViewById<View>(R.id.step2Content).visibility = View.GONE
        findViewById<View>(R.id.step3Content).visibility = View.GONE
        
        // Show current step content
        when (step) {
            1 -> {
                findViewById<View>(R.id.step1Content).visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
                btnNext.text = "Next"
                btnBackStep.visibility = View.GONE
                btnConfirm.visibility = View.GONE
            }
            2 -> {
                findViewById<View>(R.id.step2Content).visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
                btnNext.text = "Next"
                btnBackStep.visibility = View.VISIBLE
                btnConfirm.visibility = View.GONE
            }
            3 -> {
                findViewById<View>(R.id.step3Content).visibility = View.VISIBLE
                btnNext.visibility = View.GONE
                btnBackStep.visibility = View.VISIBLE
                btnConfirm.visibility = View.VISIBLE
            }
        }
    }

    private fun updateStepIndicators() {
        val activeColor = getColor(R.color.presyo_orange)
        val inactiveColor = getColor(R.color.presyo_teal)
        val labelActiveColor = getColor(R.color.presyo_orange)
        val labelInactiveColor = getColor(R.color.presyo_teal)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            stepIndicator1.backgroundTintList = android.content.res.ColorStateList.valueOf(if (currentStep >= 1) activeColor else inactiveColor)
            stepIndicator2.backgroundTintList = android.content.res.ColorStateList.valueOf(if (currentStep >= 2) activeColor else inactiveColor)
            stepIndicator3.backgroundTintList = android.content.res.ColorStateList.valueOf(if (currentStep >= 3) activeColor else inactiveColor)
        } else {
            stepIndicator1.setBackgroundColor(if (currentStep >= 1) activeColor else inactiveColor)
            stepIndicator2.setBackgroundColor(if (currentStep >= 2) activeColor else inactiveColor)
            stepIndicator3.setBackgroundColor(if (currentStep >= 3) activeColor else inactiveColor)
        }

        stepLabel1.setTextColor(if (currentStep >= 1) labelActiveColor else labelInactiveColor)
        stepLabel2.setTextColor(if (currentStep >= 2) labelActiveColor else labelInactiveColor)
        stepLabel3.setTextColor(if (currentStep >= 3) labelActiveColor else labelInactiveColor)
    }

    private fun loadSourceProducts() {
        lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject {
                        put("p_store_id", sourceStoreId!!)
                    }
                ).decodeList<SourceProduct>()
                
                sourceProducts.clear()
                sourceProducts.addAll(rows)
                updateProductAdapter()
                updateSelectedCount()
            } catch (e: Exception) {
                Toast.makeText(this@CopyPricesActivity, "Failed to load products: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateProductAdapter() {
        val searchQuery = searchEditText.text.toString().lowercase()
        val filtered = if (searchQuery.isBlank()) {
            sourceProducts
        } else {
            sourceProducts.filter { 
                (it.name ?: "").lowercase().contains(searchQuery)
            }
        }
        
        val grouped = filtered.groupBy { it.category ?: "Uncategorized" }
        productsRecyclerView.adapter = ProductSelectionAdapter(
            grouped,
            selectedProductIds,
            onProductToggle = { productId ->
                if (selectedProductIds.contains(productId)) {
                    selectedProductIds.remove(productId)
                } else {
                    selectedProductIds.add(productId)
                }
                updateProductAdapter()
                updateSelectedCount()
                updateSelectAllCheckbox()
            },
            onCategoryToggle = { category, shouldSelect ->
                val products = grouped[category].orEmpty()
                if (shouldSelect) {
                    products.forEach { selectedProductIds.add(it.product_id) }
                } else {
                    products.forEach { selectedProductIds.remove(it.product_id) }
                }
                updateProductAdapter()
                updateSelectedCount()
                updateSelectAllCheckbox()
            }
        )
    }

    private fun updateSelectedCount() {
        selectedCountText.text = "Selected: ${selectedProductIds.size} items"
    }

    private fun updateSelectAllCheckbox() {
        selectAllCheckbox.setOnCheckedChangeListener(null)
        selectAllCheckbox.isChecked = selectedProductIds.size == sourceProducts.size && sourceProducts.isNotEmpty()
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedProductIds.clear()
                selectedProductIds.addAll(sourceProducts.map { it.product_id })
            } else {
                selectedProductIds.clear()
            }
            updateProductAdapter()
            updateSelectedCount()
        }
    }

    private fun validatePasteCode(code: String) {
        lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "validate_paste_code",
                    buildJsonObject {
                        put("p_code", code)
                    }
                ).decodeList<ValidateCodeResult>()
                
                if (rows.isNotEmpty()) {
                    val row = rows[0]
                    validDestination = DestinationStore(row.store_id, row.store_name)
                    codeValidationText.text = "Destination: ${row.store_name} (verified)"
                    codeValidationText.setTextColor(getColor(R.color.presyo_teal))
                    codeValidationText.visibility = View.VISIBLE
                } else {
                    validDestination = null
                    codeValidationText.text = "Invalid or expired code"
                    codeValidationText.setTextColor(getColor(android.R.color.holo_red_dark))
                    codeValidationText.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                validDestination = null
                codeValidationText.text = "Failed to validate code"
                codeValidationText.setTextColor(getColor(android.R.color.holo_red_dark))
                codeValidationText.visibility = View.VISIBLE
            }
        }
    }

    private fun runDryRun() {
        if (validDestination == null || selectedProductIds.isEmpty()) return
        
        lifecycleScope.launch {
            try {
                btnNext.isEnabled = false
                btnNext.text = "Loading..."
                
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "copy_prices",
                    buildJsonObject {
                        put("p_source_store_id", sourceStoreId!!)
                        put("p_dest_paste_code", pasteCode)
                        put("p_items", buildJsonArray {
                            selectedProductIds.forEach { add(JsonPrimitive(it)) }
                        })
                        put("p_dry_run", true)
                    }
                ).decodeList<PreviewRow>()
                
                previewRows.clear()
                previewRows.addAll(rows)
                
                updateReviewView()
                showStep(3)
            } catch (e: Exception) {
                Toast.makeText(this@CopyPricesActivity, "Failed to preview: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnNext.isEnabled = true
                btnNext.text = "Next"
            }
        }
    }

    private fun updateReviewView() {
        val created = previewRows.count { it.action == "create" }
        val updated = previewRows.count { it.action == "update" }
        reviewSummaryText.text = "To create: $created · To update: $updated · Total: ${previewRows.size}"
        
        reviewRecyclerView.adapter = ReviewAdapter(previewRows)
    }

    private fun applyCopy() {
        if (validDestination == null || selectedProductIds.isEmpty()) return
        
        lifecycleScope.launch {
            try {
                btnConfirm.isEnabled = false
                btnConfirm.text = "Copying..."
                
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "copy_prices",
                    buildJsonObject {
                        put("p_source_store_id", sourceStoreId!!)
                        put("p_dest_paste_code", pasteCode)
                        put("p_items", buildJsonArray {
                            selectedProductIds.forEach { add(JsonPrimitive(it)) }
                        })
                        put("p_dry_run", false)
                    }
                ).decodeList<PreviewRow>()
                
                val created = rows.count { it.action == "create" }
                val updated = rows.count { it.action == "update" }
                
                Toast.makeText(this@CopyPricesActivity, "Copied: $updated updated, $created created", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@CopyPricesActivity, "Copy failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnConfirm.isEnabled = true
                btnConfirm.text = "Confirm & Copy"
            }
        }
    }

    // Sealed class for adapter items (must be outside inner class)
    private sealed class AdapterItem {
        data class Header(val category: String, val count: Int) : AdapterItem()
        data class Product(val product: SourceProduct) : AdapterItem()
    }
    
    // Adapter for product selection with checkboxes
    inner class ProductSelectionAdapter(
        private val groupedProducts: Map<String, List<SourceProduct>>,
        private val selectedIds: Set<String>,
        private val onProductToggle: (String) -> Unit,
        private val onCategoryToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        private val items = mutableListOf<AdapterItem>()
        
        init {
            groupedProducts.forEach { (category, products) ->
                items.add(AdapterItem.Header(category, products.size))
                products.forEach { items.add(AdapterItem.Product(it)) }
            }
        }
        
        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val categoryText: TextView = view.findViewById(R.id.categoryText)
            val countText: TextView = view.findViewById(R.id.countText)
            val selectCheckbox: CheckBox = view.findViewById(R.id.categorySelectCheckbox)
        }
        
        inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox: CheckBox = view.findViewById(R.id.productCheckbox)
            val nameText: TextView = view.findViewById(R.id.productNameText)
            val detailsText: TextView = view.findViewById(R.id.productDetailsText)
            val priceText: TextView = view.findViewById(R.id.productPriceText)
        }
        
        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is AdapterItem.Header -> 0
                is AdapterItem.Product -> 1
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_copy_prices_category, parent, false)
                    HeaderViewHolder(view)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_copy_prices_product, parent, false)
                    ProductViewHolder(view)
                }
            }
        }
        
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is AdapterItem.Header -> {
                    val h = holder as HeaderViewHolder
                    h.categoryText.text = item.category
                    h.countText.text = "(${item.count})"
                    val products = groupedProducts[item.category].orEmpty()
                    val allSelected = products.isNotEmpty() && products.all { selectedIds.contains(it.product_id) }
                    h.selectCheckbox.setOnCheckedChangeListener(null)
                    h.selectCheckbox.isChecked = allSelected
                    h.selectCheckbox.isEnabled = products.isNotEmpty()
                    h.selectCheckbox.alpha = if (products.isNotEmpty()) 1f else 0.4f
                    h.selectCheckbox.setOnCheckedChangeListener { _, isChecked ->
                        onCategoryToggle(item.category, isChecked)
                    }
                }
                is AdapterItem.Product -> {
                    val h = holder as ProductViewHolder
                    val product = item.product
                    h.nameText.text = product.name ?: ""
                    h.detailsText.text = "${product.category ?: ""} · ${product.units ?: product.unit ?: ""}"
                    h.priceText.text = "₱${String.format("%.2f", product.price ?: 0.0)}"
                    // Remove listener before setting checked state to avoid triggering it
                    h.checkbox.setOnCheckedChangeListener(null)
                    h.checkbox.isChecked = selectedIds.contains(product.product_id)
                    h.checkbox.setOnCheckedChangeListener { _, _ ->
                        onProductToggle(product.product_id)
                    }
                }
            }
        }
        
        override fun getItemCount(): Int = items.size
    }

    // Adapter for review list
    inner class ReviewAdapter(private val rows: List<PreviewRow>) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {
        inner class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.reviewNameText)
            val sourcePriceText: TextView = view.findViewById(R.id.reviewSourcePriceText)
            val destPriceText: TextView = view.findViewById(R.id.reviewDestPriceText)
            val actionText: TextView = view.findViewById(R.id.reviewActionText)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_copy_prices_review, parent, false)
            return ReviewViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
            val row = rows[position]
            holder.nameText.text = row.name ?: ""
            holder.sourcePriceText.text = "₱${String.format("%.2f", row.source_price ?: 0.0)}"
            holder.destPriceText.text = if (row.dest_price == null) "—" else "₱${String.format("%.2f", row.dest_price)}"
            holder.actionText.text = if (row.action == "create") "Create" else "Update"
            holder.actionText.setTextColor(
                if (row.action == "create") getColor(R.color.presyo_teal) else getColor(R.color.presyo_orange)
            )
        }
        
        override fun getItemCount(): Int = rows.size
    }
}

