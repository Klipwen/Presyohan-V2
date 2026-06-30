package com.presyohan.app

import android.app.Dialog
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

class SelectPresyohanActivity : AppCompatActivity() {

    private lateinit var btnBack: FrameLayout
    private lateinit var btnInfo: FrameLayout
    private lateinit var etSearchPresyohan: EditText
    private lateinit var rvPresyohanStores: RecyclerView
    private lateinit var progressBar: View
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyMessage: TextView
    private lateinit var btnAddPresyohan: androidx.appcompat.widget.AppCompatButton
    private lateinit var loadingOverlay: View
    


    // Data lists
    private var allPresyohanStores: List<StoreDetailRow> = emptyList()
    private var productCounts: Map<String, Int> = emptyMap()
    private val selectedStoreIds = mutableSetOf<String>()
    private val existingUserStoreIds = mutableSetOf<String>()
    private var currentSearchQuery = ""
    private var searchJob: kotlinx.coroutines.Job? = null

    // Adapter
    private lateinit var storeAdapter: PresyohanSelectionAdapter

    @Serializable
    data class SukiRelationshipRow(val store_id: String)

    @Serializable
    data class SukiRelationshipInsert(
        val user_id: String,
        val store_id: String,
        val status: String = "active"
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
    data class StoreProductCountRow(
        val store_id: String,
        val total_count: Int,
        val public_count: Int
    )

    @Serializable
    data class ProductDetailRow(
        val id: String,
        val store_id: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_presyohan)

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

        // Attach loading overlay helper
        loadingOverlay = LoadingOverlayHelper.attach(this)

        // Initialize Views
        btnBack = findViewById(R.id.btnBack)
        btnInfo = findViewById(R.id.btnInfo)
        etSearchPresyohan = findViewById(R.id.etSearchPresyohan)
        rvPresyohanStores = findViewById(R.id.rvPresyohanStores)
        progressBar = findViewById(R.id.progressBar)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
        btnAddPresyohan = findViewById(R.id.btnAddPresyohan)

        // Apply dynamic top padding to selectHeader matching status bar height
        val selectHeader = findViewById<View>(R.id.selectHeader)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(selectHeader) { view, insets ->
            val statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                statusBarHeight + (8 * resources.displayMetrics.density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        


        // Setup Back Button
        btnBack.setOnClickListener {
            goBackToCustomerHome()
        }

        // Setup Info Button
        btnInfo.setOnClickListener {
            showInfoDialog()
        }



        // Setup RecyclerView
        rvPresyohanStores.layoutManager = LinearLayoutManager(this)
        storeAdapter = PresyohanSelectionAdapter(emptyList(), selectedStoreIds) { store ->
            toggleSelection(store.id)
        }
        rvPresyohanStores.adapter = storeAdapter

        // Setup Search TextWatcher
        etSearchPresyohan.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(180)
                    filterAndRenderData()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Add Button Click
        btnAddPresyohan.setOnClickListener {
            addSelectedStoresToDashboard()
        }

        // Load data
        loadPresyohanStores()
    }



    private fun loadPresyohanStores() {
        progressBar.visibility = View.VISIBLE
        layoutEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                if (userId == null) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@SelectPresyohanActivity, "User session not found.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 1. Fetch user's existing suki relationships
                val sukiLinks = SupabaseProvider.client.postgrest["suki_relationships"]
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<SukiRelationshipRow>()
                
                val linkedStoreIds = sukiLinks.map { it.store_id }.toSet()
                existingUserStoreIds.clear()
                existingUserStoreIds.addAll(linkedStoreIds)

                // 2. Fetch all public standard stores
                val standardStores = SupabaseProvider.client.postgrest["stores"]
                    .select {
                        filter {
                            eq("is_standard_store", true)
                            eq("is_public", true)
                        }
                    }
                    .decodeList<StoreDetailRow>()

                // Keep all standard stores (we'll style them as faded at the bottom instead)
                allPresyohanStores = standardStores

                // 3. Fetch product counts of these available standard stores
                val availableStoreIds = allPresyohanStores.map { it.id }
                if (availableStoreIds.isNotEmpty()) {
                    val counts = SupabaseProvider.client.postgrest.rpc(
                        "get_store_product_counts",
                        kotlinx.serialization.json.buildJsonObject {
                            put("p_store_ids", kotlinx.serialization.json.buildJsonArray {
                                availableStoreIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                            })
                        }
                    ).decodeList<StoreProductCountRow>()
                    
                    productCounts = counts.associate { it.store_id to it.public_count }
                } else {
                    productCounts = emptyMap()
                }

                filterAndRenderData()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SelectPresyohanActivity, "Error loading stores: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun filterAndRenderData() {
        val query = currentSearchQuery.trim()

        lifecycleScope.launch {
            val finalSortedList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val filteredList = if (query.isEmpty()) {
                    allPresyohanStores
                } else {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    val matchedStores = allPresyohanStores.filter { store ->
                        var isMatch = true
                        for (token in tokens) {
                            val matchesName = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.name)
                            val matchesBranch = store.branch?.let { com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, it) } ?: false
                            val matchesType = store.type?.let { com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, it) } ?: false
                            
                            if (!matchesName && !matchesBranch && !matchesType) {
                                isMatch = false
                                break
                            }
                        }
                        isMatch
                    }

                    matchedStores.map { store ->
                        var score = 0.0
                        val cleanName = store.name.lowercase(Locale.getDefault())
                        val cleanQuery = query.lowercase(Locale.getDefault())
                        
                        if (cleanName == cleanQuery) score += 1000.0
                        if (cleanName.startsWith(cleanQuery)) score += 500.0
                        if (cleanName.contains(cleanQuery)) score += 200.0
                        
                        for (token in tokens) {
                            if (com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.name)) score += 100.0
                            if (store.branch != null && com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.branch)) score += 50.0
                            if (store.type != null && com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, store.type)) score += 30.0
                        }
                        Pair(store, score)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                }

                if (query.isEmpty()) {
                    filteredList.sortedWith(
                        compareBy<StoreDetailRow> { it.id in existingUserStoreIds }
                            .thenBy { it.name.lowercase(Locale.getDefault()) }
                    )
                } else {
                    // stable sort keeps the score ranking within groups
                    filteredList.sortedWith(
                        compareBy { it.id in existingUserStoreIds }
                    )
                }
            }

            storeAdapter.updateList(finalSortedList)

            if (finalSortedList.isEmpty()) {
                layoutEmptyState.visibility = View.VISIBLE
                tvEmptyMessage.text = if (query.isEmpty()) {
                    "No standard stores available."
                } else {
                    "No stores found matching \"$currentSearchQuery\""
                }
            } else {
                layoutEmptyState.visibility = View.GONE
            }
        }
    }

    private fun toggleSelection(storeId: String) {
        if (existingUserStoreIds.contains(storeId)) return
        if (selectedStoreIds.contains(storeId)) {
            selectedStoreIds.remove(storeId)
        } else {
            selectedStoreIds.add(storeId)
        }
        storeAdapter.notifyDataSetChanged()
        
        // Enable Add button if at least one store is selected
        btnAddPresyohan.isEnabled = selectedStoreIds.isNotEmpty()
    }

    private fun addSelectedStoresToDashboard() {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return
        if (selectedStoreIds.isEmpty()) return

        LoadingOverlayHelper.show(loadingOverlay)

        lifecycleScope.launch {
            try {
                // Bulk insert suki relationships
                val inserts = selectedStoreIds.map { storeId ->
                    SukiRelationshipInsert(
                        user_id = userId,
                        store_id = storeId,
                        status = "active"
                    )
                }

                SupabaseProvider.client.postgrest["suki_relationships"].insert(inserts)

                Toast.makeText(this@SelectPresyohanActivity, "Successfully added to your stores!", Toast.LENGTH_SHORT).show()
                
                // Redirect to the customer home screen store tab
                val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
                prefs.edit().putBoolean("redirect_to_stores_tab", true).apply()

                val intent = Intent(this@SelectPresyohanActivity, CustomerHomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SelectPresyohanActivity, "Failed to add stores: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun showInfoDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_reusable_template)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitle = dialog.findViewById<TextView>(R.id.dialogTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.confirmMessage)
        val btnClose = dialog.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnAction = dialog.findViewById<android.widget.Button>(R.id.btnDelete)

        tvTitle.text = "About Presyohan Lists"
        tvMessage.text = "Presyohan lists are system-maintained price lists showing baseline market rates for various categories. Adding them allows you to compare store prices against standard reference values."
        
        btnClose.text = "Close"
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnAction.visibility = View.GONE // Hide secondary action
        dialog.show()
    }

    // RecyclerView Adapter
    private inner class PresyohanSelectionAdapter(
        private var items: List<StoreDetailRow>,
        private val selectedIds: Set<String>,
        private val onItemClick: (StoreDetailRow) -> Unit
    ) : RecyclerView.Adapter<PresyohanSelectionAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val storeIconContainer: FrameLayout = view.findViewById(R.id.storeIconContainer)
            val ivStoreIcon: ImageView = view.findViewById(R.id.ivStoreIcon)
            val tvStoreName: TextView = view.findViewById(R.id.tvStoreName)
            val tvStoreSubtitle: TextView = view.findViewById(R.id.tvStoreSubtitle)
            val tvStoreLocation: TextView = view.findViewById(R.id.tvStoreLocation)
            val tvStoreItemCount: TextView = view.findViewById(R.id.tvStoreItemCount)
            val ivSelectCircle: ImageView = view.findViewById(R.id.ivSelectCircle)
            val tvSelectedLabel: TextView = view.findViewById(R.id.tvSelectedLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_select_presyohan, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvStoreName.text = item.name
            holder.tvStoreSubtitle.text = item.type ?: "General Store"
            holder.tvStoreLocation.text = item.branch ?: "Presyohan App"

            val productCount = productCounts[item.id] ?: 0
            holder.tvStoreItemCount.text = "$productCount Items"

            val isAlreadyAdded = existingUserStoreIds.contains(item.id)
            holder.view.alpha = if (isAlreadyAdded) 0.75f else 1.0f

            // Style standard Presyohan icon
            holder.storeIconContainer.setBackgroundResource(R.drawable.bg_rounded_store_icon)
            holder.ivStoreIcon.setImageResource(R.drawable.icon_presyohan)
            holder.ivStoreIcon.rotation = -45f

            // Update Selection Icon or Selected label
            if (isAlreadyAdded) {
                holder.ivSelectCircle.visibility = View.GONE
                holder.tvSelectedLabel.visibility = View.VISIBLE
            } else {
                holder.ivSelectCircle.visibility = View.VISIBLE
                holder.tvSelectedLabel.visibility = View.GONE
                
                if (selectedIds.contains(item.id)) {
                    holder.ivSelectCircle.setImageResource(R.drawable.ic_radio_checked_orange)
                } else {
                    holder.ivSelectCircle.setImageResource(R.drawable.ic_radio_unchecked)
                }
            }

            holder.view.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<StoreDetailRow>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goBackToCustomerHome()
    }

    private fun goBackToCustomerHome() {
        val intent = Intent(this, CustomerHomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
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
