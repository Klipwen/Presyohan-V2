package com.project.presyohan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.presyohan.adapter.Product
import com.project.presyohan.adapter.ProductAdapter
import  android.widget.TextView
import android.view.View
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Spinner
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.Auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

class HomeActivity : AppCompatActivity() {
    private val supabase: SupabaseClient
        get() = SupabaseProvider.client

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
    private lateinit var googleSignInClient: GoogleSignInClient
    private val REQUEST_EDIT_ITEM = 1001
    private val REQUEST_ADD_ITEM = 1002
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Google Sign-In setup for logout
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Drawer and menu logic
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
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
                    lifecycleScope.launch {
                        try {
                            SupabaseAuthService.signOut()
                        } catch (_: Exception) { }
                        val intent = Intent(this@HomeActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
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
        userNameText.text = (supaUser?.userMetadata?.get("name") as? String) ?: "User"
        userEmailText.text = supaUser?.email ?: ""

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
            db.collection("stores").document(storeId).get().addOnSuccessListener { doc ->
                val branch = doc.getString("branch") ?: ""
                storeBranchText.text = branch
            }
        } else {
            storeBranchText.text = ""
        }

        val addButton = findViewById<ImageView>(R.id.addButton)
        addButton.visibility = View.GONE // Hide by default until role is fetched

        val products = mutableListOf<com.project.presyohan.adapter.Product>()
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
            lifecycleScope.launch {
                try {
                    val members = supabase.postgrest["store_members"]
                        .select {
                            eq("store_id", storeId)
                            eq("user_id", userId)
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
            }
        }


        var selectedCategory: String? = null
        var currentQuery: String = ""

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

        // Use Firestore snapshot listener for real-time updates
        if (storeId != null) {
            FirebaseFirestore.getInstance()
                .collection("stores").document(storeId)
                .collection("products")
                .addSnapshotListener { result, error ->
                    if (error != null) return@addSnapshotListener
                    products.clear()
                    if (result != null) {
                        for (doc in result) {
                            val id = doc.id
                            val name = doc.getString("name") ?: ""
                            val description = doc.getString("description") ?: ""
                            val price = doc.getDouble("price") ?: 0.0
                            val volume = doc.getString("units") ?: ""
                            val category = doc.getString("category") ?: ""
                            products.add(com.project.presyohan.adapter.Product(id, name, description, price, volume, category))
                        }
                    }
                    adapter.updateProducts(products)
                    filterAndDisplayProducts()
                }
        }

        val searchEditText = findViewById<android.widget.EditText>(R.id.searchEditText)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s.toString().trim().lowercase()
                filterAndDisplayProducts()
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
                filterAndDisplayProducts()
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

        // Load categories from Supabase for the current store
        if (storeId != null) {
            lifecycleScope.launch {
                try {
                    val rows = supabase.postgrest["categories"]
                        .select { eq("store_id", storeId) }
                        .decodeList<CategoryRow>()
                    for (row in rows) {
                        val name = row.name
                        if (!categories.contains(name)) {
                            categories.add(name)
                        }
                    }
                    adapterSpinner.notifyDataSetChanged()
                } catch (e: Exception) {
                    android.util.Log.e("HomeActivity", "Categories load failed", e)
                }
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

        val notifDot = findViewById<View>(R.id.notifDot)
        val userIdNotif = FirebaseAuth.getInstance().currentUser?.uid
        if (notifDot != null && userIdNotif != null) {
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(userIdNotif)
                    .collection("notifications")
                    .whereEqualTo("status", "Pending")
                    .whereEqualTo("unread", true)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            notifDot.visibility = View.GONE
                            android.widget.Toast.makeText(applicationContext, "No internet connection. Some features may not work.", android.widget.Toast.LENGTH_SHORT).show()
                            android.util.Log.e("FirestoreNotif", "Error: ", error)
                            return@addSnapshotListener
                        }
                        notifDot.visibility = if (snapshot != null && !snapshot.isEmpty) View.VISIBLE else View.GONE
                    }
            } catch (e: Exception) {
                notifDot.visibility = View.GONE
                android.widget.Toast.makeText(applicationContext, "No internet connection. Some features may not work.", android.widget.Toast.LENGTH_SHORT).show()
                android.util.Log.e("FirestoreNotif", "Exception: ", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE).edit()
        prefs.putString("last_screen", "home")
        val storeId = intent.getStringExtra("storeId")
        val storeName = intent.getStringExtra("storeName")
        if (storeId != null && storeName != null) {
            prefs.putString("last_store_id", storeId)
            prefs.putString("last_store_name", storeName)
        }
        prefs.apply()
    }

    override fun onBackPressed() {
        // Exit the app when back is pressed from Home screen
        finishAffinity()
    }

    private fun showManageItemDialog(product: com.project.presyohan.adapter.Product, storeId: String?, storeName: String?) {
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
                android.widget.Toast.makeText(this, "Store information missing. Cannot edit item.", android.widget.Toast.LENGTH_LONG).show()
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
                                eq("id", product.id)
                                eq("store_id", storeId)
                            }
                            android.widget.Toast.makeText(this@HomeActivity, "Item deleted!", android.widget.Toast.LENGTH_SHORT).show()
                            if (product.category.isNotBlank()) {
                                deleteCategoryIfEmpty(storeId, product.category)
                            }
                            confirmDialog.dismiss()
                            dialog.dismiss()
                            recreate()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this@HomeActivity, "Failed to delete item.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            confirmDialog.show()
        }
        dialog.show()
    }

    private fun deleteCategoryIfEmpty(storeId: String, categoryName: String) {
        lifecycleScope.launch {
            try {
                val rows = supabase.postgrest["products"].select {
                    eq("store_id", storeId)
                    eq("category", categoryName)
                    limit(1)
                }.decodeList<ProductRow>()
                if (rows.isEmpty()) {
                    supabase.postgrest["categories"].delete {
                        eq("store_id", storeId)
                        eq("name", categoryName)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeActivity", "deleteCategoryIfEmpty failed", e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_ITEM || requestCode == REQUEST_ADD_ITEM) {
            // No need to manually reload, snapshot listener will update
        }
    }
}
}
