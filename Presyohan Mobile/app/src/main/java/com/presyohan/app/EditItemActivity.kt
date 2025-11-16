package com.presyohan.app

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.content.Intent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.presyohan.app.NotificationActivity

class EditItemActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_item)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val menuIcon = findViewById<ImageView>(R.id.menuIcon)
        menuIcon.setOnClickListener { drawerLayout.open() }
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> {
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    val intent = Intent(this, NotificationActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawer(android.view.Gravity.START)
                    true
                }
                else -> false
            }
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val inputName = findViewById<EditText>(R.id.inputItemName)
        val inputDescription = findViewById<EditText>(R.id.inputItemDescription)
        val inputPrice = findViewById<EditText>(R.id.inputItemPrice)
        val inputUnit = findViewById<EditText>(R.id.inputItemUnit)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerItemCategory)

        // Prefill fields from intent extras
        val productName = intent.getStringExtra("productName") ?: ""
        val productDescription = intent.getStringExtra("productDescription") ?: ""
        val productPrice = intent.getDoubleExtra("productPrice", 0.0)
        val productUnit = intent.getStringExtra("productUnit") ?: ""
        val productCategory = intent.getStringExtra("productCategory") ?: ""
        val storeId = intent.getStringExtra("storeId") ?: ""

        inputName.setText(productName)
        inputDescription.setText(productDescription)
        inputPrice.setText(if (productPrice == 0.0) "" else String.format("%.2f", productPrice))
        inputUnit.setText(productUnit)

        // Load categories from Supabase and set spinner
        val categories = mutableListOf<String>()
        val categoryIdMap = mutableMapOf<String, String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spinnerCategory.adapter = adapter
        if (storeId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    @Serializable
                    data class UserCategoryRow(
                        val category_id: String,
                        val store_id: String,
                        val name: String
                    )
                    val rows = SupabaseProvider.client.postgrest.rpc(
                        "get_user_categories",
                        buildJsonObject { put("p_store_id", storeId) }
                    ).decodeList<UserCategoryRow>()
                    categories.clear()
                    categoryIdMap.clear()
                    for (row in rows) {
                        val nameUp = row.name.trim()
                        if (nameUp.isNotEmpty() && !categories.contains(nameUp)) {
                            categories.add(nameUp)
                            categoryIdMap[nameUp] = row.category_id
                        }
                    }
                    if (productCategory.isNotEmpty() && categories.none { it.trim().equals(productCategory.trim(), ignoreCase = true) }) {
                        categories.add(productCategory.trim().uppercase())
                    }
                    if (!categories.contains("Add Category")) {
                        categories.add("Add Category")
                    }
                    adapter.notifyDataSetChanged()
                    val normalizedCategories = categories.map { it.trim().uppercase() }
                    val normalizedProductCategory = productCategory.trim().uppercase()
                    val index = normalizedCategories.indexOf(normalizedProductCategory)
                    if (index >= 0) {
                        spinnerCategory.setSelection(index)
                    } else if (categories.isNotEmpty()) {
                        spinnerCategory.setSelection(0)
                    }
                } catch (e: Exception) {
                    Log.e("EditItemActivity", "Failed to load categories: ${e.localizedMessage}")
                    Toast.makeText(this@EditItemActivity, "Failed to load categories.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.e("EditItemActivity", "storeId is empty! Cannot load categories.")
        }

        spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            var isFirst = true
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isFirst) {
                    isFirst = false
                    return
                }
                if (categories.isNotEmpty() && position == categories.size - 1) { // 'Add Category' selected
                    showAddCategoryDialog(storeId, categories, categoryIdMap, adapter, spinnerCategory)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Set store name in header
        val storeName = intent.getStringExtra("storeName") ?: "Store name"
        val storeNameText = findViewById<TextView>(R.id.storeText)
        val storeBranchText = findViewById<TextView>(R.id.storeBranchText)
        storeNameText.text = storeName
        if (!storeId.isNullOrBlank()) {
            lifecycleScope.launch {
                try {
                    val result = SupabaseProvider.client.postgrest["stores"].select(Columns.list("branch")) {
                        filter { eq("id", storeId) }
                        limit(1)
                    }.decodeList<Map<String, String>>()
                    val branch = result.firstOrNull()?.get("branch") ?: ""
                    storeBranchText.text = branch
                } catch (e: Exception) {
                    Log.e("EditItemActivity", "Failed to load store branch: ${e.localizedMessage}")
                    storeBranchText.text = ""
                }
            }
        } else {
            storeBranchText.text = ""
        }

        val notifIcon = findViewById<ImageView>(R.id.notifIcon)
        notifIcon.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        val notifDot = findViewById<View>(R.id.notifDot)
        val userIdNotif = SupabaseProvider.client.auth.currentUserOrNull()?.id
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

        // Update price label (upper right of price) live as user edits
        val priceDisplay = findViewById<TextView>(R.id.priceDisplay)
        inputPrice.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val price = s.toString().toDoubleOrNull() ?: 0.0
                priceDisplay.text = "₱ %.2f".format(price)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val doneButton = findViewById<Button>(R.id.buttonDone)
        doneButton.setOnClickListener {
            val newName = inputName.text.toString().trim()
            val newDescription = inputDescription.text.toString().trim().ifBlank { null }
            val newPrice = inputPrice.text.toString().toDoubleOrNull() ?: 0.0
            val newUnit = inputUnit.text.toString().trim()
            val selected = spinnerCategory.selectedItem?.toString() ?: ""
            val storeIdForUpdate = intent.getStringExtra("storeId") ?: return@setOnClickListener
            val productId = intent.getStringExtra("productId") ?: return@setOnClickListener
            if (newName.isEmpty() || newUnit.isEmpty() || selected.isEmpty() || selected == "Add Category") {
                Toast.makeText(this, "Complete all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val catName = selected.trim().uppercase()
                    // Resolve or create category_id via RPC
                    var categoryId = categoryIdMap[catName]
                    if (categoryId.isNullOrBlank()) {
                        @Serializable
                        data class NewCategoryRow(
                            val category_id: String,
                            val store_id: String,
                            val name: String
                        )
                        val inserted = SupabaseProvider.client.postgrest.rpc(
                            "add_category",
                            buildJsonObject {
                                put("p_store_id", storeIdForUpdate)
                                put("p_name", catName)
                            }
                        ).decodeList<NewCategoryRow>()
                        categoryId = inserted.firstOrNull()?.category_id
                        val normalized = inserted.firstOrNull()?.name ?: catName
                        if (!categoryId.isNullOrBlank()) {
                            categoryIdMap[normalized] = categoryId
                        }
                    }

                    // Update product in Supabase using JSON payload
                    val updatePayload = buildJsonObject {
                        put("name", newName)
                        if (newDescription != null) {
                            put("description", newDescription)
                        } else {
                            put("description", kotlinx.serialization.json.JsonNull)
                        }
                        put("price", newPrice)
                        put("unit", newUnit)
                        if (categoryId != null) {
                            put("category_id", categoryId)
                        } else {
                            put("category_id", kotlinx.serialization.json.JsonNull)
                        }
                    }
                    SupabaseProvider.client.postgrest["products"].update(updatePayload) {
                        filter { eq("id", productId); eq("store_id", storeIdForUpdate) }
                    }
                    Toast.makeText(this@EditItemActivity, "Product updated.", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Log.e("EditItemActivity", "Update failed: ${e.localizedMessage}")
                    Toast.makeText(this@EditItemActivity, "Unable to update product.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnManageItems).setOnClickListener {
            val storeId = intent.getStringExtra("storeId")
            val storeName = intent.getStringExtra("storeName")
            if (storeId.isNullOrBlank() || storeName.isNullOrBlank()) {
                android.widget.Toast.makeText(this, "Store info missing. Cannot manage items.", android.widget.Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this, ManageItemsActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
        }

        // Set real user name and email in navigation drawer header
        val headerView = navigationView.getHeaderView(0)
        val userNameText = headerView.findViewById<TextView>(R.id.drawerUserName)
        val userEmailText = headerView.findViewById<TextView>(R.id.drawerUserEmail)
        val currentUser = SupabaseProvider.client.auth.currentUserOrNull()
        userNameText.text = currentUser?.userMetadata?.get("name")?.toString() ?: "User"
        userEmailText.text = currentUser?.email ?: ""
    }

    private fun showAddCategoryDialog(
        storeId: String,
        categories: MutableList<String>,
        categoryIdMap: MutableMap<String, String>,
        adapter: ArrayAdapter<String>,
        spinner: Spinner
    ) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val input = view.findViewById<EditText>(R.id.inputCategoryName)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val btnBack = view.findViewById<Button>(R.id.btnBack)

        btnAdd.setOnClickListener {
            val categoryRaw = input.text.toString().trim()
            val category = categoryRaw.uppercase()
            if (category.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        @Serializable
                        data class NewCategoryRow(
                            val category_id: String,
                            val store_id: String,
                            val name: String
                        )
                        val inserted = SupabaseProvider.client.postgrest.rpc(
                            "add_category",
                            buildJsonObject {
                                put("p_store_id", storeId)
                                put("p_name", category)
                            }
                        ).decodeList<NewCategoryRow>()
                        val newId = inserted.firstOrNull()?.category_id
                        val normalizedName = inserted.firstOrNull()?.name ?: category
                        if (!newId.isNullOrBlank()) {
                            categoryIdMap[normalizedName] = newId
                        }
                        // Insert before 'Add Category'
                        val addIndex = categories.indexOf("Add Category")
                        val insertIndex = if (addIndex >= 0) addIndex else categories.size
                        if (categories.none { it.equals(normalizedName, ignoreCase = true) }) {
                            categories.add(insertIndex, normalizedName)
                        }
                        adapter.notifyDataSetChanged()
                        spinner.setSelection(categories.indexOf(normalizedName))
                        dialog.dismiss()
                    } catch (e: Exception) {
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
}