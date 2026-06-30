package com.presyohan.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.presyohan.app.adapter.Store
import com.presyohan.app.adapter.StoreAdapter
import androidx.core.content.ContextCompat
import android.animation.ValueAnimator
import android.animation.AnimatorSet
import android.view.animation.DecelerateInterpolator

import io.github.jan.supabase.auth.auth
import androidx.lifecycle.lifecycleScope
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
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.DownloadManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.activity.result.contract.ActivityResultContracts
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
import coil.load
import coil.transform.CircleCropTransformation

class StoreActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StoreAdapter
    private var allStores: List<Store> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentRoleFilter: String = "All"
    private var activeTabId: Int = R.id.chipAll
    private var searchJob: kotlinx.coroutines.Job? = null
    private var isFirstLoad = true
    private var storeRolesMap: Map<String, String> = emptyMap()
    private var lastBackPress: Long = 0
    private lateinit var loadingOverlay: android.view.View
    private lateinit var dimmedBackground: android.view.View
    private lateinit var addStoreBottomSheet: android.widget.LinearLayout
    private lateinit var btnBottomSheetJoinStore: android.view.View
    private lateinit var btnBottomSheetCreateStore: android.view.View

    // To track the countdown job inside the dialog
    private var inviteCodeCountdownJob: kotlinx.coroutines.Job? = null
    private var hasAutoOpenedBottomSheet = false

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
    data class StoreMemberRow(val store_id: String, val user_id: String, val role: String)

    @Serializable
    data class StoreRow(val id: String, val name: String, val branch: String? = null, val type: String? = null)

    // Minimal shape to avoid user_id/member_user_id mismatch; RLS filters rows for current user
    @Serializable
    data class StoreMemberLite(val store_id: String, val role: String)

    // Result shape for get_user_stores RPC
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

    // Data class for RPC response to ensure we get all members correctly (Owner check)
    @Serializable
    data class StoreMemberUser(val user_id: String, val name: String, val role: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)
        val fromHome = intent.getBooleanExtra("from_home", false)
        if (!fromHome) {
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
        }
        loadingOverlay = LoadingOverlayHelper.attach(this)

        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        drawerLayout = findViewById(R.id.drawerLayout)
        DrawerHelper.setupDrawer(this, drawerLayout)

        // Open drawer when menu icon is clicked
        findViewById<ImageView>(R.id.menuIcon).setOnClickListener {
            drawerLayout.open()
        }

        findViewById<View>(R.id.profileIconContainer)?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("from_side", "tindiro")
            startActivity(intent)
        }

        // Bottom Sheet
        dimmedBackground = findViewById(R.id.dimmedBackground)
        addStoreBottomSheet = findViewById(R.id.addStoreBottomSheet)
        btnBottomSheetJoinStore = findViewById(R.id.btnBottomSheetJoinStore)
        btnBottomSheetCreateStore = findViewById(R.id.btnBottomSheetCreateStore)

        dimmedBackground.setOnClickListener { hideBottomSheet() }

        btnBottomSheetJoinStore.setOnClickListener {
            hideBottomSheet()
            JoinStoreDialogHelper.showJoinStoreDialog(this) {
                fetchStores()
            }
        }

        btnBottomSheetCreateStore.setOnClickListener {
            hideBottomSheet()
            CreateStoreDialogHelper.showCreateStoreDialog(this) {
                fetchStores()
            }
        }

        // Add Store button logic
        findViewById<ImageButton>(R.id.addStoreButton).setOnClickListener {
            showBottomSheet()
        }

        recyclerView = findViewById(R.id.recyclerViewStores)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = StoreAdapter(
            stores = emptyList(),
            storeRoles = emptyMap(),
            onStoreClick = { store ->
                val intent = Intent(this, HomeActivity::class.java)
                intent.putExtra("storeId", store.id)
                intent.putExtra("storeName", store.name)
                val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_up,
                    R.anim.stay
                )
                startActivity(intent, options.toBundle())
            },
            onSettingsClick = { store ->
                val intent = Intent(this, ManageStoreActivity::class.java)
                intent.putExtra("storeId", store.id)
                intent.putExtra("storeName", store.name)
                startActivity(intent)
            },
            onViewClick = { store ->
                showStoreDetailsDialog(store)
            },
            onDeleteClick = { store ->
                showLeaveDeleteConfirmation(store.id, store.name, isDelete = true, isOwner = true, null)
            },
            onLeaveClick = { store ->
                val isOwner = store.role.lowercase() == "owner"
                showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, isOwner = isOwner, null)
            }
        )
        recyclerView.adapter = adapter

        // Attach Swipe Helper
        val swipeHelper = com.presyohan.app.helper.SwipeRevealTouchHelper(adapter)
        androidx.recyclerview.widget.ItemTouchHelper(swipeHelper).attachToRecyclerView(recyclerView)

        // --- Search bar listeners ---
        val searchStoreEditText = findViewById<EditText>(R.id.searchStoreEditText)
        val btnSearchClear = findViewById<ImageView>(R.id.btnSearchClear)
        
        searchStoreEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                currentSearchQuery = query
                btnSearchClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(180)
                    applyFilterAndSearch()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        btnSearchClear.setOnClickListener {
            searchStoreEditText.text.clear()
            currentSearchQuery = ""
            btnSearchClear.visibility = View.GONE
            searchJob?.cancel()
            applyFilterAndSearch()
        }

        // --- Filter Chip listeners ---
        findViewById<TextView>(R.id.chipAll).setOnClickListener { updateChipSelection(R.id.chipAll) }
        findViewById<TextView>(R.id.chipOwner).setOnClickListener { updateChipSelection(R.id.chipOwner) }
        findViewById<TextView>(R.id.chipManager).setOnClickListener { updateChipSelection(R.id.chipManager) }
        findViewById<TextView>(R.id.chipStaff).setOnClickListener { updateChipSelection(R.id.chipStaff) }

        // --- Swipe Refresh Layout ---
        val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(R.color.presyo_orange)
        swipeRefreshLayout.setOnRefreshListener {
            fetchStores(showShimmer = false)
        }

        val notifIcon = findViewById<ImageView>(R.id.notifIcon)
        notifIcon.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        loadNotifBadge()

        // Initialize sliding tab indicator layout on startup
        findViewById<View>(R.id.tabContainer).post {
            val defaultChip = findViewById<TextView>(activeTabId) ?: return@post
            val indicator = findViewById<View>(R.id.tabIndicator) ?: return@post
            val parentView = defaultChip.parent as? View ?: return@post
            val params = indicator.layoutParams
            params.width = defaultChip.width
            params.height = defaultChip.height
            indicator.layoutParams = params
            indicator.x = defaultChip.x + parentView.left
            indicator.y = defaultChip.y + parentView.top
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreList(this)
        fetchStores(showShimmer = isFirstLoad)
        isFirstLoad = false
        loadNotifBadge()
        lifecycleScope.launch {
            try {
                SupabaseAuthService.updateUserHeartbeat()
            } catch (_: Exception) {}
        }

        // Handle pending onboarding actions
        val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
        val pendingAction = prefs.getString("onboarding_action_pending", null)
        if (pendingAction != null) {
            prefs.edit().remove("onboarding_action_pending").apply()
            if (pendingAction == "create_store") {
                CreateStoreDialogHelper.showCreateStoreDialog(this) {
                    fetchStores(showShimmer = true)
                }
            } else if (pendingAction == "join_store") {
                JoinStoreDialogHelper.showJoinStoreDialog(this) {
                    fetchStores(showShimmer = true)
                }
            }
        }
    }



    override fun onBackPressed() {
        if (::addStoreBottomSheet.isInitialized && addStoreBottomSheet.visibility == View.VISIBLE) {
            hideBottomSheet()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            finishAffinity()
        } else {
            lastBackPress = now
            android.widget.Toast.makeText(this, "Press again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
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

    private fun applyFilterAndSearch() {
        lifecycleScope.launch {
            val query = currentSearchQuery.trim()
            val filtered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val roleFiltered = allStores.filter { store ->
                    when (currentRoleFilter) {
                        "Owner" -> store.role.equals("owner", ignoreCase = true)
                        "Manager" -> store.role.equals("manager", ignoreCase = true)
                        "Sales Staff" -> store.role.equals("employee", ignoreCase = true)
                        else -> true
                    }
                }

                if (query.isEmpty()) {
                    roleFiltered.sortedWith(compareBy(
                        { val role = storeRolesMap[it.id]?.lowercase(); when (role) { "owner" -> 0; "manager" -> 1; else -> 2 } },
                        { it.name.lowercase() }
                    ))
                } else {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    val matchedStores = roleFiltered.filter { store ->
                        var isMatch = true
                        for (token in tokens) {
                            val matchesName = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.name)
                            val matchesBranch = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.branch)
                            val matchesType = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.type)
                            
                            if (!matchesName && !matchesBranch && !matchesType) {
                                isMatch = false
                                break
                            }
                        }
                        isMatch
                    }

                    matchedStores.map { store ->
                        var score = 0.0
                        val cleanName = store.name.lowercase(java.util.Locale.getDefault())
                        val cleanBranch = store.branch.lowercase(java.util.Locale.getDefault())
                        val cleanQuery = query.lowercase(java.util.Locale.getDefault())
                        
                        if (cleanName == cleanQuery) score += 1000.0
                        if (cleanName.startsWith(cleanQuery)) score += 500.0
                        if (cleanName.contains(cleanQuery)) score += 200.0
                        
                        if (cleanBranch == cleanQuery) score += 400.0
                        if (cleanBranch.startsWith(cleanQuery)) score += 200.0
                        if (cleanBranch.contains(cleanQuery)) score += 100.0
                        
                        for (token in tokens) {
                            if (com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.name)) score += 100.0
                            if (com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.branch)) score += 50.0
                            if (com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.type)) score += 20.0
                        }
                        Pair(store, score)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                }
            }

            adapter.updateStores(filtered, storeRolesMap)

            val layoutEmptyState = findViewById<View>(R.id.layoutEmptyState)
            if (filtered.isEmpty()) {
                layoutEmptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                val emptyStateText = findViewById<TextView>(R.id.emptyStateText)
                if (allStores.isEmpty()) {
                    emptyStateText?.text = "No store yet"
                } else {
                    emptyStateText?.text = "No stores found"
                }
            } else {
                layoutEmptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun animateTabIndicator(targetChipId: Int) {
        val targetChip = findViewById<TextView>(targetChipId) ?: return
        val indicator = findViewById<View>(R.id.tabIndicator) ?: return
        val parentView = targetChip.parent as? View ?: return

        activeTabId = targetChipId

        val parentLeft = parentView.left
        val parentTop = parentView.top

        if (targetChip.width == 0) {
            targetChip.post {
                val params = indicator.layoutParams
                params.width = targetChip.width
                params.height = targetChip.height
                indicator.layoutParams = params
                indicator.x = targetChip.x + parentLeft
                indicator.y = targetChip.y + parentTop
            }
            return
        }

        val startX = indicator.x
        val endX = targetChip.x + parentLeft

        val startWidth = indicator.width
        val endWidth = targetChip.width

        val startHeight = indicator.height
        val endHeight = targetChip.height

        // Set vertical position immediately
        indicator.y = targetChip.y + parentTop

        val animatorX = ValueAnimator.ofFloat(startX, endX)
        animatorX.addUpdateListener { animation ->
            indicator.x = animation.animatedValue as Float
        }

        val animatorW = ValueAnimator.ofInt(startWidth, endWidth)
        animatorW.addUpdateListener { animation ->
            val p = indicator.layoutParams
            p.width = animation.animatedValue as Int
            indicator.layoutParams = p
        }

        val animatorH = ValueAnimator.ofInt(startHeight, endHeight)
        animatorH.addUpdateListener { animation ->
            val p = indicator.layoutParams
            p.height = animation.animatedValue as Int
            indicator.layoutParams = p
        }

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animatorX, animatorW, animatorH)
        animatorSet.duration = 220
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }

    private fun updateChipSelection(selectedChipId: Int) {
        val chips = listOf(
            R.id.chipAll to "All",
            R.id.chipOwner to "Owner",
            R.id.chipManager to "Manager",
            R.id.chipStaff to "Sales Staff"
        )
        
        for ((id, role) in chips) {
            val chip = findViewById<TextView>(id) ?: continue
            if (id == selectedChipId) {
                currentRoleFilter = role
                chip.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                chip.setTextColor(ContextCompat.getColor(this, R.color.edittext_hint))
                chip.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
        
        animateTabIndicator(selectedChipId)
        applyFilterAndSearch()
    }

    private fun triggerFirstTimeSwipePeek() {
        val prefs = getSharedPreferences("presyohan_prefs", MODE_PRIVATE)
        val hintShown = prefs.getBoolean("store_swipe_hint_shown", false)
        if (!hintShown && allStores.isNotEmpty()) {
            recyclerView.postDelayed({
                val firstViewHolder = recyclerView.findViewHolderForAdapterPosition(0) as? StoreAdapter.StoreViewHolder
                if (firstViewHolder != null) {
                    val foreground = firstViewHolder.foregroundCardView
                    val density = resources.displayMetrics.density
                    val peekDistance = -60f * density
                    
                    foreground.animate()
                        .translationX(peekDistance)
                        .setDuration(400)
                        .withEndAction {
                            foreground.animate()
                                .translationX(0f)
                                .setDuration(600)
                                .setInterpolator(android.view.animation.BounceInterpolator())
                                .start()
                        }
                        .start()

                    prefs.edit().putBoolean("store_swipe_hint_shown", true).apply()
                }
            }, 600)
        }
    }


    private fun showStoreDetailsDialog(store: Store) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_details, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtStoreName = view.findViewById<TextView>(R.id.dialogStoreName)
        val txtStoreBranch = view.findViewById<TextView>(R.id.dialogStoreBranch)
        txtStoreName.text = store.name
        txtStoreBranch.text = if (store.branch.isNotBlank()) "| ${store.branch}" else ""

        val txtCategoriesCount = view.findViewById<TextView>(R.id.dialogCategoriesCount)
        val txtItemsCount = view.findViewById<TextView>(R.id.dialogItemsCount)
        val txtMembersCount = view.findViewById<TextView>(R.id.dialogMembersCount)
        val txtOwnersCount = view.findViewById<TextView>(R.id.dialogOwnersCount)
        val txtManagersCount = view.findViewById<TextView>(R.id.dialogManagersCount)
        val txtEmployeesCount = view.findViewById<TextView>(R.id.dialogEmployeesCount)
        val txtSukiCount = view.findViewById<TextView>(R.id.dialogSukiCount)

        val txtRoleDesignation = view.findViewById<TextView>(R.id.textRoleDesignation)
        val txtStoreId = view.findViewById<TextView>(R.id.dialogStoreId)
        val txtCreatedAt = view.findViewById<TextView>(R.id.dialogCreatedAt)
        val txtVisibilityStatus = view.findViewById<TextView>(R.id.dialogVisibilityStatus)
        val txtJoinCode = view.findViewById<TextView>(R.id.dialogJoinCode)
        val btnLeaveStore = view.findViewById<View>(R.id.btnLeaveStore)

        val displayRole = when (store.role.lowercase(Locale.ROOT)) {
            "owner" -> "Owner"
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
        txtRoleDesignation.text = spannable

        val shimmerLayout = view.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.dialogShimmerLayout)
        val statsLayout = view.findViewById<android.view.View>(R.id.dialogStatsLayout)

        shimmerLayout.startShimmer()
        shimmerLayout.visibility = android.view.View.VISIBLE
        statsLayout.visibility = android.view.View.GONE

        lifecycleScope.launch {
            try {
                val categories = SupabaseProvider.client.postgrest.rpc(
                    "get_user_categories",
                    buildJsonObject { put("p_store_id", store.id) }
                ).decodeList<HomeActivity.UserCategoryRow>()
                txtCategoriesCount.text = categories.size.toString()

                try {
                    @Serializable
                    data class StoreProductCountRow(val store_id: String, val total_count: Int, val public_count: Int)
                    
                    val counts = SupabaseProvider.client.postgrest.rpc(
                        "get_store_product_counts",
                        buildJsonObject {
                            put("p_store_ids", kotlinx.serialization.json.buildJsonArray {
                                add(kotlinx.serialization.json.JsonPrimitive(store.id))
                            })
                        }
                    ).decodeList<StoreProductCountRow>()
                    
                    val countRow = counts.firstOrNull()
                    txtItemsCount.text = (countRow?.total_count ?: 0).toString()
                } catch (e: Exception) {
                    txtItemsCount.text = "0"
                }

                val members = SupabaseProvider.client.postgrest.rpc(
                    "get_store_members",
                    buildJsonObject { put("p_store_id", store.id) }
                ).decodeList<StoreMemberUser>()

                val owners = members.count { it.role.equals("owner", ignoreCase = true) }
                val managers = members.count { it.role.equals("manager", ignoreCase = true) }
                val employees = members.count { it.role.equals("employee", ignoreCase = true) }

                txtMembersCount.text = members.size.toString()
                txtOwnersCount.text = owners.toString()
                txtManagersCount.text = managers.toString()
                txtEmployeesCount.text = employees.toString()

                // Use RPC to bypass RLS and get actual suki count
                val sukiCount = try {
                    SupabaseProvider.client.postgrest.rpc(
                        "get_store_suki_count",
                        buildJsonObject { put("p_store_id", store.id) }
                    ).decodeAs<Int>()
                } catch (e: Exception) {
                    0
                }
                txtSukiCount.text = sukiCount.toString()

                @Serializable
                data class StoreDetailsLite(
                    val id: String,
                    val display_id: String? = null,
                    val created_at: String? = null,
                    val is_public: Boolean = false,
                    val invite_code: String? = null,
                    val invite_code_created_at: String? = null
                )
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", store.id) }
                    limit(1)
                }.decodeList<StoreDetailsLite>()
                
                val storeRow = rows.firstOrNull()
                if (storeRow != null) {
                    txtStoreId.text = storeRow.display_id ?: storeRow.id
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
                    txtCreatedAt.text = dateText
                    txtVisibilityStatus.text = if (storeRow.is_public) "Public" else "Private"

                    val inviteCode = storeRow.invite_code
                    val createdIso = storeRow.invite_code_created_at
                    val createdMillis = parseInviteCreatedMillis(createdIso)
                    val isExpired = createdMillis == null || (System.currentTimeMillis() - createdMillis > 86400000L)
                    
                    txtJoinCode.text = if (isExpired || inviteCode.isNullOrBlank()) {
                        "Expired"
                    } else {
                        inviteCode
                    }
                }
            } catch (e: Exception) {
                Log.e("StoreActivity", "Failed to load store details stats", e)
            } finally {
                shimmerLayout.stopShimmer()
                shimmerLayout.visibility = android.view.View.GONE
                statsLayout.visibility = android.view.View.VISIBLE
            }
        }

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            dialog.dismiss()
        }

        btnLeaveStore.setOnClickListener {
            dialog.dismiss()
            val isOwner = store.role.lowercase() == "owner"
            showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, isOwner = isOwner, null)
        }

        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun fetchStores(showShimmer: Boolean = true) {
        val client = SupabaseProvider.client
        val userId = client.auth.currentUserOrNull()?.id ?: return
        
        val shimmerContainer = findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmerContainer)
        val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val layoutEmptyState = findViewById<View>(R.id.layoutEmptyState)
        val noStoreLabel = findViewById<TextView>(R.id.noStoreLabel)

        noStoreLabel.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE

        if (showShimmer) {
            shimmerContainer.visibility = View.VISIBLE
            shimmerContainer.startShimmer()
            recyclerView.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                val rows = client.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()

                Log.d("StoreActivity", "RPC get_user_stores returned ${rows.size} rows")

                if (rows.isEmpty()) {
                    allStores = emptyList()
                    storeRolesMap = emptyMap()
                    adapter.updateStores(emptyList(), emptyMap())
                    findViewById<TextView>(R.id.emptyStateText)?.text = "No store yet"
                    layoutEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    noStoreLabel.visibility = View.GONE
                    if (!hasAutoOpenedBottomSheet) {
                        hasAutoOpenedBottomSheet = true
                        showBottomSheet()
                    }
                    return@launch
                }

                val roles = rows.associate { it.store_id to it.role }
                val ownersMap = mutableMapOf<String, Int>()
                rows.filter { it.role.lowercase() == "owner" }.forEach { r ->
                    try {
                        val members = client.postgrest.rpc(
                            "get_store_members",
                            buildJsonObject { put("p_store_id", r.store_id) }
                        ).decodeList<StoreMemberUser>()
                        val oCount = members.count { it.role.lowercase() == "owner" }
                        ownersMap[r.store_id] = oCount
                    } catch (e: Exception) {
                        Log.e("StoreActivity", "Failed to query owner counts for ${r.store_id}: ${e.message}", e)
                    }
                }

                @kotlinx.serialization.Serializable
                data class StoreVisibilityLite(val id: String, val is_public: Boolean = false)
                
                val storeIds = rows.map { it.store_id }
                val publicVisibilityMap = mutableMapOf<String, Boolean>()
                try {
                    if (storeIds.isNotEmpty()) {
                        val visibilityRows = client.postgrest["stores"]
                            .select {
                                filter {
                                    isIn("id", storeIds)
                                }
                            }.decodeList<StoreVisibilityLite>()
                        visibilityRows.forEach { publicVisibilityMap[it.id] = it.is_public }
                    }
                } catch (e: Exception) {
                    Log.e("StoreActivity", "Failed to query is_public for stores: ${e.message}", e)
                }

                val fetchedStores = rows.map { r ->
                    val oCount = ownersMap[r.store_id] ?: 1
                    val isPub = publicVisibilityMap[r.store_id] ?: r.is_public
                    Store(r.store_id, r.name, r.branch ?: "", r.type ?: "", r.member_count, r.role, oCount, isPub)
                }
                storeRolesMap = roles

                val sortedStores = fetchedStores.sortedWith(compareBy(
                    { val role = roles[it.id]?.lowercase(); when (role) { "owner" -> 0; "manager" -> 1; else -> 2 } },
                    { it.name.lowercase() }
                ))
                allStores = sortedStores

                applyFilterAndSearch()
                triggerFirstTimeSwipePeek()

            } catch (e: Exception) {
                Log.e("StoreActivity", "Error fetching stores via RPC: ${e.message}", e)
                allStores = emptyList()
                adapter.updateStores(emptyList(), emptyMap())
                noStoreLabel.visibility = View.VISIBLE
                noStoreLabel.text = "Failed to load stores.\nPlease check your connection."
                layoutEmptyState.visibility = View.GONE
                recyclerView.visibility = View.GONE
            } finally {
                swipeRefreshLayout.isRefreshing = false
                if (showShimmer) {
                    shimmerContainer.stopShimmer()
                    shimmerContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun showStoreMenu(store: Store, anchor: View) {
        val role = storeRolesMap[store.id]?.lowercase()
        val isOwner = role == "owner"
        if (!isOwner) {
            showStoreMenuEmployee(store)
            return
        }

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_menu, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Bind Headers
        view.findViewById<TextView>(R.id.menuStoreName).text = store.name
        view.findViewById<TextView>(R.id.menuBranchName).text = "| ${store.branch}"

        // Bind Grid Buttons
        val btnCopyPrices = view.findViewById<LinearLayout>(R.id.btnCopyPrices)
        val btnInviteStaff = view.findViewById<LinearLayout>(R.id.btnInviteStaff)
        val btnExportPrices = view.findViewById<LinearLayout>(R.id.btnExportPrices)
        val btnImportPrices = view.findViewById<LinearLayout>(R.id.btnImportPrices)

        // Bind Footer Link
        val btnSettings = view.findViewById<TextView>(R.id.btnSettings)

        // Bind Top Right Action Icon (Leave/Delete)
        val btnLeaveDelete = view.findViewById<ImageView>(R.id.btnLeaveDelete)

        // 1. Settings
        btnSettings.setOnClickListener {
            val intent = Intent(this, ManageStoreActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
            dialog.dismiss()
        }

        // 2. Invite Staff
        btnInviteStaff.setOnClickListener {
            dialog.dismiss()
            showInviteStaffWithCode(store) // Updated to pass 'store' object
        }

        // 3. Copy Prices
        btnCopyPrices.setOnClickListener {
            val intent = Intent(this, CopyPricesActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
            dialog.dismiss()
        }

        // 4. Import Prices -> Show dialog with options
        btnImportPrices.setOnClickListener {
            dialog.dismiss()
            showImportDialogForStore(store.id, store.name)
        }

        // 5. Export Prices
        btnExportPrices.setOnClickListener {
            dialog.dismiss()
            exportPricelistToExcel(store.id, store.name, store.branch)
        }

        // 6. Leave/Delete Logic
        lifecycleScope.launch {
            try {
                val members = SupabaseProvider.client.postgrest.rpc(
                    "get_store_members",
                    buildJsonObject { put("p_store_id", store.id) }
                ).decodeList<StoreMemberUser>()

                val ownerCount = members.count { it.role.equals("owner", ignoreCase = true) }
                val isSoleOwner = ownerCount <= 1

                if (isSoleOwner) {
                    // Delete Mode
                    btnLeaveDelete.setImageResource(R.drawable.icon_delete)
                    btnLeaveDelete.setColorFilter(android.graphics.Color.parseColor("#D32F2F"))

                    val sizePx = (32 * resources.displayMetrics.density).toInt()
                    btnLeaveDelete.layoutParams.width = sizePx
                    btnLeaveDelete.layoutParams.height = sizePx
                    btnLeaveDelete.requestLayout()

                    btnLeaveDelete.setOnClickListener {
                        showLeaveDeleteConfirmation(store.id, store.name, isDelete = true, isOwner = true, dialog)
                    }
                } else {
                    // Leave Mode
                    btnLeaveDelete.setImageResource(R.drawable.icon_leave_store)
                    btnLeaveDelete.setColorFilter(ContextCompat.getColor(this@StoreActivity, R.color.presyo_teal))

                    val sizePx = (38 * resources.displayMetrics.density).toInt()
                    btnLeaveDelete.layoutParams.width = sizePx
                    btnLeaveDelete.layoutParams.height = sizePx
                    btnLeaveDelete.requestLayout()

                    btnLeaveDelete.setOnClickListener {
                        showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, isOwner = true, dialog)
                    }
                }
            } catch (_: Exception) {
                // Fallback
                btnLeaveDelete.setImageResource(R.drawable.icon_logout)
                val sizePx = (38 * resources.displayMetrics.density).toInt()
                btnLeaveDelete.layoutParams.width = sizePx
                btnLeaveDelete.layoutParams.height = sizePx
                btnLeaveDelete.requestLayout()

                btnLeaveDelete.setOnClickListener {
                    showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, isOwner = true, dialog)
                }
            }
        }

        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.96).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showLeaveDeleteConfirmation(storeId: String, storeName: String, isDelete: Boolean, isOwner: Boolean = false, menuDialog: Dialog? = null) {
        val confirmDialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        confirmDialog.setContentView(view)
        confirmDialog.setCancelable(true)
        confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val btnCancel = view.findViewById<Button>(R.id.btnNegative)
        val btnAction = view.findViewById<Button>(R.id.btnPositive)

        btnCancel.text = "Cancel"

        if (isDelete) {
            title.text = "Delete Store"
            message.text = "Are you sure you want to delete this store?\n\nDeleting your store \"$storeName\" will permanently delete all products, members, and categories.\n\nThis cannot be undone."
            btnAction.text = "Delete"
        } else {
            title.text = "Leave Store"
            if (isOwner) {
                message.text = "Are you sure you want to leave this store?\n\nThe store will remain active, but you will lose all access and ownership rights."
            } else {
                message.text = "Are you sure you want to leave this store?\n\nYou will no longer be a member of this store."
            }
            btnAction.text = "Leave"
        }

        btnCancel.setOnClickListener { confirmDialog.dismiss() }

        btnAction.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (isDelete) {
                        SupabaseProvider.client.postgrest["stores"].delete { filter { eq("id", storeId) } }
                        Toast.makeText(this@StoreActivity, "Store deleted successfully.", Toast.LENGTH_SHORT).show()
                    } else {
                        SupabaseProvider.client.postgrest.rpc(
                            "leave_store",
                            buildJsonObject { put("p_store_id", storeId) }
                        )
                        Toast.makeText(this@StoreActivity, "You have left the store.", Toast.LENGTH_SHORT).show()
                    }
                    confirmDialog.dismiss()
                    menuDialog?.dismiss()
                    fetchStores(showShimmer = false)
                } catch (e: Exception) {
                    val errorMsg = if (e.message?.contains("sole owner", true) == true)
                        "You are the only owner. Delete the store instead."
                    else "Action failed. Check internet."
                    Toast.makeText(this@StoreActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
        confirmDialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        confirmDialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showImportDialogForStore(storeId: String, storeName: String) {
        val intent = Intent(this, AddMultipleItemsActivity::class.java).apply {
            putExtra("storeId", storeId)
            putExtra("storeName", storeName)
            putExtra("showImportDialog", true)
        }
        startActivity(intent)
    }

    private fun showStoreMenuEmployee(store: Store) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_menu, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Headers
        view.findViewById<TextView>(R.id.menuStoreName).text = store.name
        view.findViewById<TextView>(R.id.menuBranchName).text = "| ${store.branch}"

        // Hide buttons restricted for employees
        val btnCopyPrices = view.findViewById<LinearLayout>(R.id.btnCopyPrices)
        val btnInviteStaff = view.findViewById<LinearLayout>(R.id.btnInviteStaff)
        val btnExportPrices = view.findViewById<LinearLayout>(R.id.btnExportPrices)
        val btnImportPrices = view.findViewById<LinearLayout>(R.id.btnImportPrices)
        val btnSettings = view.findViewById<TextView>(R.id.btnSettings)

        // Hide restricted items
        btnInviteStaff.visibility = View.GONE
        btnImportPrices.visibility = View.GONE
        btnSettings.visibility = View.GONE

        // Allow Export/Copy
        btnExportPrices.setOnClickListener {
            dialog.dismiss()
            // Fix: Pass store name and branch to export function
            exportPricelistToExcel(store.id, store.name, store.branch)
        }
        btnCopyPrices.setOnClickListener {
            val intent = Intent(this, CopyPricesActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
            dialog.dismiss()
        }

        // Leave Logic (Top Right)
        val btnLeaveDelete = view.findViewById<ImageView>(R.id.btnLeaveDelete)
        btnLeaveDelete.setImageResource(R.drawable.icon_logout)
        btnLeaveDelete.setColorFilter(ContextCompat.getColor(this, R.color.presyo_teal))

        btnLeaveDelete.setOnClickListener {
            showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, menuDialog = dialog)
        }

        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
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
                } catch (e: Exception) {
                    notifDot.visibility = View.GONE
                }
            }
        } else if (notifDot != null) {
            notifDot.visibility = View.GONE
        }
    }

    // --- Export Logic ---
    private fun exportPricelistToExcel(storeId: String, storeName: String, branch: String) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            var overlayVisible = true
            try {
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject {
                        put("p_store_id", storeId)
                        put("p_category_filter", "PRICELIST")
                        put("p_search_query", null as String?)
                    }
                ).decodeList<StoreProductExportRow>()

                if (rows.isEmpty()) {
                    Toast.makeText(this@StoreActivity, "No products to export.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val sorted = rows.sortedWith(compareBy(
                    { (it.category ?: "").lowercase() },
                    { (it.name ?: "").lowercase() }
                ))

                LoadingOverlayHelper.hide(loadingOverlay)
                overlayVisible = false
                // Fix: Pass storeName and branch to dialog
                showExportConfirmationDialog(sorted, storeName, branch)
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Failed to export.", Toast.LENGTH_LONG).show()
            } finally {
                if (overlayVisible) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    // Fix: Updated signature to accept storeName and branch
    private fun showExportConfirmationDialog(rows: List<StoreProductExportRow>, storeName: String, branch: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_confirmation, null)
        val rowsCountView = dialogView.findViewById<TextView>(R.id.rowsCount)
        val rowsLabelView = dialogView.findViewById<TextView>(R.id.rowsLabel)
        val scopeValueView = dialogView.findViewById<TextView>(R.id.scopeValue)
        val subtitleView = dialogView.findViewById<TextView>(R.id.exportSubtitle)
        val exportDetailsCard = dialogView.findViewById<View>(R.id.exportDetailsCard)
        val exportTypeGroup = dialogView.findViewById<RadioGroup>(R.id.exportTypeGroup)
        val notePreviewContainer = dialogView.findViewById<View>(R.id.notePreviewContainer)
        val notePreviewText = dialogView.findViewById<TextView>(R.id.notePreviewText)
        val noteMetaText = dialogView.findViewById<TextView>(R.id.noteMetaText)
        val btnCopyNote = dialogView.findViewById<AppCompatButton>(R.id.btnCopyNote)
        val btnConfirmExport = dialogView.findViewById<AppCompatButton>(R.id.btnConfirmExport)

        rowsCountView.text = rows.size.toString()
        rowsLabelView.text = if (rows.size == 1) "Row to export" else "Rows to export"
        scopeValueView.text = "All products"
        subtitleView.text = "Generate a professionally formatted Excel file or a sharable note."

        val noteText = buildNoteText(rows, storeName, branch)
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
                        performPricelistExport(rows, storeName, branch)
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

    // Fix: Updated signature to accept storeName and branch
    private fun performPricelistExport(rows: List<StoreProductExportRow>, storeName: String, branch: String) {
        if (rows.isEmpty()) {
            Toast.makeText(this, "No products to export.", Toast.LENGTH_SHORT).show()
            return
        }

        // Fix: Use passed storeName and branch
        val filename = formatExportFilename(storeName, branch)
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

            // Fix: Use passed storeName and branch
            ws.value(1, 0, "Store: $storeName — Branch: $branch")
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
        val grouped = rows.groupBy { row ->
            val cat = row.category?.trim()
            if (cat.isNullOrBlank()) "General" else cat
        }
        val sortedCategories = grouped.keys.sortedWith(compareBy<String> { it.lowercase(Locale.getDefault()) }.thenBy { it })
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
        if (storeLine.isNotBlank()) lines.add(storeLine)
        lines.add(dateFormatter.format(Date()))
        lines.add("")

        sortedCategories.forEachIndexed { index, category ->
            lines.add("[$category]")
            grouped[category].orEmpty()
                .sortedWith(compareBy<StoreProductExportRow> { (it.name ?: "").lowercase(Locale.getDefault()) })
                .forEach { row ->
                    val name = row.name?.takeIf { it.isNotBlank() } ?: "Unnamed Item"
                    val desc = row.description?.trim().orEmpty()
                    val descPart = if (desc.isNotEmpty()) " ($desc)" else ""
                    val priceValue = row.price ?: 0.0
                    val unit = row.units?.trim().orEmpty()
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
            clipboard.setPrimaryClip(ClipData.newPlainText("Presyohan Note", noteText))
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

    // Fix: Updated signature to accept storeName and branch
    private fun formatExportFilename(storeName: String, branch: String): String {
        val sName = storeName.trim()
        val bName = branch.trim()
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

    // --- Invite Logic & Date Parsing ---

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

    private fun showInviteStaffWithCode(store: Store) {
        lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", store.id) }
                    limit(1)
                }.decodeList<StoreInviteFields>()
                val row = rows.firstOrNull()
                val code = row?.invite_code
                val createdIso = row?.invite_code_created_at

                val createdMillis = parseInviteCreatedMillis(createdIso)
                val expiryMillis = createdMillis?.plus(86400000L)

                runOnUiThread {
                    showInviteStaffDialog(store, code, expiryMillis)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@StoreActivity, "Unable to fetch code.", Toast.LENGTH_SHORT).show()
                    showInviteStaffDialog(store, null, null)
                }
            }
        }
    }

    private fun showInviteStaffDialog(store: Store, inviteCode: String?, expiryMillis: Long?) {
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
        val spinnerRole = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerRole)
        val inviteErrorText = view.findViewById<TextView>(R.id.inviteErrorText)

        @Serializable
        data class SearchedUser(
            val id: String,
            val name: String? = null,
            val email: String? = null,
            val user_code: String? = null,
            val avatar_url: String? = null
        )

        val rolesDisplay = listOf("View only price list", "Manage prices")
        val rolesValue = listOf("employee", "manager")
        val adp = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rolesDisplay)
        spinnerRole.setAdapter(adp)
        spinnerRole.setText(rolesDisplay[0], false)

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
            val role = storeRolesMap[store.id]?.lowercase()
            val isOwner = role == "owner"
            if (!isOwner) {
                Toast.makeText(this, "Only owner can generate codes.", Toast.LENGTH_SHORT).show()
                return
            }
            btnGenerateCodeInactive.isEnabled = false
            btnGenerateCodeActive.isEnabled = false

            lifecycleScope.launch {
                try {
                    val params = buildJsonObject { put("p_store_id", store.id) }
                    val rows = SupabaseProvider.client.postgrest
                        .rpc("regenerate_invite_code", params)
                        .decodeList<InviteCodeReturn>()
                    val row = rows.firstOrNull()
                    val newCode = row?.invite_code
                    val newCreated = row?.invite_code_created_at
                    val newCreatedMillis = parseInviteCreatedMillis(newCreated)
                    val newExpiry = newCreatedMillis?.plus(86400000L)
                    runOnUiThread {
                        updateCodeUI(newCode, newExpiry)
                        Toast.makeText(applicationContext, "Code updated.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Failed to generate code.", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    btnGenerateCodeInactive.isEnabled = true
                    btnGenerateCodeActive.isEnabled = true
                }
            }
        }

        btnGenerateCodeInactive.setOnClickListener { generateCode() }
        btnGenerateCodeActive.setOnClickListener { generateCode() }

        btnRevokeCodeActive.setOnClickListener {
            val role = storeRolesMap[store.id]?.lowercase()
            val isOwner = role == "owner"
            if (!isOwner) {
                Toast.makeText(this, "Only owner can revoke codes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnRevokeCodeActive.isEnabled = false
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest["stores"].update(
                        mapOf(
                            "invite_code" to null,
                            "invite_code_created_at" to null
                        )
                    ) {
                        filter { eq("id", store.id) }
                    }
                    runOnUiThread {
                        updateCodeUI(null, null)
                        Toast.makeText(applicationContext, "Code revoked.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Failed to revoke code.", Toast.LENGTH_SHORT).show()
                    }
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
                    val results = SupabaseProvider.client.postgrest.rpc(
                        "search_app_user",
                        buildJsonObject { put("search_term", query) }
                    ).decodeList<SearchedUser>()
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
                    searchLoader.visibility = View.GONE
                    textNotFound.text = "Error searching user."
                    textNotFound.visibility = View.VISIBLE
                }
                toggleInviteButton()
            }
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
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

        view.findViewById<Button>(R.id.btnBack).setOnClickListener { dialog.dismiss() }

        btnInvite.setOnClickListener {
            val user = selectedUser ?: return@setOnClickListener
            val sId = store.id
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
                    SupabaseProvider.client.postgrest.rpc("send_store_invitation", params)
                    Toast.makeText(this@StoreActivity, "Invitation sent to ${user.name}", Toast.LENGTH_SHORT).show()
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
}
