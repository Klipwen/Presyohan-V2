package com.presyohan.app

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import android.widget.ImageView

class AddItemActivity : AppCompatActivity() {
    private var storeId: String? = null
    private var storeName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_item)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName") ?: "Store name"

        fun showAddCategoryDialog(onCategoryAdded: (String, String) -> Unit) {
            val dialog = android.app.Dialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_add_category, null)
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val input = view.findViewById<android.widget.EditText>(R.id.inputCategoryName)
            val btnAdd = view.findViewById<android.widget.Button>(R.id.btnAdd)
            val btnBack = view.findViewById<android.widget.Button>(R.id.btnBack)

            btnAdd.setOnClickListener {
                val category = input.text.toString().trim()
                if (category.isNotEmpty()) {
                    val currentStoreIdInner = storeId ?: return@setOnClickListener
                    lifecycleScope.launch {
                        try {
                            val result = SupabaseProvider.client.postgrest.rpc(
                                "add_category",
                                buildJsonObject {
                                    put("p_store_id", JsonPrimitive(currentStoreIdInner))
                                    put("p_name", category)
                                }
                            ).decodeList<UserCategoryRow>()
                            val row = result.firstOrNull()
                            if (row != null) {
                                onCategoryAdded(row.name, row.category_id)
                                dialog.dismiss()
                            } else {
                                input.error = "Failed to add category"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AddItemActivity", "add_category RPC failed: ${e.message}", e)
                            input.error = "Failed to add category"
                        }
                    }
                } else {
                    input.error = "Enter a category name"
                }
            }
            btnBack.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }

        val spinner = findViewById<android.widget.Spinner>(R.id.spinnerItemCategory)
        val priceEditText = findViewById<android.widget.EditText>(R.id.inputItemPrice)
        val priceDisplay = findViewById<TextView>(R.id.priceDisplay)
        priceDisplay.visibility = View.GONE // Hide by default
        priceDisplay.textSize = 18f
        priceDisplay.setTextColor(resources.getColor(R.color.presyo_orange, null))

        val categories = mutableListOf("Add Category")
        val categoryIdByName = mutableMapOf<String, String>()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spinner.adapter = adapter

        // Load categories via Supabase RPC
        val currentStoreId = storeId
        if (currentStoreId != null) {
            lifecycleScope.launch {
                try {
                    val rows = SupabaseProvider.client.postgrest.rpc(
                        "get_user_categories",
                        buildJsonObject {
                            val sid: String = currentStoreId
                            put("p_store_id", JsonPrimitive(sid))
                        }
                    ).decodeList<UserCategoryRow>()
                    for (row in rows) {
                        if (!categories.contains(row.name)) {
                            categories.add(row.name)
                            categoryIdByName[row.name] = row.category_id
                        }
                    }
                    adapter.notifyDataSetChanged()
                } catch (_: Exception) { /* noop */ }
            }
        }

        // Show dialog on touch if only 'Add Category' exists
        spinner.setOnTouchListener { v, event ->
            if (categories.size == 1) {
                showAddCategoryDialog { newCategory, newCategoryId ->
                    categories.add(newCategory)
                    categoryIdByName[newCategory] = newCategoryId
                    adapter.notifyDataSetChanged()
                    spinner.setSelection(categories.indexOf(newCategory))
                }
                true // consume the touch event
            } else {
                false
            }
        }

        var isSpinnerInitialized = false
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true
                    return
                }
                if (position == 0 && categories.size > 1) { // Only show dialog if there are other categories
                    showAddCategoryDialog { newCategory, newCategoryId ->
                        categories.add(newCategory)
                        categoryIdByName[newCategory] = newCategoryId
                        adapter.notifyDataSetChanged()
                        spinner.setSelection(categories.indexOf(newCategory))
                    }
                    // Reset selection to previous valid category if needed
                    spinner.setSelection(1)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Back button logic
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Price formatting logic
        priceEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val price = s.toString().toDoubleOrNull() ?: 0.0
                if (s.isNullOrEmpty()) {
                    priceDisplay.visibility = View.GONE
                } else {
                    priceDisplay.text = "₱ %.2f".format(price)
                    priceDisplay.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val buttonDone = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.buttonDone)
        val unitsEditText = findViewById<android.widget.EditText>(R.id.inputItemUnit)
        buttonDone.setOnClickListener {
            val itemName = findViewById<android.widget.EditText>(R.id.inputItemName).text.toString().trim()
            val description = findViewById<android.widget.EditText>(R.id.inputItemDescription).text.toString().trim()
            val priceText = findViewById<android.widget.EditText>(R.id.inputItemPrice).text.toString().trim()
            val units = unitsEditText.text.toString().trim()
            val spinner = findViewById<android.widget.Spinner>(R.id.spinnerItemCategory)
            val category = spinner.selectedItem?.toString() ?: ""

            if (itemName.isEmpty() || priceText.isEmpty() || units.isEmpty() || category == "Add Category" || category.isEmpty()) {
                android.widget.Toast.makeText(this, "Complete all fields.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = priceText.toDoubleOrNull() ?: 0.0
                    val storeId = this@AddItemActivity.storeId ?: return@setOnClickListener
                    val storeName = this@AddItemActivity.storeName ?: "Store"

            val categoryId = categoryIdByName[category]
            if (categoryId.isNullOrBlank()) {
                android.widget.Toast.makeText(this, "Invalid category.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "add_product",
                        buildJsonObject {
                            put("p_store_id", JsonPrimitive(storeId))
                            put("p_category_id", JsonPrimitive(categoryId))
                            put("p_name", itemName)
                            put("p_description", description)
                            put("p_price", JsonPrimitive(price))
                            put("p_unit", JsonPrimitive(units))
                        }
                    )
                    android.widget.Toast.makeText(this@AddItemActivity, "Product added.", android.widget.Toast.LENGTH_SHORT).show()
                    val intent = android.content.Intent(this@AddItemActivity, HomeActivity::class.java)
                    intent.putExtra("storeId", storeId)
                    intent.putExtra("storeName", storeName)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    android.util.Log.e("AddItemActivity", "add_product RPC failed: ${e.message}", e)
                    android.widget.Toast.makeText(this@AddItemActivity, "Unable to add product.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnAddMultipleItems = findViewById<android.widget.Button>(R.id.btnAddMultipleItems)
        val storeNameText = findViewById<TextView>(R.id.storeText)
        val storeBranchText = findViewById<TextView>(R.id.storeBranchText)
        storeNameText.text = storeName
        if (storeId != null) {
            // Make a stable local copy to avoid smart-cast issues inside lambdas/coroutines
            val sid: String = storeId!!
            lifecycleScope.launch {
                try {
                    val rows = SupabaseProvider.client.postgrest["stores"].select(columns = Columns.list("branch")) {
                        filter { eq("id", sid) }
                        limit(1)
                    }.decodeList<StoreBranchRow>()
                    val branch = rows.firstOrNull()?.branch ?: ""
                    storeBranchText.text = branch
                } catch (_: Exception) {
                    // Fallback to blank if fetch fails
                    storeBranchText.text = ""
                }
            }
        } else {
            storeBranchText.text = ""
        }
        btnAddMultipleItems.setOnClickListener {
            val intent = android.content.Intent(this, AddMultipleItemsActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
        }

        // Set real store name
        storeNameText.text = storeName

        // Navigation menu icon functionality
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        findViewById<ImageView>(R.id.menuIcon).setOnClickListener {
            drawerLayout.open()
        }

        // Notification icon functionality
        findViewById<ImageView>(R.id.notifIcon).setOnClickListener {
            val intent = android.content.Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        // Set user name and email in navigation drawer header via Supabase
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val headerView = navigationView.getHeaderView(0)
        val userNameText = headerView.findViewById<TextView>(R.id.drawerUserName)
        val userEmailText = headerView.findViewById<TextView>(R.id.drawerUserEmail)
        val supaUser = SupabaseProvider.client.auth.currentUserOrNull()
        userEmailText.text = supaUser?.email ?: ""
        userNameText.text = "User"
        lifecycleScope.launch {
            try {
                val name = SupabaseAuthService.getDisplayName() ?: "User"
                userNameText.text = name
            } catch (_: Exception) { /* noop */ }
        }
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
    }

    @kotlinx.serialization.Serializable
    data class MinimalCategoryRow(val id: String, val store_id: String, val name: String)
    @kotlinx.serialization.Serializable
    data class MinimalProductRow(val id: String, val store_id: String, val category_id: String)

    // Top-level serializers for RPC decoding
    @kotlinx.serialization.Serializable
    data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)

    @kotlinx.serialization.Serializable
    data class StoreBranchRow(val branch: String?)

    private fun deleteCategoryIfEmpty(storeId: String, categoryName: String) {
        lifecycleScope.launch {
            try {
                val supabase = SupabaseProvider.client
                // Find category id by name
                val cats = supabase.postgrest["categories"].select {
                    filter { eq("store_id", storeId); eq("name", categoryName) }
                    limit(1)
                }.decodeList<MinimalCategoryRow>()
                val catId = cats.firstOrNull()?.id ?: return@launch
                // Check if any products exist for this category
                val prods = supabase.postgrest["products"].select {
                    filter { eq("store_id", storeId); eq("category_id", catId) }
                    limit(1)
                }.decodeList<MinimalProductRow>()
                if (prods.isEmpty()) {
                    supabase.postgrest["categories"].delete {
                        filter { eq("id", catId); eq("store_id", storeId) }
                    }
                }
            } catch (_: Exception) { /* noop */ }
        }
    }
}