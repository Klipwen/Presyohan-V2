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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.presyohan.app.adapter.ProductAdapter
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

    // Store details
    private var currentStoreId: String? = null
    private var currentStoreName: String? = null
    private var currentBranchName: String? = null // Added to capture branch name for dialog
    private var userRole: String? = null

    // Invite Code Countdown
    private var inviteCodeCountdownJob: kotlinx.coroutines.Job? = null

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
        val category: String? = null
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
        val category: String? = null
    )

    @Serializable
    data class UserStoreRow(
        val store_id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val role: String
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

    // Activity Result Launchers
    private lateinit var addItemLauncher: ActivityResultLauncher<Intent>
    private lateinit var editItemLauncher: ActivityResultLauncher<Intent>

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

        // Register Activity Result Launchers
        addItemLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Snapshot listener triggers update
        }
        editItemLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Snapshot listener triggers update
        }

        // --- View Initialization ---
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val menuIcon = findViewById<ImageView>(R.id.menuIcon)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val storeText = findViewById<TextView>(R.id.textStoreName)
        val productRecyclerView = findViewById<RecyclerView>(R.id.productRecyclerView)
        val categoryLabel = findViewById<TextView>(R.id.categoryLabel)
        val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
        val categoryDrawerButton = findViewById<ImageView>(R.id.categoryDrawerButton)
        val notifIcon = findViewById<ImageView>(R.id.notifIcon)
        val searchItemButton = findViewById<ImageView>(R.id.searchItemButton)
        val btnPerformSearch = findViewById<ImageView>(R.id.btnPerformSearch)
        val btnStoreOptions = findViewById<ImageView>(R.id.btnStoreOptions)

        // Assign to class-level vars
        searchBarContainer = findViewById(R.id.searchBarContainer)
        searchEditText = findViewById(R.id.searchEditText)
        layoutPricelistTrigger = findViewById(R.id.layoutPricelistTrigger)
        addButton = findViewById(R.id.addButton)

        if (searchBarContainer == null || layoutPricelistTrigger == null || addButton == null) {
            Log.e("HomeActivity", "Critical views not found in XML")
            return
        }

        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        currentStoreId = intent.getStringExtra("storeId")
        currentStoreName = intent.getStringExtra("storeName")

        // --- Navigation & Header Logic ---
        menuIcon.setOnClickListener { drawerLayout.open() }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> {
                    startActivity(Intent(this, StoreActivity::class.java))
                    drawerLayout.close()
                    true
                }
                R.id.nav_logout -> {
                    showLogoutDialog(drawerLayout)
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, NotificationActivity::class.java))
                    drawerLayout.close()
                    true
                }
                else -> false
            }
        }

        updateDrawerHeader(navigationView)

        btnBack.setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        storeText.text = currentStoreName ?: "Store"
        storeText.setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // --- Product List & Role Logic ---
        val products = mutableListOf<com.presyohan.app.adapter.Product>()
        val adapter = ProductAdapter(products, userRole,
            onOptionsClick = { product, anchor ->
                if (userRole == "owner" || userRole == "manager") {
                    showManageItemDialog(product, currentStoreId, currentStoreName)
                }
            },
            onLongPress = { product, anchor ->
                if (userRole == "owner" || userRole == "manager") {
                    showManageItemDialog(product, currentStoreId, currentStoreName)
                }
            }
        )

        productRecyclerView.layoutManager = GridLayoutManager(this, 2)
        productRecyclerView.adapter = adapter

        // Fetch user role and update UI accordingly
        if (userId != null && currentStoreId != null) {
            LoadingOverlayHelper.show(loadingOverlay)
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
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        // Load store branch name from database
        if (currentStoreId != null) {
            lifecycleScope.launch {
                try {
                    val rows = supabase.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                    val row = rows.firstOrNull { it.store_id == currentStoreId }
                    currentBranchName = row?.branch ?: ""
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Store branch load failed", e)
                    currentBranchName = ""
                }
            }
        }

        // --- Search Functionality (Supabase) ---
        fun loadProductsFromSupabase(showLoading: Boolean = false) {
            val sId = currentStoreId ?: return
            if (showLoading) LoadingOverlayHelper.show(loadingOverlay)
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

                    products.clear()
                    for (row in rows) {
                        products.add(
                            com.presyohan.app.adapter.Product(
                                row.product_id, row.name, row.description ?: "",
                                row.price, row.units ?: "", row.category ?: ""
                            )
                        )
                    }
                    adapter.updateProducts(products)

                } catch (e: Exception) {
                    Log.e("HomeActivity", "Products load failed", e)
                }
                if (showLoading) LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        // Initial Load
        if (currentStoreId != null) {
            reloadProductsFn = { loadProductsFromSupabase(false) }
            loadProductsFromSupabase(true)
        }

        // --- Search Bar Behavior Implementation ---

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s.toString().trim().lowercase()
                loadProductsFromSupabase(false)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Search Button Click (Toggle)
        searchItemButton?.setOnClickListener {
            if (searchBarContainer.visibility == View.VISIBLE) {
                hideSearchBar(hideKeyboard = true)
            } else {
                showSearchBar(showKeyboard = true)
            }
        }

        btnPerformSearch.setOnClickListener {
            currentQuery = searchEditText.text.toString().trim().lowercase()
            loadProductsFromSupabase(false)
            hideKeyboard(searchEditText) // Close keyboard on enter
        }

        // Scroll Logic for Search Bar (Professional: 0.5s delay, Top check)
        productRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Cancel pending checks
                searchVisibilityRunnable?.let { scrollHandler.removeCallbacks(it) }

                val scrollY = recyclerView.computeVerticalScrollOffset()

                // 1. If at very top, show immediately
                if (scrollY == 0) {
                    if (searchBarContainer.visibility != View.VISIBLE) {
                        showSearchBar(showKeyboard = false)
                    }
                    return
                }

                // 2. Logic for Scrolling UP or DOWN with 0.5s (500ms) delay
                if (dy > 0) {
                    // Scrolling DOWN -> Hide after 0.5s
                    searchVisibilityRunnable = Runnable {
                        if (searchBarContainer.visibility == View.VISIBLE) {
                            hideSearchBar(hideKeyboard = true)
                        }
                    }
                    scrollHandler.postDelayed(searchVisibilityRunnable!!, 500)

                } else if (dy < 0) {
                    // Scrolling UP -> Show after 0.5s
                    searchVisibilityRunnable = Runnable {
                        if (searchBarContainer.visibility != View.VISIBLE) {
                            showSearchBar(showKeyboard = false)
                        }
                    }
                    scrollHandler.postDelayed(searchVisibilityRunnable!!, 500)
                }
            }
        })

        // --- Category Logic ---
        setupCategorySpinner(currentStoreId, categoryLabel, categorySpinner, categoryDrawerButton) { category ->
            selectedCategory = category
            productRecyclerView.layoutManager = GridLayoutManager(this, 2)
            loadProductsFromSupabase(true)
        }
        categoryLabel.setOnClickListener { categorySpinner.performClick() }

        // --- Actions ---
        addButton.setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java)
            intent.putExtra("storeId", currentStoreId)
            intent.putExtra("storeName", currentStoreName)
            addItemLauncher.launch(intent)
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

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_menu, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 1. Set Dialog Width to 90% of Screen (Robust sizing for "broken" layout fix)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // Bind Header Views
        val txtStoreName = view.findViewById<TextView>(R.id.menuStoreName)
        val txtBranchName = view.findViewById<TextView>(R.id.menuBranchName)
        val btnLeaveDelete = view.findViewById<ImageView>(R.id.btnLeaveDelete) // Top right icon

        txtStoreName.text = sName
        txtBranchName.text = if (!currentBranchName.isNullOrBlank()) "| $currentBranchName" else ""

        // Bind Grid Buttons
        val btnCopyPrices = view.findViewById<LinearLayout>(R.id.btnCopyPrices)
        val btnInviteStaff = view.findViewById<LinearLayout>(R.id.btnInviteStaff)
        val btnExportPrices = view.findViewById<LinearLayout>(R.id.btnExportPrices)
        val btnImportPrices = view.findViewById<LinearLayout>(R.id.btnImportPrices)

        // Bind Footer Link
        val btnSettings = view.findViewById<TextView>(R.id.btnSettings)

        val isOwner = userRole == "owner"

        // --- Set Click Listeners ---

        // 1. Settings (More Settings)
        btnSettings.setOnClickListener {
            val intent = Intent(this, ManageStoreActivity::class.java)
            intent.putExtra("storeId", sId)
            intent.putExtra("storeName", currentStoreName)
            startActivity(intent)
            dialog.dismiss()
        }

        // 2. Invite Staff
        btnInviteStaff.setOnClickListener {
            dialog.dismiss()
            showInviteStaffWithCode(sId)
        }

        // 3. Copy Prices
        btnCopyPrices.setOnClickListener {
            val intent = Intent(this, CopyPricesActivity::class.java)
            intent.putExtra("storeId", sId)
            intent.putExtra("storeName", currentStoreName)
            startActivity(intent)
            dialog.dismiss()
        }

        // 4. Import Prices
        btnImportPrices.setOnClickListener {
            val intent = Intent(this, ImportPricesActivity::class.java)
            intent.putExtra("storeId", sId)
            intent.putExtra("storeName", currentStoreName)
            intent.putExtra("storeBranch", sName)
            startActivity(intent)
            dialog.dismiss()
        }

        // 5. Export Prices
        btnExportPrices.setOnClickListener {
            dialog.dismiss()
            exportPricelistToExcel()
        }

        // 6. Leave / Delete Logic (Top Right Icon)
        if (!isOwner) {
            // STAFF -> Always "Leave"
            btnLeaveDelete.setImageResource(R.drawable.icon_leave_store)
            btnLeaveDelete.setColorFilter(ContextCompat.getColor(this, R.color.presyo_teal))

            btnLeaveDelete.setOnClickListener {
                showLeaveDeleteConfirmation(sId, sName, isDelete = false, dialog)
            }
            dialog.show()
        } else {
            // OWNER -> Check if sole owner
            lifecycleScope.launch {
                try {
                    val members = supabase.postgrest.rpc(
                        "get_store_members",
                        buildJsonObject { put("p_store_id", sId) }
                    ).decodeList<StoreMemberUser>()

                    val ownerCount = members.count { it.role.equals("owner", ignoreCase = true) }
                    val isSoleOwner = ownerCount <= 1

                    if (isSoleOwner) {
                        // Sole Owner -> DELETE
                        btnLeaveDelete.setImageResource(R.drawable.icon_delete)
                        val redColor = android.graphics.Color.parseColor("#D32F2F")
                        btnLeaveDelete.setColorFilter(redColor)

                        btnLeaveDelete.setOnClickListener {
                            showLeaveDeleteConfirmation(sId, sName, isDelete = true, dialog)
                        }
                    } else {
                        // Multiple Owners -> LEAVE
                        btnLeaveDelete.setImageResource(R.drawable.icon_leave_store)
                        btnLeaveDelete.setColorFilter(ContextCompat.getColor(this@HomeActivity, R.color.presyo_teal))

                        btnLeaveDelete.setOnClickListener {
                            showLeaveDeleteConfirmation(sId, sName, isDelete = false, dialog)
                        }
                    }
                    dialog.show()
                } catch(e: Exception) {
                    // Fallback
                    btnLeaveDelete.setImageResource(R.drawable.icon_leave_store)
                    btnLeaveDelete.setOnClickListener {
                        showLeaveDeleteConfirmation(sId, sName, isDelete = false, dialog)
                    }
                    dialog.show()
                }
            }
        }
    }

    private fun showLeaveDeleteConfirmation(storeId: String, storeName: String, isDelete: Boolean, menuDialog: Dialog) {
        val confirmDialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        confirmDialog.setContentView(view)
        confirmDialog.setCancelable(true)
        confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.confirmMessage)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnAction = view.findViewById<Button>(R.id.btnDelete)

        if (isDelete) {
            title.text = "Delete Store"
            message.text = "Are you sure you want to delete this store?.\n\nYour store \"$storeName\" permanently erase all products, members, and data. This cannot be undone."
            btnAction.text = "Delete Store" // Red
        } else {
            title.text = "Leave Store"
            message.text = "Are you sure you want to leave \"$storeName\"?\n\nYou will lose access to the dashboard and products unless invited back."
            btnAction.text = "Leave Store"
        }

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
    }

    // --- Invite Code Logic ---

    private fun parseInviteCreatedMillis(createdIso: String?): Long? {
        if (createdIso.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(createdIso).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(createdIso).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    val normalized = if (createdIso.contains("T")) createdIso else createdIso.replace(" ", "T")
                    java.time.LocalDateTime.parse(normalized).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) { null }
            }
        }
    }

    private fun startInviteCountdown(expiryText: TextView, codeText: TextView, copyBtn: ImageView, expiryMillis: Long) {
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

        val codeText = view.findViewById<TextView>(R.id.storeCodeText)
        val expiryText = view.findViewById<TextView>(R.id.inviteCodeExpiry)
        val copyBtn = view.findViewById<ImageView>(R.id.btnCopyCode)
        val generateBtn = view.findViewById<Button>(R.id.btnGenerateCode)

        fun updateCodeUI(code: String?, expiresAt: Long?) {
            codeText.text = code ?: "No Code"
            inviteCodeCountdownJob?.cancel()

            val now = System.currentTimeMillis()
            if (code != null && expiresAt != null) {
                if (expiresAt > now) {
                    startInviteCountdown(expiryText, codeText, copyBtn, expiresAt)
                } else {
                    expiryText.text = "Code expired"
                }
            } else {
                expiryText.text = if (code == null) "" else "Code expired"
            }
        }
        updateCodeUI(inviteCode, expiryMillis)

        copyBtn.setOnClickListener {
            val code = codeText.text.toString()
            if (code.isNotEmpty() && code != "No Code") {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Store Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Code copied.", Toast.LENGTH_SHORT).show()
            }
        }

        generateBtn.setOnClickListener {
            if (userRole != "owner") {
                Toast.makeText(this, "Only owner can generate codes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sId = currentStoreId ?: return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
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
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        view.findViewById<Button>(R.id.btnBack).setOnClickListener { dialog.dismiss() }

        view.findViewById<Button>(R.id.btnInvite).setOnClickListener {
            Toast.makeText(this, "Invite via email coming soon.", Toast.LENGTH_SHORT).show()
        }

        // Populate roles
        val roleSpinner = view.findViewById<Spinner>(R.id.roleSpinner)
        val perms = listOf("View only prices", "Manage prices")
        val adp = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, perms)
        roleSpinner.adapter = adp

        dialog.setOnDismissListener { inviteCodeCountdownJob?.cancel() }
        dialog.show()
    }

    /**
     * Updates the UI Layout based on the user's role.
     * Specific request: For Staff, hide add button and widen search bar to center.
     */
    private fun updateUiForRole(role: String?) {
        val isOwnerOrManager = role == "owner" || role == "manager"

        val params = searchBarContainer.layoutParams as RelativeLayout.LayoutParams

        if (isOwnerOrManager) {
            addButton.visibility = View.VISIBLE
            params.removeRule(RelativeLayout.CENTER_HORIZONTAL)
            params.addRule(RelativeLayout.ALIGN_PARENT_START)
            params.addRule(RelativeLayout.START_OF, R.id.addButton)
            params.width = RelativeLayout.LayoutParams.WRAP_CONTENT
        } else {
            addButton.visibility = View.GONE
            params.removeRule(RelativeLayout.START_OF)
            params.removeRule(RelativeLayout.ALIGN_PARENT_START)
            params.addRule(RelativeLayout.CENTER_HORIZONTAL)
            params.width = RelativeLayout.LayoutParams.MATCH_PARENT
        }

        searchBarContainer.layoutParams = params
    }

    // --- Search and Scroll Helper Methods ---

    private fun showSearchBar(showKeyboard: Boolean) {
        if (searchBarContainer.visibility != View.VISIBLE) {
            val categoryParams = layoutPricelistTrigger.layoutParams as RelativeLayout.LayoutParams
            categoryParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            categoryParams.addRule(RelativeLayout.BELOW, R.id.searchBarContainer)
            layoutPricelistTrigger.layoutParams = categoryParams

            val addParams = addButton.layoutParams as RelativeLayout.LayoutParams
            addParams.removeRule(RelativeLayout.ALIGN_BOTTOM)
            addParams.removeRule(RelativeLayout.ALIGN_TOP)
            addParams.addRule(RelativeLayout.ALIGN_TOP, R.id.searchBarContainer)
            addParams.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.searchBarContainer)
            addButton.layoutParams = addParams

            searchBarContainer.visibility = View.VISIBLE
        }

        if (showKeyboard) {
            searchEditText.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
            isKeyboardOpen = true
        }
    }

    private fun hideSearchBar(hideKeyboard: Boolean) {
        if (searchBarContainer.visibility == View.VISIBLE) {
            searchBarContainer.visibility = View.GONE

            val categoryParams = layoutPricelistTrigger.layoutParams as RelativeLayout.LayoutParams
            categoryParams.removeRule(RelativeLayout.BELOW)
            categoryParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            layoutPricelistTrigger.layoutParams = categoryParams

            val addParams = addButton.layoutParams as RelativeLayout.LayoutParams
            addParams.removeRule(RelativeLayout.ALIGN_TOP)
            addParams.removeRule(RelativeLayout.ALIGN_BOTTOM)
            addParams.addRule(RelativeLayout.ALIGN_TOP, R.id.layoutPricelistTrigger)
            addParams.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.layoutPricelistTrigger)
            addButton.layoutParams = addParams
        }

        if (hideKeyboard) {
            hideKeyboard(searchEditText)
        }
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
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.dialogTitle).text = "Log Out?"
        view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to log out of Presyohan?"
        view.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<android.widget.Button>(R.id.btnDelete).apply {
            text = "Log Out"
            setOnClickListener {
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
        }
        dialog.show()
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
        val categories = mutableListOf("PRICELIST")
        val adapterSpinner = object : android.widget.ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, categories
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.text = categories[position].uppercase()
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.text = categories[position].uppercase()
                return view
            }
        }
        categorySpinner.adapter = adapterSpinner
        categorySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val cat = categories[position]
                categoryLabel.text = cat.uppercase()
                onCategorySelected(cat)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        categorySpinner.background = null
        categorySpinner.layoutParams.width = 1
        categorySpinner.requestLayout()
        categoryDrawerButton.setOnClickListener { categorySpinner.performClick() }

        if (storeId != null) {
            lifecycleScope.launch {
                try {
                    val rows = supabase.postgrest.rpc("get_user_categories", buildJsonObject { put("p_store_id", storeId) }).decodeList<UserCategoryRow>()
                    for (row in rows) {
                        if (!categories.contains(row.name)) categories.add(row.name)
                    }
                    adapterSpinner.notifyDataSetChanged()
                } catch (e: Exception) { Log.e("HomeActivity", "Cat load failed", e) }
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
        view.findViewById<TextView>(R.id.textProductDescription).text = product.description
        view.findViewById<TextView>(R.id.textProductPrice).text = "₱%.2f".format(product.price)
        view.findViewById<TextView>(R.id.textProductUnit).text = product.volume

        view.findViewById<ImageView>(R.id.btnEdit).setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            val intent = Intent(this, EditItemActivity::class.java)
            intent.putExtra("productId", product.id)
            intent.putExtra("productName", product.name)
            intent.putExtra("productDescription", product.description)
            intent.putExtra("productPrice", product.price)
            intent.putExtra("productUnit", product.volume)
            intent.putExtra("productCategory", product.category)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            editItemLauncher.launch(intent)
            dialog.dismiss()
        }
        view.findViewById<ImageView>(R.id.btnDelete).setOnClickListener {
            val confirmDialog = android.app.Dialog(this)
            val confirmView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
            confirmDialog.setContentView(confirmView)
            confirmDialog.setCancelable(true)
            confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            confirmView.findViewById<TextView>(R.id.dialogTitle).text = "Delete Item"
            confirmView.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to delete this item?"
            confirmView.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener { confirmDialog.dismiss() }
            confirmView.findViewById<android.widget.Button>(R.id.btnDelete).setOnClickListener {
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
        }
        dialog.show()
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
        val eT = h.findViewById<TextView>(R.id.drawerUserEmail)
        val u = SupabaseProvider.client.auth.currentUserOrNull()
        uT.text = "User"
        eT.text = u?.email ?: ""
        lifecycleScope.launch {
            val n = SupabaseAuthService.getDisplayName()
            if(n!=null) uT.text = n
        }
    }

    override fun onResume() {
        super.onResume()
        val sId = intent.getStringExtra("storeId")
        val sName = intent.getStringExtra("storeName")
        SessionManager.markStoreHome(this, sId, sName)
        reloadProductsFn?.invoke()
        loadNotifBadge()
    }

    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) finishAffinity() else {
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
                lines.add("• $name$descPart — ₱${priceFormat.format(priceValue)}")
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
}
