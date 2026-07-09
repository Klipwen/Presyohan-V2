package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.presyohan.app.adapter.MembershipItem
import com.presyohan.app.adapter.MembershipsAdapter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MembershipsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MembershipsAdapter
    private lateinit var loadingOverlay: View
    private lateinit var shimmerContainer: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyState: TextView

    // Tabs Control
    private lateinit var tabContainer: View
    private lateinit var tabIndicator: View
    private var activeTabId: Int = R.id.chipMembership

    // Search Bottom Sheet UI
    private lateinit var fabSearch: ImageButton
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bottomSearchEditText: EditText
    private lateinit var btnSearchClear: ImageView

    private var searchQuery: String = ""
    private var searchJob: kotlinx.coroutines.Job? = null

    // Separate Data Caches
    private var allMemberships = listOf<MembershipItem>()
    private var allSukis = listOf<MembershipItem>()
    private var allPresyohans = listOf<MembershipItem>()
    private var isFirstResume = true

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
    data class SukiRelationshipRow(val store_id: String)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memberships)

        loadingOverlay = LoadingOverlayHelper.attach(this)
        shimmerContainer = findViewById(R.id.shimmerContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(R.color.presyo_orange)
        swipeRefreshLayout.setOnRefreshListener {
            fetchData(showShimmer = false)
        }

        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        recyclerView = findViewById(R.id.recyclerViewMemberships)
        recyclerView.layoutManager = LinearLayoutManager(this)

        tabContainer = findViewById(R.id.tabContainer)
        tabIndicator = findViewById(R.id.tabIndicator)

        fabSearch = findViewById(R.id.fabSearch)
        bottomSheet = findViewById(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSearchEditText = findViewById(R.id.bottomSearchEditText)
        btnSearchClear = findViewById(R.id.btnSearchClear)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Setup Adapter
        adapter = MembershipsAdapter(
            onViewStore = { item -> handleViewStore(item) },
            onSettings = { item -> handleSettingsStore(item) },
            onAction = { item -> handleActionStore(item) }
        )
        recyclerView.adapter = adapter

        // Setup Tab Clicks
        findViewById<TextView>(R.id.chipMembership).setOnClickListener { updateTabSelection(R.id.chipMembership) }
        findViewById<TextView>(R.id.chipSuki).setOnClickListener { updateTabSelection(R.id.chipSuki) }
        findViewById<TextView>(R.id.chipPresyohan).setOnClickListener { updateTabSelection(R.id.chipPresyohan) }

        // Initialize Sliding Tab Indicator on layout layout pass
        tabContainer.post {
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

        // Setup Search and Bottom Sheet Interaction
        setupBottomSheet()

        // Load all data
        fetchData(showShimmer = true)
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
        } else {
            fetchData(showShimmer = false)
        }
    }

    private fun setupBottomSheet() {
        fun updateRecyclerPadding(bottomHeight: Int) {
            val density = resources.displayMetrics.density
            val minPadding = (120 * density).toInt() // Clears the floating FAB
            val safetyPadding = (16 * density).toInt()
            val targetPadding = maxOf(minPadding, bottomHeight + safetyPadding)
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
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        fabSearch.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

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
                    filterAndRenderActiveList()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSearchClear.setOnClickListener {
            bottomSearchEditText.setText("")
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun updateTabSelection(selectedChipId: Int) {
        val chips = listOf(
            R.id.chipMembership,
            R.id.chipSuki,
            R.id.chipPresyohan
        )

        for (id in chips) {
            val chip = findViewById<TextView>(id) ?: continue
            if (id == selectedChipId) {
                chip.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                chip.setTextColor(ContextCompat.getColor(this, R.color.edittext_hint))
                chip.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }

        animateTabIndicator(selectedChipId)
        filterAndRenderActiveList()
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

        indicator.y = targetChip.y + parentTop

        val animatorX = android.animation.ValueAnimator.ofFloat(startX, endX)
        animatorX.addUpdateListener { animation ->
            indicator.x = animation.animatedValue as Float
        }

        val animatorW = android.animation.ValueAnimator.ofInt(startWidth, endWidth)
        animatorW.addUpdateListener { animation ->
            val p = indicator.layoutParams
            p.width = animation.animatedValue as Int
            indicator.layoutParams = p
        }

        val animatorH = android.animation.ValueAnimator.ofInt(startHeight, endHeight)
        animatorH.addUpdateListener { animation ->
            val p = indicator.layoutParams
            p.height = animation.animatedValue as Int
            indicator.layoutParams = p
        }

        val animatorSet = android.animation.AnimatorSet()
        animatorSet.playTogether(animatorX, animatorW, animatorH)
        animatorSet.duration = 220
        animatorSet.interpolator = android.view.animation.DecelerateInterpolator()
        animatorSet.start()
    }

    private fun fetchData(showShimmer: Boolean) {
        if (showShimmer) {
            shimmerContainer.visibility = View.VISIBLE
            shimmerContainer.startShimmer()
            recyclerView.visibility = View.GONE
            layoutEmptyState.visibility = View.GONE
        } else {
            LoadingOverlayHelper.show(loadingOverlay)
        }

        lifecycleScope.launch {
            try {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return@launch

                // 1. Fetch Store Memberships via RPC
                val membershipRows = SupabaseProvider.client.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                
                @Serializable
                data class StoreMemberUser(val user_id: String, val name: String, val role: String)
                
                allMemberships = membershipRows.map { row ->
                    val isOwner = row.role == "owner"
                    var hasOtherOwner = false
                    
                    if (isOwner) {
                        try {
                            val members = SupabaseProvider.client.postgrest.rpc(
                                "get_store_members",
                                buildJsonObject { put("p_store_id", row.store_id) }
                            ).decodeList<StoreMemberUser>()
                            val owners = members.count { it.role.lowercase() == "owner" }
                            hasOtherOwner = owners > 1
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    MembershipItem(
                        id = row.store_id,
                        name = row.name,
                        branch = row.branch,
                        type = row.type,
                        role = row.role,
                        isStandard = false,
                        hasOtherOwner = hasOtherOwner
                    )
                }

                // 2. Fetch Customer Suki relationships
                val sukiRelationships = SupabaseProvider.client.postgrest["suki_relationships"]
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("status", "active")
                        }
                    }
                    .decodeList<SukiRelationshipRow>()

                val linkedStoreIds = sukiRelationships.map { it.store_id }
                if (linkedStoreIds.isNotEmpty()) {
                    val sukiStores = SupabaseProvider.client.postgrest["stores"]
                        .select { filter { isIn("id", linkedStoreIds) } }
                        .decodeList<StoreDetailRow>()

                    val matchedSukis = mutableListOf<MembershipItem>()
                    val matchedPresyohans = mutableListOf<MembershipItem>()

                    for (s in sukiStores) {
                        val item = MembershipItem(
                            id = s.id,
                            name = s.name,
                            branch = s.branch,
                            type = s.type,
                            role = null,
                            isStandard = s.is_standard_store
                        )
                        if (s.is_standard_store) {
                            matchedPresyohans.add(item)
                        } else {
                            matchedSukis.add(item)
                        }
                    }

                    allSukis = matchedSukis
                    allPresyohans = matchedPresyohans
                } else {
                    allSukis = emptyList()
                    allPresyohans = emptyList()
                }

                filterAndRenderActiveList()
                ReusableDialogHelper.resetReloadCount()

            } catch (e: Exception) {
                e.printStackTrace()
                val handled = ReusableDialogHelper.handleNetworkError(this@MembershipsActivity, e) {
                    fetchData(showShimmer)
                }
                if (!handled) {
                    Toast.makeText(this@MembershipsActivity, "Failed to load: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                shimmerContainer.stopShimmer()
                shimmerContainer.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                swipeRefreshLayout.isRefreshing = false
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun filterAndRenderActiveList() {
        val query = searchQuery.trim().lowercase()

        val rawList = when (activeTabId) {
            R.id.chipMembership -> allMemberships
            R.id.chipSuki -> allSukis
            R.id.chipPresyohan -> allPresyohans
            else -> allMemberships
        }

        val filtered = if (query.isEmpty()) {
            rawList
        } else {
            rawList.filter {
                it.name.lowercase().contains(query) ||
                it.branch?.lowercase()?.contains(query) == true ||
                it.type?.lowercase()?.contains(query) == true
            }
        }

        adapter.updateList(filtered)

        // Show/hide empty state
        if (filtered.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            val emptyMsg = when (activeTabId) {
                R.id.chipMembership -> "No store memberships found."
                R.id.chipSuki -> "No suking tindahan links found."
                R.id.chipPresyohan -> "No presyohan standard lists found."
                else -> "No stores found."
            }
            tvEmptyState.text = emptyMsg
        } else {
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun handleViewStore(item: MembershipItem) {
        if (item.role != null) {
            // Member view -> HomeActivity (owner/staff view)
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra("storeId", item.id)
                putExtra("storeName", item.name)
            }
            startActivity(intent)
        } else {
            // Customer view -> StoreViewActivity
            val intent = Intent(this, StoreViewActivity::class.java).apply {
                putExtra("STORE_ID", item.id)
                putExtra("STORE_NAME", item.name)
                putExtra("STORE_BRANCH", item.branch)
                putExtra("STORE_TYPE", item.type ?: "General Store")
                putExtra("IS_PRESYOHAN", item.isStandard)
            }
            startActivity(intent)
        }
    }

    private fun handleSettingsStore(item: MembershipItem) {
        val intent = Intent(this, ManageStoreActivity::class.java).apply {
            putExtra("storeId", item.id)
            putExtra("storeName", item.name)
        }
        startActivity(intent)
    }

    private fun handleActionStore(item: MembershipItem) {
        if (item.role != null) {
            if (item.role == "owner") {
                if (item.hasOtherOwner) {
                    // Leave Store Dialog
                    showReusableDialog(
                        title = "Leave Store",
                        message = "Are you sure you want to leave this store?\n\nYou will no longer be a member of this store.",
                        positiveButtonText = "Leave",
                        positiveAction = { leaveStore(item) },
                        negativeButtonText = "Cancel"
                    )
                } else {
                    // Delete Store Dialog
                    showReusableDialog(
                        title = "Delete Store",
                        message = "Are you sure you want to delete this store?\n\nDeleting your store \"${item.name}\" will permanently delete all products, members, and categories.\n\nThis cannot be undone.",
                        positiveButtonText = "Delete",
                        positiveAction = { deleteStore(item) },
                        negativeButtonText = "Cancel"
                    )
                }
            } else {
                // Leave Store Dialog
                showReusableDialog(
                    title = "Leave Store",
                    message = "Are you sure you want to leave this store?\n\nYou will no longer be a member of this store.",
                    positiveButtonText = "Leave",
                    positiveAction = { leaveStore(item) },
                    negativeButtonText = "Cancel"
                )
            }
        } else {
            if (item.isStandard) {
                // Remove Presyohan Dialog
                showReusableDialog(
                    title = "Remove Presyohan",
                    message = "Are you sure you want to remove this presyohan list?\n\nYou will no longer see \"${item.name}\" on your dashboard comparison.",
                    positiveButtonText = "Delete",
                    positiveAction = { removeSukiRelationship(item) },
                    negativeButtonText = "Cancel"
                )
            } else {
                // Remove Suking Tindahan Dialog
                showReusableDialog(
                    title = "Remove Suking Tindahan",
                    message = "Are you sure you want to remove this suking tindahan?\n\nYou will no longer be a suki to \"${item.name}\" and lose access to their prices.",
                    positiveButtonText = "Delete",
                    positiveAction = { removeSukiRelationship(item) },
                    negativeButtonText = "Cancel"
                )
            }
        }
    }

    private fun deleteStore(item: MembershipItem) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.postgrest["stores"].delete {
                    filter { eq("id", item.id) }
                }
                Toast.makeText(this@MembershipsActivity, "Store deleted successfully.", Toast.LENGTH_SHORT).show()
                fetchData(showShimmer = false)
            } catch (e: Exception) {
                Toast.makeText(this@MembershipsActivity, "Action failed. Check internet.", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun leaveStore(item: MembershipItem) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.postgrest.rpc(
                    "leave_store",
                    buildJsonObject { put("p_store_id", item.id) }
                )
                Toast.makeText(this@MembershipsActivity, "You have left the store.", Toast.LENGTH_SHORT).show()
                fetchData(showShimmer = false)
            } catch (e: Exception) {
                Toast.makeText(this@MembershipsActivity, "Action failed. Check internet.", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun removeSukiRelationship(item: MembershipItem) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return@launch
                SupabaseProvider.client.postgrest["suki_relationships"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("store_id", item.id)
                    }
                }
                Toast.makeText(this@MembershipsActivity, "Removed successfully.", Toast.LENGTH_SHORT).show()
                fetchData(showShimmer = false)
            } catch (e: Exception) {
                Toast.makeText(this@MembershipsActivity, "Action failed. Check internet.", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
}
