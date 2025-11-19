package com.presyohan.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import androidx.appcompat.widget.AppCompatButton
import com.presyohan.app.adapter.ManageItemsAdapter
import com.presyohan.app.adapter.ManageItemData
// ... existing code ...
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.app.Dialog
import android.view.LayoutInflater
import android.widget.Button

class ManageItemsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var doneButton: AppCompatButton
    private lateinit var backButton: ImageView
    private lateinit var itemCounter: TextView
    private lateinit var categoryLabel: TextView
    private lateinit var categorySpinner: Spinner
    private lateinit var categoryDrawerButton: ImageView
    private lateinit var adapter: ManageItemsAdapter
    private var allItems = mutableListOf<ManageItemData>()
    private var filteredItems = mutableListOf<ManageItemData>()
    private var categories = mutableListOf<String>()
    private var selectedCategory: String? = null
    private var hasChanges = false
    private var storeId: String? = null
    private var storeName: String? = null
    private lateinit var loadingOverlay: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_items)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        val storeId = this.storeId
        if (storeId.isNullOrBlank()) {
            android.widget.Toast.makeText(this, "No store ID provided. Cannot manage items.", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }
        recyclerView = findViewById(R.id.recyclerViewItems)
        doneButton = findViewById(R.id.buttonDone)
        backButton = findViewById(R.id.btnBack)
        itemCounter = findViewById(R.id.itemCounter)
        categoryLabel = findViewById(R.id.categoryLabel)
        categorySpinner = findViewById(R.id.categorySpinner)
        categoryDrawerButton = findViewById(R.id.categoryDrawerButton)
        adapter = ManageItemsAdapter(filteredItems, { position, item ->
            hasChanges = true
            updateItemCounter()
            // Also update the corresponding item in allItems
            val globalIndex = allItems.indexOfFirst { it.id == item.id }
            if (globalIndex != -1) {
                allItems[globalIndex].name = item.name
                allItems[globalIndex].unit = item.unit
                allItems[globalIndex].price = item.price
                allItems[globalIndex].description = item.description
                allItems[globalIndex].category = item.category
            }
        }, { position, item ->
            try {
                // Show confirmation dialog for delete
                val dialog = Dialog(this)
                val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
                dialog.setContentView(view)
                dialog.setCancelable(true)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                view.findViewById<TextView>(R.id.dialogTitle).text = "Delete Item?"
                view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to delete this item? This action cannot be undone."
                view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
                view.findViewById<Button>(R.id.btnDelete).apply {
                    text = "Delete"
                    setOnClickListener {
                        try {
                            // Remove from both lists with bounds checks
                            val globalIndex = allItems.indexOfFirst { it.id == item.id }
                            if (globalIndex != -1 && globalIndex < allItems.size) allItems.removeAt(globalIndex)
                            if (position >= 0 && position < filteredItems.size) filteredItems.removeAt(position)
                            adapter.updateItems(filteredItems)
                            hasChanges = true
                            updateItemCounter()
                            val storeId = this@ManageItemsActivity.storeId
                            if (!storeId.isNullOrBlank()) {
                                lifecycleScope.launch {
                                    try {
                                        // Delete from Supabase products
                                        SupabaseProvider.client.postgrest["products"].delete {
                                            filter {
                                                eq("id", item.id)
                                                eq("store_id", storeId)
                                            }
                                        }
                                        // If no items remain in this category, delete category from Supabase
                                        val categoryToCheck = item.category
                                        if (categoryToCheck.isNotBlank() && allItems.none { it.category == categoryToCheck }) {
                                            @Serializable
                                            data class MinimalCategoryRow(val id: String, val store_id: String, val name: String)
                                            @Serializable
                                            data class MinimalProductRow(val id: String, val store_id: String, val category_id: String)
                                            val cats = SupabaseProvider.client.postgrest["categories"].select {
                                                filter { eq("store_id", storeId); eq("name", categoryToCheck) }
                                                limit(1)
                                            }.decodeList<MinimalCategoryRow>()
                                            val catId = cats.firstOrNull()?.id
                                            if (!catId.isNullOrBlank()) {
                                                val prods = SupabaseProvider.client.postgrest["products"].select {
                                                    filter { eq("store_id", storeId); eq("category_id", catId) }
                                                    limit(1)
                                                }.decodeList<MinimalProductRow>()
                                                if (prods.isEmpty()) {
                                                    SupabaseProvider.client.postgrest["categories"].delete {
                                                        filter { eq("id", catId); eq("store_id", storeId) }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (_: Exception) { /* noop */ }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ManageItems", "Delete error: "+e.localizedMessage)
                            android.widget.Toast.makeText(this@ManageItemsActivity, "Unable to delete item.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        dialog.dismiss()
                    }
                }
                dialog.show()
            } catch (e: Exception) {
                android.util.Log.e("ManageItems", "Dialog error: "+e.localizedMessage)
                android.widget.Toast.makeText(this@ManageItemsActivity, "Unable to open dialog.", android.widget.Toast.LENGTH_LONG).show()
            }
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val filterCategory = intent.getStringExtra("filterCategory")
        if (!filterCategory.isNullOrBlank()) {
            selectedCategory = filterCategory
            categoryLabel.text = filterCategory.replaceFirstChar { it.uppercase() }
        }

        // Fetch items and categories from Supabase via resilient RPC
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @kotlinx.serialization.Serializable
                data class UserProductRow(
                    val product_id: String,
                    val store_id: String,
                    val name: String,
                    val description: String? = null,
                    val price: Double = 0.0,
                    val units: String? = null,
                    val category: String? = null
                )

                val sId = storeId
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    kotlinx.serialization.json.buildJsonObject {
                        put("p_store_id", sId)
                        selectedCategory?.takeIf { it != "PRICELIST" && it.isNotBlank() }?.let {
                            put("p_category_filter", it)
                        }
                    }
                ).decodeList<UserProductRow>()

                allItems.clear()
                categories.clear()
                val categorySet = mutableSetOf<String>()
                for (row in rows) {
                    val name = row.name
                    val description = row.description ?: ""
                    val price = row.price
                    val unit = row.units ?: ""
                    val category = row.category ?: ""
                    allItems.add(ManageItemData(row.product_id, name, unit, price, description, category))
                    if (category.isNotBlank()) categorySet.add(category)
                }
                allItems.sortBy { it.name.lowercase() }
                categories.add("All")
                categories.addAll(categorySet.sortedBy { it.lowercase() })
                updateFilter()

                categoryDrawerButton.setOnClickListener { showCategoryDialog() }
                // Set label to selected category or ALL ITEMS
                if (!selectedCategory.isNullOrBlank()) {
                    categoryLabel.text = selectedCategory!!.replaceFirstChar { it.uppercase() }
                } else {
                    categoryLabel.text = "ALL ITEMS"
                }
            } catch (e: Exception) {
                android.util.Log.e("ManageItems", "Fetch error: ${e.localizedMessage}")
                android.widget.Toast.makeText(this@ManageItemsActivity, "Unable to load items.", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }

        // Done button with confirmation dialog
        doneButton.setOnClickListener {
            val dialog = Dialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            view.findViewById<TextView>(R.id.dialogTitle).text = "Save Changes?"
            view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to save all changes to your items?"
            view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
            view.findViewById<Button>(R.id.btnDelete).apply {
                text = "Save"
                setOnClickListener {
                    // Force all EditTexts in the RecyclerView to lose focus
                    recyclerView.clearFocus()
                    val storeId = this@ManageItemsActivity.storeId
                    if (storeId.isNullOrBlank()) {
                        android.widget.Toast.makeText(this@ManageItemsActivity, "Store ID missing. Cannot save.", android.widget.Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        return@setOnClickListener
                    }
                    // Save all items to Supabase
                    LoadingOverlayHelper.show(loadingOverlay)
                    lifecycleScope.launch {
                        try {
                            // Preload categories for mapping name -> id

                            val existingCategories = try {
                                SupabaseProvider.client.postgrest.rpc(
                                    "get_user_categories",
                                    kotlinx.serialization.json.buildJsonObject { put("p_store_id", storeId) }
                                ).decodeList<UserCategoryRow>()
                            } catch (_: Exception) {
                                @kotlinx.serialization.Serializable
                                data class MinimalCategoryRow(val id: String, val name: String)
                                SupabaseProvider.client.postgrest["categories"].select {
                                    filter { eq("store_id", storeId) }
                                }.decodeList<MinimalCategoryRow>().map { UserCategoryRow(it.id, storeId ?: "", it.name) }
                            }
                            val categoryMap = existingCategories.associate { it.name to it.category_id }.toMutableMap()

                            for (item in allItems) {
                                // Resolve or create category
                                val catName = item.category.trim()
                                val categoryId = if (catName.isBlank()) {
                                    null
                                } else {
                                    categoryMap[catName] ?: run {
                                        // Create missing category via RPC
                                        val newId = try {
                                            val inserted = SupabaseProvider.client.postgrest.rpc(
                                                "add_category",
                                                kotlinx.serialization.json.buildJsonObject {
                                                    put("p_store_id", storeId)
                                                    put("p_name", catName)
                                                }
                                            ).decodeList<NewCategoryRow>()
                                            inserted.firstOrNull()?.category_id
                                        } catch (_: Exception) {
                                            @kotlinx.serialization.Serializable
                                            data class InsertedCategory(val id: String)
                                            val res = SupabaseProvider.client.postgrest["categories"].insert(
                                                kotlinx.serialization.json.buildJsonObject {
                                                    put("store_id", storeId)
                                                    put("name", catName)
                                                }
                                            ).decodeList<InsertedCategory>()
                                            res.firstOrNull()?.id
                                        }
                                        if (!newId.isNullOrBlank()) {
                                            categoryMap[catName] = newId
                                        }
                                        newId
                                    }
                                }

                                // Update product row in Supabase using JSON payload (avoid Any serializer errors)
                                val updatePayload = kotlinx.serialization.json.buildJsonObject {
                                    put("name", item.name)
                                    if (item.description != null) {
                                        put("description", item.description)
                                    } else {
                                        put("description", kotlinx.serialization.json.JsonNull)
                                    }
                                    put("price", item.price)
                                    val unitVal = item.unit ?: ""
                                    put("unit", unitVal)
                                    if (categoryId != null) {
                                        put("category_id", categoryId)
                                    } else {
                                        put("category_id", kotlinx.serialization.json.JsonNull)
                                    }
                                }
                                SupabaseProvider.client.postgrest["products"].update(updatePayload) {
                                    filter { eq("id", item.id); eq("store_id", storeId) }
                                }
                            }

                            hasChanges = false
                            dialog.dismiss()
                            // Navigate to HomeActivity with storeId and storeName
                            val storeName = intent.getStringExtra("storeName")
                            val homeIntent = android.content.Intent(this@ManageItemsActivity, HomeActivity::class.java)
                            homeIntent.putExtra("storeId", storeId)
                            homeIntent.putExtra("storeName", storeName)
                            homeIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(homeIntent)
                            finish()
                        } catch (e: Exception) {
                            android.util.Log.e("ManageItems", "Save error: ${e.localizedMessage}")
                            android.widget.Toast.makeText(this@ManageItemsActivity, "Unable to save items.", android.widget.Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        }
                        LoadingOverlayHelper.hide(loadingOverlay)
                    }
                }
            }
            dialog.show()
        }

        // Back button with confirmation dialog if changes
        backButton.setOnClickListener {
            // Force all EditTexts in the RecyclerView to lose focus
            recyclerView.clearFocus()
            handleBackWithConfirmation()
        }
    }

    override fun onBackPressed() {
        // Force all EditTexts in the RecyclerView to lose focus
        recyclerView.clearFocus()
        handleBackWithConfirmation()
    }

    private fun handleBackWithConfirmation() {
        if (hasChanges) {
            val dialog = Dialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            view.findViewById<TextView>(R.id.dialogTitle).text = "Discard Changes?"
            view.findViewById<TextView>(R.id.confirmMessage).text = "You have unsaved changes. Are you sure you want to leave? All changes will be lost."
            view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
            view.findViewById<Button>(R.id.btnDelete).apply {
                text = "Discard"
                setOnClickListener {
                    hasChanges = false
                    dialog.dismiss()
                    finish()
                }
            }
            dialog.show()
        } else {
            finish()
        }
    }

    private fun updateFilter() {
        filteredItems.clear()
        if (selectedCategory == null || selectedCategory == "All") {
            // Use references from allItems
            filteredItems.addAll(allItems)
        } else {
            // Use references from allItems, not copies
            filteredItems.addAll(allItems.filter { it.category == selectedCategory })
        }
        filteredItems.sortBy { it.name.lowercase() }
        adapter.updateItems(filteredItems)
        updateItemCounter()
    }

    private fun updateItemCounter() {
        val count = filteredItems.size
        itemCounter.text = if (count == 1) "1 Item" else "$count Items"
    }

    private fun showCategoryDialog() {
        if (categories.isEmpty()) return
        val displayCategories = categories.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Category")
        builder.setItems(displayCategories) { _, which ->
            selectedCategory = if (which == 0) null else categories[which]
            // Update label to selected category or 'ALL ITEMS', always capitalize
            categoryLabel.text = if (selectedCategory.isNullOrBlank()) "ALL ITEMS" else selectedCategory!!.replaceFirstChar { c -> c.uppercase() }
            updateFilter()
        }
        builder.show()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
    }
}

@kotlinx.serialization.Serializable
data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)

@kotlinx.serialization.Serializable
data class NewCategoryRow(val category_id: String, val store_id: String, val name: String)