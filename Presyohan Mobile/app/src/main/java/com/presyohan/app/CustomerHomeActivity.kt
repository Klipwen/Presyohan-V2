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
import android.widget.LinearLayout
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import android.app.Dialog
import io.github.jan.supabase.postgrest.query.Columns
import android.view.Window
import com.google.zxing.integration.android.IntentIntegrator
import java.util.Locale
import com.presyohan.app.helper.ISwipeAdapter
import com.presyohan.app.helper.ISwipeViewHolder
import com.presyohan.app.helper.SwipeRevealTouchHelper
import android.util.Log
import java.text.SimpleDateFormat

class CustomerHomeActivity : AppCompatActivity() {

    private lateinit var profileIconContainer: View
    private lateinit var profileIcon: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var rvCustomerPrices: RecyclerView
    private lateinit var rvCustomerStores: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var emptyStateMessage: TextView
    private lateinit var shimmerPricesContainer: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var shimmerStoresContainer: com.facebook.shimmer.ShimmerFrameLayout

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
    private var baseProducts: List<DisplayProduct> = emptyList()
    private var storeProductCounts: Map<String, Int> = emptyMap()
    private var searchJob: kotlinx.coroutines.Job? = null

    // Screen States
    private var isPricesTabActive = false
    private var currentSearchQuery = ""
    private var lastBackPress: Long = 0
    private var hasAutoOpenedBottomSheet = false

    // Adapters
    private lateinit var searchAdapter: CustomerSearchAdapter
    private lateinit var storeAdapter: CustomerStoreAdapter

    // Serialization Models
    @Serializable
    data class SukiRelationshipRow(val store_id: String, val status: String = "active")

    @Serializable
    data class StoreMemberCheckRow(val user_id: String)

    @Serializable
    data class StoreMemberRoleRow(val user_id: String, val role: String)

    @Serializable
    data class StoreProductCountRow(
        val store_id: String,
        val total_count: Int,
        val public_count: Int
    )

    @Serializable
    data class StoreDetailRow(
        val id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val is_public: Boolean = false,
        val is_standard_store: Boolean = false,
        val display_id: String? = null,
        val owner_id: String? = null
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
        val storeId: String,
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
        val itemCount: Int,
        val displayId: String? = null
    )

    private class StoreSearchIndex(
        val storeDetails: List<String>,
        val contentStrings: List<String>,
        val productPrices: List<Double>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_home)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_up,
                R.anim.stay
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_up, R.anim.stay)
        }

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

        // Initialize Views
        searchEditText = findViewById(R.id.searchEditText)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        rvCustomerPrices = findViewById(R.id.rvCustomerPrices)
        rvCustomerStores = findViewById(R.id.rvCustomerStores)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        emptyStateMessage = findViewById(R.id.emptyStateMessage)
        profileIconContainer = findViewById(R.id.profileIconContainer)
        profileIcon = findViewById(R.id.profileIcon)

        // Apply dynamic top padding to headerPresyohan matching status bar height
        val headerPresyohan = findViewById<View>(R.id.headerPresyohan)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerPresyohan) { view, insets ->
            val statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                statusBarHeight + (4 * resources.displayMetrics.density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

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

        // Shimmer Layouts
        shimmerPricesContainer = findViewById(R.id.shimmerPricesContainer)
        shimmerStoresContainer = findViewById(R.id.shimmerStoresContainer)



        // Setup Avatar click to Settings
        profileIconContainer.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("from_side", "customer")
            startActivity(intent)
        }

        // Setup notification icon click to NotificationActivity
        findViewById<View>(R.id.notifIcon).setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        // Setup RecyclerViews
        rvCustomerPrices.layoutManager = LinearLayoutManager(this)
        rvCustomerStores.layoutManager = LinearLayoutManager(this)

        searchAdapter = CustomerSearchAdapter(
            items = emptyList(),
            onCategoryClick = { displayCategory ->
                val intent = Intent(this, StoreViewActivity::class.java).apply {
                    putExtra("STORE_ID", displayCategory.storeId)
                    putExtra("STORE_NAME", displayCategory.storeName)
                    putExtra("STORE_BRANCH", displayCategory.storeLocation)
                    val store = allStores.find { it.id == displayCategory.storeId }
                    putExtra("STORE_TYPE", store?.type ?: "General Store")
                    putExtra("IS_PRESYOHAN", store?.is_standard_store ?: false)
                    putExtra("FILTER_CATEGORY", displayCategory.categoryName)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_up,
                    R.anim.stay
                )
                startActivity(intent, options.toBundle())
            },
            onProductCategoryClick = { product ->
                val intent = Intent(this, StoreViewActivity::class.java).apply {
                    val store = allStores.find { it.id == product.storeId }
                    putExtra("STORE_ID", product.storeId)
                    putExtra("STORE_NAME", product.storeName)
                    putExtra("STORE_BRANCH", product.storeLocation)
                    putExtra("STORE_TYPE", store?.type ?: "General Store")
                    putExtra("IS_PRESYOHAN", store?.is_standard_store ?: false)
                    putExtra("FILTER_CATEGORY", product.categoryName)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_up,
                    R.anim.stay
                )
                startActivity(intent, options.toBundle())
            },
            onStoreClick = { displayStore ->
                val intent = Intent(this, StoreViewActivity::class.java).apply {
                    putExtra("STORE_ID", displayStore.id)
                    putExtra("STORE_NAME", displayStore.name)
                    putExtra("STORE_BRANCH", displayStore.location)
                    putExtra("STORE_TYPE", displayStore.subtitle)
                    putExtra("IS_PRESYOHAN", displayStore.isPresyohan)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_up,
                    R.anim.stay
                )
                startActivity(intent, options.toBundle())
            },
            onMoreClick = { store ->
                searchEditText.setText("${store.name} Categories: ")
                searchEditText.setSelection(searchEditText.text.length)
            },
            onViewAllClick = {
                searchEditText.setText("Category: ")
                searchEditText.setSelection(searchEditText.text.length)
            }
        )
        storeAdapter = CustomerStoreAdapter(
            emptyList(),
            onStoreClick = { displayStore ->
                val intent = Intent(this, StoreViewActivity::class.java).apply {
                    putExtra("STORE_ID", displayStore.id)
                    putExtra("STORE_NAME", displayStore.name)
                    putExtra("STORE_BRANCH", displayStore.location)
                    putExtra("STORE_TYPE", displayStore.subtitle)
                    putExtra("IS_PRESYOHAN", displayStore.isPresyohan)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_up,
                    R.anim.stay
                )
                startActivity(intent, options.toBundle())
            },
            onViewClick = { displayStore ->
                showStoreDetailsDialog(displayStore)
            },
            onDeleteClick = { displayStore ->
                confirmRemoveStore(displayStore)
            }
        )

        rvCustomerPrices.adapter = searchAdapter
        rvCustomerStores.adapter = storeAdapter

        val swipeHelper = SwipeRevealTouchHelper(storeAdapter)
        androidx.recyclerview.widget.ItemTouchHelper(swipeHelper).attachToRecyclerView(rvCustomerStores)

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
            val intent = Intent(this, SelectPresyohanActivity::class.java)
            val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_up,
                R.anim.stay
            )
            startActivity(intent, options.toBundle())
        }



        val btnClearSearch = findViewById<ImageView>(R.id.btnClearSearch)
        btnClearSearch.setOnClickListener {
            searchEditText.setText("")
        }

        // Search text watcher with debounce
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (currentSearchQuery.isEmpty()) View.GONE else View.VISIBLE
                
                if (currentSearchQuery.isEmpty()) {
                    findViewById<View>(R.id.layoutSearchLoading).visibility = View.GONE
                    rvCustomerPrices.visibility = if (isPricesTabActive) View.VISIBLE else View.GONE
                    rvCustomerStores.visibility = if (isPricesTabActive) View.GONE else View.VISIBLE
                    filterAndRenderData(delayMillis = 0L)
                } else {
                    findViewById<View>(R.id.layoutSearchLoading).visibility = View.VISIBLE
                    rvCustomerPrices.visibility = View.GONE
                    rvCustomerStores.visibility = View.GONE
                    filterAndRenderData(delayMillis = 1400L)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Swipe Refresh
        swipeRefreshLayout.setColorSchemeResources(R.color.presyo_orange)
        swipeRefreshLayout.setOnRefreshListener {
            loadCustomerData(showShimmer = false)
            loadUserProfile()
        }

        // Initial setup
        selectTab(true)
        loadCustomerData(showShimmer = true)
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markCustomerHome(this)
        // Reload in case the user joined a new store
        loadCustomerData(showShimmer = true)
        loadUserProfile()

        // Handle pending onboarding actions
        val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
        val pendingAction = prefs.getString("onboarding_action_pending", null)
        if (pendingAction != null) {
            prefs.edit().remove("onboarding_action_pending").apply()
            if (pendingAction == "add_suki") {
                showAddSukiDialog()
            }
        }

        // Handle standard standard presyohan redirection to stores tab
        val redirectToStores = prefs.getBoolean("redirect_to_stores_tab", false)
        if (redirectToStores) {
            prefs.edit().remove("redirect_to_stores_tab").apply()
            selectTab(false) // Switch to Stores tab!
        }
        ReusableDialogHelper.checkAndShowBroadcast(this, lifecycleScope)
    }

    private fun scrollToTopWithLimit(recyclerView: RecyclerView) {
        recyclerView.smoothScrollToPosition(0)
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1200)
            recyclerView.scrollToPosition(0)
        }
    }

    override fun onBackPressed() {
        if (currentSearchQuery.isNotEmpty()) {
            searchEditText.setText("")
            return
        }
        if (!isPricesTabActive) {
            selectTab(true)
            return
        }
        if (isPricesTabActive) {
            val layoutManager = rvCustomerPrices.layoutManager as? LinearLayoutManager
            val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: 0
            if (firstVisible > 0) {
                scrollToTopWithLimit(rvCustomerPrices)
                return
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            lastBackPress = now
            Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectTab(isPrices: Boolean) {
        if (isPricesTabActive == isPrices) {
            if (isPrices) {
                scrollToTopWithLimit(rvCustomerPrices)
            } else {
                rvCustomerStores.smoothScrollToPosition(0)
            }
            return
        }
        searchJob?.cancel()
        isPricesTabActive = isPrices
        val orangeColor = ContextCompat.getColor(this, R.color.presyo_orange)
        val greyColor = Color.parseColor("#999A9A")

        if (shimmerPricesContainer.visibility == View.VISIBLE || shimmerStoresContainer.visibility == View.VISIBLE) {
            if (isPrices) {
                shimmerPricesContainer.visibility = View.VISIBLE
                shimmerPricesContainer.startShimmer()
                shimmerStoresContainer.visibility = View.GONE
                shimmerStoresContainer.stopShimmer()
            } else {
                shimmerStoresContainer.visibility = View.VISIBLE
                shimmerStoresContainer.startShimmer()
                shimmerPricesContainer.visibility = View.GONE
                shimmerPricesContainer.stopShimmer()
            }
        }

        val activePadding = (11 * resources.displayMetrics.density).toInt()
        val inactivePadding = (14 * resources.displayMetrics.density).toInt()

        if (isPrices) {
            // Prices Active
            pricesIcon.setColorFilter(orangeColor)
            pricesText.setTextColor(orangeColor)
            pricesIconContainer.setBackgroundResource(R.drawable.bg_nav_icon_active)
            pricesIconContainer.setPadding(activePadding, activePadding, activePadding, activePadding)

            // Stores Inactive
            storesIcon.setColorFilter(greyColor)
            storesText.setTextColor(greyColor)
            storesIconContainer.setBackgroundResource(R.drawable.bg_nav_icon_inactive)
            storesIconContainer.setPadding(inactivePadding, inactivePadding, inactivePadding, inactivePadding)

            rvCustomerPrices.visibility = if (shimmerPricesContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            rvCustomerStores.visibility = View.GONE

            searchEditText.hint = "Search Item.."
        } else {
            // Stores Active
            storesIcon.setColorFilter(orangeColor)
            storesText.setTextColor(orangeColor)
            storesIconContainer.setBackgroundResource(R.drawable.bg_nav_icon_active)
            storesIconContainer.setPadding(activePadding, activePadding, activePadding, activePadding)

            // Prices Inactive
            pricesIcon.setColorFilter(greyColor)
            pricesText.setTextColor(greyColor)
            pricesIconContainer.setBackgroundResource(R.drawable.bg_nav_icon_inactive)
            pricesIconContainer.setPadding(inactivePadding, inactivePadding, inactivePadding, inactivePadding)

            rvCustomerPrices.visibility = View.GONE
            rvCustomerStores.visibility = if (shimmerStoresContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE

            // Clear inputted search when user navigates to stores tab
            searchEditText.setText("")
            currentSearchQuery = ""

            searchEditText.hint = "Search Store..."
        }
        filterAndRenderData(delayMillis = 0)
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

    class SukiStoreSearchAdapter(
        private var stores: List<StoreDetailRow>,
        private val onSelectionChanged: (List<StoreDetailRow>) -> Unit
    ) : RecyclerView.Adapter<SukiStoreSearchAdapter.ViewHolder>() {
        private val selectedStoreIds = mutableSetOf<String>()

        fun getSelectedStores(): List<StoreDetailRow> {
            return stores.filter { it.id in selectedStoreIds }
        }

        fun updateData(newStores: List<StoreDetailRow>) {
            stores = newStores
            selectedStoreIds.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suki_store_result, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = stores.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val store = stores[position]
            holder.bind(store, store.id in selectedStoreIds)
            holder.itemView.setOnClickListener {
                if (store.id in selectedStoreIds) {
                    selectedStoreIds.remove(store.id)
                } else {
                    selectedStoreIds.add(store.id)
                }
                notifyItemChanged(holder.adapterPosition)
                onSelectionChanged(getSelectedStores())
            }
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvStoreName = itemView.findViewById<TextView>(R.id.tvStoreName)
            private val tvStoreType = itemView.findViewById<TextView>(R.id.tvStoreType)
            private val tvStoreBranch = itemView.findViewById<TextView>(R.id.tvStoreBranch)
            private val tvPrivateBadge = itemView.findViewById<TextView>(R.id.tvPrivateBadge)
            private val ivSelectIndicator = itemView.findViewById<ImageView>(R.id.ivSelectIndicator)

            fun bind(store: StoreDetailRow, isSelected: Boolean) {
                tvStoreName.text = store.name
                tvStoreType.text = store.type ?: "General Merchandise"
                tvStoreBranch.text = store.branch ?: "Main Branch"
                
                tvPrivateBadge.visibility = if (!store.is_public) View.VISIBLE else View.GONE
                
                itemView.setBackgroundResource(R.drawable.bg_card_unselected)
                ivSelectIndicator.imageTintList = null
                if (isSelected) {
                    ivSelectIndicator.setImageResource(R.drawable.ic_radio_checked_orange)
                } else {
                    ivSelectIndicator.setImageResource(R.drawable.ic_radio_unchecked)
                }
            }
        }
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

        val rvStoreResults = dialog.findViewById<RecyclerView>(R.id.rvStoreResults)
        val layoutPlaceholder = dialog.findViewById<View>(R.id.layoutPlaceholder)
        val tvPlaceholderText = dialog.findViewById<TextView>(R.id.tvPlaceholderText)

        rvStoreResults.layoutManager = LinearLayoutManager(this)
        val resultsAdapter = SukiStoreSearchAdapter(emptyList()) { selectedList ->
            btnDialogAdd.isEnabled = selectedList.isNotEmpty()
        }
        rvStoreResults.adapter = resultsAdapter

        etStoreId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.trim() ?: ""
                btnDialogEnter.isEnabled = input.length >= 2
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnDialogEnter.setOnClickListener {
            val rawId = etStoreId.text.toString().trim()
            val queryPattern = "%${rawId}%"
            
            Toast.makeText(this, "Searching for store...", Toast.LENGTH_SHORT).show()
            
            lifecycleScope.launch {
                try {
                    val stores = SupabaseProvider.client.postgrest["stores"]
                        .select(Columns.list("id", "name", "branch", "type", "is_public", "is_standard_store", "display_id")) {
                            filter {
                                eq("is_public", true)
                                or {
                                    ilike("name", queryPattern)
                                    eq("display_id", rawId.uppercase())
                                }
                            }
                        }
                        .decodeList<StoreDetailRow>()
                        
                    val filtered = stores.filter { !it.is_standard_store }
                    val hasStandardStoresExcluded = stores.any { it.is_standard_store }

                    runOnUiThread {
                        if (filtered.isEmpty()) {
                            if (hasStandardStoresExcluded) {
                                tvPlaceholderText?.text = "Store not found (standard comparator stores cannot be partnered)."
                            } else {
                                tvPlaceholderText?.text = "No store found in our system"
                            }
                            resultsAdapter.updateData(emptyList())
                            layoutPlaceholder.visibility = View.VISIBLE
                            rvStoreResults.visibility = View.GONE
                            btnDialogAdd.isEnabled = false
                        } else {
                            resultsAdapter.updateData(filtered)
                            layoutPlaceholder.visibility = View.GONE
                            rvStoreResults.visibility = View.VISIBLE
                            btnDialogAdd.isEnabled = false // Wait for selection

                            val density = resources.displayMetrics.density
                            val layoutParams = rvStoreResults.layoutParams
                            if (filtered.size >= 3) {
                                layoutParams.height = (190 * density).toInt()
                            } else {
                                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                            rvStoreResults.layoutParams = layoutParams
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@CustomerHomeActivity, "Search error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnDialogScanQr.setOnClickListener {
            activeAddSukiDialog = dialog
            val integrator = IntentIntegrator(this@CustomerHomeActivity)
            integrator.setPrompt("Scan Store QR Code")
            integrator.setOrientationLocked(false)
            integrator.setCaptureActivity(CustomScannerActivity::class.java)
            integrator.initiateScan()
        }

        btnDialogBack.setOnClickListener {
            dialog.dismiss()
        }

        btnDialogAdd.setOnClickListener {
            val selectedStores = resultsAdapter.getSelectedStores()
            val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
            if (selectedStores.isNotEmpty() && userId != null) {
                lifecycleScope.launch {
                    var successCount = 0
                    val failMessages = mutableListOf<String>()

                    for (store in selectedStores) {
                        try {
                            val isMember = try {
                                val members = SupabaseProvider.client.postgrest["store_members"]
                                    .select {
                                        filter {
                                            eq("store_id", store.id)
                                            eq("user_id", userId)
                                        }
                                    }
                                    .decodeList<StoreMemberCheckRow>()
                                members.isNotEmpty()
                            } catch (e: Exception) {
                                false
                            }

                            if (isMember) {
                                failMessages.add("${store.name}: Staff members cannot partner with their own stores.")
                                continue
                            }

                            val existing = try {
                                SupabaseProvider.client.postgrest["suki_relationships"]
                                    .select {
                                        filter {
                                            eq("user_id", userId)
                                            eq("store_id", store.id)
                                        }
                                    }
                                    .decodeList<SukiRelationshipRow>()
                            } catch (e: Exception) {
                                emptyList()
                            }

                            if (existing.isNotEmpty()) {
                                val status = existing.first().status
                                if (status == "active") {
                                    failMessages.add("${store.name}: Already a Suki.")
                                } else if (status == "pending") {
                                    failMessages.add("${store.name}: Partnership request is already pending.")
                                }
                                continue
                            }

                            val insertRow = mapOf(
                                "user_id" to userId,
                                "store_id" to store.id,
                                "status" to "pending"
                            )
                            SupabaseProvider.client.postgrest["suki_relationships"].insert(insertRow)

                            val senderProfile = try { SupabaseAuthService.getUserProfile() } catch (e: Exception) { null }
                            val senderName = senderProfile?.name ?: "A user"

                            val members = try {
                                SupabaseProvider.client.postgrest["store_members"]
                                    .select(Columns.list("user_id", "role")) {
                                        filter {
                                            eq("store_id", store.id)
                                            isIn("role", listOf("owner", "manager"))
                                        }
                                    }
                                    .decodeList<StoreMemberRoleRow>()
                            } catch (e: Exception) {
                                emptyList()
                            }

                            val receivers = (members.map { it.user_id } + store.owner_id.orEmpty()).filter { it.isNotBlank() }.distinct()

                            for (receiverId in receivers) {
                                if (receiverId.isNotBlank()) {
                                    SupabaseProvider.client.postgrest["notifications"].insert(
                                        buildJsonObject {
                                            put("receiver_user_id", receiverId)
                                            put("sender_user_id", userId)
                                            put("store_id", store.id)
                                            put("type", "suki_request_received")
                                            put("title", "Suki Request")
                                            put("message", "$senderName requested to connect with your store as a Suki.")
                                            put("read", false)
                                        }
                                    )
                                }
                            }

                            // Insert notification for current user (Flow A)
                            SupabaseProvider.client.postgrest["notifications"].insert(
                                buildJsonObject {
                                    put("receiver_user_id", userId)
                                    put("sender_user_id", userId)
                                    put("store_id", store.id)
                                    put("type", "suki_request_sent")
                                    put("title", "Suki Request")
                                    put("message", "You requested to partner with ${store.name} as your Suking Tindahan. Please wait for the owner to respond.")
                                    put("read", false)
                                }
                            )

                            successCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                            failMessages.add("${store.name}: Failed to request: ${e.localizedMessage}")
                        }
                    }

                    if (successCount > 0) {
                        Toast.makeText(this@CustomerHomeActivity, "Successfully requested Suki partnership for $successCount store(s)!", Toast.LENGTH_SHORT).show()
                    }
                    if (failMessages.isNotEmpty()) {
                        Toast.makeText(this@CustomerHomeActivity, failMessages.joinToString("\n"), Toast.LENGTH_LONG).show()
                    }
                    dialog.dismiss()
                    loadCustomerData()
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
        profileIcon.clearColorFilter()

        lifecycleScope.launch {
            try {
                SupabaseAuthService.refreshSessionIfExpired()
            } catch (_: Exception) {}

            val profile = SupabaseAuthService.getUserProfile()
            if (profile != null) {
                if (!profile.avatar_url.isNullOrBlank()) {
                    profileIcon.clearColorFilter()
                    profileIcon.load(profile.avatar_url) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        error(R.drawable.avatar_default)
                    }
                } else {
                    profileIcon.setImageResource(R.drawable.avatar_default)
                    profileIcon.clearColorFilter()
                }
            } else {
                profileIcon.setImageResource(R.drawable.avatar_default)
                profileIcon.clearColorFilter()
            }
        }
    }

    private fun loadCustomerData(showShimmer: Boolean = true) {
        if (showShimmer) {
            if (isPricesTabActive) {
                shimmerPricesContainer.visibility = View.VISIBLE
                shimmerPricesContainer.startShimmer()
                shimmerStoresContainer.visibility = View.GONE
                shimmerStoresContainer.stopShimmer()
            } else {
                shimmerStoresContainer.visibility = View.VISIBLE
                shimmerStoresContainer.startShimmer()
                shimmerPricesContainer.visibility = View.GONE
                shimmerPricesContainer.stopShimmer()
            }
            swipeRefreshLayout.visibility = View.GONE
            layoutEmptyState.visibility = View.GONE
        } else {
            swipeRefreshLayout.isRefreshing = true
        }

        lifecycleScope.launch {
            try {
                try {
                    SupabaseAuthService.refreshSessionIfExpired()
                } catch (_: Exception) {}
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                if (userId == null) {
                    if (showShimmer) {
                        shimmerPricesContainer.stopShimmer()
                        shimmerPricesContainer.visibility = View.GONE
                        shimmerStoresContainer.stopShimmer()
                        shimmerStoresContainer.visibility = View.GONE
                        swipeRefreshLayout.visibility = View.VISIBLE
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                    }
                    return@launch
                }

                // 1. Fetch suki store links
                val sukiLinks = SupabaseProvider.client.postgrest["suki_relationships"]
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<SukiRelationshipRow>()

                val storeIds = sukiLinks.filter { it.status == "active" }.map { it.store_id }
                if (storeIds.isEmpty()) {
                    allStores = emptyList()
                    allCategories = emptyList()
                    allProducts = emptyList()
                    baseProducts = emptyList()
                    filterAndRenderData()
                    if (showShimmer) {
                        shimmerPricesContainer.stopShimmer()
                        shimmerPricesContainer.visibility = View.GONE
                        shimmerStoresContainer.stopShimmer()
                        shimmerStoresContainer.visibility = View.GONE
                        swipeRefreshLayout.visibility = View.VISIBLE
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                    }
                    return@launch
                }

                // 2. Fetch linked store details
                allStores = SupabaseProvider.client.postgrest["stores"]
                    .select {
                        filter { isIn("id", storeIds) }
                        limit(1000)
                    }
                    .decodeList<StoreDetailRow>()

                val (allCategoriesResult, allProductsResult) = coroutineScope {
                    val categoryDeferreds = storeIds.map { sId ->
                        async {
                            try {
                                SupabaseProvider.client.postgrest["categories"]
                                    .select {
                                        filter { eq("store_id", sId) }
                                        limit(1000)
                                    }
                                    .decodeList<CategoryDetailRow>()
                            } catch (e: Exception) {
                                emptyList<CategoryDetailRow>()
                            }
                        }
                    }

                    val productDeferreds = storeIds.map { sId ->
                        async {
                            try {
                                SupabaseProvider.client.postgrest["products"]
                                    .select {
                                        filter {
                                            eq("store_id", sId)
                                            eq("is_public", true)
                                        }
                                        limit(5000)
                                    }
                                    .decodeList<ProductDetailRow>()
                            } catch (e: Exception) {
                                android.util.Log.e("CustomerHomeActivity", "Failed to fetch products for store $sId", e)
                                emptyList<ProductDetailRow>()
                            }
                        }
                    }

                    Pair(
                        awaitAll(*categoryDeferreds.toTypedArray()).flatten(),
                        awaitAll(*productDeferreds.toTypedArray()).flatten()
                    )
                }

                allCategories = allCategoriesResult
                allProducts = allProductsResult

                // 5. Fetch accurate product counts from database
                try {
                    val counts = SupabaseProvider.client.postgrest.rpc(
                        "get_store_product_counts",
                        kotlinx.serialization.json.buildJsonObject {
                            put("p_store_ids", kotlinx.serialization.json.buildJsonArray {
                                storeIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                            })
                        }
                    ).decodeList<StoreProductCountRow>()
                    storeProductCounts = counts.associate { it.store_id to it.public_count }
                } catch (e: Exception) {
                    android.util.Log.e("CustomerHomeActivity", "Failed to fetch product counts", e)
                    // Fallback to in-memory counting if RPC fails
                    storeProductCounts = allProducts.groupBy { it.store_id }.mapValues { it.value.size }
                }

                // Precompute baseProducts once data is successfully loaded to avoid lag during search queries
                // Include products from all linked (Suki) stores — user is authorized to see public prices of their suki stores
                val publicStoreIds = allStores.filter { it.is_public || it.is_standard_store }.map { it.id }.toSet()
                val storeMap = allStores.associateBy { it.id }
                val categoryMap = allCategories.associateBy { it.id }
                baseProducts = allProducts
                    .filter { it.store_id in publicStoreIds }
                    .map { prod ->
                    val store = storeMap[prod.store_id]
                    val category = categoryMap[prod.category_id]
                    DisplayProduct(
                        id = prod.id,
                        name = prod.name,
                        description = prod.description,
                        price = prod.price,
                        unit = prod.unit,
                        storeId = prod.store_id,
                        storeName = store?.name ?: "Unknown Store",
                        storeLocation = store?.branch ?: "Main Branch",
                        categoryName = category?.name ?: "PRICELIST"
                    )
                }

                filterAndRenderData()
                ReusableDialogHelper.resetReloadCount()
            } catch (e: Exception) {
                e.printStackTrace()
                val handled = ReusableDialogHelper.handleNetworkError(this@CustomerHomeActivity, e) {
                    loadCustomerData(showShimmer = true)
                }
                if (!handled) {
                    Toast.makeText(this@CustomerHomeActivity, "Error loading data: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                swipeRefreshLayout.isRefreshing = false
                if (showShimmer) {
                    shimmerPricesContainer.stopShimmer()
                    shimmerPricesContainer.visibility = View.GONE
                    shimmerStoresContainer.stopShimmer()
                    shimmerStoresContainer.visibility = View.GONE
                    swipeRefreshLayout.visibility = View.VISIBLE

                    // Ensure the correct RecyclerView is visible after shimmer
                    if (isPricesTabActive) {
                        rvCustomerPrices.visibility = View.VISIBLE
                        rvCustomerStores.visibility = View.GONE
                    } else {
                        rvCustomerPrices.visibility = View.GONE
                        rvCustomerStores.visibility = View.VISIBLE
                    }
                }

                if (allStores.isEmpty() && !hasAutoOpenedBottomSheet) {
                    hasAutoOpenedBottomSheet = true
                    showBottomSheet()
                }
            }
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        return com.presyohan.app.helper.SearchHelper.levenshteinDistance(s1, s2)
    }

    private fun isFuzzyMatch(token: String, text: String): Boolean {
        return com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, text)
    }

    private fun matchPrice(token: String, price: Double): Boolean {
        return com.presyohan.app.helper.SearchHelper.matchPrice(token, price)
    }

    private fun matchesProduct(tokens: List<String>, product: DisplayProduct): Boolean {
        for (token in tokens) {
            if (token.isEmpty()) continue

            val matchesName = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, product.name)
            val matchesDesc = product.description?.let { com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, it) } ?: false
            val matchesCategory = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, product.categoryName)
            val matchesStore = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, product.storeName)
            val matchesUnit = product.unit?.let { com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, it) } ?: false
            val matchesPrice = com.presyohan.app.helper.SearchHelper.matchPrice(token, product.price)

            if (!matchesName && !matchesDesc && !matchesCategory && !matchesStore && !matchesUnit && !matchesPrice) {
                return false
            }
        }
        return true
    }

    private fun calculateMatchScore(query: String, product: DisplayProduct): Double {
        return com.presyohan.app.helper.SearchHelper.calculateProductScore(
            query = query,
            name = product.name,
            description = product.description,
            categoryName = product.categoryName,
            storeName = product.storeName
        )
    }

    private fun matchesCategoryName(query: String, categoryName: String): Boolean {
        if (query.isEmpty()) return false
        val q = query.lowercase(Locale.getDefault())
        val c = categoryName.lowercase(Locale.getDefault())
        if (!c.contains(q) && !q.contains(c)) return false
        return q.length >= 6 || (q.length.toDouble() / c.length.toDouble()) >= 0.6
    }

    private fun matchesCategoryTokens(categoryQuery: String, categoryName: String): Boolean {
        val tokens = categoryQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        return tokens.all { token -> isFuzzyMatch(token, categoryName) }
    }

    private fun filterAndRenderData(delayMillis: Long = 0) {
        val query = currentSearchQuery.trim()
        val isPrices = isPricesTabActive
        
        // Take snapshots of data for thread-safe background processing
        val snapshotStores = allStores
        val snapshotCategories = allCategories
        val snapshotProducts = allProducts
        val snapshotBaseProducts = baseProducts

        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (delayMillis > 0) {
                kotlinx.coroutines.delay(delayMillis)
            }

            if (isPrices) {
                // Render Prices/Products Tab
                val searchItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val storeMap = snapshotStores.associateBy { it.id }

                    // 1. Extract price range
                    var maxPrice: Double? = null
                    val priceRangeRegex = Regex("(?i)price\\s+range:\\s*(\\d+(?:\\.\\d+)?)")
                    val priceRangeMatch = priceRangeRegex.find(query)
                    var cleanQuery = query
                    if (priceRangeMatch != null) {
                        maxPrice = priceRangeMatch.groupValues[1].toDoubleOrNull()
                        cleanQuery = query.replace(priceRangeMatch.value, "").trim()
                    }

                    val cleanQueryLower = cleanQuery.lowercase(Locale.getDefault())

                    // 2. Parse category queries
                    var storeNameQuery: String? = null
                    var categoryNameQuery: String? = null
                    var isAllCategoriesQuery = false

                    val storeCategoriesRegex = Regex("(?i)(.+?)\\s+categories:\\s*(.*)")
                    val storeCategoriesMatch = storeCategoriesRegex.matchEntire(cleanQuery)

                    if (storeCategoriesMatch != null) {
                        storeNameQuery = storeCategoriesMatch.groupValues[1].trim()
                        categoryNameQuery = storeCategoriesMatch.groupValues[2].trim()
                    } else {
                        val categoryRegex = Regex("(?i)^category:\\s*(.*)")
                        val categoryRegex2 = Regex("(?i)^category\\s+(.*)")
                        val categoryMatch = categoryRegex.matchEntire(cleanQuery) ?: categoryRegex2.matchEntire(cleanQuery)
                        if (categoryMatch != null) {
                            categoryNameQuery = categoryMatch.groupValues[1].trim()
                        } else {
                            isAllCategoriesQuery = cleanQueryLower == "category" ||
                                    (cleanQueryLower.length >= 6 && levenshteinDistance(cleanQueryLower, "category") <= 2)
                        }
                    }

                    val isStoreQuery = cleanQueryLower == "store" ||
                            (cleanQueryLower.length >= 4 && levenshteinDistance(cleanQueryLower, "store") <= 1)

                    // 3. Filter categories
                    val matchingCategories = if (isAllCategoriesQuery) {
                        snapshotCategories
                    } else if (storeNameQuery != null) {
                        val matchedStores = snapshotStores.filter { isFuzzyMatch(storeNameQuery, it.name) }
                        val storeIds = matchedStores.map { it.id }.toSet()
                        if (categoryNameQuery.isNullOrBlank()) {
                            snapshotCategories.filter { it.store_id in storeIds }
                        } else {
                            snapshotCategories.filter { it.store_id in storeIds && matchesCategoryTokens(categoryNameQuery, it.name) }
                        }
                    } else if (categoryNameQuery != null) {
                        if (categoryNameQuery.isBlank()) {
                            snapshotCategories
                        } else {
                            snapshotCategories.filter { matchesCategoryTokens(categoryNameQuery, it.name) }
                        }
                    } else {
                        if (cleanQuery.isNotEmpty() && !isStoreQuery) {
                            snapshotCategories.filter { matchesCategoryTokens(cleanQuery, it.name) }
                        } else {
                            emptyList()
                        }
                    }

                    val displayCategories = matchingCategories.map { cat ->
                        val store = storeMap[cat.store_id]
                        val itemCount = snapshotProducts.count { it.category_id == cat.id }
                        DisplayCategory(
                            categoryId = cat.id,
                            categoryName = cat.name,
                            storeId = cat.store_id,
                            storeName = store?.name ?: "Unknown Store",
                            storeLocation = store?.branch ?: "Main Branch",
                            itemCount = itemCount
                        )
                    }.filter { it.itemCount > 0 }

                    // 4. Filter products
                    val filteredProducts = if (isAllCategoriesQuery) {
                        emptyList()
                    } else if (storeNameQuery != null) {
                        val matchedStores = snapshotStores.filter { isFuzzyMatch(storeNameQuery, it.name) }
                        val storeIds = matchedStores.map { it.id }.toSet()
                        if (categoryNameQuery.isNullOrBlank()) {
                            emptyList()
                        } else {
                            snapshotBaseProducts.filter { prod ->
                                prod.storeId in storeIds && matchesCategoryTokens(categoryNameQuery, prod.categoryName)
                            }
                        }
                    } else if (categoryNameQuery != null) {
                        if (categoryNameQuery.isBlank()) {
                            snapshotBaseProducts
                        } else {
                            snapshotBaseProducts.filter { prod ->
                                matchesCategoryTokens(categoryNameQuery, prod.categoryName)
                            }
                        }
                    } else {
                        val cleanTokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (cleanTokens.isEmpty() || isStoreQuery) {
                            snapshotBaseProducts
                        } else {
                            snapshotBaseProducts.filter { matchesProduct(cleanTokens, it) }
                        }
                    }

                    // 5. Apply price range filter and sort
                    val finalProducts = if (maxPrice != null) {
                        filteredProducts
                            .filter { it.price <= maxPrice }
                            .sortedByDescending { it.price }
                    } else {
                        if (cleanQuery.isEmpty()) {
                            filteredProducts
                        } else {
                            filteredProducts.sortedByDescending { calculateMatchScore(cleanQuery, it) }
                        }
                    }

                    if (query.isEmpty()) {
                        val publicStores = snapshotStores.filter { it.is_public || it.is_standard_store }
                        if (publicStores.isEmpty()) {
                            emptyList<SearchItem>()
                        } else {
                            val defaultHeader = SearchItem.DefaultHeader(
                                stores = publicStores,
                                categories = snapshotCategories,
                                products = snapshotProducts,
                                storeProductCounts = storeProductCounts
                            )
                            listOf(defaultHeader) + finalProducts.map { SearchItem.Product(it) }
                        }
                    } else {
                        displayCategories.map { SearchItem.Category(it) } + finalProducts.map { SearchItem.Product(it) }
                    }
                }

                searchAdapter.updateList(searchItems)
                findViewById<View>(R.id.layoutSearchLoading).visibility = View.GONE
                rvCustomerPrices.visibility = View.VISIBLE
                val emptyMessage = if (snapshotStores.isEmpty() && query.isEmpty()) {
                    "No products yet."
                } else if (query.isEmpty()) {
                    "No products found."
                } else {
                    "No products found matching \"$query\""
                }
                toggleEmptyState(searchItems.isEmpty(), emptyMessage)
            } else {
                // Render Stores Tab
                val filteredStores = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    val displayStores = snapshotStores.map { store ->
                        val productCount = if (store.is_public || store.is_standard_store) (storeProductCounts[store.id] ?: 0) else 0
                        val isPresyohan = store.is_standard_store
                        val typeTag = if (isPresyohan) "Presyohan" else if (store.is_public) "Public" else "Private"

                        DisplayStore(
                            id = store.id,
                            name = store.name,
                            subtitle = store.type ?: "General Store",
                            location = store.branch ?: "Main Branch",
                            typeTag = typeTag,
                            isPresyohan = isPresyohan,
                            itemCount = productCount,
                            displayId = store.display_id
                        )
                    }

                    if (tokens.isEmpty()) {
                        displayStores
                    } else {
                        val storeSearchIndexes = snapshotStores.associate { store ->
                            val catNames = snapshotCategories.filter { it.store_id == store.id }.map { it.name }
                            val storeProducts = snapshotBaseProducts.filter { it.storeId == store.id }
                            val contents = catNames + storeProducts.flatMap { listOfNotNull(it.name, it.description, it.unit) }
                            val prices = storeProducts.map { it.price }
                            
                            store.id to StoreSearchIndex(
                                storeDetails = listOfNotNull(store.name, store.branch, store.type),
                                contentStrings = contents,
                                productPrices = prices
                            )
                        }

                        displayStores.filter { store ->
                            val index = storeSearchIndexes[store.id] ?: return@filter false
                            tokens.all { token ->
                                index.storeDetails.any { isFuzzyMatch(token, it) } ||
                                index.contentStrings.any { isFuzzyMatch(token, it) } ||
                                index.productPrices.any { matchPrice(token, it) }
                            }
                        }
                    }
                }.sortedWith(
                    compareBy<DisplayStore> { it.typeTag == "Private" }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )

                storeAdapter.updateList(filteredStores)
                findViewById<View>(R.id.layoutSearchLoading).visibility = View.GONE
                rvCustomerStores.visibility = View.VISIBLE
                val emptyMessage = if (snapshotStores.isEmpty() && query.isEmpty()) {
                    "No stores yet."
                } else if (query.isEmpty()) {
                    "No stores found."
                } else {
                    "No stores found matching \"$query\""
                }
                toggleEmptyState(filteredStores.isEmpty(), emptyMessage)
            }
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

    // --- RECYCLERVIEW ADAPTERS ---

    sealed class SearchItem {
        data class DefaultHeader(
            val stores: List<StoreDetailRow>,
            val categories: List<CategoryDetailRow>,
            val products: List<ProductDetailRow>,
            val storeProductCounts: Map<String, Int>
        ) : SearchItem()
        data class Category(val category: DisplayCategory) : SearchItem()
        data class Product(val product: DisplayProduct) : SearchItem()
    }

    private class StoresCarouselAdapter(
        private val stores: List<StoreDetailRow>,
        private val categories: List<CategoryDetailRow>,
        private val products: List<ProductDetailRow>,
        private val storeProductCounts: Map<String, Int>,
        private val isSingleStore: Boolean,
        private val onCategoryClick: (DisplayCategory) -> Unit,
        private val onStoreClick: (DisplayStore) -> Unit,
        private val onMoreClick: (StoreDetailRow) -> Unit
    ) : RecyclerView.Adapter<StoresCarouselAdapter.StoreCardViewHolder>() {

        class StoreCardViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvStoreNameBranch: TextView = view.findViewById(R.id.tvStoreNameBranch)
            val tvViewStore: TextView = view.findViewById(R.id.tvViewStore)
            val layoutCategoriesContainer: LinearLayout = view.findViewById(R.id.layoutCategoriesContainer)
            val btnMoreDropdown: View = view.findViewById(R.id.btnMoreDropdown)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreCardViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_store_category_card, parent, false)
            return StoreCardViewHolder(view)
        }

        override fun onBindViewHolder(holder: StoreCardViewHolder, position: Int) {
            val store = stores[position]
            holder.tvStoreNameBranch.text = "${store.name} - ${store.branch ?: "Main Branch"}"
            
            val cardHeight = (360 * holder.view.resources.displayMetrics.density).toInt()
            val lp = holder.itemView.layoutParams ?: ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cardHeight
            )
            val marginLp = lp as ViewGroup.MarginLayoutParams
            marginLp.height = cardHeight
            if (isSingleStore) {
                marginLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                marginLp.setMargins(
                    (12 * holder.view.resources.displayMetrics.density).toInt(),
                    (4 * holder.view.resources.displayMetrics.density).toInt(),
                    (12 * holder.view.resources.displayMetrics.density).toInt(),
                    (4 * holder.view.resources.displayMetrics.density).toInt()
                )
            } else {
                marginLp.width = (290 * holder.view.resources.displayMetrics.density).toInt()
                marginLp.setMargins(
                    (4 * holder.view.resources.displayMetrics.density).toInt(),
                    (4 * holder.view.resources.displayMetrics.density).toInt(),
                    (4 * holder.view.resources.displayMetrics.density).toInt(),
                    (4 * holder.view.resources.displayMetrics.density).toInt()
                )
            }
            holder.itemView.layoutParams = marginLp

            holder.tvViewStore.setOnClickListener {
                val displayStore = DisplayStore(
                    id = store.id,
                    name = store.name,
                    subtitle = store.type ?: "General Store",
                    location = store.branch ?: "Main Branch",
                    typeTag = if (store.is_standard_store) "Presyohan" else if (store.is_public) "Public" else "Private",
                    isPresyohan = store.is_standard_store,
                    itemCount = storeProductCounts[store.id] ?: 0,
                    displayId = store.display_id
                )
                onStoreClick(displayStore)
            }

            holder.btnMoreDropdown.setOnClickListener {
                onMoreClick(store)
            }

            holder.layoutCategoriesContainer.removeAllViews()
            val storeCategories = categories.filter { it.store_id == store.id }
            val displayedCategories = storeCategories.take(5)

            for (category in displayedCategories) {
                val rowView = LayoutInflater.from(holder.view.context).inflate(R.layout.item_customer_category_row, holder.layoutCategoriesContainer, false)
                val tvName = rowView.findViewById<TextView>(R.id.tvCategoryName)
                val tvCount = rowView.findViewById<TextView>(R.id.tvCategoryItemCount)

                tvName.text = category.name.uppercase(Locale.getDefault())
                val count = products.count { it.category_id == category.id }
                tvCount.text = "$count Items"

                rowView.setOnClickListener {
                    val displayCat = DisplayCategory(
                        categoryId = category.id,
                        categoryName = category.name,
                        storeId = store.id,
                        storeName = store.name,
                        storeLocation = store.branch ?: "Main Branch",
                        itemCount = count
                    )
                    onCategoryClick(displayCat)
                }

                holder.layoutCategoriesContainer.addView(rowView)
            }
        }

        override fun getItemCount(): Int = stores.size
    }

    private class CustomerSearchAdapter(
        private var items: List<SearchItem>,
        private val onCategoryClick: (DisplayCategory) -> Unit,
        private val onProductCategoryClick: (DisplayProduct) -> Unit,
        private val onStoreClick: (DisplayStore) -> Unit,
        private val onMoreClick: (StoreDetailRow) -> Unit,
        private val onViewAllClick: () -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_CATEGORY = 0
            private const val TYPE_PRODUCT = 1
            private const val TYPE_DEFAULT_HEADER = 2
        }

        class CategoryViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvCategoryName: TextView = view.findViewById(R.id.tvCategoryName)
            val tvCategoryStoreName: TextView = view.findViewById(R.id.tvCategoryStoreName)
            val tvCategoryLocation: TextView = view.findViewById(R.id.tvCategoryLocation)
            val tvCategoryItemCount: TextView = view.findViewById(R.id.tvCategoryItemCount)
        }

        class ProductViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvStoreNameBranch: TextView = view.findViewById(R.id.tvStoreNameBranch)
            val tvProductCategory: TextView = view.findViewById(R.id.tvProductCategory)
            val tvProductName: TextView = view.findViewById(R.id.tvProductName)
            val tvProductDescription: TextView = view.findViewById(R.id.tvProductDescription)
            val tvProductUnits: TextView = view.findViewById(R.id.tvProductUnits)
            val tvProductPrice: TextView = view.findViewById(R.id.tvProductPrice)
        }

        class DefaultHeaderViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvViewAll: TextView = view.findViewById(R.id.tvViewAll)
            val rvStoresCarousel: RecyclerView = view.findViewById(R.id.rvStoresCarousel)
            val tvTotalItems: TextView = view.findViewById(R.id.tvTotalItems)
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is SearchItem.Category -> TYPE_CATEGORY
                is SearchItem.Product -> TYPE_PRODUCT
                is SearchItem.DefaultHeader -> TYPE_DEFAULT_HEADER
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_DEFAULT_HEADER -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_default_header, parent, false)
                    DefaultHeaderViewHolder(view)
                }
                TYPE_CATEGORY -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_category, parent, false)
                    CategoryViewHolder(view)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_product, parent, false)
                    ProductViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is SearchItem.DefaultHeader -> {
                    val headerHolder = holder as DefaultHeaderViewHolder
                    headerHolder.tvViewAll.setOnClickListener {
                        onViewAllClick()
                    }
                    headerHolder.tvTotalItems.text = "Total: ${item.products.size}"
                    
                    val context = headerHolder.view.context
                    val isSingle = item.stores.size == 1
                    headerHolder.rvStoresCarousel.layoutManager = LinearLayoutManager(
                        context,
                        if (isSingle) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL,
                        false
                    )
                    headerHolder.rvStoresCarousel.adapter = StoresCarouselAdapter(
                        stores = item.stores,
                        categories = item.categories,
                        products = item.products,
                        storeProductCounts = item.storeProductCounts,
                        isSingleStore = isSingle,
                        onCategoryClick = onCategoryClick,
                        onStoreClick = onStoreClick,
                        onMoreClick = onMoreClick
                    )
                }
                is SearchItem.Category -> {
                    val catHolder = holder as CategoryViewHolder
                    val data = item.category
                    catHolder.tvCategoryName.text = data.categoryName.uppercase(Locale.getDefault())
                    catHolder.tvCategoryStoreName.text = data.storeName
                    catHolder.tvCategoryLocation.text = data.storeLocation
                    catHolder.tvCategoryItemCount.text = "${data.itemCount} Items"
                    catHolder.itemView.setOnClickListener {
                        onCategoryClick(data)
                    }
                }
                is SearchItem.Product -> {
                    val prodHolder = holder as ProductViewHolder
                    val data = item.product
                    prodHolder.tvStoreNameBranch.text = "${data.storeName} - ${data.storeLocation}"
                    prodHolder.tvProductCategory.text = data.categoryName.uppercase(Locale.getDefault())
                    prodHolder.tvProductName.text = data.name
                    prodHolder.tvProductDescription.text = data.description ?: ""
                    prodHolder.tvProductDescription.visibility = if (data.description.isNullOrBlank()) View.GONE else View.VISIBLE
                    prodHolder.tvProductUnits.text = data.unit ?: ""
                    prodHolder.tvProductUnits.visibility = if (data.unit.isNullOrBlank()) View.GONE else View.VISIBLE
                    prodHolder.tvProductPrice.text = String.format(Locale.US, "₱ %,.2f", data.price)
                    prodHolder.tvProductCategory.setOnClickListener {
                        onProductCategoryClick(data)
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<SearchItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    private fun showStoreDetailsDialog(store: DisplayStore) {
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

        txtStoreName.text = store.name
        txtStoreBranch.text = store.location
        txtStoreCategory.text = store.subtitle

        if (store.isPresyohan) {
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
        txtStoreId.text = store.displayId ?: store.id
        txtCategoriesCount.text = allCategories.count { it.store_id == store.id }.toString()
        txtItemsCount.text = store.itemCount.toString()

        val shimmerLayout = view.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.dialogShimmerLayout)
        val statsTable = view.findViewById<android.widget.TableLayout>(R.id.dialogStatsTable)

        shimmerLayout.startShimmer()
        shimmerLayout.visibility = android.view.View.VISIBLE
        statsTable.visibility = android.view.View.GONE

        lifecycleScope.launch {
            try {
                // Use RPC to bypass RLS and get actual suki count
                val sukiCount = try {
                    SupabaseProvider.client.postgrest.rpc(
                        "get_store_suki_count",
                        kotlinx.serialization.json.buildJsonObject { put("p_store_id", kotlinx.serialization.json.JsonPrimitive(store.id)) }
                    ).decodeAs<Int>()
                } catch (e: Exception) {
                    0
                }
                txtSukiCount.text = sukiCount.toString()

                // Fetch store metadata (created_at, is_public)
                @Serializable
                data class StoreMeta(
                    val created_at: String? = null,
                    val is_public: Boolean = false
                )
                val metaRows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", store.id) }
                    limit(1)
                }.decodeList<StoreMeta>()

                val meta = metaRows.firstOrNull()
                if (meta != null) {
                    val rawDate = meta.created_at?.split("T")?.firstOrNull() ?: ""
                    val dateText = try {
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val parsedDate = inputFormat.parse(rawDate)
                        if (parsedDate != null) {
                            val outputFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
                            outputFormat.format(parsedDate)
                        } else {
                            rawDate
                        }
                    } catch (_: Exception) {
                        rawDate
                    }
                    txtCreatedAt.text = dateText
                    txtVisibilityStatus.text = if (meta.is_public) "Public" else "Private"
                }
            } catch (e: Exception) {
                Log.e("CustomerHomeActivity", "Failed to load store metadata", e)
            } finally {
                shimmerLayout.stopShimmer()
                shimmerLayout.visibility = android.view.View.GONE
                statsTable.visibility = android.view.View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun confirmRemoveStore(store: DisplayStore) {
        showReusableDialog(
            title = if (store.isPresyohan) "Remove Presyohan" else "Remove Suking Tindahan",
            message = if (store.isPresyohan) {
                "Are you sure you want to remove this presyohan from your list?\n\nYou will lose quick access to its reference price guides, but you can add it back anytime."
            } else {
                "Are you sure you want to remove this suking tindahan?\n\nYou will no longer be a suki to \"${store.name}\" and lose access to their prices."
            },
            positiveButtonText = "Remove",
            positiveAction = {
                performRemoveStore(store.id)
            },
            negativeButtonText = "Cancel",
            negativeAction = {
                // Do nothing
            }
        )
    }

    private fun performRemoveStore(storeId: String) {
        lifecycleScope.launch {
            try {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    SupabaseProvider.client.postgrest["suki_relationships"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("store_id", storeId)
                        }
                    }
                    Toast.makeText(this@CustomerHomeActivity, "Store removed successfully", Toast.LENGTH_SHORT).show()
                    loadCustomerData(showShimmer = false)
                }
            } catch (e: Exception) {
                Log.e("CustomerHomeActivity", "Failed to remove store", e)
                Toast.makeText(this@CustomerHomeActivity, "Failed to remove store", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Store Adapter
    class CustomerStoreAdapter(
        private var items: List<DisplayStore>,
        private val onStoreClick: (DisplayStore) -> Unit,
        private val onViewClick: (DisplayStore) -> Unit,
        private val onDeleteClick: (DisplayStore) -> Unit
    ) : RecyclerView.Adapter<CustomerStoreAdapter.ViewHolder>(), ISwipeAdapter {

        var swipedPosition: Int = -1
            private set

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view), ISwipeViewHolder {
            override val foregroundCardView: View = view.findViewById(R.id.foregroundCardView)
            override val backgroundActionCard: View = view.findViewById(R.id.backgroundActionCard)
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
                holder.storeIconContainer.setBackgroundResource(R.drawable.bg_rounded_store_icon)
                holder.ivStoreIcon.setImageResource(R.drawable.icon_presyohan)
                holder.ivStoreIcon.rotation = -45f
                holder.ivStoreIcon.setPadding(0, 0, 0, 0)
            } else {
                holder.storeIconContainer.setBackgroundResource(R.drawable.bg_rounded_store_icon)
                holder.ivStoreIcon.setImageResource(R.drawable.icon_store)
                holder.ivStoreIcon.rotation = 0f
                val paddingPx = (10 * holder.view.resources.displayMetrics.density).toInt()
                holder.ivStoreIcon.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            }

            // Handle swiped translation
            val isSwiped = position == swipedPosition
            val density = holder.view.resources.displayMetrics.density
            val fallbackWidth = 120f * density
            val actionWidth = if (holder.backgroundActionCard.width > 0) holder.backgroundActionCard.width.toFloat() else fallbackWidth
            holder.foregroundCardView.translationX = if (isSwiped) -actionWidth else 0f

            // Action Button setup
            holder.view.findViewById<View>(R.id.btnAction1).setOnClickListener {
                closeSwipedItem()
                onViewClick(item)
            }
            holder.view.findViewById<View>(R.id.btnAction2).setOnClickListener {
                closeSwipedItem()
                onDeleteClick(item)
            }

            // Tap Behavior on Card
            holder.foregroundCardView.setOnClickListener {
                if (isSwiped(position)) {
                    closeSwipedItem()
                } else {
                    if (swipedPosition != -1) {
                        closeSwipedItem()
                    } else {
                        onStoreClick(item)
                    }
                }
            }

            holder.foregroundCardView.setOnLongClickListener {
                onItemSwiped(position)
                true
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<DisplayStore>) {
            items = newItems
            swipedPosition = -1
            notifyDataSetChanged()
        }

        override fun onItemSwiped(position: Int) {
            val previousSwiped = swipedPosition
            if (swipedPosition == position) return
            swipedPosition = position
            if (previousSwiped != -1) {
                notifyItemChanged(previousSwiped)
            }
            notifyItemChanged(swipedPosition)
        }

        override fun isSwiped(position: Int): Boolean {
            return swipedPosition == position
        }

        override fun closeSwipedItem() {
            if (swipedPosition != -1) {
                val prev = swipedPosition
                swipedPosition = -1
                notifyItemChanged(prev)
            }
        }
    }
}
