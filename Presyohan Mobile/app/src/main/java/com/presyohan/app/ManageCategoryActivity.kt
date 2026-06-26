package com.presyohan.app

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
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.presyohan.app.adapter.ManageCategoryAdapter
import com.presyohan.app.adapter.ManageItemData
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManageCategoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManageCategoryAdapter
    private var storeId: String? = null
    private var storeName: String? = null
    private lateinit var loadingOverlay: View
    private lateinit var shimmerContainer: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    // Search and Bottom Sheet UI
    private lateinit var fabSearch: ImageButton
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bottomSearchEditText: EditText
    private lateinit var btnSearchClear: ImageView
    private var searchQuery: String = ""
    private var searchJob: kotlinx.coroutines.Job? = null

    // Data lists
    private var allCategories = listOf<String>()
    private var allCategoryCounts = mapOf<String, Int>()
    private var publicCategories = setOf<String>()
    private var allProducts = listOf<UserProductRow>()
    private var categoryNameToId = mapOf<String, String>()

    @Serializable
    data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_category)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        if (storeId.isNullOrBlank()) {
            finish()
            return
        }

        // Initialize Views
        recyclerView = findViewById(R.id.recyclerViewCategories)
        recyclerView.layoutManager = LinearLayoutManager(this)
        shimmerContainer = findViewById(R.id.shimmerContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(R.color.presyo_orange)
        swipeRefreshLayout.setOnRefreshListener {
            fetchCategories(showShimmer = false)
        }
        swipeRefreshLayout.isNestedScrollingEnabled = false

        fabSearch = findViewById(R.id.fabSearch)
        bottomSheet = findViewById(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSearchEditText = findViewById(R.id.bottomSearchEditText)
        btnSearchClear = findViewById(R.id.btnSearchClear)

        // Dummy/hidden views bindings to keep safety
        val textStoreName = findViewById<TextView>(R.id.textStoreName)
        val textStoreBranch = findViewById<TextView>(R.id.textStoreBranch)

        // Load store info
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @Serializable
                data class StoreRow(val id: String, val name: String, val branch: String? = null)
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", storeId!!) }
                    limit(1)
                }.decodeList<StoreRow>()
                val s = rows.firstOrNull()
                storeName = s?.name ?: storeName
                textStoreName.text = storeName ?: "Store Name"
                textStoreBranch.text = s?.branch ?: "Branch Name"
                SessionManager.markStoreHome(this@ManageCategoryActivity, storeId, storeName)
            } catch (_: Exception) { /* ignore */ }
            LoadingOverlayHelper.hide(loadingOverlay)
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

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

        // Dismiss keyboard when list is touched
        recyclerView.setOnTouchListener { _, _ ->
            if (bottomSearchEditText.hasFocus()) {
                bottomSearchEditText.clearFocus()
                hideKeyboard(bottomSearchEditText)
            }
            false
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // Scrolling down hides bottom sheet completely (only if dragged by user)
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
                    filterCategories()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSearchClear.setOnClickListener {
            bottomSearchEditText.setText("")
        }

        // Initialize Adapter
        adapter = ManageCategoryAdapter(
            categories = emptyList(),
            itemCounts = emptyMap(),
            publicCategories = emptySet(),
            onViewItems = { category ->
                val intent = Intent(this, ManageItemsActivity::class.java)
                intent.putExtra("storeId", storeId)
                intent.putExtra("storeName", storeName ?: "Store Name")
                intent.putExtra("filterCategory", category)
                startActivity(intent)
            },
            onRename = { category ->
                showRenameCategoryDialog(category)
            },
            onDelete = { category ->
                showDeleteCategoryDialog(category)
            },
            onCopy = { category ->
                copyCategory(category)
            },
            onConvert = { category ->
                convertCategory(category)
            },
            onPublicToggle = { category, makePublic ->
                showPublicToggleConfirmationDialog(category, makePublic)
            }
        )
        recyclerView.adapter = adapter

        fetchCategories()
    }

    private fun fetchCategories(showShimmer: Boolean = true) {
        val sId = storeId ?: return
        if (showShimmer) {
            shimmerContainer.visibility = View.VISIBLE
            shimmerContainer.startShimmer()
            recyclerView.visibility = View.GONE
        } else {
            LoadingOverlayHelper.show(loadingOverlay)
        }
        lifecycleScope.launch {
            try {
                val categoryRows = SupabaseProvider.client.postgrest.rpc(
                    "get_user_categories",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<UserCategoryRow>()

                val categories = categoryRows.map { it.name }
                categoryNameToId = categoryRows.associate { it.name to it.category_id }

                val products = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<UserProductRow>()

                allCategories = categories.sortedBy { it.lowercase() }
                allProducts = products

                val counts = mutableMapOf<String, Int>()
                for (cat in allCategories) counts[cat] = 0
                for (p in allProducts) {
                    val cat = p.category?.trim().orEmpty()
                    if (cat.isNotEmpty() && counts.containsKey(cat)) {
                        counts[cat] = (counts[cat] ?: 0) + 1
                    }
                }
                allCategoryCounts = counts

                val pubCats = mutableSetOf<String>()
                for (cat in allCategories) {
                    val catProducts = allProducts.filter { it.category?.trim().equals(cat.trim(), ignoreCase = true) }
                    if (catProducts.isNotEmpty() && catProducts.all { it.is_public }) {
                        pubCats.add(cat)
                    }
                }
                publicCategories = pubCats

                filterCategories()
            } catch (e: Exception) {
                Toast.makeText(this@ManageCategoryActivity, "Unable to load categories.", Toast.LENGTH_LONG).show()
            } finally {
                swipeRefreshLayout.isRefreshing = false
                if (showShimmer) {
                    shimmerContainer.stopShimmer()
                    shimmerContainer.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                } else {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    private fun filterCategories() {
        val query = searchQuery.trim()

        lifecycleScope.launch {
            val filtered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                if (query.isEmpty()) {
                    allCategories.sortedWith(String.CASE_INSENSITIVE_ORDER)
                } else {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    val matchedCats = allCategories.filter { cat ->
                        var isMatch = true
                        for (token in tokens) {
                            if (!com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, cat)) {
                                isMatch = false
                                break
                            }
                        }
                        isMatch
                    }

                    matchedCats.map { cat ->
                        var score = 0.0
                        val cleanCat = cat.lowercase(Locale.getDefault())
                        val cleanQuery = query.lowercase(Locale.getDefault())
                        
                        if (cleanCat == cleanQuery) score += 1000.0
                        if (cleanCat.startsWith(cleanQuery)) score += 500.0
                        if (cleanCat.contains(cleanQuery)) score += 200.0
                        
                        for (token in tokens) {
                            if (com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, cat)) score += 100.0
                        }
                        Pair(cat, score)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                }
            }

            adapter.updateCategories(filtered, allCategoryCounts, publicCategories)
        }
    }

    private fun copyCategory(category: String) {
        val count = allCategoryCounts[category] ?: 0
        CopyPricesDialogHelper.show(
            activity = this,
            storeId = storeId ?: return,
            storeName = storeName ?: "",
            preselectedCategory = category,
            descriptionText = "Enter the 6-digit paste-code generated by the destination store to securely transfer all $count prices in \"$category\"."
        )
    }

    private fun convertCategory(category: String) {
        val categoryProducts = allProducts.filter { it.category?.trim().equals(category.trim(), ignoreCase = true) }
        if (categoryProducts.isEmpty()) {
            Toast.makeText(this, "No products in this category to convert.", Toast.LENGTH_SHORT).show()
            return
        }
        val items = categoryProducts.map {
            ManageItemData(it.product_id, it.name, it.units ?: "", it.price, it.description ?: "", it.category ?: "", it.is_public)
        }
        showConvertPricelistDialog(items)
    }

    private fun toggleCategoryPublicStatus(category: String, makePublic: Boolean) {
        val sId = storeId ?: return
        val catId = categoryNameToId[category]
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.postgrest["products"].update(
                    buildJsonObject { put("is_public", makePublic) }
                ) {
                    filter {
                        eq("store_id", sId)
                        if (catId != null) {
                            eq("category_id", catId)
                        } else {
                            eq("category", category)
                        }
                    }
                }
                Toast.makeText(
                    this@ManageCategoryActivity,
                    if (makePublic) "Category made public." else "Category made private.",
                    Toast.LENGTH_SHORT
                ).show()
                fetchCategories(showShimmer = false)
            } catch (e: Exception) {
                Toast.makeText(this@ManageCategoryActivity, "Failed to update category public status.", Toast.LENGTH_LONG).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun showRenameCategoryDialog(oldCategory: String) {
        val view = layoutInflater.inflate(R.layout.dialog_new_category, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.title)?.text = "Rename Category"
        val input = view.findViewById<EditText>(R.id.inputCategory)
        input.setText(oldCategory)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        btnAdd.text = "Rename"
        btnAdd.setOnClickListener {
            val newCategory = input.text.toString().trim()
            if (newCategory.isEmpty()) {
                input.error = "Enter a category name"
                return@setOnClickListener
            }
            val sId = storeId ?: return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "rename_or_merge_category",
                        buildJsonObject {
                            put("p_store_id", sId)
                            put("p_old_name", oldCategory)
                            put("p_new_name", newCategory)
                        }
                    )
                    fetchCategories(showShimmer = false)
                    Toast.makeText(this@ManageCategoryActivity, "Category renamed.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    try {
                        @Serializable
                        data class MinimalCategoryRow(val id: String, val store_id: String, val name: String)
                        val cats = SupabaseProvider.client.postgrest["categories"].select {
                            filter { eq("store_id", sId); eq("name", oldCategory) }
                            limit(1)
                        }.decodeList<MinimalCategoryRow>()
                        val catId = cats.firstOrNull()?.id
                        if (!catId.isNullOrBlank()) {
                            SupabaseProvider.client.postgrest["categories"].update(
                                buildJsonObject { put("name", newCategory) }
                            ) {
                                filter { eq("id", catId); eq("store_id", sId) }
                            }
                            fetchCategories(showShimmer = false)
                            Toast.makeText(this@ManageCategoryActivity, "Category renamed.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@ManageCategoryActivity, "Unable to rename category.", Toast.LENGTH_LONG).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this@ManageCategoryActivity, "Unable to rename category.", Toast.LENGTH_LONG).show()
                    }
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        btnBack.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showDeleteCategoryDialog(category: String) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val btnNegative = view.findViewById<Button>(R.id.btnNegative)
        val btnPositive = view.findViewById<Button>(R.id.btnPositive)

        title.text = "Delete Category"
        message.text = "Are you sure you want to delete this category?\n\nAll items in this category will also be removed."

        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { dialog.dismiss() }

        btnPositive.text = "Delete"
        btnPositive.setOnClickListener {
            dialog.dismiss()
            val sId = storeId ?: return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    @Serializable
                    data class MinimalCategoryRow(val id: String)
                    val cats = SupabaseProvider.client.postgrest["categories"].select {
                        filter { eq("store_id", sId); eq("name", category) }
                        limit(1)
                    }.decodeList<MinimalCategoryRow>()
                    val catId = cats.firstOrNull()?.id

                    if (!catId.isNullOrBlank()) {
                        // Delete products associated with the category
                        SupabaseProvider.client.postgrest["products"].delete {
                            filter { eq("store_id", sId); eq("category_id", catId) }
                        }
                        // Delete the category itself
                        SupabaseProvider.client.postgrest["categories"].delete {
                            filter { eq("id", catId); eq("store_id", sId) }
                        }
                        fetchCategories(showShimmer = false)
                        Toast.makeText(this@ManageCategoryActivity, "Category and items deleted.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ManageCategoryActivity, "Category not found.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ManageCategory", "Delete failed: ${e.localizedMessage}")
                    Toast.makeText(this@ManageCategoryActivity, "Failed to delete category.", Toast.LENGTH_SHORT).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showPublicToggleConfirmationDialog(category: String, makePublic: Boolean) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val btnNegative = view.findViewById<Button>(R.id.btnNegative)
        val btnPositive = view.findViewById<Button>(R.id.btnPositive)

        if (makePublic) {
            title.text = "Publish Category"
            message.text = "Are you sure you want to make all items in this category public?"
            btnPositive.text = "Publish"
        } else {
            title.text = "Unpublish Category"
            message.text = "Are you sure you want to make all items in this category private?"
            btnPositive.text = "Unpublish"
        }

        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { dialog.dismiss() }

        btnPositive.setOnClickListener {
            dialog.dismiss()
            toggleCategoryPublicStatus(category, makePublic)
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // ==================== CONVERT/EXPORT LOGIC (MAPPED TO CATEGORY) ====================
    private fun showConvertPricelistDialog(items: List<ManageItemData>) {
        if (items.isEmpty()) {
            Toast.makeText(this, "No items in this category.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_convert_pricelist, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvSummary      = view.findViewById<TextView>(R.id.tvConvertSummary)
        val cardExcel      = view.findViewById<View>(R.id.cardExcelOption)
        val cardNotes      = view.findViewById<View>(R.id.cardNotesOption)
        val imgExcelRadio  = view.findViewById<ImageView>(R.id.imgExcelRadio)
        val imgNotesRadio  = view.findViewById<ImageView>(R.id.imgNotesRadio)
        val panelExcel     = view.findViewById<View>(R.id.panelExcelStats)
        val panelNotes     = view.findViewById<View>(R.id.panelNotesPreview)
        val tvStatRows     = view.findViewById<TextView>(R.id.tvStatRows)
        val tvStatScope    = view.findViewById<TextView>(R.id.tvStatScope)
        val textPreview    = view.findViewById<TextView>(R.id.textNotesPreview)
        val tvNoteStats    = view.findViewById<TextView>(R.id.tvNoteStats)
        val btnCopyPreview = view.findViewById<ImageView>(R.id.btnCopyNotePreview)
        val btnBack        = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnBack)
        val btnConvert     = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnConvert)

        val catCount = 1
        val itemCount = items.size
        tvSummary.text = "$catCount ${if (catCount == 1) "category" else "categories"} and " +
                "$itemCount ${if (itemCount == 1) "item" else "items"} to convert"

        var selectedMode = 0
        var generatedNoteText = ""

        fun applySelection(mode: Int) {
            selectedMode = mode

            imgExcelRadio.setImageResource(if (mode == 1) R.drawable.ic_radio_checked_orange else R.drawable.ic_radio_unchecked)
            imgNotesRadio.setImageResource(if (mode == 2) R.drawable.ic_radio_checked_orange else R.drawable.ic_radio_unchecked)

            cardExcel.setBackgroundResource(if (mode == 1) R.drawable.bg_card_selected_orange else R.drawable.bg_card_unselected_teal)
            cardNotes.setBackgroundResource(if (mode == 2) R.drawable.bg_card_selected_orange else R.drawable.bg_card_unselected_teal)

            panelExcel.visibility = if (mode == 1) View.VISIBLE else View.GONE
            panelNotes.visibility = if (mode == 2) View.VISIBLE else View.GONE

            if (mode == 1) {
                tvStatRows.text  = itemCount.toString()
                tvStatScope.text = "${catCount} ${if (catCount == 1) "Category" else "Categories"}, $itemCount Items"
                btnConvert.text  = "CONVERT"
            } else if (mode == 2) {
                generatedNoteText = buildPlainTextNotes(
                    items,
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

            btnConvert.isEnabled = true
            btnConvert.alpha     = 1.0f
        }

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
                    exportToExcel(items)
                }
                2 -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Pricelist Note")
                        putExtra(Intent.EXTRA_TEXT, generatedNoteText)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Pricelist via"))
                }
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
            ViewGroup.LayoutParams.WRAP_CONTENT
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
                Toast.makeText(this@ManageCategoryActivity, "Excel exported to Downloads.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ManageCategory", "Excel generation failed: ${e.localizedMessage}")
                Toast.makeText(this@ManageCategoryActivity, "Failed to generate Excel file.", Toast.LENGTH_LONG).show()
            } finally {
                try { output.close() } catch (_: Exception) {}
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
        fetchCategories()
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
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