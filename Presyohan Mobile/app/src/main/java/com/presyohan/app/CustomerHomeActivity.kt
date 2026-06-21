package com.presyohan.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import coil.transform.CircleCropTransformation
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import android.app.Dialog
import android.view.Window
import com.google.zxing.integration.android.IntentIntegrator
import java.util.Locale

class CustomerHomeActivity : AppCompatActivity() {

    private lateinit var profileIconContainer: View
    private lateinit var profileIcon: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var rvCustomerPrices: RecyclerView
    private lateinit var rvCustomerStores: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var emptyStateMessage: TextView

    // Bottom Navigation Views
    private lateinit var btnTabPrices: View
    private lateinit var btnTabStores: View
    private lateinit var btnNavAdd: View
    private lateinit var pricesIconContainer: View
    private lateinit var pricesIcon: ImageView
    private lateinit var pricesText: TextView
    private lateinit var storesIconContainer: View
    private lateinit var storesIcon: ImageView
    private lateinit var storesText: TextView

    // Bottom Sheet Views
    private lateinit var dimmedBackground: View
    private lateinit var addStoreBottomSheet: android.widget.LinearLayout
    private lateinit var btnBottomSheetAddSuki: View
    private lateinit var btnBottomSheetPresyohan: View



    private var activeAddSukiDialog: Dialog? = null

    // Data lists
    private var allStores: List<StoreDetailRow> = emptyList()
    private var allCategories: List<CategoryDetailRow> = emptyList()
    private var allProducts: List<ProductDetailRow> = emptyList()

    // Screen States
    private var isPricesTabActive = true
    private var currentSearchQuery = ""

    // Adapters
    private lateinit var productAdapter: CustomerProductAdapter
    private lateinit var categoryAdapter: CustomerCategoryAdapter
    private lateinit var storeAdapter: CustomerStoreAdapter

    // Serialization Models
    @Serializable
    data class SukiRelationshipRow(val store_id: String)

    @Serializable
    data class StoreMemberCheckRow(val user_id: String)

    @Serializable
    data class StoreDetailRow(
        val id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val is_public: Boolean = false,
        val display_id: String? = null
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

    // UI Presentation Models
    data class DisplayProduct(
        val id: String,
        val name: String,
        val description: String?,
        val price: Double,
        val unit: String?,
        val storeName: String,
        val storeLocation: String,
        val categoryName: String
    )

    data class DisplayCategory(
        val categoryId: String,
        val categoryName: String,
        val storeId: String,
        val storeName: String,
        val storeLocation: String,
        val itemCount: Int
    )

    data class DisplayStore(
        val id: String,
        val name: String,
        val subtitle: String,
        val location: String,
        val typeTag: String,
        val isPresyohan: Boolean,
        val itemCount: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_home)

        // Initialize Views
        searchEditText = findViewById(R.id.searchEditText)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        rvCustomerPrices = findViewById(R.id.rvCustomerPrices)
        rvCustomerStores = findViewById(R.id.rvCustomerStores)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        emptyStateMessage = findViewById(R.id.emptyStateMessage)
        profileIconContainer = findViewById(R.id.profileIconContainer)
        profileIcon = findViewById(R.id.profileIcon)

        // Bottom Nav
        btnTabPrices = findViewById(R.id.btnTabPrices)
        btnTabStores = findViewById(R.id.btnTabStores)
        btnNavAdd = findViewById(R.id.btnNavAdd)
        pricesIconContainer = findViewById(R.id.pricesIconContainer)
        pricesIcon = findViewById(R.id.pricesIcon)
        pricesText = findViewById(R.id.pricesText)
        storesIconContainer = findViewById(R.id.storesIconContainer)
        storesIcon = findViewById(R.id.storesIcon)
        storesText = findViewById(R.id.storesText)

        // Bottom Sheet
        dimmedBackground = findViewById(R.id.dimmedBackground)
        addStoreBottomSheet = findViewById(R.id.addStoreBottomSheet)
        btnBottomSheetAddSuki = findViewById(R.id.btnBottomSheetAddSuki)
        btnBottomSheetPresyohan = findViewById(R.id.btnBottomSheetPresyohan)



        // Setup Avatar click to Settings
        profileIconContainer.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Setup RecyclerViews
        rvCustomerPrices.layoutManager = LinearLayoutManager(this)
        rvCustomerStores.layoutManager = LinearLayoutManager(this)

        productAdapter = CustomerProductAdapter(emptyList())
        categoryAdapter = CustomerCategoryAdapter(emptyList())
        storeAdapter = CustomerStoreAdapter(emptyList()) { displayStore ->
            // Click store -> switch search query to that store name, or search prices of that store
            currentSearchQuery = displayStore.name
            searchEditText.setText(displayStore.name)
            selectTab(true)
        }

        rvCustomerPrices.adapter = productAdapter
        rvCustomerStores.adapter = storeAdapter

        // Setup Tab Navigation clicks
        btnTabPrices.setOnClickListener { selectTab(true) }
        btnTabStores.setOnClickListener { selectTab(false) }

        // Add Button triggers Bottom Sheet
        btnNavAdd.setOnClickListener { showBottomSheet() }
        dimmedBackground.setOnClickListener { hideBottomSheet() }

        // Bottom Sheet actions
        btnBottomSheetAddSuki.setOnClickListener {
            hideBottomSheet()
            showAddSukiDialog()
        }
        btnBottomSheetPresyohan.setOnClickListener {
            hideBottomSheet()
            Toast.makeText(this, "Presyohan selection under construction", Toast.LENGTH_SHORT).show()
        }



        // Search text watcher
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                filterAndRenderData()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Swipe Refresh
        swipeRefreshLayout.setOnRefreshListener {
            loadCustomerData()
        }

        // Initial setup
        selectTab(true)
        loadCustomerData()
    }

    override fun onResume() {
        super.onResume()
        // Reload in case the user joined a new store
        loadCustomerData()
        loadUserProfile()
    }

    private fun selectTab(isPrices: Boolean) {
        isPricesTabActive = isPrices
        val orangeColor = ContextCompat.getColor(this, R.color.presyo_orange)
        val greyColor = Color.parseColor("#999A9A")

        if (isPrices) {
            // Prices Active
            pricesIcon.setColorFilter(orangeColor)
            pricesText.setTextColor(orangeColor)
            pricesIconContainer.setBackgroundResource(R.drawable.bg_nav_icon_active)

            // Stores Inactive
            storesIcon.setColorFilter(greyColor)
            storesText.setTextColor(greyColor)
            storesIconContainer.background = null

            rvCustomerPrices.visibility = View.VISIBLE
            rvCustomerStores.visibility = View.GONE
        } else {
            // Stores Active
            storesIcon.setColorFilter(orangeColor)
            storesText.setTextColor(orangeColor)
            storesIconContainer.setBackgroundResource(R.drawable.bg_nav_icon_active)

            // Prices Inactive
            pricesIcon.setColorFilter(greyColor)
            pricesText.setTextColor(greyColor)
            pricesIconContainer.background = null

            rvCustomerPrices.visibility = View.GONE
            rvCustomerStores.visibility = View.VISIBLE
        }
        filterAndRenderData()
    }

    private fun showBottomSheet() {
        dimmedBackground.visibility = View.VISIBLE
        addStoreBottomSheet.visibility = View.VISIBLE
        addStoreBottomSheet.post {
            val height = addStoreBottomSheet.height.toFloat()
            addStoreBottomSheet.translationY = height
            addStoreBottomSheet.animate()
                .translationY(0f)
                .setDuration(300)
                .start()
        }
        dimmedBackground.alpha = 0f
        dimmedBackground.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun hideBottomSheet() {
        val height = addStoreBottomSheet.height.toFloat()
        addStoreBottomSheet.animate()
            .translationY(height)
            .setDuration(300)
            .withEndAction {
                addStoreBottomSheet.visibility = View.GONE
                dimmedBackground.visibility = View.GONE
            }
            .start()

        dimmedBackground.animate()
            .alpha(0f)
            .setDuration(300)
            .start()
    }

    private fun showAddSukiDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_suki)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etStoreId = dialog.findViewById<EditText>(R.id.etStoreId)
        val btnDialogEnter = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnDialogEnter)
        val btnDialogScanQr = dialog.findViewById<ImageView>(R.id.btnDialogScanQr)
        val btnDialogBack = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnDialogBack)
        val btnDialogAdd = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnDialogAdd)

        val ivStoreDefaultIcon = dialog.findViewById<ImageView>(R.id.ivStoreDefaultIcon)
        val ivStoreIcon = dialog.findViewById<ImageView>(R.id.ivStoreIcon)
        val tvPrivateBadge = dialog.findViewById<TextView>(R.id.tvPrivateBadge)
        val layoutStoreInfo = dialog.findViewById<View>(R.id.layoutStoreInfo)
        val tvStoreName = dialog.findViewById<TextView>(R.id.tvStoreName)
        val tvStoreType = dialog.findViewById<TextView>(R.id.tvStoreType)
        val tvStoreBranch = dialog.findViewById<TextView>(R.id.tvStoreBranch)

        var resolvedStore: StoreDetailRow? = null

        etStoreId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.trim() ?: ""
                btnDialogEnter.isEnabled = input.length >= 4

                if (resolvedStore != null) {
                    resolvedStore = null
                    btnDialogAdd.isEnabled = false
                    
                    ivStoreDefaultIcon.visibility = View.VISIBLE
                    ivStoreIcon.visibility = View.GONE
                    tvPrivateBadge.visibility = View.GONE
                    layoutStoreInfo.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnDialogEnter.setOnClickListener {
            val rawId = etStoreId.text.toString().trim()
            val normalizedId = normalizeStoreId(rawId)
            
            Toast.makeText(this, "Searching for store...", Toast.LENGTH_SHORT).show()
            
            lifecycleScope.launch {
                try {
                    val store = SupabaseProvider.client.postgrest["stores"]
                        .select {
                            filter {
                                eq("display_id", normalizedId)
                            }
                        }
                        .decodeList<StoreDetailRow>()
                        .firstOrNull()
                        
                    if (store != null) {
                        val currentUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                        val isMember = if (currentUserId != null) {
                            try {
                                val members = SupabaseProvider.client.postgrest["store_members"]
                                    .select {
                                        filter {
                                            eq("store_id", store.id)
                                            eq("user_id", currentUserId)
                                        }
                                    }
                                    .decodeList<StoreMemberCheckRow>()
                                members.isNotEmpty()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            }
                        } else {
                            false
                        }

                        if (isMember) {
                            Toast.makeText(this@CustomerHomeActivity, "You are a member or owner of this store. Staff members cannot partner with their own stores.", Toast.LENGTH_LONG).show()
                            
                            ivStoreDefaultIcon.visibility = View.VISIBLE
                            ivStoreIcon.visibility = View.GONE
                            layoutStoreInfo.visibility = View.GONE
                            tvPrivateBadge.visibility = View.GONE
                            btnDialogAdd.isEnabled = false
                            resolvedStore = null
                        } else {
                            resolvedStore = store
                            
                            ivStoreDefaultIcon.visibility = View.GONE
                            ivStoreIcon.visibility = View.VISIBLE
                            layoutStoreInfo.visibility = View.VISIBLE
                            
                            tvStoreName.text = store.name
                            tvStoreType.text = store.type ?: "General Merchandise"
                            tvStoreBranch.text = store.branch ?: "Main Branch"
                            
                            tvPrivateBadge.visibility = if (!store.is_public) View.VISIBLE else View.GONE
                            
                            btnDialogAdd.isEnabled = true
                        }
                    } else {
                        Toast.makeText(this@CustomerHomeActivity, "Store ID not found.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@CustomerHomeActivity, "Search error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnDialogScanQr.setOnClickListener {
            activeAddSukiDialog = dialog
            val integrator = IntentIntegrator(this@CustomerHomeActivity)
            integrator.setPrompt("Scan Store QR Code")
            integrator.setOrientationLocked(false)
            integrator.initiateScan()
        }

        btnDialogBack.setOnClickListener {
            dialog.dismiss()
        }

        btnDialogAdd.setOnClickListener {
            val store = resolvedStore
            val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
            if (store != null && userId != null) {
                lifecycleScope.launch {
                    try {
                        val insertRow = mapOf(
                            "user_id" to userId,
                            "store_id" to store.id,
                            "status" to "active"
                        )
                        SupabaseProvider.client.postgrest["suki_relationships"].insert(insertRow)
                        Toast.makeText(this@CustomerHomeActivity, "Successfully added to your Suki stores!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadCustomerData()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val msg = e.localizedMessage ?: ""
                        if (msg.contains("suki_user_store_unique")) {
                            Toast.makeText(this@CustomerHomeActivity, "You are already suki with this store!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CustomerHomeActivity, "Failed to partner: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun normalizeStoreId(input: String): String {
        val trimmed = input.trim().uppercase()
        val regex = Regex("^SID(\\d{2})-(\\d+)$")
        val matchResult = regex.matchEntire(trimmed)
        if (matchResult != null) {
            val year = matchResult.groupValues[1]
            val numStr = matchResult.groupValues[2]
            val paddedNum = numStr.padStart(4, '0')
            return "SID$year-$paddedNum"
        }
        return trimmed
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Scanning cancelled", Toast.LENGTH_SHORT).show()
            } else {
                val scannedContent = result.contents
                val displayIdRegex = Regex("SID\\d{2}-\\d+")
                val match = displayIdRegex.find(scannedContent.uppercase())
                val extractedId = match?.value ?: scannedContent.trim()
                
                val dialog = activeAddSukiDialog
                if (dialog != null && dialog.isShowing) {
                    val etStoreId = dialog.findViewById<EditText>(R.id.etStoreId)
                    val btnDialogEnter = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnDialogEnter)
                    if (etStoreId != null && btnDialogEnter != null) {
                        etStoreId.setText(extractedId)
                        btnDialogEnter.performClick()
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun loadUserProfile() {
        profileIcon.setImageResource(R.drawable.avatar_default)
        profileIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.white))

        lifecycleScope.launch {
            val profile = SupabaseAuthService.getUserProfile()
            if (profile != null && !profile.avatar_url.isNullOrBlank()) {
                profileIcon.clearColorFilter()
                profileIcon.load(profile.avatar_url) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    error(R.drawable.avatar_default)
                }
            }
        }
    }

    private fun loadCustomerData() {
        swipeRefreshLayout.isRefreshing = true
        lifecycleScope.launch {
            try {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                if (userId == null) {
                    swipeRefreshLayout.isRefreshing = false
                    return@launch
                }

                // 1. Fetch suki store links
                val sukiLinks = SupabaseProvider.client.postgrest["suki_relationships"]
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<SukiRelationshipRow>()

                val storeIds = sukiLinks.map { it.store_id }
                if (storeIds.isEmpty()) {
                    allStores = emptyList()
                    allCategories = emptyList()
                    allProducts = emptyList()
                    filterAndRenderData()
                    swipeRefreshLayout.isRefreshing = false
                    return@launch
                }

                // 2. Fetch linked store details
                allStores = SupabaseProvider.client.postgrest["stores"]
                    .select {
                        filter { isIn("id", storeIds) }
                    }
                    .decodeList<StoreDetailRow>()

                // 3. Fetch linked categories
                allCategories = SupabaseProvider.client.postgrest["categories"]
                    .select {
                        filter { isIn("store_id", storeIds) }
                    }
                    .decodeList<CategoryDetailRow>()

                // 4. Fetch public products
                allProducts = SupabaseProvider.client.postgrest["products"]
                    .select {
                        filter {
                            isIn("store_id", storeIds)
                            eq("is_public", true)
                        }
                    }
                    .decodeList<ProductDetailRow>()

                filterAndRenderData()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CustomerHomeActivity, "Error loading data: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun filterAndRenderData() {
        val query = currentSearchQuery.lowercase(Locale.getDefault())

        if (isPricesTabActive) {
            // Render Prices / Categories
            val storeMap = allStores.associateBy { it.id }
            val categoryMap = allCategories.associateBy { it.id }

            // Base list of displayed products
            val baseProducts = allProducts.map { prod ->
                val store = storeMap[prod.store_id]
                val category = categoryMap[prod.category_id]
                DisplayProduct(
                    id = prod.id,
                    name = prod.name,
                    description = prod.description,
                    price = prod.price,
                    unit = prod.unit,
                    storeName = store?.name ?: "Unknown Store",
                    storeLocation = store?.branch ?: "Main Branch",
                    categoryName = category?.name ?: "PRICELIST"
                )
            }

            // Filter base products by search query
            val filteredProducts = if (query.isEmpty()) {
                baseProducts
            } else {
                baseProducts.filter {
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                            (it.description?.lowercase(Locale.getDefault())?.contains(query) == true) ||
                            it.categoryName.lowercase(Locale.getDefault()).contains(query) ||
                            it.storeName.lowercase(Locale.getDefault()).contains(query)
                }
            }

            productAdapter.updateList(filteredProducts)
            toggleEmptyState(filteredProducts.isEmpty(), "No products found matching \"$currentSearchQuery\"")
        } else {
            // Render Stores Tab
            val displayStores = allStores.map { store ->
                val productCount = allProducts.count { it.store_id == store.id }
                val isPresyohan = store.type?.lowercase(Locale.getDefault()) == "presyohan"
                val typeTag = if (isPresyohan) {
                    "Presyohan"
                } else if (store.is_public) {
                    "Public"
                } else {
                    "Private"
                }

                DisplayStore(
                    id = store.id,
                    name = store.name,
                    subtitle = store.type ?: "General Store",
                    location = store.branch ?: "Main Branch",
                    typeTag = typeTag,
                    isPresyohan = isPresyohan,
                    itemCount = productCount
                )
            }

            // Filter stores by search query
            val filteredStores = if (query.isEmpty()) {
                displayStores
            } else {
                displayStores.filter {
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                            it.location.lowercase(Locale.getDefault()).contains(query) ||
                            it.subtitle.lowercase(Locale.getDefault()).contains(query)
                }
            }

            storeAdapter.updateList(filteredStores)
            toggleEmptyState(filteredStores.isEmpty(), "No stores found matching \"$currentSearchQuery\"")
        }
    }

    private fun toggleEmptyState(isEmpty: Boolean, message: String) {
        if (isEmpty) {
            layoutEmptyState.visibility = View.VISIBLE
            emptyStateMessage.text = message
        } else {
            layoutEmptyState.visibility = View.GONE
        }
    }

    // --- RECYCLERVIEW ADAPTERS ---

    // Product Adapter
    private class CustomerProductAdapter(private var items: List<DisplayProduct>) :
        RecyclerView.Adapter<CustomerProductAdapter.ViewHolder>() {

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvStoreNameBranch: TextView = view.findViewById(R.id.tvStoreNameBranch)
            val tvProductCategory: TextView = view.findViewById(R.id.tvProductCategory)
            val tvProductName: TextView = view.findViewById(R.id.tvProductName)
            val tvProductDescription: TextView = view.findViewById(R.id.tvProductDescription)
            val tvProductUnits: TextView = view.findViewById(R.id.tvProductUnits)
            val tvProductPrice: TextView = view.findViewById(R.id.tvProductPrice)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_product, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvStoreNameBranch.text = "${item.storeName} - ${item.storeLocation}"
            holder.tvProductCategory.text = item.categoryName.uppercase(Locale.getDefault())
            holder.tvProductName.text = item.name
            holder.tvProductDescription.text = item.description ?: ""
            holder.tvProductDescription.visibility = if (item.description.isNullOrBlank()) View.GONE else View.VISIBLE
            holder.tvProductUnits.text = item.unit ?: ""
            holder.tvProductUnits.visibility = if (item.unit.isNullOrBlank()) View.GONE else View.VISIBLE
            holder.tvProductPrice.text = String.format("₱ %.2f", item.price)
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<DisplayProduct>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    // Category Adapter
    private class CustomerCategoryAdapter(private var items: List<DisplayCategory>) :
        RecyclerView.Adapter<CustomerCategoryAdapter.ViewHolder>() {

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvCategoryName: TextView = view.findViewById(R.id.tvCategoryName)
            val tvCategoryStoreName: TextView = view.findViewById(R.id.tvCategoryStoreName)
            val tvCategoryLocation: TextView = view.findViewById(R.id.tvCategoryLocation)
            val tvCategoryItemCount: TextView = view.findViewById(R.id.tvCategoryItemCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvCategoryName.text = item.categoryName.uppercase(Locale.getDefault())
            holder.tvCategoryStoreName.text = item.storeName
            holder.tvCategoryLocation.text = item.storeLocation
            holder.tvCategoryItemCount.text = "${item.itemCount} Items"
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<DisplayCategory>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    // Store Adapter
    private class CustomerStoreAdapter(
        private var items: List<DisplayStore>,
        private val onStoreClick: (DisplayStore) -> Unit
    ) : RecyclerView.Adapter<CustomerStoreAdapter.ViewHolder>() {

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvStoreTypeTag: TextView = view.findViewById(R.id.tvStoreTypeTag)
            val storeIconContainer: FrameLayout = view.findViewById(R.id.storeIconContainer)
            val ivStoreIcon: ImageView = view.findViewById(R.id.ivStoreIcon)
            val tvStoreName: TextView = view.findViewById(R.id.tvStoreName)
            val tvStoreSubtitle: TextView = view.findViewById(R.id.tvStoreSubtitle)
            val tvStoreLocation: TextView = view.findViewById(R.id.tvStoreLocation)
            val tvStoreItemCount: TextView = view.findViewById(R.id.tvStoreItemCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_store, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvStoreTypeTag.text = item.typeTag
            holder.tvStoreName.text = item.name
            holder.tvStoreSubtitle.text = item.subtitle
            holder.tvStoreLocation.text = item.location
            holder.tvStoreItemCount.text = "${item.itemCount} Items"

            if (item.isPresyohan) {
                holder.storeIconContainer.setBackgroundResource(R.drawable.bg_asymmetric_presyohan)
                holder.ivStoreIcon.setImageResource(R.drawable.icon_presyohan_parser)
            } else {
                holder.storeIconContainer.setBackgroundResource(R.drawable.bg_rounded_store_icon)
                holder.ivStoreIcon.setImageResource(R.drawable.icon_store)
            }

            holder.view.setOnClickListener {
                onStoreClick(item)
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<DisplayStore>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
