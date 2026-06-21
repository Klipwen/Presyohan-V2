package com.presyohan.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import coil.load
import coil.transform.CircleCropTransformation
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.presyohan.app.adapter.CategoryGroupAdapter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
// Imports for Export
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.net.Uri
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.DownloadManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.pm.PackageManager
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private val supabase: SupabaseClient
        get() = SupabaseProvider.client

    private var selectedCategory: String? = null
    private var currentQuery: String = ""
    private var reloadProductsFn: (() -> Unit)? = null
    private lateinit var loadingOverlay: android.view.View
    private var activeOverlayProductId: String? = null
    private var productGroups: List<ProductGroup> = emptyList()

    // Store details
    private var currentStoreId: String? = null
    private var currentStoreName: String? = null
    private var currentBranchName: String? = null // Added to capture branch name for dialog
    private var currentStoreType: String? = null
    private var userRole: String? = null

    // Invite Code Countdown
    private var inviteCodeCountdownJob: kotlinx.coroutines.Job? = null

    private val spinnerCategories = mutableListOf("PRICELIST")
    private lateinit var spinnerAdapter: android.widget.ArrayAdapter<String>

    // Export permission handler
    private var lastExportFilenamePending: String? = null
    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = lastExportFilenamePending
        lastExportFilenamePending = null
        if (granted && pending != null) {
            postExportNotification(pending)
        } else {
            Toast.makeText(this, "Enable notifications to see download alerts.", Toast.LENGTH_LONG).show()
        }
    }

    @Serializable
    data class StoreMember(val store_id: String, val user_id: String, val role: String)

    // Data class for RPC response to ensure we get all members correctly
    @Serializable
    data class StoreMemberUser(val user_id: String, val name: String, val role: String)

    @Serializable
    data class ProductRow(
        val id: String,
        val store_id: String,
        val name: String,
        val description: String? = null,
        val price: Double = 0.0,
        val units: String? = null,
        val category: String? = null,
        val is_public: Boolean = false
    )

    @Serializable
    data class CategoryRow(val id: String, val store_id: String, val name: String)

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

    @Serializable
    data class UserStoreRow(
        val store_id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val role: String,
        val is_public: Boolean = false,
        val member_count: Int = 0
    )

    data class StoreStats(
        val categoriesCount: Int = 0,
        val productsCount: Int = 0,
        val membersCount: Int = 0,
        val ownersCount: Int = 0,
        val managersCount: Int = 0,
        val employeesCount: Int = 0
    )

    data class ProductGroup(
        val categoryName: String,
        val itemCount: Int,
        val products: List<com.presyohan.app.adapter.Product>
    )

    @Serializable
    data class NotificationRow(
        val id: String,
        val receiver_user_id: String,
        val read: Boolean = false
    )

    @Serializable
    data class InviteCodeReturn(
        val invite_code: String,
        val invite_code_created_at: String
    )

    @Serializable
    data class StoreInviteFields(
        val invite_code: String? = null,
        val invite_code_created_at: String? = null
    )

    @Serializable
    data class SearchedUser(
        val id: String,
        val name: String? = null,
        val email: String? = null,
        val user_code: String? = null,
        val avatar_url: String? = null
    )

    // For Export
    @Serializable
    data class StoreProductExportRow(
        val category: String? = null,
        val name: String? = null,
        val description: String? = null,
        val units: String? = null,
        val price: Double? = null
    )

    private enum class ExportType {
        EXCEL,
        NOTES
    }

    private var lastBackPress: Long = 0


    // UI Variables for Scope
    private lateinit var searchBarContainer: View
    private lateinit var layoutPricelistTrigger: View
    private lateinit var addButton: View
    private lateinit var searchEditText: EditText

    // Search & Scroll Logic Variables
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var searchVisibilityRunnable: Runnable? = null
    private var isKeyboardOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        loadingOverlay = LoadingOverlayHelper.attach(this)


        // --- View Initialization ---
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val menuIcon = findViewById<ImageView>(R.id.menuIcon)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val storeText = findViewById<TextView>(R.id.textStoreName)
        val productRecyclerView = findViewById<RecyclerView>(R.id.productRecyclerView)
        val categoryLabel = findViewById<TextView>(R.id.categoryLabel)
        val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
        val categoryDrawerButton = findViewById<ImageView>(R.id.categoryDrawerButton)
        val notifIcon = findViewById<ImageView>(R.id.notifIcon)
        val searchItemButton = findViewById<android.widget.ImageButton>(R.id.searchItemButton)
        val btnStoreOptions = findViewById<ImageView>(R.id.btnStoreOptions)

        // Assign to class-level vars
        searchBarContainer = findViewById(R.id.bottomSheet)
        searchEditText = findViewById(R.id.bottomSearchEditText)
        layoutPricelistTrigger = findViewById(R.id.layoutPricelistTrigger)
        addButton = findViewById(R.id.btnSheetAddItem)

        if (searchBarContainer == null || layoutPricelistTrigger == null || addButton == null) {
            Log.e("HomeActivity", "Critical views not found in XML")
            return
        }

        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        currentStoreId = intent.getStringExtra("storeId")
        currentStoreName = intent.getStringExtra("storeName")

        // --- Navigation & Header Logic ---
        menuIcon.setOnClickListener { drawerLayout.open() }
        DrawerHelper.setupDrawer(this, drawerLayout)

        btnBack.setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            startActivity(intent, options.toBundle())
            finish()
        }

        storeText.text = currentStoreName ?: "Store"
        storeText.setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            startActivity(intent, options.toBundle())
            finish()
        }

        // --- Product List & Role Logic ---
        val adapter = com.presyohan.app.adapter.CategoryGroupAdapter(
            groups = productGroups,
            userRole = userRole,
            activeOverlayProductId = activeOverlayProductId,
            onEditClick = { product ->
                if (currentStoreId.isNullOrBlank()) return@CategoryGroupAdapter
                AddEditItemDialogHelper.showAddOrEditItemDialog(
                    activity = this@HomeActivity,
                    storeId = currentStoreId!!,
                    storeName = currentStoreName ?: "Store",
                    productData = EditProductData(
                        id = product.id,
                        name = product.name,
                        description = product.description,
                        price = product.price,
                        unit = product.volume,
                        category = product.category,
                        isPublic = product.is_public
                    ),
                    onComplete = {
                        reloadProductsFn?.invoke()
                    }
                )
            },
            onDeleteClick = { product ->
                showDeleteConfirmationDialog(product)
            },
            onItemLongPressed = { product ->
                // Toggle active overlay on long-press
                activeOverlayProductId = if (activeOverlayProductId == product.id) null else product.id
                (productRecyclerView.adapter as? com.presyohan.app.adapter.CategoryGroupAdapter)?.updateActiveOverlayProductId(activeOverlayProductId)
            },
            onItemClicked = {
                // Dismiss active overlay when clicking an item normally
                if (activeOverlayProductId != null) {
                    activeOverlayProductId = null
                    (productRecyclerView.adapter as? com.presyohan.app.adapter.CategoryGroupAdapter)?.updateActiveOverlayProductId(null)
                }
            }
        )

        productRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        productRecyclerView.adapter = adapter

        // Fetch user role and update UI accordingly
        if (userId != null && currentStoreId != null) {
            lifecycleScope.launch {
                try {
                    val members = supabase.postgrest["store_members"]
                        .select {
                            filter {
                                eq("store_id", currentStoreId!!)
                                eq("user_id", userId)
                            }
                            limit(1)
                        }
                        .decodeList<StoreMember>()

                    val role = members.firstOrNull()?.role
                    userRole = role
                    adapter.setUserRole(role)

                    // --- UI UPDATE BASED ON ROLE ---
                    updateUiForRole(role)

                } catch (e: Exception) {
                    Log.e("HomeActivity", "Role fetch failed", e)
                }
            }
        }

        // Load store branch name and type from database
        if (currentStoreId != null) {
            lifecycleScope.launch {
                try {
                    val rows = supabase.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                    val row = rows.firstOrNull { it.store_id == currentStoreId }
                    currentBranchName = row?.branch ?: ""
                    currentStoreType = row?.type ?: ""
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Store branch/type load failed", e)
                    currentBranchName = ""
                    currentStoreType = ""
                }
            }
        }

        val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(R.color.presyo_orange)
        val shimmerContainer = findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmerContainer)
        val layoutEmptyState = findViewById<android.view.View>(R.id.layoutEmptyState)

        // --- Search Functionality (Supabase) ---
        fun loadProductsFromSupabase(showLoading: Boolean = false) {
            val sId = currentStoreId ?: return
            
            if (showLoading) {
                shimmerContainer.visibility = View.VISIBLE
                shimmerContainer.startShimmer()
                productRecyclerView.visibility = View.GONE
                layoutEmptyState.visibility = View.GONE
            }

            lifecycleScope.launch {
                try {
                    val query = currentQuery.takeIf { it.isNotBlank() }
                    val category = selectedCategory.takeIf { it != "PRICELIST" }

                    val rows = supabase.postgrest.rpc(
                        "get_store_products",
                        buildJsonObject {
                            put("p_store_id", sId)
                            if (category != null) put("p_category_filter", category)
                            if (query != null) put("p_search_query", query)
                        }
                    ).decodeList<UserProductRow>()

                    val rawProducts = rows.map { row ->
                        com.presyohan.app.adapter.Product(
                            row.product_id, row.name, row.description ?: "",
                            row.price, row.units ?: "", row.category ?: "",
                            is_public = row.is_public
                        )
                    }

                    // Group by category, sort categories alphabetically, sort items alphabetically
                    val groupedMap = rawProducts.groupBy { it.category.trim() }
                    val sortedGroups = groupedMap.entries.sortedBy { it.key.lowercase() }.map { entry ->
                        ProductGroup(
                            categoryName = entry.key,
                            itemCount = entry.value.size,
                            products = entry.value.sortedBy { it.name.lowercase() }
                        )
                    }

                    productGroups = sortedGroups
                    adapter.updateGroups(productGroups, activeOverlayProductId)

                    // Update UI Visibility
                    if (productGroups.isEmpty()) {
                        layoutEmptyState.visibility = View.VISIBLE
                        productRecyclerView.visibility = View.GONE
                    } else {
                        layoutEmptyState.visibility = View.GONE
                        productRecyclerView.visibility = View.VISIBLE
                    }

                } catch (e: Exception) {
                    Log.e("HomeActivity", "Products load failed", e)
                    // Fallback empty state if load failed
                    if (productGroups.isEmpty()) {
                        layoutEmptyState.visibility = View.VISIBLE
                        productRecyclerView.visibility = View.GONE
                    }
                } finally {
                    swipeRefreshLayout.isRefreshing = false
                    if (showLoading) {
                        shimmerContainer.stopShimmer()
                        shimmerContainer.visibility = View.GONE
                    }
                }
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadProductsFromSupabase(false)
            refreshSpinnerCategories(currentStoreId)
        }

        // Initial Load
        if (currentStoreId != null) {
            reloadProductsFn = {
                loadProductsFromSupabase(false)
                refreshSpinnerCategories(currentStoreId)
            }
            loadProductsFromSupabase(true)
        }

        // --- Bottom Sheet & Search Behavior Implementation ---
        val bottomSheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(searchBarContainer)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
        val btnSearchClear = findViewById<ImageView>(R.id.btnSearchClear)

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s.toString().trim().lowercase()
                btnSearchClear.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                loadProductsFromSupabase(false)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnSearchClear.setOnClickListener {
            searchEditText.text.clear()
            currentQuery = ""
            loadProductsFromSupabase(false)
        }

        // Search Button Click (Toggle Bottom Sheet State)
        searchItemButton?.setOnClickListener {
            bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
        }

        // Tap handle to toggle sheet state between COLLAPSED and EXPANDED
        val bottomSheetHandle = findViewById<View>(R.id.bottomSheetHandle)
        bottomSheetHandle?.setOnClickListener {
            if (bottomSheetBehavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            } else if (bottomSheetBehavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        // Bottom Sheet Behavior Callback
        bottomSheetBehavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED -> {
                        searchItemButton?.visibility = View.GONE
                    }
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED -> {
                        searchItemButton?.visibility = View.GONE
                        hideKeyboard(searchEditText)
                    }
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN -> {
                        searchItemButton?.visibility = View.VISIBLE
                        hideKeyboard(searchEditText)
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val collapsedHeight = bottomSheetBehavior.peekHeight
                val expandedHeight = bottomSheet.height
                val currentHeight = if (slideOffset >= 0) {
                    collapsedHeight + (expandedHeight - collapsedHeight) * slideOffset
                } else {
                    collapsedHeight * (1 + slideOffset)
                }
                val safetyPadding = (16 * resources.displayMetrics.density).toInt()
                productRecyclerView.setPadding(
                    productRecyclerView.paddingLeft,
                    productRecyclerView.paddingTop,
                    productRecyclerView.paddingRight,
                    (currentHeight + safetyPadding).toInt()
                )
            }
        })

        productRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // Scrolling down hides bottom sheet completely (only if dragged by user)
                if (dy > 10 && recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (bottomSheetBehavior.state != com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
                        bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
                    }
                }
            }
        })

        // Setup bottom sheet quick actions
        val btnManagePrices = findViewById<View>(R.id.btnManagePrices)
        btnManagePrices.setOnClickListener {
            val sId = currentStoreId ?: return@setOnClickListener
            val intent = Intent(this, ManageItemsActivity::class.java)
            intent.putExtra("storeId", sId)
            intent.putExtra("storeName", currentStoreName)
            startActivity(intent)
        }

        val btnManageCategories = findViewById<View>(R.id.btnManageCategories)
        btnManageCategories.setOnClickListener {
            val sId = currentStoreId ?: return@setOnClickListener
            val intent = Intent(this, ManageCategoryActivity::class.java)
            intent.putExtra("storeId", sId)
            intent.putExtra("storeName", currentStoreName)
            startActivity(intent)
        }

        // --- Category Logic ---
        setupCategorySpinner(currentStoreId, categoryLabel, categorySpinner, categoryDrawerButton) { category ->
            selectedCategory = category
            productRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            loadProductsFromSupabase(true)
        }
        categoryLabel.setOnClickListener { categorySpinner.performClick() }

        // --- Actions ---
        addButton.setOnClickListener {
            if (currentStoreId.isNullOrBlank()) return@setOnClickListener
            AddEditItemDialogHelper.showAddOrEditItemDialog(
                activity = this@HomeActivity,
                storeId = currentStoreId!!,
                storeName = currentStoreName ?: "Store",
                onComplete = {
                    reloadProductsFn?.invoke()
                }
            )
        }

        notifIcon.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        // --- Store Options Menu ---
        btnStoreOptions.setOnClickListener {
            showStoreMenu()
        }

        loadNotifBadge()
    }

    // --- Helper Methods ---

    /**
     * Displays the Store Menu Modal (Dialog)
     */
    private fun showStoreMenu() {
        val sId = currentStoreId ?: return
        val sName = currentStoreName ?: "Store"
        val isOwner = userRole == "owner"

        val dialog = Dialog(this)
        val view = if (isOwner) {
            LayoutInflater.from(this).inflate(R.layout.dialog_owner_menu, null)
        } else {
            LayoutInflater.from(this).inflate(R.layout.dialog_store_details, null)
        }
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set Dialog Width to 90% of Screen
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // Bind Common Header Views
        val txtStoreName = view.findViewById<TextView>(R.id.dialogStoreName)
        val txtStoreBranch = view.findViewById<TextView>(R.id.dialogStoreBranch)
        val txtStoreCategory = view.findViewById<TextView>(R.id.dialogStoreCategory)

        txtStoreName?.text = sName
        txtStoreBranch?.text = currentBranchName ?: ""
        txtStoreCategory?.text = currentStoreType ?: "Store Category"

        val txtCategoriesCount = view.findViewById<TextView>(R.id.dialogCategoriesCount)
        val txtItemsCount = view.findViewById<TextView>(R.id.dialogItemsCount)
        val txtMembersCount = view.findViewById<TextView>(R.id.dialogMembersCount)
        val txtOwnersCount = view.findViewById<TextView>(R.id.dialogOwnersCount)
        val txtManagersCount = view.findViewById<TextView>(R.id.dialogManagersCount)
        val txtEmployeesCount = view.findViewById<TextView>(R.id.dialogEmployeesCount)

        // Load stats and populate views (Only if views exist, i.e., in non-owner details modal)
        if (!isOwner) {
            lifecycleScope.launch {
                try {
                    // 1. Fetch categories count
                    val categories = supabase.postgrest.rpc(
                        "get_user_categories",
                        buildJsonObject { put("p_store_id", sId) }
                    ).decodeList<UserCategoryRow>()
                    val categoriesCount = categories.size

                    // 2. Fetch products count
                    val products = supabase.postgrest.rpc(
                        "get_store_products",
                        buildJsonObject { put("p_store_id", sId) }
                    ).decodeList<UserProductRow>()
                    val productsCount = products.size

                    // 3. Fetch members count & details
                    val members = supabase.postgrest.rpc(
                        "get_store_members",
                        buildJsonObject { put("p_store_id", sId) }
                    ).decodeList<StoreMemberUser>()

                    val ownersCount = members.count { it.role.equals("owner", ignoreCase = true) }
                    val managersCount = members.count { it.role.equals("manager", ignoreCase = true) }
                    val employeesCount = members.count { it.role.equals("employee", ignoreCase = true) }
                    val totalMembers = members.size

                    txtCategoriesCount?.text = categoriesCount.toString()
                    txtItemsCount?.text = productsCount.toString()
                    txtMembersCount?.text = totalMembers.toString()
                    txtOwnersCount?.text = ownersCount.toString()
                    txtManagersCount?.text = managersCount.toString()
                    txtEmployeesCount?.text = employeesCount.toString()

                } catch (e: Exception) {
                    Log.e("HomeActivity", "Stats loading failed", e)
                }
            }
        }

        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            dialog.dismiss()
        }

        if (isOwner) {
            // OWNER SPECIFIC BINDINGS
            val layoutSettings = view.findViewById<View>(R.id.layoutSettings)
            val layoutInvite = view.findViewById<View>(R.id.layoutInvite)

            layoutSettings?.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, ManageStoreActivity::class.java)
                intent.putExtra("storeId", sId)
                intent.putExtra("storeName", sName)
                startActivity(intent)
            }

            layoutInvite?.setOnClickListener {
                dialog.dismiss()
                showInviteStaffWithCode(sId)
            }

        } else {
            // STAFF/MANAGER SPECIFIC BINDINGS
            val txtRoleDesignation = view.findViewById<TextView>(R.id.textRoleDesignation)
            val txtStoreId = view.findViewById<TextView>(R.id.dialogStoreId)
            val txtCreatedAt = view.findViewById<TextView>(R.id.dialogCreatedAt)
            val txtVisibilityStatus = view.findViewById<TextView>(R.id.dialogVisibilityStatus)
            val txtJoinCode = view.findViewById<TextView>(R.id.dialogJoinCode)
            val btnLeaveStore = view.findViewById<View>(R.id.btnLeaveStore)

            val displayRole = when (userRole?.lowercase(Locale.ROOT)) {
                "employee", "staff" -> "Sales staff"
                "manager" -> "Manager"
                else -> "Staff"
            }
            val prefix = "You are the "
            val suffix = " of this store."
            val fullText = "$prefix$displayRole$suffix"
            val spannable = android.text.SpannableString(fullText)
            val start = prefix.length
            val end = start + displayRole.length
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start,
                end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            txtRoleDesignation?.text = spannable

            txtStoreId?.text = "SID25-009"

            // Load store details for staff
            lifecycleScope.launch {
                try {
                    @Serializable
                    data class StoreDetailsRow(val id: String, val created_at: String? = null, val is_public: Boolean = false, val invite_code: String? = null, val invite_code_created_at: String? = null)
                    val rows = supabase.postgrest["stores"].select {
                        filter { eq("id", sId) }
                        limit(1)
                    }.decodeList<StoreDetailsRow>()
                    
                    val storeRow = rows.firstOrNull()
                    if (storeRow != null) {
                        // format created_at date
                        val rawDate = storeRow.created_at?.split("T")?.firstOrNull() ?: ""
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
                        txtCreatedAt?.text = dateText
                        txtVisibilityStatus?.text = if (storeRow.is_public) "Public" else "Private"

                        // Check invite code expiration
                        val inviteCode = storeRow.invite_code
                        val createdIso = storeRow.invite_code_created_at
                        val createdMillis = parseInviteCreatedMillis(createdIso)
                        val isExpired = createdMillis == null || (System.currentTimeMillis() - createdMillis > 86400000L)
                        
                        txtJoinCode?.text = if (isExpired || inviteCode.isNullOrBlank()) {
                            "Expired"
                        } else {
                            inviteCode
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Details loading failed", e)
                }
            }

            btnLeaveStore?.setOnClickListener {
                showLeaveDeleteConfirmation(sId, sName, isDelete = false, dialog)
            }
        }

        dialog.show()
    }

    private fun showLeaveDeleteConfirmation(storeId: String, storeName: String, isDelete: Boolean, menuDialog: Dialog) {
        val confirmDialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        confirmDialog.setContentView(view)
        confirmDialog.setCancelable(true)
        confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val btnCancel = view.findViewById<Button>(R.id.btnNegative)
        val btnAction = view.findViewById<Button>(R.id.btnPositive)

        if (isDelete) {
            title.text = "Delete Store"
            message.text = "Are you sure you want to delete this store?\n\nYour store \"$storeName\" permanently erase all products, members, and data. This cannot be undone."
            btnAction.text = "Delete"
        } else {
            title.text = "Leave Store"
            message.text = "Are you sure you want to leave this store?\n\nYou will no longer be a member of this store."
            btnAction.text = "Leave"
        }

        btnCancel.text = "Cancel"
        btnCancel.setOnClickListener { confirmDialog.dismiss() }

        btnAction.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (isDelete) {
                        supabase.postgrest["stores"].delete { filter { eq("id", storeId) } }
                        Toast.makeText(this@HomeActivity, "Store deleted successfully.", Toast.LENGTH_SHORT).show()
                    } else {
                        supabase.postgrest.rpc("leave_store", buildJsonObject { put("p_store_id", storeId) })
                        Toast.makeText(this@HomeActivity, "You have left the store.", Toast.LENGTH_SHORT).show()
                    }
                    confirmDialog.dismiss()
                    menuDialog.dismiss()
                    finish() // Return to Store Selection
                } catch (e: Exception) {
                    val errorMsg = if (e.message?.contains("sole owner", true) == true)
                        "You are the only owner. Delete the store instead."
                    else "Action failed. Check internet."
                    Toast.makeText(this@HomeActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
        confirmDialog.show()
        confirmDialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // --- Invite Code Logic ---

    private fun parseInviteCreatedMillis(createdIso: String?): Long? {
        if (createdIso.isNullOrBlank()) return null
        val clean = createdIso.trim().replace(" ", "T")
        val hasTimezone = clean.contains("+") || (clean.lastIndexOf("-") > clean.indexOf("T")) || clean.endsWith("Z")
        val parsedStr = if (hasTimezone) clean else clean + "Z"
        return try {
            java.time.Instant.parse(parsedStr).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(parsedStr).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDateTime.parse(clean).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) { null }
            }
        }
    }

    private fun startInviteCountdown(expiryText: TextView, codeText: TextView, copyBtn: View, expiryMillis: Long) {
        inviteCodeCountdownJob?.cancel()
        inviteCodeCountdownJob = lifecycleScope.launch {
            try {
                while (true) {
                    val remaining = expiryMillis - System.currentTimeMillis()
                    if (remaining <= 0) {
                        codeText.visibility = View.VISIBLE
                        copyBtn.visibility = View.VISIBLE
                        expiryText.text = "Code expired"
                        break
                    }
                    val hrs = remaining / 3600000
                    val mins = (remaining % 3600000) / 60000
                    val secs = (remaining % 60000) / 1000
                    expiryText.text = String.format("Expires in %02d:%02d:%02d", hrs, mins, secs)
                    kotlinx.coroutines.delay(1000)
                }
            } catch (_: Exception) {
                expiryText.text = "Expires soon"
            }
        }
    }

    private fun showInviteStaffWithCode(storeId: String) {
        lifecycleScope.launch {
            try {
                val rows = supabase.postgrest["stores"].select {
                    filter { eq("id", storeId) }
                    limit(1)
                }.decodeList<StoreInviteFields>()
                val row = rows.firstOrNull()
                val code = row?.invite_code
                val createdIso = row?.invite_code_created_at

                val createdMillis = parseInviteCreatedMillis(createdIso)
                val expiryMillis = createdMillis?.plus(86400000L)

                runOnUiThread {
                    showInviteStaffDialog(code, expiryMillis)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@HomeActivity, "Unable to fetch code.", Toast.LENGTH_SHORT).show()
                    showInviteStaffDialog(null, null)
                }
            }
        }
    }

    private fun showInviteStaffDialog(inviteCode: String?, expiryMillis: Long?) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_invite_staff, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val layoutDirectInvitation = view.findViewById<View>(R.id.layoutDirectInvitation)
        val layoutJoinStoreCode = view.findViewById<View>(R.id.layoutJoinStoreCode)
        val btnSwitchToCode = view.findViewById<View>(R.id.btnSwitchToCode)
        val btnSwitchToDirect = view.findViewById<View>(R.id.btnSwitchToDirect)
        val btnDone = view.findViewById<View>(R.id.btnDone)

        val layoutCodeInactive = view.findViewById<View>(R.id.layoutCodeInactive)
        val layoutCodeActive = view.findViewById<View>(R.id.layoutCodeActive)

        val codeText = view.findViewById<TextView>(R.id.storeCodeText)
        val expiryText = view.findViewById<TextView>(R.id.inviteCodeExpiry)
        val copyBtnActive = view.findViewById<View>(R.id.btnCopyCodeActive)

        val btnGenerateCodeInactive = view.findViewById<View>(R.id.btnGenerateCodeInactive)
        val btnGenerateCodeActive = view.findViewById<View>(R.id.btnGenerateCodeActive)
        val btnRevokeCodeActive = view.findViewById<View>(R.id.btnRevokeCodeActive)

        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchLoader = view.findViewById<View>(R.id.searchLoader)
        val textNotFound = view.findViewById<TextView>(R.id.textNotFound)
        val userResultContainer = view.findViewById<LinearLayout>(R.id.userResultContainer)
        val foundAvatar = view.findViewById<View>(R.id.foundUserAvatar) as? ImageView
        val foundName = view.findViewById<TextView>(R.id.foundUserName)
        val foundDetails = view.findViewById<TextView>(R.id.foundUserDetails)
        val btnInvite = view.findViewById<Button>(R.id.btnInvite)
        val inviteErrorText = view.findViewById<TextView>(R.id.inviteErrorText)

        var selectedUser: SearchedUser? = null
        val searchHandler = Handler(Looper.getMainLooper())
        var searchRunnable: Runnable? = null

        fun updateCodeUI(code: String?, expiresAt: Long?) {
            inviteCodeCountdownJob?.cancel()
            val now = System.currentTimeMillis()
            val isValid = !code.isNullOrBlank() && expiresAt != null && expiresAt > now

            if (isValid) {
                layoutCodeInactive.visibility = View.GONE
                layoutCodeActive.visibility = View.VISIBLE
                codeText.text = code
                startInviteCountdown(expiryText, codeText, copyBtnActive, expiresAt)
            } else {
                layoutCodeInactive.visibility = View.VISIBLE
                layoutCodeActive.visibility = View.GONE
                expiryText.text = ""
            }
        }
        updateCodeUI(inviteCode, expiryMillis)

        btnSwitchToCode.setOnClickListener {
            layoutDirectInvitation.visibility = View.GONE
            layoutJoinStoreCode.visibility = View.VISIBLE
        }

        btnSwitchToDirect.setOnClickListener {
            layoutJoinStoreCode.visibility = View.GONE
            layoutDirectInvitation.visibility = View.VISIBLE
        }

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        copyBtnActive.setOnClickListener {
            val code = codeText.text.toString()
            if (code.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Store Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Code copied.", Toast.LENGTH_SHORT).show()
            }
        }

        fun generateCode() {
            if (userRole != "owner") {
                Toast.makeText(this, "Only owner can generate codes.", Toast.LENGTH_SHORT).show()
                return
            }
            val sId = currentStoreId ?: return
            btnGenerateCodeInactive.isEnabled = false
            btnGenerateCodeActive.isEnabled = false

            lifecycleScope.launch {
                try {
                    val params = buildJsonObject { put("p_store_id", sId) }
                    val rows = supabase.postgrest.rpc("regenerate_invite_code", params).decodeList<InviteCodeReturn>()
                    val row = rows.firstOrNull()
                    val newCode = row?.invite_code
                    val created = row?.invite_code_created_at

                    val newCreatedMillis = parseInviteCreatedMillis(created)
                    val newExpiry = newCreatedMillis?.plus(86400000L)

                    runOnUiThread {
                        updateCodeUI(newCode, newExpiry)
                        Toast.makeText(this@HomeActivity, "Code updated.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@HomeActivity, "Failed to generate code.", Toast.LENGTH_SHORT).show()
                } finally {
                    btnGenerateCodeInactive.isEnabled = true
                    btnGenerateCodeActive.isEnabled = true
                }
            }
        }

        btnGenerateCodeInactive.setOnClickListener { generateCode() }
        btnGenerateCodeActive.setOnClickListener { generateCode() }

        btnRevokeCodeActive.setOnClickListener {
            if (userRole != "owner") {
                Toast.makeText(this, "Only owner can revoke codes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sId = currentStoreId ?: return@setOnClickListener
            btnRevokeCodeActive.isEnabled = false

            lifecycleScope.launch {
                try {
                    supabase.postgrest.from("stores").update(
                        buildJsonObject {
                            put("invite_code", null as String?)
                            put("invite_code_created_at", null as String?)
                        }
                    ) {
                        filter {
                            eq("id", sId)
                        }
                    }
                    runOnUiThread {
                        updateCodeUI(null, null)
                        Toast.makeText(this@HomeActivity, "Code revoked.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@HomeActivity, "Failed to revoke code.", Toast.LENGTH_SHORT).show()
                } finally {
                    btnRevokeCodeActive.isEnabled = true
                }
            }
        }

        fun toggleInviteButton() {
            btnInvite.isEnabled = selectedUser != null
            btnInvite.alpha = if (selectedUser != null) 1.0f else 0.5f
        }

        val performSearch = { query: String ->
            searchLoader.visibility = View.VISIBLE
            textNotFound.visibility = View.GONE
            userResultContainer.visibility = View.GONE
            selectedUser = null
            toggleInviteButton()

            lifecycleScope.launch {
                try {
                    val results = supabase.postgrest.rpc("search_app_user", buildJsonObject { put("search_term", query) }).decodeList<SearchedUser>()
                    searchLoader.visibility = View.GONE

                    if (results.isNotEmpty()) {
                        val user = results[0]
                        selectedUser = user

                        userResultContainer.visibility = View.VISIBLE
                        foundName.text = user.name ?: "Unnamed User"
                        val code = user.user_code ?: "NO-ID"
                        val email = user.email ?: ""
                        foundDetails.text = "$code • $email"

                        if (!user.avatar_url.isNullOrBlank()) {
                            foundAvatar?.load(user.avatar_url) {
                                crossfade(true)
                                transformations(CircleCropTransformation())
                            }
                        } else {
                            foundAvatar?.setImageResource(R.drawable.avatar_default)
                        }
                    } else {
                        textNotFound.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    Log.e("InviteStaff", "Search failed", e)
                    searchLoader.visibility = View.GONE
                    textNotFound.text = "Error searching user."
                    textNotFound.visibility = View.VISIBLE
                }
                toggleInviteButton()
            }
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                inviteErrorText.visibility = View.GONE
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    searchLoader.visibility = View.GONE
                    textNotFound.visibility = View.GONE
                    userResultContainer.visibility = View.GONE
                    selectedUser = null
                    toggleInviteButton()
                    return
                }
                searchRunnable = Runnable { performSearch(query) }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val spinnerRole = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerRole)
        val rolesDisplay = listOf("View only price list", "Manage prices")
        val rolesValue = listOf("employee", "manager")
        val adp = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rolesDisplay)
        spinnerRole.setAdapter(adp)
        spinnerRole.setText(rolesDisplay[0], false)

        view.findViewById<Button>(R.id.btnBack).setOnClickListener { dialog.dismiss() }

        btnInvite.setOnClickListener {
            val user = selectedUser ?: return@setOnClickListener
            val sId = currentStoreId ?: return@setOnClickListener
            val roleText = spinnerRole.text.toString()
            val roleIdx = rolesDisplay.indexOf(roleText).coerceAtLeast(0)
            val selectedRole = rolesValue.getOrElse(roleIdx) { "employee" }

            btnInvite.text = "Inviting..."
            btnInvite.isEnabled = false

            lifecycleScope.launch {
                try {
                    val params = buildJsonObject {
                        put("p_store_id", sId)
                        put("p_email", user.email)
                        put("p_role", selectedRole)
                    }
                    supabase.postgrest.rpc("send_store_invitation", params)
                    Toast.makeText(this@HomeActivity, "Invitation sent to ${user.name}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    val msg = e.message ?: "Failed to invite."
                    inviteErrorText.text = if (msg.contains("already a member", ignoreCase = true)) "User is already a member." else "Failed to send invitation."
                    inviteErrorText.visibility = View.VISIBLE
                    btnInvite.text = "Invite"
                    btnInvite.isEnabled = true
                }
            }
        }

        dialog.setOnDismissListener { inviteCodeCountdownJob?.cancel() }
        dialog.show()
    }

    private fun showImportDialogForStore(storeId: String?, storeName: String?) {
        val intent = Intent(this, AddMultipleItemsActivity::class.java).apply {
            putExtra("storeId", storeId)
            putExtra("storeName", storeName)
            putExtra("showImportDialog", true)
        }
        startActivity(intent)
    }

    /**
     * Updates the UI Layout based on the user's role.
     * Specific request: For Staff, hide add button and widen search bar to center.
     */
    private fun updateUiForRole(role: String?) {
        val isOwnerOrManager = role == "owner" || role == "manager"
        val layoutSheetActions = findViewById<View>(R.id.layoutSheetActions)
        val bottomSheetHandle = findViewById<View>(R.id.bottomSheetHandle)

        if (isOwnerOrManager) {
            layoutSheetActions?.visibility = View.VISIBLE
            bottomSheetHandle?.visibility = View.VISIBLE
            addButton.visibility = View.VISIBLE
        } else {
            layoutSheetActions?.visibility = View.GONE
            bottomSheetHandle?.visibility = View.GONE
            addButton.visibility = View.GONE
        }

        try {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(searchBarContainer)
            behavior.isDraggable = isOwnerOrManager
        } catch (e: Exception) {
            Log.e("HomeActivity", "Failed to update bottom sheet draggable state", e)
        }
    }

    // --- Search and Scroll Helper Methods (Obsolete in Bottom Sheet design) ---

    private fun showSearchBar(showKeyboard: Boolean) {
        // Obsolete in new bottom sheet layout
    }

    private fun hideSearchBar(hideKeyboard: Boolean) {
        // Obsolete in new bottom sheet layout
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        searchEditText.clearFocus()
        isKeyboardOpen = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    hideKeyboard(v)
                    hideSearchBar(hideKeyboard = false)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun showLogoutDialog(drawerLayout: DrawerLayout) {
        val dialog = android.app.Dialog(this)
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.dialogTitle).text = "Logout"
        view.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to log out?\n\nYou can sign back in anytime. See you again soon!"
        val btnNegative = view.findViewById<android.widget.Button>(R.id.btnNegative)
        val btnPositive = view.findViewById<android.widget.Button>(R.id.btnPositive)
        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { dialog.dismiss() }
        btnPositive.text = "Logout"
        btnPositive.setOnClickListener {
            lifecycleScope.launch {
                try { SupabaseAuthService.signOut() } catch (_: Exception) { }
                val intent = Intent(this@HomeActivity, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
            dialog.dismiss()
            drawerLayout.close()
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadStoreDetails(storeId: String, storeName: String?, storeText: TextView, storeBranchText: TextView) {
        lifecycleScope.launch {
            try {
                val rows = supabase.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                val row = rows.firstOrNull { it.store_id == storeId }
                storeText.text = row?.name ?: (storeName ?: "Store")
                storeBranchText.text = row?.branch ?: ""
                // SAVE BRANCH NAME FOR DIALOG
                currentBranchName = row?.branch ?: ""
            } catch (e: Exception) {
                Log.e("HomeActivity", "Store header load failed", e)
            }
        }
    }

    private fun setupCategorySpinner(storeId: String?, categoryLabel: TextView, categorySpinner: Spinner, categoryDrawerButton: ImageView, onCategorySelected: (String) -> Unit) {
        spinnerAdapter = object : android.widget.ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, spinnerCategories
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.text = spinnerCategories[position].uppercase()
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.text = spinnerCategories[position].uppercase()
                return view
            }
        }
        categorySpinner.adapter = spinnerAdapter
        categorySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val cat = spinnerCategories[position]
                categoryLabel.text = cat.uppercase()
                onCategorySelected(cat)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        categorySpinner.background = null
        categorySpinner.layoutParams.width = 1
        categorySpinner.requestLayout()
        categoryDrawerButton.setOnClickListener { categorySpinner.performClick() }

        refreshSpinnerCategories(storeId)
    }

    private fun refreshSpinnerCategories(storeId: String?) {
        val sId = storeId ?: return
        lifecycleScope.launch {
            try {
                val rows = supabase.postgrest.rpc("get_user_categories", buildJsonObject { put("p_store_id", sId) }).decodeList<UserCategoryRow>()
                val selected = spinnerCategories.getOrNull(findViewById<Spinner>(R.id.categorySpinner)?.selectedItemPosition ?: 0) ?: "PRICELIST"
                spinnerCategories.clear()
                spinnerCategories.add("PRICELIST")
                for (row in rows) {
                    val catName = row.name.uppercase()
                    if (!spinnerCategories.contains(catName)) {
                        spinnerCategories.add(catName)
                    }
                }
                spinnerAdapter.notifyDataSetChanged()
                
                val index = spinnerCategories.indexOf(selected)
                if (index >= 0) {
                    findViewById<Spinner>(R.id.categorySpinner)?.setSelection(index, false)
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Failed to refresh spinner categories: ${e.localizedMessage}")
            }
        }
    }

    private fun showManageItemDialog(product: com.presyohan.app.adapter.Product, storeId: String?, storeName: String?) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_manage_item, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.textProductName).text = product.name
        val descView = view.findViewById<TextView>(R.id.textProductDescription)
        if (product.description.isBlank()) {
            descView.visibility = View.GONE
        } else {
            descView.visibility = View.VISIBLE
            descView.text = product.description
        }
        view.findViewById<TextView>(R.id.textProductPrice).text = "₱%,.2f".format(java.util.Locale.US, product.price)
        view.findViewById<TextView>(R.id.textProductUnit).text = product.volume

        view.findViewById<ImageView>(R.id.btnEdit).setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            dialog.dismiss()
            AddEditItemDialogHelper.showAddOrEditItemDialog(
                activity = this@HomeActivity,
                storeId = storeId,
                storeName = storeName ?: "Store",
                productData = EditProductData(
                    id = product.id,
                    name = product.name,
                    description = product.description,
                    price = product.price,
                    unit = product.volume,
                    category = product.category,
                    isPublic = product.is_public
                ),
                onComplete = {
                    reloadProductsFn?.invoke()
                }
            )
        }
        view.findViewById<ImageView>(R.id.btnDelete).setOnClickListener {
            val confirmDialog = android.app.Dialog(this)
            val confirmView = layoutInflater.inflate(R.layout.dialog_reusable_template, null)
            confirmDialog.setContentView(confirmView)
            confirmDialog.setCancelable(true)
            confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            confirmView.findViewById<TextView>(R.id.dialogTitle).text = "Delete Item"
            confirmView.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to delete this item?"
            val btnNegative = confirmView.findViewById<android.widget.Button>(R.id.btnNegative)
            val btnPositive = confirmView.findViewById<android.widget.Button>(R.id.btnPositive)
            btnNegative.text = "Cancel"
            btnNegative.setOnClickListener { confirmDialog.dismiss() }
            btnPositive.text = "Delete"
            btnPositive.setOnClickListener {
                if(storeId != null) {
                    lifecycleScope.launch {
                        try {
                            supabase.postgrest["products"].delete { filter { eq("id", product.id); eq("store_id", storeId) } }
                            Toast.makeText(this@HomeActivity, "Item deleted.", Toast.LENGTH_SHORT).show()
                            deleteCategoryIfEmpty(storeId, product.category.trim())
                            confirmDialog.dismiss()
                            dialog.dismiss()
                            recreate()
                        } catch(e: Exception) { Toast.makeText(this@HomeActivity, "Error deleting", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            confirmDialog.show()
            confirmDialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun showDeleteConfirmationDialog(product: com.presyohan.app.adapter.Product) {
        val sId = currentStoreId ?: return
        val confirmDialog = android.app.Dialog(this)
        val confirmView = layoutInflater.inflate(R.layout.dialog_reusable_template, null)
        confirmDialog.setContentView(confirmView)
        confirmDialog.setCancelable(true)
        confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        confirmView.findViewById<TextView>(R.id.dialogTitle).text = "Delete Item"
        confirmView.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to delete this item?"
        val btnNegative = confirmView.findViewById<android.widget.Button>(R.id.btnNegative)
        val btnPositive = confirmView.findViewById<android.widget.Button>(R.id.btnPositive)
        btnNegative.text = "Cancel"
        btnNegative.setOnClickListener { confirmDialog.dismiss() }
        btnPositive.text = "Delete"
        btnPositive.setOnClickListener {
            confirmDialog.dismiss()
            
            // Backup the current state for rollback
            val backupGroups = productGroups
            
            // Optimistic UI update: remove item locally
            val updatedGroups = productGroups.map { group ->
                if (group.products.any { it.id == product.id }) {
                    val remainingProducts = group.products.filter { it.id != product.id }
                    group.copy(products = remainingProducts, itemCount = remainingProducts.size)
                } else {
                    group
                }
            }.filter { it.itemCount > 0 }
            
            productGroups = updatedGroups
            val mainRecyclerView = findViewById<RecyclerView>(R.id.productRecyclerView)
            val emptyStateView = findViewById<View>(R.id.layoutEmptyState)
            
            (mainRecyclerView.adapter as? com.presyohan.app.adapter.CategoryGroupAdapter)?.updateGroups(productGroups, activeOverlayProductId)
            
            if (productGroups.isEmpty()) {
                emptyStateView.visibility = View.VISIBLE
                mainRecyclerView.visibility = View.GONE
            }
            
            lifecycleScope.launch {
                try {
                    supabase.postgrest["products"].delete { filter { eq("id", product.id); eq("store_id", sId) } }
                    Toast.makeText(this@HomeActivity, "Item deleted.", Toast.LENGTH_SHORT).show()
                    deleteCategoryIfEmpty(sId, product.category.trim())
                    // Refresh state to match Supabase exactly
                    reloadProductsFn?.invoke()
                } catch(e: Exception) {
                    Toast.makeText(this@HomeActivity, "Failed to delete item. Reverting change.", Toast.LENGTH_SHORT).show()
                    // Rollback UI update
                    productGroups = backupGroups
                    (mainRecyclerView.adapter as? com.presyohan.app.adapter.CategoryGroupAdapter)?.updateGroups(productGroups, activeOverlayProductId)
                    if (productGroups.isNotEmpty()) {
                        emptyStateView.visibility = View.GONE
                        mainRecyclerView.visibility = View.VISIBLE
                    }
                }
            }
        }
        confirmDialog.show()
        confirmDialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadNotifBadge() {
        val notifDot = findViewById<View>(R.id.notifDot)
        val userIdNotif = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (notifDot != null && userIdNotif != null) {
            lifecycleScope.launch {
                try {
                    val rows = SupabaseProvider.client.postgrest["notifications"].select {
                        filter {
                            eq("receiver_user_id", userIdNotif)
                            eq("read", false)
                        }
                        limit(1)
                    }.decodeList<com.presyohan.app.HomeActivity.NotificationRow>()
                    notifDot.visibility = if (rows.isNotEmpty()) View.VISIBLE else View.GONE
                } catch (e: Exception) { notifDot.visibility = View.GONE }
            }
        } else if (notifDot != null) {
            notifDot.visibility = View.GONE
        }
    }

    private suspend fun deleteCategoryIfEmpty(storeId: String, categoryName: String) {
        if(categoryName.isEmpty()) return
        try {
            val catRows = supabase.postgrest["categories"].select {
                filter {
                    eq("store_id", storeId)
                    eq("name", categoryName)
                }
                limit(1)
            }.decodeList<CategoryRow>()
            val cat = catRows.firstOrNull() ?: return

            @Serializable data class PID(val id: String)
            val pRows = supabase.postgrest["products"].select {
                filter {
                    eq("store_id", storeId)
                    eq("category_id", cat.id)
                }
                limit(1)
            }.decodeList<PID>()

            if(pRows.isEmpty()) {
                supabase.postgrest["categories"].delete {
                    filter {
                        eq("store_id", storeId)
                        eq("id", cat.id)
                    }
                }
            }
        } catch(_:Exception){}
    }

    private fun updateDrawerHeader(navView: NavigationView) {
        val h = navView.getHeaderView(0)
        val uT = h.findViewById<TextView>(R.id.drawerUserName)
        val codeT = h.findViewById<TextView>(R.id.drawerUserCode)
        val img = h.findViewById<ImageView>(R.id.drawerUserIcon)
        val u = SupabaseProvider.client.auth.currentUserOrNull()

        // Default state
        uT.text = "User"
        codeT?.visibility = View.GONE
        img.setImageResource(R.drawable.avatar_default)
        img.setColorFilter(ContextCompat.getColor(this, R.color.white))

        lifecycleScope.launch {
            // Fetch full profile (name + user_code)
            val profile = SupabaseAuthService.getUserProfile()
            if (profile != null) {
                if (!profile.name.isNullOrBlank()) {
                    uT.text = profile.name
                }
                if (!profile.user_code.isNullOrBlank()) {
                    val formattedId = profile.user_code.uppercase()
                    codeT?.text = "ID: $formattedId"
                    codeT?.visibility = View.VISIBLE
                }
                if (!profile.avatar_url.isNullOrBlank()) {
                    img.clearColorFilter()
                    img.load(profile.avatar_url) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        error(R.drawable.avatar_default)
                    }
                }
            } else {
                // Fallback name from metadata if DB fails
                val simpleName = SupabaseAuthService.getDisplayName()
                if (simpleName != null) uT.text = simpleName
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sId = intent.getStringExtra("storeId")
        val sName = intent.getStringExtra("storeName")
        SessionManager.markStoreHome(this, sId, sName)
        reloadProductsFn?.invoke()
        refreshSpinnerCategories(sId ?: currentStoreId)
        loadNotifBadge()
    }

    override fun onBackPressed() {
        val isSearching = currentQuery.isNotEmpty()
        val isFilteringCategory = selectedCategory != null && selectedCategory != "PRICELIST"

        if (isSearching || isFilteringCategory) {
            if (isSearching) {
                searchEditText.text.clear()
                currentQuery = ""
            }
            if (isFilteringCategory) {
                val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
                categorySpinner?.setSelection(0)
                selectedCategory = "PRICELIST"
            } else {
                reloadProductsFn?.invoke()
            }
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            finishAffinity()
        } else {
            lastBackPress = now
            Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Export Logic ---
    private fun exportPricelistToExcel() {
        if (currentStoreId.isNullOrBlank()) return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            var overlayVisible = true
            try {
                val rows = supabase.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject {
                        put("p_store_id", currentStoreId!!)
                        put("p_category_filter", "PRICELIST")
                        put("p_search_query", null as String?)
                    }
                ).decodeList<StoreProductExportRow>()

                if (rows.isEmpty()) {
                    Toast.makeText(this@HomeActivity, "No products to export.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val sorted = rows.sortedWith(compareBy(
                    { (it.category ?: "").lowercase() },
                    { (it.name ?: "").lowercase() }
                ))

                LoadingOverlayHelper.hide(loadingOverlay)
                overlayVisible = false
                showExportConfirmationDialog(sorted)
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "Failed to export.", Toast.LENGTH_LONG).show()
            } finally {
                if (overlayVisible) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    private fun showExportConfirmationDialog(rows: List<StoreProductExportRow>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_confirmation, null)
        val rowsCountView = dialogView.findViewById<TextView>(R.id.rowsCount)
        val rowsLabelView = dialogView.findViewById<TextView>(R.id.rowsLabel)
        val scopeValueView = dialogView.findViewById<TextView>(R.id.scopeValue)
        val subtitleView = dialogView.findViewById<TextView>(R.id.exportSubtitle)
        val exportDetailsCard = dialogView.findViewById<View>(R.id.exportDetailsCard)
        val exportTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.exportTypeGroup)
        val notePreviewContainer = dialogView.findViewById<View>(R.id.notePreviewContainer)
        val notePreviewText = dialogView.findViewById<TextView>(R.id.notePreviewText)
        val noteMetaText = dialogView.findViewById<TextView>(R.id.noteMetaText)
        val btnCopyNote = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCopyNote)
        val btnConfirmExport = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnConfirmExport)

        rowsCountView.text = rows.size.toString()
        rowsLabelView.text = if (rows.size == 1) "Row to export" else "Rows to export"
        scopeValueView.text = "All products"
        subtitleView.text = "Generate a formatted Excel file or a Google Keep-ready note."

        val noteText = buildNoteText(rows, currentStoreName, currentBranchName)
        notePreviewText.text = if (noteText.isNotBlank()) noteText else "No products available. Add items to generate a note."
        noteMetaText.text = "${rows.size} items • ${noteText.length} characters"
        btnCopyNote.isEnabled = noteText.isNotBlank()
        btnCopyNote.setOnClickListener {
            if (noteText.isNotBlank()) {
                copyNoteToClipboard(noteText)
            } else {
                Toast.makeText(this, "Nothing to copy.", Toast.LENGTH_SHORT).show()
            }
        }

        var selectedExportType = ExportType.EXCEL
        fun refreshExportTypeUi() {
            val isExcel = selectedExportType == ExportType.EXCEL
            exportDetailsCard.visibility = if (isExcel) View.VISIBLE else View.GONE
            notePreviewContainer.visibility = if (isExcel) View.GONE else View.VISIBLE
            btnConfirmExport.text = if (isExcel) "Export Excel" else "Share Note"
            btnConfirmExport.isEnabled = if (isExcel) true else noteText.isNotBlank()
        }

        exportTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedExportType = if (checkedId == R.id.rbExportNotes) ExportType.NOTES else ExportType.EXCEL
            refreshExportTypeUi()
        }
        exportTypeGroup.check(R.id.rbExportExcel)
        refreshExportTypeUi()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<View>(R.id.btnCancelExport).setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmExport.setOnClickListener {
            if (selectedExportType == ExportType.EXCEL) {
                dialog.dismiss()
                lifecycleScope.launch {
                    LoadingOverlayHelper.show(loadingOverlay)
                    try {
                        performPricelistExport(rows)
                    } finally {
                        LoadingOverlayHelper.hide(loadingOverlay)
                    }
                }
            } else {
                if (noteText.isBlank()) {
                    Toast.makeText(this, "No products available for notes.", Toast.LENGTH_SHORT).show()
                } else {
                    shareNoteText(noteText)
                }
            }
        }

        dialog.show()
    }

    private fun performPricelistExport(rows: List<StoreProductExportRow>) {
        if (rows.isEmpty()) {
            Toast.makeText(this, "No products to export.", Toast.LENGTH_SHORT).show()
            return
        }

        val filename = formatExportFilename()
        val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        val output: java.io.OutputStream? = try {
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
            Toast.makeText(this, "Failed to create file.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val wb = org.dhatim.fastexcel.Workbook(output, "Presyohan", "1.0")
            val ws = wb.newWorksheet("Pricelist")

            ws.value(0, 0, "Presyohan")
            ws.style(0, 0).bold().set()

            ws.value(1, 0, "Store: ${currentStoreName ?: ""} — Branch: ${currentBranchName ?: ""}")
            val exporter = try { SupabaseAuthService.getDisplayNameImmediate() } catch (_: Exception) { "User" }
            val exportedAt = java.util.Date().toLocaleString()
            ws.value(2, 0, "Exported by: ${exporter}    |    Exported at: ${exportedAt}")

            val headers = listOf("Category", "Name", "Description", "Unit", "Price")
            headers.forEachIndexed { idx, h ->
                ws.value(4, idx, h)
                ws.style(4, idx).bold().set()
            }

            var rowIndex = 5
            rows.forEach { r ->
                ws.value(rowIndex, 0, r.category ?: "")
                ws.value(rowIndex, 1, r.name ?: "")
                ws.value(rowIndex, 2, r.description ?: "")
                ws.value(rowIndex, 3, r.units ?: "")
                val priceNum = r.price ?: 0.0
                ws.value(rowIndex, 4, priceNum)
                ws.style(rowIndex, 4).format("\"₱\"#,##0.00").set()
                rowIndex++
            }

            try {
                ws.width(0, 20.0)
                ws.width(1, 28.0)
                ws.width(2, 40.0)
                ws.width(3, 12.0)
                ws.width(4, 14.0)
            } catch (_: Throwable) { /* optional */ }

            wb.finish()
            output.flush()
            Toast.makeText(this, "Excel exported to Downloads.", Toast.LENGTH_SHORT).show()
            notifyExportSuccessOrRequest(filename)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            try { output.close() } catch (_: Exception) {}
        }
    }

    private fun buildNoteText(rows: List<StoreProductExportRow>, storeName: String?, branchName: String?): String {
        if (rows.isEmpty()) return ""
        val priceFormat = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val grouped = rows.groupBy {
            val cat = it.category?.trim()
            if (cat.isNullOrBlank()) "General" else cat
        }
        val sortedCategories = grouped.keys.sortedWith(
            compareBy<String> { it.lowercase(Locale.getDefault()) }.thenBy { it }
        )
        val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val lines = mutableListOf<String>()
        lines.add("PRICELIST:")
        val cleanStore = storeName?.trim().orEmpty()
        val cleanBranch = branchName?.trim().orEmpty()
        val storeLine = when {
            cleanStore.isNotEmpty() && cleanBranch.isNotEmpty() -> "$cleanStore — $cleanBranch"
            cleanStore.isNotEmpty() -> cleanStore
            cleanBranch.isNotEmpty() -> cleanBranch
            else -> ""
        }
        if (storeLine.isNotBlank()) {
            lines.add(storeLine)
        }
        lines.add(dateFormatter.format(Date()))
        lines.add("")

        sortedCategories.forEachIndexed { index, category ->
            lines.add("[$category]")
            val itemsInCategory = grouped[category].orEmpty().sortedWith(
                compareBy<StoreProductExportRow> { (it.name ?: "").lowercase(Locale.getDefault()) }
            )
            itemsInCategory.forEach { item ->
                val name = item.name?.takeIf { it.isNotBlank() } ?: "Unnamed Item"
                val desc = item.description?.trim().orEmpty()
                val descPart = if (desc.isNotEmpty()) " ($desc)" else ""
                val priceValue = item.price ?: 0.0
                val unit = item.units?.trim().orEmpty()
                val unitPart = if (unit.isNotEmpty()) " | $unit" else ""
                lines.add("• $name$descPart — ₱${priceFormat.format(priceValue)}$unitPart")
            }
            if (index != sortedCategories.lastIndex) {
                lines.add("")
            }
        }
        lines.add("")
        lines.add("Shared via Presyohan")
        return lines.joinToString("\n").trim()
    }

    private fun copyNoteToClipboard(noteText: String) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Presyohan Note", noteText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Note copied to clipboard.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to copy note.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareNoteText(noteText: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Presyohan Pricelist")
                putExtra(Intent.EXTRA_TEXT, noteText)
            }
            startActivity(Intent.createChooser(intent, "Share note"))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share note.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatExportFilename(): String {
        val sName = (currentStoreName ?: "store").trim()
        val bName = (currentBranchName ?: "main").trim()
        val slug = "${sName}_${bName}".lowercase()
            .replace("\n", " ")
            .replace("\\s+".toRegex(), "-")
            .replace("[^a-z0-9\\-]".toRegex(), "")
        val d = java.util.Calendar.getInstance()
        val y = d.get(java.util.Calendar.YEAR)
        val m = String.format("%02d", d.get(java.util.Calendar.MONTH) + 1)
        val day = String.format("%02d", d.get(java.util.Calendar.DAY_OF_MONTH))
        val hh = String.format("%02d", d.get(java.util.Calendar.HOUR_OF_DAY))
        val mm = String.format("%02d", d.get(java.util.Calendar.MINUTE))
        val ss = String.format("%02d", d.get(java.util.Calendar.SECOND))
        return "presyohan_${slug}_pricelist_${y}${m}${day}_${hh}${mm}${ss}.xlsx"
    }

    private fun ensureExportNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "presyohan.exports"
            val name = "File Exports"
            val descriptionText = "Notifications when files are saved to Downloads"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            channel.description = descriptionText
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun notifyExportSuccessOrRequest(filename: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                postExportNotification(filename)
            } else {
                lastExportFilenamePending = filename
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            postExportNotification(filename)
        }
    }

    private fun postExportNotification(filename: String) {
        try {
            ensureExportNotificationChannel()
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "presyohan.exports" else ""
            val intent = android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("File downloaded")
                .setContentText("Saved to Downloads: $filename")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        } catch (se: SecurityException) {
            Toast.makeText(this, "Enable notifications for Presyohan to see download alerts.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            // ignore
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}
