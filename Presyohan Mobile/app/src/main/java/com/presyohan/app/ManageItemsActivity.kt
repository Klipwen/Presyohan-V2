package com.presyohan.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.presyohan.app.adapter.ManageItemData
import com.presyohan.app.adapter.ManageItemsAdapter
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManageItemsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var backButton: ImageView
    private lateinit var categoryLabel: TextView
    private lateinit var categoryDrawerButton: ImageView
    private lateinit var btnManageCategory: AppCompatButton
    private lateinit var btnAddItemGeneral: android.widget.ImageButton
    
    // Summary Row Views
    private lateinit var layoutSummaryRow: View
    private lateinit var textSummaryTitle: TextView
    private lateinit var textSummarySubtitle: TextView
    private lateinit var btnViewPublicItems: TextView
    private lateinit var btnSelectMode: TextView

    // Selection Header & Views
    private lateinit var layoutCategoryBar: View
    private lateinit var layoutSelectionActions: View
    private lateinit var layoutSummaryNormalContent: View
    private lateinit var layoutSummarySelectionContent: View
    private lateinit var textSelectionCount: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var btnCancelSelection: TextView

    // Bulk Action Buttons
    private lateinit var btnBulkPublish: View
    private lateinit var iconBulkPublish: ImageView
    private lateinit var textBulkPublish: TextView
    private lateinit var btnBulkCopy: View
    private lateinit var btnBulkConvert: View
    private lateinit var btnBulkDelete: View

    // Search Views
    private lateinit var fabSearch: View
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bottomSearchEditText: EditText
    private lateinit var btnSearchClear: ImageView

    private lateinit var adapter: ManageItemsAdapter
    private var allItems = mutableListOf<ManageItemData>()
    private var filteredItems = mutableListOf<ManageItemData>()
    private var categories = mutableListOf<String>()
    
    private var selectedCategory: String? = null
    private var searchQuery: String = ""
    private var searchJob: kotlinx.coroutines.Job? = null
    private var showOnlyPublic: Boolean = false
    
    private var storeId: String? = null
    private var storeName: String? = null
    private lateinit var loadingOverlay: android.view.View
    private lateinit var shimmerContainer: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_items)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        val initialFilterCategory = intent.getStringExtra("filterCategory")
        if (!initialFilterCategory.isNullOrBlank()) {
            selectedCategory = initialFilterCategory
        }
        
        val sId = storeId
        if (sId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided. Cannot manage items.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
        fetchItems(showShimmer = true)
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewItems)
        shimmerContainer = findViewById(R.id.shimmerContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(R.color.presyo_orange)
        swipeRefreshLayout.setOnRefreshListener {
            fetchItems(showShimmer = false, isPullToRefresh = true)
        }
        swipeRefreshLayout.isNestedScrollingEnabled = false
        backButton = findViewById(R.id.btnBack)
        categoryLabel = findViewById(R.id.categoryLabel)
        categoryDrawerButton = findViewById(R.id.categoryDrawerButton)
        btnManageCategory = findViewById(R.id.btnManageCategory)
        btnAddItemGeneral = findViewById(R.id.btnAddItemGeneral)

        // Summary Row
        layoutSummaryRow = findViewById(R.id.layoutSummaryRow)
        textSummaryTitle = findViewById(R.id.textSummaryTitle)
        textSummarySubtitle = findViewById(R.id.textSummarySubtitle)
        btnViewPublicItems = findViewById(R.id.btnViewPublicItems)
        btnSelectMode = findViewById(R.id.btnSelectMode)

        // Headers & Selection Views
        layoutCategoryBar = findViewById(R.id.layoutCategoryBar)
        layoutSelectionActions = findViewById(R.id.layoutSelectionActions)
        layoutSummaryNormalContent = findViewById(R.id.layoutSummaryNormalContent)
        layoutSummarySelectionContent = findViewById(R.id.layoutSummarySelectionContent)
        textSelectionCount = findViewById(R.id.textSelectionCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnCancelSelection = findViewById(R.id.btnCancelSelection)

        // Bulk Actions
        btnBulkPublish = findViewById(R.id.btnBulkPublish)
        iconBulkPublish = findViewById(R.id.iconBulkPublish)
        textBulkPublish = findViewById(R.id.textBulkPublish)
        btnBulkCopy = findViewById(R.id.btnBulkCopy)
        btnBulkConvert = findViewById(R.id.btnBulkConvert)
        btnBulkDelete = findViewById(R.id.btnBulkDelete)

        // Search Bottom Sheet
        fabSearch = findViewById(R.id.fabSearch)
        bottomSheet = findViewById(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSearchEditText = findViewById(R.id.bottomSearchEditText)
        btnSearchClear = findViewById(R.id.btnSearchClear)

        // Adapter initialization
        adapter = ManageItemsAdapter(filteredItems, object : ManageItemsAdapter.OnManageItemsListener {
            override fun onProductEditClick(item: ManageItemData) {
                val editData = EditProductData(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    price = item.price,
                    unit = item.unit,
                    category = item.category,
                    isPublic = item.is_public
                )
                AddEditItemDialogHelper.showAddOrEditItemDialog(
                    activity = this@ManageItemsActivity,
                    storeId = storeId ?: "",
                    storeName = storeName ?: "",
                    productData = editData,
                    onComplete = { fetchItems() }
                )
            }

            override fun onProductDeleteClick(item: ManageItemData) {
                deleteProduct(item)
            }

            override fun onProductInfoClick(item: ManageItemData) {
                showProductInfo(item)
            }

            override fun onCategoryRenameClick(category: String) {
                showRenameCategoryDialog(category)
            }

            override fun onCategoryDeleteClick(category: String) {
                deleteCategory(category)
            }

            override fun onAddItemClick(category: String) {
                showAddItemDialog(category)
            }

            override fun onSelectionChanged() {
                updateSelectionUI()
            }

            override fun onProductLongClick(item: ManageItemData) {
                if (!adapter.isSelectionMode) {
                    setSelectionMode(true)
                }
                adapter.selectedProductIds.add(item.id)
                adapter.notifyDataSetChanged()
                updateSelectionUI()
            }

            override fun onCategoryLongClick(category: String) {
                if (!adapter.isSelectionMode) {
                    setSelectionMode(true)
                }
                val catProducts = allItems.filter { it.category.trim().equals(category.trim(), ignoreCase = true) }
                catProducts.forEach { adapter.selectedProductIds.add(it.id) }
                adapter.notifyDataSetChanged()
                updateSelectionUI()
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        btnManageCategory.setOnClickListener {
            val intent = Intent(this, ManageCategoryActivity::class.java).apply {
                putExtra("storeId", storeId)
                putExtra("storeName", storeName)
            }
            startActivity(intent)
        }

        btnAddItemGeneral.setOnClickListener {
            showAddItemDialog("")
        }

        layoutCategoryTrigger.setOnClickListener { showCategoryDialog() }
        categoryDrawerButton.setOnClickListener { showCategoryDialog() }

        btnSelectMode.setOnClickListener { setSelectionMode(true) }
        btnCancelSelection.setOnClickListener { setSelectionMode(false) }
        btnSelectAll.setOnClickListener { adapter.selectAll() }

        btnViewPublicItems.setOnClickListener {
            showOnlyPublic = !showOnlyPublic
            updateFilter()
        }

        // Search FAB & Sheet
        fabSearch.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        // Dismiss keyboard when list is touched
        recyclerView.setOnTouchListener { _, _ ->
            if (bottomSearchEditText.hasFocus()) {
                bottomSearchEditText.clearFocus()
                hideKeyboard(bottomSearchEditText)
            }
            false
        }



        fun updateRecyclerPadding(bottomHeight: Int) {
            val safetyPadding = (16 * resources.displayMetrics.density).toInt()
            val targetPadding = bottomHeight + safetyPadding
            if (recyclerView.paddingBottom != targetPadding) {
                recyclerView.setPadding(
                    recyclerView.paddingLeft,
                    recyclerView.paddingTop,
                    recyclerView.paddingRight,
                    targetPadding
                )
            }
        }
        updateRecyclerPadding(0)

        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        
        // Bottom Sheet Behavior Callback
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        fabSearch.visibility = View.GONE
                        updateRecyclerPadding(bottomSheet.height)
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        fabSearch.visibility = View.GONE
                        hideKeyboard(bottomSearchEditText)
                        updateRecyclerPadding(bottomSheetBehavior.peekHeight)
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        fabSearch.visibility = View.VISIBLE
                        hideKeyboard(bottomSearchEditText)
                        updateRecyclerPadding(0)
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Empty to prevent recursive layout requests during drag/slide gestures
            }
        })

        fabSearch.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // Scrolling down hides bottom sheet completely
                if (dy > 10 && recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                }
            }
        })

        bottomSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                btnSearchClear.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(180)
                    updateFilter()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSearchClear.setOnClickListener {
            bottomSearchEditText.setText("")
        }

        // Bulk operations
        btnBulkPublish.setOnClickListener {
            val selected = adapter.getSelectedItems()
            val hasUnpublished = selected.any { !it.is_public }
            showBulkPublishDialog(hasUnpublished)
        }

        btnBulkCopy.setOnClickListener {
            showCopyPricesDialog()
        }

        btnBulkConvert.setOnClickListener {
            showConvertPricelistDialog()
        }

        btnBulkDelete.setOnClickListener {
            bulkDelete()
        }
    }

    private fun fetchItems(showShimmer: Boolean = true, isPullToRefresh: Boolean = false) {
        val sId = storeId ?: return
        if (showShimmer) {
            shimmerContainer.visibility = View.VISIBLE
            shimmerContainer.startShimmer()
            recyclerView.visibility = View.GONE
        } else if (!isPullToRefresh) {
            LoadingOverlayHelper.show(loadingOverlay)
        }
        lifecycleScope.launch {
            try {
                @Serializable
                data class UserProductRow(
                    val product_id: String,
                    val store_id: String,
                    val name: String,
                    val description: String? = null,
                    val price: Double = 0.0,
                    val units: String? = null,
                    val category: String? = null,
                    val is_public: Boolean = false
                )

                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject {
                        put("p_store_id", sId)
                    }
                ).decodeList<UserProductRow>()

                allItems.clear()
                categories.clear()
                val categorySet = mutableSetOf<String>()
                for (row in rows) {
                    val name = row.name
                    val description = row.description ?: ""
                    val price = row.price
                    val unit = row.units ?: ""
                    val category = row.category ?: ""
                    allItems.add(ManageItemData(row.product_id, name, unit, price, description, category, row.is_public))
                    if (category.isNotBlank()) categorySet.add(category)
                }

                categories.add("Pricelist")
                categories.addAll(categorySet.sortedBy { it.lowercase() })
                updateFilter()
            } catch (e: Exception) {
                Log.e("ManageItems", "Fetch items failed: ${e.localizedMessage}")
                Toast.makeText(this@ManageItemsActivity, "Failed to load items.", Toast.LENGTH_LONG).show()
            } finally {
                swipeRefreshLayout.isRefreshing = false
                if (showShimmer) {
                    shimmerContainer.stopShimmer()
                    shimmerContainer.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                } else if (!isPullToRefresh) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    private fun updateFilter() {
        val query = searchQuery.trim()
        val cat = selectedCategory
        val isPublicOnly = showOnlyPublic

        // Show UI labels instantly on main thread:
        val rawLabelName = if (cat.isNullOrBlank() || cat == "Pricelist") {
            "PRICELIST"
        } else {
            cat!!
        }
        val formattedLabel = if (rawLabelName.length > 15) {
            rawLabelName.take(12) + "..."
        } else {
            rawLabelName
        }
        categoryLabel.text = formattedLabel.uppercase()

        if (::btnAddItemGeneral.isInitialized) {
            btnAddItemGeneral.visibility = if ((cat == null || cat == "Pricelist") && !adapter.isSelectionMode) View.VISIBLE else View.GONE
        }

        lifecycleScope.launch {
            // Run heavy filtering and scoring in background thread
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val baseList = if (cat == null || cat == "Pricelist") {
                    allItems
                } else {
                    allItems.filter { it.category.trim().equals(cat.trim(), ignoreCase = true) }
                }

                val queryFiltered = if (query.isEmpty()) {
                    baseList.sortedBy { it.name.lowercase(Locale.getDefault()) }
                } else {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    val matchedItems = baseList.filter { item ->
                        var isMatch = true
                        for (token in tokens) {
                            val matchesName = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, item.name)
                            val matchesDesc = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, item.description)
                            val matchesCategory = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, item.category)
                            val matchesUnit = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, item.unit)
                            val matchesPrice = com.presyohan.app.helper.SearchHelper.matchPrice(token, item.price)
                            
                            if (!matchesName && !matchesDesc && !matchesCategory && !matchesUnit && !matchesPrice) {
                                isMatch = false
                                break
                            }
                        }
                        isMatch
                    }

                    matchedItems.map { item ->
                        val score = com.presyohan.app.helper.SearchHelper.calculateProductScore(
                            query = query,
                            name = item.name,
                            description = item.description,
                            categoryName = item.category
                        )
                        Pair(item, score)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                }

                val distinctCategoriesCount = queryFiltered.map { it.category.trim() }.distinct().filter { it.isNotBlank() }.size
                val totalCount = queryFiltered.size
                val publicCount = queryFiltered.count { it.is_public }

                val finalFilteredList = if (isPublicOnly) {
                    queryFiltered.filter { it.is_public }
                } else {
                    queryFiltered
                }

                Triple(finalFilteredList, Triple(distinctCategoriesCount, totalCount, publicCount), isPublicOnly)
            }

            val finalFilteredList = result.first
            val distinctCategoriesCount = result.second.first
            val totalCount = result.second.second
            val publicCount = result.second.third
            val isPublicOnlyVal = result.third

            filteredItems.clear()
            filteredItems.addAll(finalFilteredList)

            if (isPublicOnlyVal) {
                textSummaryTitle.text = "$distinctCategoriesCount Categories and $publicCount Public Items"
                textSummarySubtitle.text = "$totalCount total items."
                btnViewPublicItems.text = "View All"
            } else {
                textSummaryTitle.text = "$distinctCategoriesCount Categories and $totalCount total items"
                textSummarySubtitle.text = "$publicCount Public items."
                btnViewPublicItems.text = "View"
            }

            adapter.updateItems(filteredItems)
        }
    }

    private fun setSelectionMode(enabled: Boolean) {
        if (enabled) {
            // Show selection views
            layoutSelectionActions.visibility = View.VISIBLE
            layoutSummarySelectionContent.visibility = View.VISIBLE
            layoutSummaryRow.setBackgroundResource(R.drawable.bg_outline_button_orange)

            // Hide normal views
            btnManageCategory.visibility = View.GONE
            btnAddItemGeneral.visibility = View.GONE
            layoutSummaryNormalContent.visibility = View.GONE
            btnSelectMode.visibility = View.GONE

            fabSearch.visibility = View.VISIBLE
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            // Show normal views
            btnManageCategory.visibility = View.VISIBLE
            btnAddItemGeneral.visibility = if (selectedCategory == null || selectedCategory == "Pricelist") View.VISIBLE else View.GONE
            layoutSummaryNormalContent.visibility = View.VISIBLE
            btnSelectMode.visibility = View.VISIBLE
            layoutSummaryRow.setBackgroundResource(R.drawable.bg_summary_box_grey)

            // Hide selection views
            layoutSelectionActions.visibility = View.GONE
            layoutSummarySelectionContent.visibility = View.GONE

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            fabSearch.visibility = View.VISIBLE
        }

        adapter.setSelectionMode(enabled)
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        if (!adapter.isSelectionMode) return

        val selectedItems = adapter.getSelectedItems()
        val totalSelectedItems = selectedItems.size

        val allSelected = filteredItems.isNotEmpty() && filteredItems.all { adapter.selectedProductIds.contains(it.id) }
        if (allSelected) {
            btnSelectAll.text = "Unselect"
            btnSelectAll.setOnClickListener { adapter.clearSelection() }
        } else {
            btnSelectAll.text = "Select all"
            btnSelectAll.setOnClickListener { adapter.selectAll() }
        }

        val grouped = rawItemsGrouped()
        var selectedCategoriesCount = 0
        grouped.forEach { (catName, catProds) ->
            if (catProds.isNotEmpty() && catProds.all { adapter.selectedProductIds.contains(it.id) }) {
                selectedCategoriesCount++
            }
        }

        val selectionText = formatSelectionText(selectedCategoriesCount, totalSelectedItems, capitalize = true)
        textSelectionCount.text = "$selectionText selected"

        val hasSelection = totalSelectedItems > 0
        btnBulkCopy.isEnabled = hasSelection
        btnBulkCopy.alpha = if (hasSelection) 1.0f else 0.5f
        btnBulkConvert.isEnabled = hasSelection
        btnBulkConvert.alpha = if (hasSelection) 1.0f else 0.5f
        btnBulkDelete.isEnabled = hasSelection
        btnBulkDelete.alpha = if (hasSelection) 1.0f else 0.5f

        if (!hasSelection) {
            btnBulkPublish.visibility = View.VISIBLE
            btnBulkPublish.isEnabled = false
            btnBulkPublish.alpha = 0.5f
            textBulkPublish.text = "Publish"
            iconBulkPublish.setImageResource(R.drawable.icon_globe)
        } else {
            btnBulkPublish.isEnabled = true
            btnBulkPublish.alpha = 1.0f

            val hasPublic = selectedItems.any { it.is_public }
            val hasUnpublished = selectedItems.any { !it.is_public }

            if (hasPublic && hasUnpublished) {
                btnBulkPublish.visibility = View.GONE
            } else {
                btnBulkPublish.visibility = View.VISIBLE
                if (hasUnpublished) {
                    textBulkPublish.text = "Publish"
                    iconBulkPublish.setImageResource(R.drawable.icon_globe)
                } else {
                    textBulkPublish.text = "Unpublish"
                    iconBulkPublish.setImageResource(R.drawable.icon_unpublish)
                }
            }
        }
    }

    private fun formatSelectionText(categoriesCount: Int, itemsCount: Int, capitalize: Boolean = false): String {
        val catPart = when {
            categoriesCount <= 0 -> ""
            categoriesCount == 1 -> if (capitalize) "1 Category" else "1 category"
            else -> if (capitalize) "$categoriesCount Categories" else "$categoriesCount categories"
        }
        val itemPart = when {
            itemsCount <= 0 -> ""
            itemsCount == 1 -> if (capitalize) "1 Item" else "1 item"
            else -> if (capitalize) "$itemsCount Items" else "$itemsCount items"
        }
        return when {
            catPart.isNotEmpty() && itemPart.isNotEmpty() -> "$catPart and $itemPart"
            catPart.isNotEmpty() -> catPart
            itemPart.isNotEmpty() -> itemPart
            else -> if (capitalize) "0 Items" else "0 items"
        }
    }

    private fun rawItemsGrouped(): Map<String, List<ManageItemData>> {
        val baseList = if (selectedCategory == null || selectedCategory == "Pricelist") {
            allItems
        } else {
            allItems.filter { it.category.trim().equals(selectedCategory?.trim(), ignoreCase = true) }
        }
        return baseList.groupBy { it.category.trim() }
    }

    private fun showCategoryDialog() {
        if (categories.isEmpty()) return
        val displayCategories = categories.map {
            if (it.equals("Pricelist", ignoreCase = true)) "PRICELIST"
            else it.replaceFirstChar { c -> c.uppercase() }
        }.toTypedArray()
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Category")
        builder.setItems(displayCategories) { _, which ->
            selectedCategory = if (which == 0) null else categories[which]
            categoryLabel.text = if (selectedCategory.isNullOrBlank()) "PRICELIST" else selectedCategory!!.replaceFirstChar { c -> c.uppercase() }
            updateFilter()
        }
        builder.show()
    }

    private fun showAddItemDialog(category: String) {
        val editData = EditProductData(
            id = "",
            name = "",
            description = "",
            price = 0.0,
            unit = "",
            category = category,
            isPublic = true
        )
        AddEditItemDialogHelper.showAddOrEditItemDialog(
            activity = this@ManageItemsActivity,
            storeId = storeId ?: "",
            storeName = storeName ?: "",
            productData = editData,
            onComplete = { fetchItems() }
        )
    }

    private fun showProductInfo(product: ManageItemData) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_product_info, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<TextView>(R.id.textInfoName).text = product.name
        view.findViewById<TextView>(R.id.textInfoDescription).text = product.description.ifBlank { "No description provided." }
        view.findViewById<TextView>(R.id.textInfoPrice).text = "₱%,.2f".format(Locale.US, product.price)
        view.findViewById<TextView>(R.id.textInfoUnit).text = product.unit.ifBlank { "pcs" }
        view.findViewById<TextView>(R.id.textInfoVisibility).apply {
            if (product.is_public) {
                text = "Public"
                setTextColor(resources.getColor(R.color.presyo_teal))
            } else {
                text = "Private"
                setTextColor(resources.getColor(R.color.presyo_orange))
            }
        }

        view.findViewById<Button>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showRenameCategoryDialog(oldName: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_new_category, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val input = view.findViewById<EditText>(R.id.inputCategory)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val btnBack = view.findViewById<Button>(R.id.btnBack)

        view.findViewById<TextView>(R.id.title)?.let {
            it.text = "Rename Category"
        }
        btnAdd?.text = "Rename"
        input?.setText(oldName)

        btnAdd.setOnClickListener {
            val newName = input.text.toString().trim().uppercase()
            if (newName.isNotEmpty()) {
                dialog.dismiss()
                renameCategory(oldName, newName)
            } else {
                input.error = "Enter a category name"
            }
        }
        btnBack.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun renameCategory(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        val sId = storeId ?: return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @Serializable
                data class CatRow(val id: String, val name: String)
                val cats = SupabaseProvider.client.postgrest["categories"].select {
                    filter { eq("store_id", sId); eq("name", oldName) }
                    limit(1)
                }.decodeList<CatRow>()

                val catId = cats.firstOrNull()?.id
                if (!catId.isNullOrBlank()) {
                    SupabaseProvider.client.postgrest["categories"].update(
                        buildJsonObject { put("name", newName) }
                    ) {
                        filter { eq("id", catId); eq("store_id", sId) }
                    }
                    if (selectedCategory?.trim()?.equals(oldName.trim(), ignoreCase = true) == true) {
                        selectedCategory = newName
                    }
                    fetchItems()
                    Toast.makeText(this@ManageItemsActivity, "Category renamed.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ManageItems", "Rename category failed: ${e.localizedMessage}")
                Toast.makeText(this@ManageItemsActivity, "Failed to rename category.", Toast.LENGTH_SHORT).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun deleteCategory(categoryName: String) {
        val sId = storeId ?: return
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogTitle).text = "Delete Category"
        view.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to delete this category?\n\nAll items in this category will also be removed."

        val btnNegative = view.findViewById<Button>(R.id.btnNegative)
        val btnPositive = view.findViewById<Button>(R.id.btnPositive)

        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { dialog.dismiss() }

        btnPositive.text = "Delete"
        btnPositive.setOnClickListener {
            dialog.dismiss()
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    @Serializable
                    data class CatRow(val id: String, val name: String)
                    val cats = SupabaseProvider.client.postgrest["categories"].select {
                        filter { eq("store_id", sId); eq("name", categoryName) }
                        limit(1)
                    }.decodeList<CatRow>()

                    val catId = cats.firstOrNull()?.id
                    if (!catId.isNullOrBlank()) {
                        SupabaseProvider.client.postgrest["products"].delete {
                            filter { eq("store_id", sId); eq("category_id", catId) }
                        }
                        SupabaseProvider.client.postgrest["categories"].delete {
                            filter { eq("id", catId); eq("store_id", sId) }
                        }
                        fetchItems()
                        Toast.makeText(this@ManageItemsActivity, "Category deleted.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ManageItems", "Delete category failed: ${e.localizedMessage}")
                    Toast.makeText(this@ManageItemsActivity, "Failed to delete category.", Toast.LENGTH_SHORT).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun deleteProduct(product: ManageItemData) {
        val sId = storeId ?: return
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogTitle).text = "Delete Item"
        view.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to delete this item?"

        val btnNegative = view.findViewById<Button>(R.id.btnNegative)
        val btnPositive = view.findViewById<Button>(R.id.btnPositive)

        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { dialog.dismiss() }

        btnPositive.text = "Delete"
        btnPositive.setOnClickListener {
            dialog.dismiss()
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest["products"].delete {
                        filter { eq("id", product.id); eq("store_id", sId) }
                    }

                    val categoryToCheck = product.category
                    if (categoryToCheck.isNotBlank() && allItems.none { it.category == categoryToCheck && it.id != product.id }) {
                        @Serializable
                        data class MinimalCategoryRow(val id: String, val name: String)
                        val cats = SupabaseProvider.client.postgrest["categories"].select {
                            filter { eq("store_id", sId); eq("name", categoryToCheck) }
                            limit(1)
                        }.decodeList<MinimalCategoryRow>()
                        val catId = cats.firstOrNull()?.id
                        if (!catId.isNullOrBlank()) {
                            SupabaseProvider.client.postgrest["categories"].delete {
                                filter { eq("id", catId); eq("store_id", sId) }
                            }
                        }
                    }

                    fetchItems()
                    Toast.makeText(this@ManageItemsActivity, "Product deleted.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ManageItems", "Delete product failed: ${e.localizedMessage}")
                    Toast.makeText(this@ManageItemsActivity, "Failed to delete product.", Toast.LENGTH_SHORT).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showBulkPublishDialog(publish: Boolean) {
        val selectedItems = adapter.getSelectedItems()
        val totalSelectedItems = selectedItems.size
        if (totalSelectedItems == 0) return

        val grouped = rawItemsGrouped()
        var selectedCategoriesCount = 0
        grouped.forEach { (catName, catProds) ->
            if (catProds.isNotEmpty() && catProds.all { adapter.selectedProductIds.contains(it.id) }) {
                selectedCategoriesCount++
            }
        }

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        val btnNegative = view.findViewById<Button>(R.id.btnNegative)
        val btnPositive = view.findViewById<Button>(R.id.btnPositive)

        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { dialog.dismiss() }

        val selectionText = formatSelectionText(selectedCategoriesCount, totalSelectedItems, capitalize = false)

        if (publish) {
            titleView.text = "Publish Selected Items"
            messageView.text = "Are you sure you want to make the selected\n$selectionText public?\n\nThis will make them instantly visible to all your\nsuki."
            btnPositive.text = "Make Public"
        } else {
            titleView.text = "Unpublish Selected Items"
            messageView.text = "Are you sure you want to hide the selected\n$selectionText?\n\nThis will hide them from your suki, but keeps\nthem in your store."
            btnPositive.text = "Unpublish"
        }

        btnPositive.setOnClickListener {
            dialog.dismiss()
            bulkPublish(publish)
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun bulkPublish(publish: Boolean) {
        val selectedIds = adapter.selectedProductIds.toList()
        if (selectedIds.isEmpty()) return
        val sId = storeId ?: return

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.postgrest["products"].update(
                    buildJsonObject { put("is_public", publish) }
                ) {
                    filter {
                        isIn("id", selectedIds)
                        eq("store_id", sId)
                    }
                }
                Toast.makeText(this@ManageItemsActivity, if (publish) "Items published." else "Items unpublished.", Toast.LENGTH_SHORT).show()
                setSelectionMode(false)
                fetchItems()
            } catch (e: Exception) {
                Log.e("ManageItems", "Bulk visibility update failed: ${e.localizedMessage}")
                Toast.makeText(this@ManageItemsActivity, "Failed to update visibility.", Toast.LENGTH_SHORT).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun bulkDelete() {
        val selectedIds = adapter.selectedProductIds.toList()
        if (selectedIds.isEmpty()) return
        val sId = storeId ?: return

        val selectedItems = adapter.getSelectedItems()
        val totalSelectedItems = selectedItems.size

        val grouped = rawItemsGrouped()
        var selectedCategoriesCount = 0
        grouped.forEach { (catName, catProds) ->
            if (catProds.isNotEmpty() && catProds.all { adapter.selectedProductIds.contains(it.id) }) {
                selectedCategoriesCount++
            }
        }

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val selectionText = formatSelectionText(selectedCategoriesCount, totalSelectedItems, capitalize = false)

        view.findViewById<TextView>(R.id.dialogTitle).text = "Delete Selected Items"
        view.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to delete the selected\n$selectionText?\n\nThis action will permanently remove them from\nyour pricelist and cannot be undone."

        val btnNegative = view.findViewById<Button>(R.id.btnNegative)
        val btnPositive = view.findViewById<Button>(R.id.btnPositive)

        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { dialog.dismiss() }

        btnPositive.text = "Delete"
        btnPositive.setOnClickListener {
            dialog.dismiss()
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest["products"].delete {
                        filter {
                            isIn("id", selectedIds)
                            eq("store_id", sId)
                        }
                    }

                    // Cleanup empty categories
                    val selectedCategories = adapter.getSelectedItems().map { it.category }.distinct()
                    for (categoryToCheck in selectedCategories) {
                        if (categoryToCheck.isNotBlank() && allItems.none { it.category == categoryToCheck && !selectedIds.contains(it.id) }) {
                            @Serializable
                            data class MinimalCategoryRow(val id: String, val name: String)
                            val cats = SupabaseProvider.client.postgrest["categories"].select {
                                filter { eq("store_id", sId); eq("name", categoryToCheck) }
                                limit(1)
                            }.decodeList<MinimalCategoryRow>()
                            val catId = cats.firstOrNull()?.id
                            if (!catId.isNullOrBlank()) {
                                SupabaseProvider.client.postgrest["categories"].delete {
                                    filter { eq("id", catId); eq("store_id", sId) }
                                }
                            }
                        }
                    }

                    Toast.makeText(this@ManageItemsActivity, "Selected items deleted.", Toast.LENGTH_SHORT).show()
                    setSelectionMode(false)
                    fetchItems()
                } catch (e: Exception) {
                    Log.e("ManageItems", "Bulk delete failed: ${e.localizedMessage}")
                    Toast.makeText(this@ManageItemsActivity, "Failed to delete selected items.", Toast.LENGTH_SHORT).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showConvertPricelistDialog() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No items selected.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_convert_pricelist, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // --- Views ---
        val tvSummary      = view.findViewById<TextView>(R.id.tvConvertSummary)
        val cardExcel      = view.findViewById<android.view.View>(R.id.cardExcelOption)
        val cardNotes      = view.findViewById<android.view.View>(R.id.cardNotesOption)
        val imgExcelRadio  = view.findViewById<ImageView>(R.id.imgExcelRadio)
        val imgNotesRadio  = view.findViewById<ImageView>(R.id.imgNotesRadio)
        val panelExcel     = view.findViewById<android.view.View>(R.id.panelExcelStats)
        val panelNotes     = view.findViewById<android.view.View>(R.id.panelNotesPreview)
        val tvStatRows     = view.findViewById<TextView>(R.id.tvStatRows)
        val tvStatScope    = view.findViewById<TextView>(R.id.tvStatScope)
        val textPreview    = view.findViewById<TextView>(R.id.textNotesPreview)
        val tvNoteStats    = view.findViewById<TextView>(R.id.tvNoteStats)
        val btnCopyPreview = view.findViewById<ImageView>(R.id.btnCopyNotePreview)
        val btnBack        = view.findViewById<AppCompatButton>(R.id.btnBack)
        val btnConvert     = view.findViewById<AppCompatButton>(R.id.btnConvert)

        // --- Summary line ---
        val grouped = selectedItems.groupBy { it.category.trim() }
        val catCount  = grouped.keys.filter { it.isNotBlank() }.size
        val itemCount = selectedItems.size
        tvSummary.text = "$catCount ${if (catCount == 1) "category" else "categories"} and " +
                "$itemCount ${if (itemCount == 1) "item" else "items"} to convert"

        // --- State ---
        // 0 = nothing chosen yet, 1 = Excel, 2 = Notes
        var selectedMode = 0
        var generatedNoteText = ""

        fun applySelection(mode: Int) {
            selectedMode = mode

            // Update radio icon states
            imgExcelRadio.setImageResource(if (mode == 1) R.drawable.ic_radio_checked_orange else R.drawable.ic_radio_unchecked)
            imgNotesRadio.setImageResource(if (mode == 2) R.drawable.ic_radio_checked_orange else R.drawable.ic_radio_unchecked)

            // Update card backgrounds
            cardExcel.setBackgroundResource(if (mode == 1) R.drawable.bg_card_selected_orange else R.drawable.bg_card_unselected_teal)
            cardNotes.setBackgroundResource(if (mode == 2) R.drawable.bg_card_selected_orange else R.drawable.bg_card_unselected_teal)

            // Show / hide panels
            panelExcel.visibility = if (mode == 1) android.view.View.VISIBLE else android.view.View.GONE
            panelNotes.visibility = if (mode == 2) android.view.View.VISIBLE else android.view.View.GONE

            if (mode == 1) {
                // Populate Excel stats
                tvStatRows.text  = itemCount.toString()
                tvStatScope.text = "${catCount} ${if (catCount == 1) "Category" else "Categories"}, $itemCount Items"
                btnConvert.text  = "CONVERT"
            } else if (mode == 2) {
                // Build and show note preview
                generatedNoteText = buildPlainTextNotes(
                    selectedItems,
                    includeTitle = true,
                    includeDesc  = true,
                    includePrice = true,
                    includeUnit  = true,
                    footerText   = ""
                )
                textPreview.text  = generatedNoteText
                tvNoteStats.text  = "$itemCount ${if (itemCount == 1) "item" else "items"} • ${generatedNoteText.length} characters"
                btnConvert.text   = "SHARE NOTE"
            }

            // Enable the action button once a mode is chosen
            btnConvert.isEnabled = true
            btnConvert.alpha     = 1.0f
        }

        // --- Click listeners ---
        cardExcel.setOnClickListener { applySelection(1) }
        cardNotes.setOnClickListener { applySelection(2) }

        btnCopyPreview.setOnClickListener {
            if (generatedNoteText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Pricelist Note", generatedNoteText))
                Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener { dialog.dismiss() }

        btnConvert.setOnClickListener {
            when (selectedMode) {
                1 -> {
                    dialog.dismiss()
                    exportToExcel(selectedItems)
                }
                2 -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, generatedNoteText)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Pricelist via"))
                }
                else -> Toast.makeText(this, "Please select a format first.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun buildPlainTextNotes(
        items: List<ManageItemData>,
        includeTitle: Boolean,
        includeDesc: Boolean,
        includePrice: Boolean,
        includeUnit: Boolean,
        footerText: String
    ): String {
        val sb = java.lang.StringBuilder()
        if (includeTitle) {
            sb.append("PRICELIST: ${storeName ?: "Store"}\n")
            sb.append("Date: ${SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date())}\n\n")
        }

        val grouped = items.groupBy { it.category.trim() }
        val sortedCategories = grouped.keys.sortedBy { it.lowercase() }

        for (category in sortedCategories) {
            val catName = if (category.isBlank()) "General" else category
            sb.append("[$catName]\n")

            val products = grouped[category].orEmpty().sortedBy { it.name.lowercase() }
            for (prod in products) {
                sb.append("- ${prod.name}")

                val details = mutableListOf<String>()
                if (includeUnit && prod.unit.isNotBlank()) {
                    details.add(prod.unit)
                }
                if (includePrice) {
                    details.add("₱%,.2f".format(Locale.US, prod.price))
                }
                if (details.isNotEmpty()) {
                    sb.append(" (${details.joinToString(" - ")})")
                }
                sb.append("\n")
                if (includeDesc && prod.description.isNotBlank()) {
                    sb.append("  * ${prod.description}\n")
                }
            }
            sb.append("\n")
        }

        if (footerText.isNotBlank()) {
            sb.append(footerText)
        }

        return sb.toString().trim()
    }

    private fun showNotesPreviewDialog(notesText: String) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_notes_preview, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val textNotesPreview = view.findViewById<TextView>(R.id.textNotesPreview)
        textNotesPreview.text = notesText

        view.findViewById<Button>(R.id.btnClose).setOnClickListener { dialog.dismiss() }

        view.findViewById<Button>(R.id.btnCopyToClipboard).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Pricelist Notes", notesText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, notesText)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Pricelist via"))
        }

        dialog.show()
    }

    private fun exportToExcel(items: List<ManageItemData>) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "Pricelist_${storeName?.replace(" ", "_") ?: "Store"}_$timeStamp.xlsx"
        val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        val output: OutputStream? = try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri: Uri? = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) contentResolver.openOutputStream(uri) else null
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, filename)
                FileOutputStream(file)
            }
        } catch (_: Exception) { null }

        if (output == null) {
            Toast.makeText(this, "Failed to export Excel file.", Toast.LENGTH_LONG).show()
            return
        }

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val wb = org.dhatim.fastexcel.Workbook(output, "Presyohan", "1.0")
                    val ws = wb.newWorksheet("Pricelist")

                    ws.value(0, 0, "Presyohan Store Pricelist")
                    ws.style(0, 0).bold().set()

                    ws.value(1, 0, "Store: ${storeName ?: ""}")
                    val exportedAt = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US).format(Date())
                    ws.value(2, 0, "Exported at: $exportedAt")

                    val headers = listOf("Category", "Name", "Description", "Unit", "Price")
                    headers.forEachIndexed { idx, h ->
                        ws.value(4, idx, h)
                        ws.style(4, idx).bold().set()
                    }

                    var rowIndex = 5
                    items.forEach { item ->
                        ws.value(rowIndex, 0, item.category)
                        ws.value(rowIndex, 1, item.name)
                        ws.value(rowIndex, 2, item.description)
                        ws.value(rowIndex, 3, item.unit)
                        ws.value(rowIndex, 4, item.price)
                        ws.style(rowIndex, 4).format("\"₱\"#,##0.00").set()
                        rowIndex++
                    }

                    try {
                        ws.width(0, 20.0)
                        ws.width(1, 28.0)
                        ws.width(2, 40.0)
                        ws.width(3, 12.0)
                        ws.width(4, 14.0)
                    } catch (_: Throwable) {}

                    wb.finish()
                    output.flush()
                }
                Toast.makeText(this@ManageItemsActivity, "Excel exported to Downloads.", Toast.LENGTH_SHORT).show()
                setSelectionMode(false)
            } catch (e: Exception) {
                Log.e("ManageItems", "Excel generation failed: ${e.localizedMessage}")
                Toast.makeText(this@ManageItemsActivity, "Failed to generate Excel file.", Toast.LENGTH_LONG).show()
            } finally {
                try { output.close() } catch (_: Exception) {}
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }


    private fun showCopyPricesDialog() {
        val sId = storeId ?: return
        val selectedItems = adapter.getSelectedItems()
        val selectedIds = selectedItems.map { it.id }
        CopyPricesDialogHelper.show(
            activity = this,
            storeId = sId,
            storeName = storeName ?: "",
            selectedIds = selectedIds
        )
    }



    private fun layoutCategoryTriggerView(): View {
        return findViewById(R.id.layoutCategoryTrigger)
    }

    private val layoutCategoryTrigger: View get() = layoutCategoryTriggerView()

    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            setSelectionMode(false)
        } else {
            super.onBackPressed()
        }
    }


    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    hideKeyboard(v)
                    v.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
