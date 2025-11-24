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
import android.view.MotionEvent
import android.widget.AdapterView

class AddItemActivity : AppCompatActivity() {
    private var storeId: String? = null
    private var storeName: String? = null
    private lateinit var loadingOverlay: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_item)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName") ?: "Store name"

        // Helper function to show the dialog
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
        priceDisplay.visibility = View.GONE
        priceDisplay.textSize = 18f
        priceDisplay.setTextColor(resources.getColor(R.color.presyo_orange, null))

        val categories = mutableListOf("Add Category")
        val categoryIdByName = mutableMapOf<String, String>()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spinner.adapter = adapter

        // Load categories via Supabase RPC
        val currentStoreId = storeId
        if (currentStoreId != null) {
            LoadingOverlayHelper.show(loadingOverlay)
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
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        // --- FIXED SPINNER LOGIC START ---

        // 1. Handle touch: If list is empty (only "Add Category"), open dialog immediately without dropdown.
        // We check ACTION_UP to ensure it only fires once (fixes double dialog).
        spinner.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP && categories.size == 1) {
                showAddCategoryDialog { newCategory, newCategoryId ->
                    categories.add(newCategory)
                    categoryIdByName[newCategory] = newCategoryId
                    adapter.notifyDataSetChanged()
                    spinner.setSelection(categories.indexOf(newCategory))
                }
                v.performClick()
                true // Consume event
            } else {
                false // Allow dropdown to open
            }
        }

        // 2. Handle selection: If list has items, wait for user to select "Add Category" (Index 0).
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0 && categories.size > 1) {
                    showAddCategoryDialog { newCategory, newCategoryId ->
                        categories.add(newCategory)
                        categoryIdByName[newCategory] = newCategoryId
                        adapter.notifyDataSetChanged()
                        spinner.setSelection(categories.indexOf(newCategory))
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        // --- FIXED SPINNER LOGIC END ---

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

            LoadingOverlayHelper.show(loadingOverlay)
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
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        val btnAddMultipleItems = findViewById<android.widget.Button>(R.id.btnAddMultipleItems)
        val storeNameText = findViewById<TextView>(R.id.storeText)
        val storeBranchText = findViewById<TextView>(R.id.storeBranchText)
        storeNameText.text = storeName
        if (storeId != null) {
            val sid: String = storeId!!
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    val rows = SupabaseProvider.client.postgrest["stores"].select(columns = Columns.list("branch")) {
                        filter { eq("id", sid) }
                        limit(1)
                    }.decodeList<StoreBranchRow>()
                    val branch = rows.firstOrNull()?.branch ?: ""
                    storeBranchText.text = branch
                } catch (_: Exception) {
                    storeBranchText.text = ""
                }
                LoadingOverlayHelper.hide(loadingOverlay)
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

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> {
                    val intent = android.content.Intent(this, StoreActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    val intent = android.content.Intent(this, NotificationActivity::class.java)
                    startActivity(intent)
                    drawerLayout.close()
                    true
                }
                R.id.nav_logout -> {
                    showLogoutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun showLogoutDialog() {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogTitle).text = "Log Out?"
        view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to log out of Presyohan?"

        view.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<android.widget.Button>(R.id.btnDelete).apply {
            text = "Log Out"
            setOnClickListener {
                lifecycleScope.launch {
                    try {
                        SupabaseAuthService.signOut()
                    } catch (_: Exception) { }
                    val intent = android.content.Intent(this@AddItemActivity, LoginActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
    }

    @kotlinx.serialization.Serializable
    data class MinimalCategoryRow(val id: String, val store_id: String, val name: String)
    @kotlinx.serialization.Serializable
    data class MinimalProductRow(val id: String, val store_id: String, val category_id: String)
    @kotlinx.serialization.Serializable
    data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)
    @kotlinx.serialization.Serializable
    data class StoreBranchRow(val branch: String?)
}