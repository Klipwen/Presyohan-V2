package com.presyohan.app

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.adapter.CategoryGroupAdapter
import com.presyohan.app.adapter.Product
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

class StoreViewActivity : AppCompatActivity() {

    private lateinit var btnBack: FrameLayout
    private lateinit var tvStoreName: TextView
    private lateinit var tvStoreBranch: TextView
    private lateinit var btnToggleSubscribe: FrameLayout
    private lateinit var ivSubscribeIcon: ImageView
    private lateinit var btnUnsubscribe: FrameLayout
    private lateinit var searchEditText: EditText
    private lateinit var layoutPricelistTrigger: LinearLayout
    private lateinit var categoryLabel: TextView
    private lateinit var categorySpinner: Spinner
    private lateinit var rvStoreProducts: RecyclerView
    private lateinit var shimmerContainer: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var layoutPrivateStoreState: View
    private lateinit var layoutStoreHeaderDetails: View
    private var storeMetadata: StoreDbRow? = null

    private var isPrivateAndNotSubscribed = false

    // Intent parameters
    private var storeId: String = ""
    private var storeName: String = ""
    private var storeBranch: String? = null
    private var storeType: String? = null
    private var isPresyohan: Boolean = false
    private var initialCategory: String? = null

    // Data lists
    private var allCategories: List<CategoryDetailRow> = emptyList()
    private var allProducts: List<ProductDetailRow> = emptyList()
    private var rawProducts: List<Product> = emptyList()

    // Filter states
    private var currentSearchQuery = ""
    private var selectedCategory = "PRICELIST"
    private var isSubscribed = false
    private val spinnerCategories = mutableListOf("PRICELIST")
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    // Adapter
    private lateinit var groupAdapter: CategoryGroupAdapter
    private var searchJob: kotlinx.coroutines.Job? = null

    @Serializable
    data class SukiRelationshipRow(val store_id: String)

    @Serializable
    data class StoreDbRow(
        val id: String,
        val name: String,
        val is_public: Boolean = false,
        val display_id: String? = null,
        val created_at: String? = null,
        val is_standard_store: Boolean = false,
        val type: String? = null,
        val branch: String? = null
    )

    @Serializable
    data class CategoryDetailRow(
        val id: String,
        val store_id: String,
        val name: String
    )

    @Serializable
    data class ProductDetailRow(
        val id: String,
        val store_id: String,
        val name: String,
        val description: String? = null,
        val price: Double = 0.0,
        val unit: String? = null,
        val is_public: Boolean = false,
        val category_id: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_view)

        // Set transparent status bar and make layout extend under it
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }

        // Retrieve intent extras
        storeId = intent.getStringExtra("STORE_ID") ?: ""
        storeName = intent.getStringExtra("STORE_NAME") ?: "Store"
        storeBranch = intent.getStringExtra("STORE_BRANCH")
        storeType = intent.getStringExtra("STORE_TYPE")
        isPresyohan = intent.getBooleanExtra("IS_PRESYOHAN", false)
        initialCategory = intent.getStringExtra("FILTER_CATEGORY")

        // Initialize Views
        btnBack = findViewById(R.id.btnBack)
        tvStoreName = findViewById(R.id.tvStoreName)
        tvStoreBranch = findViewById(R.id.tvStoreBranch)
        btnToggleSubscribe = findViewById(R.id.btnToggleSubscribe)
        ivSubscribeIcon = findViewById(R.id.ivSubscribeIcon)
        btnUnsubscribe = findViewById(R.id.btnUnsubscribe)
        searchEditText = findViewById(R.id.searchEditText)
        layoutPricelistTrigger = findViewById(R.id.layoutPricelistTrigger)
        categoryLabel = findViewById(R.id.categoryLabel)
        categorySpinner = findViewById(R.id.categorySpinner)
        rvStoreProducts = findViewById(R.id.rvStoreProducts)
        shimmerContainer = findViewById(R.id.shimmerContainer)
        layoutPrivateStoreState = findViewById(R.id.layoutPrivateStoreState)
        layoutStoreHeaderDetails = findViewById(R.id.layoutStoreHeaderDetails)

        // Apply dynamic top padding to curvedHeaderContainer matching status bar height
        val curvedHeaderContainer = findViewById<View>(R.id.curvedHeaderContainer)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(curvedHeaderContainer) { view, insets ->
            val statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                statusBarHeight + (8 * resources.displayMetrics.density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        // Set store details in UI
        tvStoreName.text = storeName
        tvStoreBranch.text = storeBranch ?: "Main Branch"

        // Setup Back Click
        btnBack.setOnClickListener {
            finish()
        }

        // Setup RecyclerView
        rvStoreProducts.layoutManager = LinearLayoutManager(this)
        groupAdapter = CategoryGroupAdapter(
            groups = emptyList(),
            userRole = null,
            activeOverlayProductId = null,
            onEditClick = {},
            onDeleteClick = {},
            onItemLongPressed = {},
            onItemClicked = {}
        )
        rvStoreProducts.adapter = groupAdapter

        // Setup Category Spinner
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerCategories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = spinnerAdapter

        layoutPricelistTrigger.setOnClickListener {
            categorySpinner.performClick()
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategory = spinnerCategories[position]
                categoryLabel.text = selectedCategory
                filterAndGroupProducts()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Search bar
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(180)
                    filterAndGroupProducts()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Subscribe & Unsubscribe Buttons
        btnToggleSubscribe.setOnClickListener {
            if (!isSubscribed) {
                subscribeToStore()
            } else {
                showStoreDetailsDialog()
            }
        }

        btnUnsubscribe.setOnClickListener {
            showUnsubscribeDialog()
        }

        layoutStoreHeaderDetails.setOnClickListener {
            showStoreDetailsDialog()
        }

        // Load data
        loadStoreData()
    }

    private fun loadStoreData() {
        shimmerContainer.visibility = View.VISIBLE
        shimmerContainer.startShimmer()
        rvStoreProducts.visibility = View.GONE
        layoutPrivateStoreState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    // Check subscription status
                    val suki = SupabaseProvider.client.postgrest["suki_relationships"]
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("store_id", storeId)
                            }
                        }
                        .decodeList<SukiRelationshipRow>()
                    
                    isSubscribed = suki.isNotEmpty()
                    updateSubscriptionUI()
                }

                // Check store metadata
                val storeInfo = SupabaseProvider.client.postgrest["stores"]
                    .select {
                        filter { eq("id", storeId) }
                    }
                    .decodeList<StoreDbRow>()
                val meta = storeInfo.firstOrNull()
                storeMetadata = meta
                val isStorePublic = meta?.is_public ?: true
                isPresyohan = meta?.is_standard_store ?: isPresyohan
                storeType = meta?.type ?: storeType
                storeBranch = meta?.branch ?: storeBranch
                meta?.let {
                    tvStoreName.text = it.name
                    tvStoreBranch.text = it.branch ?: "Main Branch"
                }
                isPrivateAndNotSubscribed = !isStorePublic && !isSubscribed

                if (isPrivateAndNotSubscribed) {
                    // Hide content and button indicators
                    layoutPricelistTrigger.visibility = View.GONE
                    rvStoreProducts.visibility = View.GONE
                    btnToggleSubscribe.visibility = View.GONE
                    btnUnsubscribe.visibility = View.GONE
                    layoutPrivateStoreState.visibility = View.VISIBLE
                } else {
                    // Show standard controls
                    layoutPricelistTrigger.visibility = View.VISIBLE
                    rvStoreProducts.visibility = View.VISIBLE
                    layoutPrivateStoreState.visibility = View.GONE
                    btnToggleSubscribe.visibility = View.VISIBLE
                    updateSubscriptionUI()

                    // Fetch Categories
                    allCategories = SupabaseProvider.client.postgrest["categories"]
                        .select {
                            filter { eq("store_id", storeId) }
                        }
                        .decodeList<CategoryDetailRow>()

                    // Fetch Public Products
                    allProducts = SupabaseProvider.client.postgrest["products"]
                        .select {
                            filter {
                                eq("store_id", storeId)
                                eq("is_public", true)
                            }
                        }
                        .decodeList<ProductDetailRow>()

                    // Populate rawProducts
                    val categoryMap = allCategories.associateBy { it.id }
                    rawProducts = allProducts.map { prod ->
                        val categoryName = categoryMap[prod.category_id]?.name ?: "PRICELIST"
                        Product(
                            id = prod.id,
                            name = prod.name,
                            description = prod.description ?: "",
                            price = prod.price,
                            volume = prod.unit ?: "",
                            category = categoryName,
                            is_public = prod.is_public
                        )
                    }

                    // Build Spinner Categories
                    spinnerCategories.clear()
                    spinnerCategories.add("PRICELIST")
                    val uniqueCategories = rawProducts.map { it.category }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
                    spinnerCategories.addAll(uniqueCategories)
                    spinnerAdapter.notifyDataSetChanged()

                    // Apply initial category redirect if matches
                    val filterCat = initialCategory
                    if (filterCat != null) {
                        val index = spinnerCategories.indexOfFirst { it.equals(filterCat, ignoreCase = true) }
                        if (index != -1) {
                            categorySpinner.setSelection(index)
                            selectedCategory = spinnerCategories[index]
                            categoryLabel.text = selectedCategory
                        }
                    }

                    filterAndGroupProducts()
                }

            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Toast.makeText(this@StoreViewActivity, "Failed to load store content: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                shimmerContainer.stopShimmer()
                shimmerContainer.visibility = View.GONE
                if (isPrivateAndNotSubscribed) {
                    rvStoreProducts.visibility = View.GONE
                    layoutPricelistTrigger.visibility = View.GONE
                    layoutPrivateStoreState.visibility = View.VISIBLE
                    btnToggleSubscribe.visibility = View.GONE
                    btnUnsubscribe.visibility = View.GONE
                } else {
                    rvStoreProducts.visibility = View.VISIBLE
                    layoutPrivateStoreState.visibility = View.GONE
                    btnToggleSubscribe.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateSubscriptionUI() {
        val orangeColor = ContextCompat.getColor(this, R.color.presyo_orange)
        val greyColor = Color.parseColor("#94A3B8")

        if (isSubscribed) {
            ivSubscribeIcon.setColorFilter(orangeColor)
            btnUnsubscribe.visibility = View.VISIBLE
        } else {
            ivSubscribeIcon.setColorFilter(greyColor)
            btnUnsubscribe.visibility = View.GONE
        }
    }

    private fun subscribeToStore() {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return

        lifecycleScope.launch {
            try {
                val insertRow = mapOf(
                    "user_id" to userId,
                    "store_id" to storeId,
                    "status" to "active"
                )
                SupabaseProvider.client.postgrest["suki_relationships"].insert(insertRow)
                Toast.makeText(this@StoreViewActivity, "Successfully added to your Suki stores!", Toast.LENGTH_SHORT).show()
                isSubscribed = true
                updateSubscriptionUI()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@StoreViewActivity, "Subscription failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUnsubscribeDialog() {
        showReusableDialog(
            title = if (isPresyohan) "Remove Presyohan" else "Remove Suking Tindahan",
            message = if (isPresyohan) {
                "Are you sure you want to remove this presyohan from your list?\n\nYou will lose quick access to its reference price guides, but you can add it back anytime."
            } else {
                "Are you sure you want to remove this suking tindahan?\n\nYou will no longer be a suki to \"$storeName\" and lose access to their prices."
            },
            positiveButtonText = "Remove",
            positiveAction = {
                unsubscribeFromStore()
            },
            negativeButtonText = "Cancel",
            negativeAction = {
                // Do nothing
            }
        )
    }

    private fun showStoreDetailsDialog() {
        val meta = storeMetadata ?: return
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_details_customer, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set Dialog Width to 90% of Screen
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val txtStoreName = view.findViewById<TextView>(R.id.dialogStoreName)
        val txtStoreBranch = view.findViewById<TextView>(R.id.dialogStoreBranch)
        val txtStoreCategory = view.findViewById<TextView>(R.id.dialogStoreCategory)
        val dialogStoreIcon = view.findViewById<ImageView>(R.id.dialogStoreIcon)

        txtStoreName.text = meta.name
        txtStoreBranch.text = meta.branch ?: "Main Branch"
        txtStoreCategory.text = meta.type ?: "General Store"

        if (meta.is_standard_store) {
            dialogStoreIcon.setImageResource(R.drawable.icon_presyohan)
            dialogStoreIcon.rotation = -45f
        } else {
            dialogStoreIcon.setImageResource(R.drawable.icon_store)
            dialogStoreIcon.rotation = 0f
        }

        val txtCategoriesCount = view.findViewById<TextView>(R.id.dialogCategoriesCount)
        val txtItemsCount = view.findViewById<TextView>(R.id.dialogItemsCount)
        val txtSukiCount = view.findViewById<TextView>(R.id.dialogSukiCount)
        val txtStoreId = view.findViewById<TextView>(R.id.dialogStoreId)
        val txtCreatedAt = view.findViewById<TextView>(R.id.dialogCreatedAt)
        val txtVisibilityStatus = view.findViewById<TextView>(R.id.dialogVisibilityStatus)

        // Set BACK button action
        val btnBack = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            dialog.dismiss()
        }

        // Set local info
        txtStoreId.text = meta.display_id ?: meta.id
        txtCategoriesCount.text = allCategories.size.toString()
        txtItemsCount.text = allProducts.size.toString()

        val shimmerLayout = view.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.dialogShimmerLayout)
        val statsTable = view.findViewById<android.widget.TableLayout>(R.id.dialogStatsTable)

        shimmerLayout.startShimmer()
        shimmerLayout.visibility = android.view.View.VISIBLE
        statsTable.visibility = android.view.View.GONE

        lifecycleScope.launch {
            try {
                // Fetch suki relationships count for this store
                @Serializable
                data class SukiRow(val store_id: String)
                val sukiRows = SupabaseProvider.client.postgrest["suki_relationships"].select {
                    filter { eq("store_id", meta.id) }
                }.decodeList<SukiRow>()
                txtSukiCount.text = sukiRows.size.toString()

                val rawDate = meta.created_at?.split("T")?.firstOrNull() ?: ""
                val dateText = try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val parsedDate = inputFormat.parse(rawDate)
                    if (parsedDate != null) {
                        val outputFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
                        outputFormat.format(parsedDate)
                    } else {
                        rawDate
                    }
                } catch (_: Exception) {
                    rawDate
                }
                txtCreatedAt.text = dateText
                txtVisibilityStatus.text = if (meta.is_public) "Public" else "Private"
            } catch (e: Exception) {
                Log.e("StoreViewActivity", "Failed to load store metadata in dialog", e)
            } finally {
                shimmerLayout.stopShimmer()
                shimmerLayout.visibility = android.view.View.GONE
                statsTable.visibility = android.view.View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun unsubscribeFromStore() {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return

        lifecycleScope.launch {
            try {
                SupabaseProvider.client.postgrest["suki_relationships"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("store_id", storeId)
                    }
                }
                Toast.makeText(this@StoreViewActivity, "Removed store from your dashboard.", Toast.LENGTH_SHORT).show()
                isSubscribed = false
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@StoreViewActivity, "Failed to unsubscribe: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterAndGroupProducts() {
        val query = currentSearchQuery.trim()
        val cat = selectedCategory

        lifecycleScope.launch {
            val sortedGroups = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // Apply category filter
                val catFiltered = if (cat == "PRICELIST") {
                    rawProducts
                } else {
                    rawProducts.filter { it.category.equals(cat, ignoreCase = true) }
                }

                // Apply search query filter using SearchHelper
                val searchFiltered = if (query.isBlank()) {
                    catFiltered.sortedBy { it.name.lowercase(Locale.getDefault()) }
                } else {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    val matchedProducts = catFiltered.filter { prod ->
                        var isMatch = true
                        for (token in tokens) {
                            val matchesName = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, prod.name)
                            val matchesDesc = prod.description?.let { com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, it) } ?: false
                            val matchesCategory = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, prod.category)
                            val matchesPrice = com.presyohan.app.helper.SearchHelper.matchPrice(token, prod.price)
                            
                            if (!matchesName && !matchesDesc && !matchesCategory && !matchesPrice) {
                                isMatch = false
                                break
                            }
                        }
                        isMatch
                    }

                    matchedProducts.map { prod ->
                        val score = com.presyohan.app.helper.SearchHelper.calculateProductScore(
                            query = query,
                            name = prod.name,
                            description = prod.description,
                            categoryName = prod.category
                        )
                        Pair(prod, score)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                }

                // Group by category, sort categories alphabetically, preserve inner order (sorted by score if searched)
                val groupedMap = searchFiltered.groupBy { it.category.trim() }
                groupedMap.entries.sortedBy { it.key.lowercase(Locale.getDefault()) }.map { entry ->
                    com.presyohan.app.HomeActivity.ProductGroup(
                        categoryName = entry.key,
                        itemCount = entry.value.size,
                        products = entry.value
                    )
                }
            }

            groupAdapter.updateGroups(sortedGroups, null)
        }
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.stay,
                R.anim.slide_out_down
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.stay, R.anim.slide_out_down)
        }
    }
}
