package com.presyohan.app

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.adapter.CategoryAdapter
import com.presyohan.app.adapter.CategoryWithItems
import com.presyohan.app.adapter.ItemFormData
import androidx.appcompat.widget.AppCompatButton
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.postgrest

class AddMultipleItemsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter
    private lateinit var buttonSelectCategory: AppCompatButton
    private lateinit var itemCounter: TextView
    private val categories = mutableListOf<CategoryWithItems>()
    private val categoryNames = mutableListOf<String>()
    private val categoryIdByName = mutableMapOf<String, String>()
    private val createdCategories = mutableSetOf<String>()
    private val emptiedCategories = mutableSetOf<String>()
    private var storeId: String? = null
    private var storeName: String? = null
    private lateinit var loadingOverlay: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_multiple_items)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        recyclerView = findViewById(R.id.recyclerViewItems)
        buttonSelectCategory = findViewById(R.id.buttonSelectCategory)
        itemCounter = findViewById(R.id.itemCounter)
        adapter = CategoryAdapter(categories,
            onAddItem = { categoryPos ->
                val items = categories[categoryPos].items
                val recyclerView = recyclerView.findViewHolderForAdapterPosition(categoryPos)?.itemView?.findViewById<RecyclerView>(R.id.recyclerViewItems)
                val lastItem = items.lastOrNull()
                if (lastItem != null && recyclerView != null) {
                    val lastIndex = items.size - 1
                    val holder = recyclerView.findViewHolderForAdapterPosition(lastIndex)
                    if (holder != null) {
                        val name = holder.itemView.findViewById<EditText>(R.id.inputItemName).text.toString().trim()
                        val unit = holder.itemView.findViewById<EditText>(R.id.inputItemUnit).text.toString().trim()
                        val price = holder.itemView.findViewById<EditText>(R.id.inputItemPrice).text.toString().trim()
                        if (name.isBlank() || unit.isBlank() || price.isBlank()) {
                            Toast.makeText(this, "Complete all required fields before adding another item.", Toast.LENGTH_SHORT).show()
                            return@CategoryAdapter
                        }
                        lastItem.name = name
                        lastItem.unit = unit
                        lastItem.price = price
                    }
                }
                items.add(ItemFormData(items.size + 1))
                adapter.notifyItemChanged(categoryPos)
                updateItemCounter()
            },
            onDeleteItem = { categoryPos, itemPos ->
                val items = categories[categoryPos].items
                val categoryName = categories[categoryPos].categoryName
                items.removeAt(itemPos)
                items.forEachIndexed { idx, item -> item.itemNumber = idx + 1 }
                // If no items left, remove the category card
                if (items.isEmpty()) {
                    // Track that this category became empty in this session
                    emptiedCategories.add(categoryName)
                    categories.removeAt(categoryPos)
                    adapter.notifyDataSetChanged()
                } else {
                    adapter.notifyItemChanged(categoryPos)
                }
                updateItemCounter()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        recyclerView.visibility = View.GONE

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        // Only update the counter, do not show dialog here
        updateItemCounter()
        // Show dialog with fetch on screen open
        showCategoryDialogWithFetch()

        buttonSelectCategory.setOnClickListener {
            showCategoryDialogWithFetch()
        }
        val buttonDone = findViewById<AppCompatButton>(R.id.buttonDone)
        buttonDone.setOnClickListener {
            val hasItems = categories.any { it.items.isNotEmpty() }
            if (hasItems) {
                val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
                val dialog = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()
                val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
                val message = dialogView.findViewById<TextView>(R.id.confirmMessage)
                val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
                val btnDelete = dialogView.findViewById<AppCompatButton>(R.id.btnDelete)
                title.text = "Save Items?"
                message.text = "Are you sure you want to save all entered items? This will add them to your store."
                btnDelete.text = "Save"
                btnDelete.setTextColor(resources.getColor(R.color.presyo_orange, null))
                btnCancel.text = "Cancel"
                btnCancel.setOnClickListener { dialog.dismiss() }
                btnDelete.setOnClickListener {
                    dialog.dismiss()
            saveAllItemsToSupabase()
                }
                dialog.show()
            } else {
            saveAllItemsToSupabase()
            }
        }
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
    }

    override fun onBackPressed() {
        val hasItems = categories.any { it.items.isNotEmpty() }
        if (hasItems) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()
            val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
            val message = dialogView.findViewById<TextView>(R.id.confirmMessage)
            val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
            val btnDelete = dialogView.findViewById<AppCompatButton>(R.id.btnDelete)
            title.text = "Discard Items?"
            message.text = "You have unsaved items. Are you sure you want to leave? All entered data will be lost."
            btnDelete.text = "Discard"
            btnDelete.setTextColor(resources.getColor(R.color.presyo_orange, null))
            btnCancel.text = "Cancel"
            btnCancel.setOnClickListener { dialog.dismiss() }
            btnDelete.setOnClickListener {
                dialog.dismiss()
                val intent = android.content.Intent(this, HomeActivity::class.java)
                intent.putExtra("storeId", storeId)
                intent.putExtra("storeName", storeName)
                startActivity(intent)
                finish()
            }
            dialog.show()
        } else {
            val intent = android.content.Intent(this, HomeActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
            finish()
        }
    }

    private fun showCategoryDialogWithFetch() {
        fetchCategoriesFromSupabase {
            showCategorySelectionDialog()
        }
    }

    @Serializable
    private data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)

    private fun fetchCategoriesFromSupabase(onFetched: () -> Unit) {
        val id = storeId ?: return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_user_categories",
                    buildJsonObject { put("p_store_id", id) }
                ).decodeList<UserCategoryRow>()
                categoryNames.clear()
                categoryIdByName.clear()
                for (row in rows) {
                    if (!categoryNames.contains(row.name)) {
                        categoryNames.add(row.name)
                        categoryIdByName[row.name] = row.category_id
                    }
                }
                categoryNames.add("Add new Category")
                onFetched()
            } catch (e: Exception) {
                android.util.Log.e("AddMultipleItems", "Fetch categories error: ${e.localizedMessage}", e)
                categoryNames.clear()
                categoryNames.add("Add new Category")
                onFetched()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun showCategorySelectionDialog() {
        val categoriesArray = categoryNames.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setItems(categoriesArray) { _, which ->
                val selected = categoriesArray[which]
                if (selected == "Add new Category") {
                    showAddCategoryDialog { newCategory ->
                        categoryNames.add(categoryNames.size - 1, newCategory)
                        // Immediately show the new category card and begin item entry
                        showCategoryCard(newCategory)
                    }
                } else {
                    showCategoryCard(selected)
                }
            }
            .show()
    }

    private fun showAddCategoryDialog(onCategoryAdded: (String) -> Unit) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_category, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val input = view.findViewById<android.widget.EditText>(R.id.inputCategoryName)
        val btnAdd = view.findViewById<android.widget.Button>(R.id.btnAdd)
        val btnBack = view.findViewById<android.widget.Button>(R.id.btnBack)

        @Serializable
        data class NewCategoryRow(val category_id: String, val store_id: String, val name: String)

        btnAdd.setOnClickListener {
            val category = input.text.toString().trim()
            if (category.isNotEmpty()) {
                val sId = storeId ?: return@setOnClickListener
                LoadingOverlayHelper.show(loadingOverlay)
                lifecycleScope.launch {
                    try {
                        val inserted = SupabaseProvider.client.postgrest.rpc(
                            "add_category",
                            buildJsonObject {
                                put("p_store_id", sId)
                                put("p_name", category)
                            }
                        ).decodeList<NewCategoryRow>()
                        val newId = inserted.firstOrNull()?.category_id
                        if (!newId.isNullOrBlank()) {
                            categoryIdByName[category] = newId
                        }
                        // Track categories created in this session
                        createdCategories.add(category)
                        onCategoryAdded(category)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        android.util.Log.e("AddMultipleItems", "add_category RPC failed: ${e.localizedMessage}", e)
                        input.error = "Failed to add category"
                    }
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            } else {
                input.error = "Enter a category name"
            }
        }
        btnBack.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCategoryCard(category: String) {
        // Prevent duplicate cards for the same category
        if (categories.any { it.categoryName == category }) return
        categories.add(CategoryWithItems(category, mutableListOf(ItemFormData(1))))
        adapter.notifyDataSetChanged()
        recyclerView.visibility = View.VISIBLE
        updateItemCounter()
    }

    private fun updateItemCounter() {
        val totalItems = categories.sumOf { it.items.size }
        if (totalItems == 0) {
            itemCounter.visibility = View.GONE
        } else {
            itemCounter.visibility = View.VISIBLE
            itemCounter.text = when (totalItems) {
                1 -> "1 Item"
                else -> "$totalItems Items"
            }
        }
    }

    private fun saveAllItemsToSupabase() {
        val sId = storeId ?: return
        val sName = storeName
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                var totalCount = 0
                for (category in categories) {
                    val catName = category.categoryName
                    // Ensure category_id is available; create if missing
                    var catId = categoryIdByName[catName]
                    if (catId.isNullOrBlank()) {
                        @Serializable
                        data class NewCategoryRow(val category_id: String, val store_id: String, val name: String)
                        val inserted = SupabaseProvider.client.postgrest.rpc(
                            "add_category",
                            buildJsonObject { put("p_store_id", sId); put("p_name", catName) }
                        ).decodeList<NewCategoryRow>()
                        catId = inserted.firstOrNull()?.category_id
                        if (!catId.isNullOrBlank()) {
                            categoryIdByName[catName] = catId
                        }
                        // Track categories created in this session when auto-creating
                        createdCategories.add(catName)
                    }
                    for (item in category.items) {
                        val name = item.name.trim()
                        val unit = item.unit.trim()
                        val priceStr = item.price.trim()
                        if (name.isBlank() || unit.isBlank() || priceStr.isBlank()) {
                            Toast.makeText(this@AddMultipleItemsActivity, "Complete all required fields for every item.", Toast.LENGTH_SHORT).show()
                            // Abort saving gracefully
                            totalCount = 0
                            throw IllegalStateException("Validation failed")
                        }
                        val priceVal = priceStr.toDoubleOrNull() ?: 0.0
                        val desc = item.description.trim().takeIf { it.isNotBlank() }
                        val payload = buildJsonObject {
                            put("p_store_id", sId)
                            put("p_name", name)
                            if (desc != null) put("p_description", desc) else put("p_description", JsonNull)
                            put("p_price", priceVal)
                            put("p_unit", unit)
                            if (!catId.isNullOrBlank()) put("p_category_id", catId!!) else put("p_category_id", JsonNull)
                        }
                        try {
                            SupabaseProvider.client.postgrest.rpc("add_product", payload)
                            totalCount += 1
                        } catch (e: Exception) {
                            android.util.Log.e("AddMultipleItems", "add_product RPC failed: ${e.localizedMessage}", e)
                            Toast.makeText(this@AddMultipleItemsActivity, "Unable to add items.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }
                }
                // After saving, delete any categories that ended up with no items (new or existing)
                try {
                    @Serializable
                    data class MinimalProductRow(val id: String)
                    @Serializable
                    data class MinimalCategoryRow(val id: String)
                    val candidateNames = mutableSetOf<String>()
                    candidateNames.addAll(createdCategories)
                    candidateNames.addAll(emptiedCategories)
                    // Also include any current cards that are empty
                    candidateNames.addAll(categories.filter { it.items.isEmpty() }.map { it.categoryName })

                    for (name in candidateNames) {
                        // Resolve category id by name; fallback to DB lookup if missing
                        var catId = categoryIdByName[name]
                        if (catId.isNullOrBlank()) {
                            val cats = SupabaseProvider.client.postgrest["categories"].select {
                                filter { eq("store_id", sId); eq("name", name) }
                                limit(1)
                            }.decodeList<MinimalCategoryRow>()
                            catId = cats.firstOrNull()?.id
                            if (!catId.isNullOrBlank()) {
                                categoryIdByName[name] = catId
                            }
                        }
                        if (!catId.isNullOrBlank()) {
                            val prods = SupabaseProvider.client.postgrest["products"].select {
                                filter { eq("store_id", sId); eq("category_id", catId!!) }
                                limit(1)
                            }.decodeList<MinimalProductRow>()
                            if (prods.isEmpty()) {
                                SupabaseProvider.client.postgrest["categories"].delete {
                                    filter { eq("id", catId!!); eq("store_id", sId) }
                                }
                                android.util.Log.d("AddMultipleItems", "Deleted empty category: $name")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddMultipleItems", "Cleanup empty categories failed: ${e.localizedMessage}", e)
                }
                if (totalCount == 0) {
                    Toast.makeText(this@AddMultipleItemsActivity, "No items to save.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Toast.makeText(this@AddMultipleItemsActivity, "Items added.", Toast.LENGTH_SHORT).show()
                val intent = android.content.Intent(this@AddMultipleItemsActivity, HomeActivity::class.java)
                intent.putExtra("storeId", sId)
                intent.putExtra("storeName", sName)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                android.util.Log.e("AddMultipleItems", "Save items error: ${e.localizedMessage}", e)
                Toast.makeText(this@AddMultipleItemsActivity, "Unable to add items.", Toast.LENGTH_SHORT).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
}