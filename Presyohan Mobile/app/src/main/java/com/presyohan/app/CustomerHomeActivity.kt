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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import android.app.Dialog
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
    private var connectionLostDialog: Dialog? = null

    private fun showConnectionLostDialog(reloadAction: () -> Unit) {
        if (connectionLostDialog?.isShowing == true) return
        connectionLostDialog = ReusableDialogHelper.showConnectionLostDialog(this) {
            connectionLostDialog = null
            reloadAction()
        }
    }

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
    data class SukiRelationshipRow(val store_id: String)

    @Serializable
    data class StoreMemberCheckRow(val user_id: String)

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

        // Setup RecyclerViews
        rvCustomerPrices.layoutManager = LinearLayoutManager(this)
        rvCustomerStores.layoutManager = LinearLayoutManager(this)

        searchAdapter = CustomerSearchAdapter(
            emptyList(),
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
            integrator.setCaptureActivity(CustomScannerActivity::class.java)
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

                val storeIds = sukiLinks.map { it.store_id }
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
            } catch (e: Exception) {
                e.printStackTrace()
                if (ReusableDialogHelper.isNetworkError(e)) {
                    showConnectionLostDialog {
                        loadCustomerData(showShimmer = true)
                    }
                } else {
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

                    displayCategories.map { SearchItem.Category(it) } + finalProducts.map { SearchItem.Product(it) }
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

    sealed class SearchItem {
        data class Category(val category: DisplayCategory) : SearchItem()
        data class Product(val product: DisplayProduct) : SearchItem()
    }

    private class CustomerSearchAdapter(
        private var items: List<SearchItem>,
        private val onCategoryClick: (DisplayCategory) -> Unit,
        private val onProductCategoryClick: (DisplayProduct) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_CATEGORY = 0
            private const val TYPE_PRODUCT = 1
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

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is SearchItem.Category -> TYPE_CATEGORY
                is SearchItem.Product -> TYPE_PRODUCT
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_CATEGORY) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_category, parent, false)
                CategoryViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_product, parent, false)
                ProductViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
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
