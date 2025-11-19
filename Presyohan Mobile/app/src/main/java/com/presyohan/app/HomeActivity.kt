package com.presyohan.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.adapter.Product
import com.presyohan.app.adapter.ProductAdapter
import  android.widget.TextView
import android.view.View
import android.widget.ImageView
// Firebase Firestore removed for product/listener and header; using Supabase instead
import android.widget.Spinner
 
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

class HomeActivity : AppCompatActivity() {
    private val supabase: SupabaseClient
        get() = SupabaseProvider.client

    private var selectedCategory: String? = null
    private var currentQuery: String = ""
    private var reloadProductsFn: (() -> Unit)? = null
    private lateinit var loadingOverlay: android.view.View

    @Serializable
    data class StoreMember(val store_id: String, val user_id: String, val role: String)

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

    // Result shape for get_user_categories RPC
    @Serializable
    data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)

    // Result shape for get_store_products RPC
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
    data class StoreRow(val id: String, val name: String, val branch: String? = null, val type: String? = null)

    // Result shape for get_user_stores RPC (for header)
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
    private val REQUEST_EDIT_ITEM = 1001
    private val REQUEST_ADD_ITEM = 1002
    private var lastBackPress: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        // Removed Google Play Services client setup; logout handled via Supabase only

        // Drawer and menu logic
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        // Firestore db removed; using Supabase for data reads
        val menuIcon = findViewById<android.widget.ImageView>(R.id.menuIcon)
        menuIcon.setOnClickListener {
            drawerLayout.open()
        }
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> {
                    // Navigate to StoreActivity
                    val intent = Intent(this, StoreActivity::class.java)
                    startActivity(intent)
                    drawerLayout.close()
                    true
                }
                R.id.nav_logout -> {
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
                    true
                }
                R.id.nav_notifications -> {
                    val intent = Intent(this, NotificationActivity::class.java)
                    startActivity(intent)
                    drawerLayout.close()
                    true
                }
                // Handle other menu items if needed
                else -> false
            }
        }

        // Set real user name and email in navigation drawer header
        val headerView = navigationView.getHeaderView(0)
        val userNameText = headerView.findViewById<TextView>(R.id.drawerUserName)
        val userEmailText = headerView.findViewById<TextView>(R.id.drawerUserEmail)
        val supaUser = SupabaseProvider.client.auth.currentUserOrNull()
        userNameText.text = "User"
        userEmailText.text = supaUser?.email ?: ""
        lifecycleScope.launch {
            val name = SupabaseAuthService.getDisplayName() ?: "User"
            userNameText.text = name
        }

        // Get storeId and storeName from intent
        val storeId = intent.getStringExtra("storeId")
        val storeName = intent.getStringExtra("storeName")

        // Set store name and branch in header
        val storeText = findViewById<TextView>(R.id.storeText)
        storeText.text = storeName ?: "Store"
        storeText.setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
        val storeBranchText = findViewById<TextView>(R.id.storeBranchText)
        if (storeId != null) {
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    // Use RPC to fetch user-visible stores (respects RLS/policies)
                    val rows = supabase.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                    val row = rows.firstOrNull { it.store_id == storeId }
                    storeText.text = row?.name ?: (storeName ?: "Store")
                    storeBranchText.text = row?.branch ?: ""
                } catch (e: Exception) {
                    android.util.Log.e("HomeActivity", "Store header load via RPC failed", e)
                    storeBranchText.text = ""
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        } else {
            storeBranchText.text = ""
        }

        val addButton = findViewById<ImageView>(R.id.addButton)
        addButton.visibility = View.GONE // Hide by default until role is fetched

        val products = mutableListOf<com.presyohan.app.adapter.Product>()
        var userRole: String? = null
        val adapter = ProductAdapter(products, userRole,
            onOptionsClick = { product, anchor ->
                if (userRole == "owner" || userRole == "manager") {
                    showManageItemDialog(product, storeId, storeName)
                }
            },
            onLongPress = { product, anchor ->
                if (userRole == "owner" || userRole == "manager") {
                    showManageItemDialog(product, storeId, storeName)
                }
            }
        )
        val recyclerView = findViewById<RecyclerView>(R.id.productRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        // Fetch user role via Supabase
        if (userId != null && storeId != null) {
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    val members = supabase.postgrest["store_members"]
                        .select {
                            filter {
                                eq("store_id", storeId)
                                eq("user_id", userId)
                            }
                            limit(1)
                        }
                        .decodeList<StoreMember>()
                    val role = members.firstOrNull()?.role
                    userRole = role
                    android.util.Log.d("HomeActivity", "Fetched role via Supabase: $role")
                    if (role == "owner" || role == "manager") {
                        addButton.visibility = View.VISIBLE
                    } else {
                        addButton.visibility = View.GONE
                    }
                    adapter.setUserRole(role)
                } catch (e: Exception) {
                    android.util.Log.e("HomeActivity", "Role fetch failed", e)
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }


        // selectedCategory & currentQuery are class-level, used across callbacks

        // Update search logic to combine with category filter
        fun filterAndDisplayProducts(showMessageIfEmpty: Boolean = false) {
            val query = currentQuery
            val category = selectedCategory
            if (query.isNotEmpty()) {
                recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@HomeActivity)
            } else {
                recyclerView.layoutManager = GridLayoutManager(this@HomeActivity, 2)
            }
            val filtered = products.filter {
                (query.isEmpty() ||
                    it.name.lowercase().contains(query) ||
                    it.description.lowercase().contains(query) ||
                    it.volume.lowercase().contains(query)) &&
                (category == null || category == "PRICELIST" || it.category == category)
            }
            adapter.updateProducts(filtered)
            if (showMessageIfEmpty && filtered.isEmpty()) {
                android.widget.Toast.makeText(this, "No items found.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Supabase-driven product loading with server-side filters via resilient RPC
        fun loadProductsFromSupabase(showLoading: Boolean = false) {
            val sId = storeId ?: return
            if (showLoading) LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    val query = currentQuery.takeIf { it.isNotBlank() }
                    val category = selectedCategory.takeIf { it != "PRICELIST" }
                    
                    val rows = supabase.postgrest.rpc(
                        "get_store_products",
                        buildJsonObject {
                            put("p_store_id", sId)
                            if (category != null) {
                                put("p_category_filter", category)
                            }
                            if (query != null) {
                                put("p_search_query", query)
                            }
                        }
                    ).decodeList<UserProductRow>()
                    
                    Log.d("HomeActivity", "RPC get_store_products returned ${rows.size} products for store $sId")
                    products.clear()
                    for (row in rows) {
                        products.add(
                            com.presyohan.app.adapter.Product(
                                row.product_id,
                                row.name,
                                row.description ?: "",
                                row.price,
                                row.units ?: "",
                                row.category ?: ""
                            )
                        )
                    }
                    adapter.updateProducts(products)
                    filterAndDisplayProducts()
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Products load via RPC failed: ${e.message}", e)
                }
                if (showLoading) LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        // Initial load
        if (storeId != null) {
            reloadProductsFn = { loadProductsFromSupabase(false) }
            loadProductsFromSupabase(true)
        }

        val searchEditText = findViewById<android.widget.EditText>(R.id.searchEditText)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s.toString().trim().lowercase()
                // Re-query Supabase on search update, no overlay for search
                loadProductsFromSupabase(false)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val searchIcon = findViewById<ImageView>(R.id.imageView4)
        searchIcon.setOnClickListener {
            currentQuery = searchEditText.text.toString().trim().lowercase()
            filterAndDisplayProducts(true)
        }

        val searchItemButton = findViewById<ImageView>(R.id.searchItemButton)
        searchItemButton.setOnClickListener {
            searchEditText.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        val categoryLabel = findViewById<TextView>(R.id.categoryLabel)
        val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
        val categoryDrawerButton = findViewById<ImageView>(R.id.categoryDrawerButton)

        val categories = mutableListOf("PRICELIST")
        // Make category dropdown functional
        categorySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                selectedCategory = categories[position]
                categoryLabel.text = categories[position].uppercase()
                recyclerView.layoutManager = GridLayoutManager(this@HomeActivity, 2)
                // Re-query Supabase on category change, brief overlay is OK
                loadProductsFromSupabase(true)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        // Capitalize spinner items visually only
        val adapterSpinner = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                (view as? android.widget.TextView)?.text = categories[position].uppercase()
                return view
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? android.widget.TextView)?.text = categories[position].uppercase()
                return view
            }
        }
        categorySpinner.adapter = adapterSpinner

        // Load categories from Supabase for the current store via resilient RPC
        if (storeId != null) {
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    val rows = supabase.postgrest.rpc(
                        "get_user_categories",
                        buildJsonObject { put("p_store_id", storeId) }
                    ).decodeList<UserCategoryRow>()
                    
                    Log.d("HomeActivity", "RPC get_user_categories returned ${rows.size} categories")
                    for (row in rows) {
                        val name = row.name
                        if (!categories.contains(name)) {
                            categories.add(name)
                        }
                    }
                    adapterSpinner.notifyDataSetChanged()
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Categories load via RPC failed: ${e.message}", e)
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        // Set default selection to PRICELIST
        categorySpinner.setSelection(0)
        categoryLabel.text = categories[0]

        // Make Spinner 1dp wide and transparent so only the dropdown is used
        categorySpinner.background = null
        categorySpinner.layoutParams.width = 1
        categorySpinner.requestLayout()

        // When the drawer icon is clicked, open the Spinner dropdown
        categoryDrawerButton.setOnClickListener {
            categorySpinner.performClick()
        }

        addButton.setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivityForResult(intent, REQUEST_ADD_ITEM)
        }

        val notifIcon = findViewById<ImageView>(R.id.notifIcon)
        notifIcon.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        loadNotifBadge()
    }

    override fun onResume() {
        super.onResume()
        val storeId = intent.getStringExtra("storeId")
        val storeName = intent.getStringExtra("storeName")
        SessionManager.markStoreHome(this, storeId, storeName)
        // Refresh products and notification badge when returning to this screen
        reloadProductsFn?.invoke()
        loadNotifBadge()
    }

    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            finishAffinity()
        } else {
            lastBackPress = now
            android.widget.Toast.makeText(this, "Press again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showManageItemDialog(product: com.presyohan.app.adapter.Product, storeId: String?, storeName: String?) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_manage_item, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<android.widget.TextView>(R.id.textProductName).text = product.name
        view.findViewById<android.widget.TextView>(R.id.textProductDescription).text = product.description
        view.findViewById<android.widget.TextView>(R.id.textProductPrice).text = "₱%.2f".format(product.price)
        view.findViewById<android.widget.TextView>(R.id.textProductUnit).text = product.volume

        view.findViewById<android.widget.ImageView>(R.id.btnEdit).setOnClickListener {
            // Open Edit Item activity, pass product info
            if (storeId.isNullOrBlank() || storeName.isNullOrBlank()) {
                android.widget.Toast.makeText(this, "Store info missing. Cannot edit item.", android.widget.Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this, EditItemActivity::class.java)
            intent.putExtra("productId", product.id)
            intent.putExtra("productName", product.name)
            intent.putExtra("productDescription", product.description)
            intent.putExtra("productPrice", product.price)
            intent.putExtra("productUnit", product.volume)
            intent.putExtra("productCategory", product.category)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            android.util.Log.d("HomeActivity", "Passing storeId='$storeId', storeName='$storeName' to EditItemActivity")
            startActivityForResult(intent, REQUEST_EDIT_ITEM)
            dialog.dismiss()
        }
        view.findViewById<android.widget.ImageView>(R.id.btnDelete).setOnClickListener {
            // Show confirmation dialog before deleting item
            val confirmDialog = android.app.Dialog(this)
            val confirmView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
            confirmDialog.setContentView(confirmView)
            confirmDialog.setCancelable(true)
            confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            confirmView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = "Delete Item"
            confirmView.findViewById<android.widget.TextView>(R.id.confirmMessage).text = "Are you sure you want to delete this item? This action cannot be undone."
            confirmView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancel).setOnClickListener { confirmDialog.dismiss() }
            confirmView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnDelete).setOnClickListener {
                // Delete product via Supabase
                val storeId = intent.getStringExtra("storeId")
                if (storeId != null) {
                    lifecycleScope.launch {
                        try {
                            supabase.postgrest["products"].delete {
                                filter {
                                    eq("id", product.id)
                                    eq("store_id", storeId)
                                }
                            }
                            android.widget.Toast.makeText(this@HomeActivity, "Item deleted.", android.widget.Toast.LENGTH_SHORT).show()
                            val catName = product.category.trim()
                            if (catName.isNotEmpty()) {
                                // Await cleanup to avoid coroutine cancellation on activity recreate
                                deleteCategoryIfEmpty(storeId, catName)
                            }
                            confirmDialog.dismiss()
                            dialog.dismiss()
                            recreate()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this@HomeActivity, "Unable to delete item.", android.widget.Toast.LENGTH_SHORT).show()
                        }
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
                    val pending = supabase.postgrest["notifications"].select(Columns.list("id", "receiver_user_id", "read")) {
                        filter {
                            eq("receiver_user_id", userIdNotif)
                            eq("read", false)
                        }
                        limit(1)
                    }.decodeList<NotificationRow>()
                    notifDot.visibility = if (pending.isNotEmpty()) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    notifDot.visibility = View.GONE
                    android.util.Log.e("HomeActivity", "Notif badge load failed", e)
                }
            }
        } else if (notifDot != null) {
            notifDot.visibility = View.GONE
        }
    }

    private suspend fun deleteCategoryIfEmpty(storeId: String, categoryName: String) {
        try {
            // Find the category id by name
            val catRows = supabase.postgrest["categories"].select {
                filter {
                    eq("store_id", storeId)
                    eq("name", categoryName)
                }
                limit(1)
            }.decodeList<CategoryRow>()
            val cat = catRows.firstOrNull() ?: return

            // Check if any products still reference this category id
            @kotlinx.serialization.Serializable
            data class ProductIdRow(val id: String, val category_id: String? = null)
            val prodRows = supabase.postgrest["products"].select {
                filter {
                    eq("store_id", storeId)
                    eq("category_id", cat.id)
                }
                limit(1)
            }.decodeList<ProductIdRow>()

            if (prodRows.isEmpty()) {
                supabase.postgrest["categories"].delete {
                    filter {
                        eq("store_id", storeId)
                        eq("id", cat.id)
                    }
                }
                android.util.Log.d("HomeActivity", "Deleted empty category: $categoryName")
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeActivity", "deleteCategoryIfEmpty failed", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_ITEM || requestCode == REQUEST_ADD_ITEM) {
            // No need to manually reload, snapshot listener will update
        }
    }
}
