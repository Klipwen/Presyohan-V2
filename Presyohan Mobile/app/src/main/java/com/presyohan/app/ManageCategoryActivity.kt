package com.presyohan.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.widget.Button
import com.presyohan.app.adapter.ManageCategoryAdapter

class ManageCategoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManageCategoryAdapter
    private var storeId: String? = null
    private var storeName: String? = null
    private lateinit var loadingOverlay: android.view.View
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_category)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        if (storeId.isNullOrBlank()) {
            finish()
            return
        }

        recyclerView = findViewById(R.id.recyclerViewCategories)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val textStoreName = findViewById<TextView>(R.id.textStoreName)
        val textStoreBranch = findViewById<TextView>(R.id.textStoreBranch)
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @Serializable
                data class StoreRow(val id: String, val name: String, val branch: String? = null)
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", storeId!!) }
                    limit(1)
                }.decodeList<StoreRow>()
                val s = rows.firstOrNull()
                storeName = s?.name ?: storeName
                textStoreName.text = storeName ?: "Store Name"
                textStoreBranch.text = s?.branch ?: "Branch Name"
                SessionManager.markStoreHome(this@ManageCategoryActivity, storeId, storeName)
            } catch (_: Exception) { /* ignore */ }
            LoadingOverlayHelper.hide(loadingOverlay)
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        adapter = ManageCategoryAdapter(
            emptyList(),
            onViewItems = { category ->
                val intent = Intent(this, ManageItemsActivity::class.java)
                intent.putExtra("storeId", storeId)
                intent.putExtra("storeName", textStoreName.text.toString())
                intent.putExtra("filterCategory", category)
                startActivity(intent)
            },
            onRename = { category ->
                showRenameCategoryDialog(category)
            },
            onDelete = { category ->
                showDeleteCategoryDialog(category)
            }
        )
        recyclerView.adapter = adapter

        fetchCategories()
    }

    private fun fetchCategories() {
        val sId = storeId ?: return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @Serializable
                data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)
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

                val categories = SupabaseProvider.client.postgrest.rpc(
                    "get_user_categories",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<UserCategoryRow>().map { it.name }

                val products = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<UserProductRow>()

                val counts = mutableMapOf<String, Int>()
                for (cat in categories) counts[cat] = 0
                for (p in products) {
                    val cat = p.category?.trim().orEmpty()
                    if (cat.isNotEmpty() && counts.containsKey(cat)) {
                        counts[cat] = (counts[cat] ?: 0) + 1
                    }
                }
                val sortedCats = categories.sortedBy { it.lowercase() }
                adapter.updateCategories(sortedCats, counts)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this@ManageCategoryActivity, "Unable to load categories.", android.widget.Toast.LENGTH_LONG).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun showRenameCategoryDialog(oldCategory: String) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_category, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.dialogTitle)?.text = "Rename Category"
        val input = view.findViewById<android.widget.EditText>(R.id.inputCategoryName)
        input.setText(oldCategory)
        val btnAdd = view.findViewById<android.widget.Button>(R.id.btnAdd)
        val btnBack = view.findViewById<android.widget.Button>(R.id.btnBack)
        btnAdd.text = "Rename"
        btnAdd.setOnClickListener {
            val newCategory = input.text.toString().trim()
            if (newCategory.isEmpty()) {
                input.error = "Enter a category name"
                return@setOnClickListener
            }
            val sId = storeId ?: return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "rename_or_merge_category",
                        buildJsonObject {
                            put("p_store_id", sId)
                            put("p_old_name", oldCategory)
                            put("p_new_name", newCategory)
                        }
                    )
                    fetchCategories()
                    android.widget.Toast.makeText(this@ManageCategoryActivity, "Category renamed.", android.widget.Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    try {
                        @Serializable
                        data class MinimalCategoryRow(val id: String, val store_id: String, val name: String)
                        val cats = SupabaseProvider.client.postgrest["categories"].select {
                            filter { eq("store_id", sId); eq("name", oldCategory) }
                            limit(1)
                        }.decodeList<MinimalCategoryRow>()
                        val catId = cats.firstOrNull()?.id
                        if (!catId.isNullOrBlank()) {
                            SupabaseProvider.client.postgrest["categories"].update(
                                buildJsonObject { put("name", newCategory) }
                            ) {
                                filter { eq("id", catId); eq("store_id", sId) }
                            }
                            fetchCategories()
                            android.widget.Toast.makeText(this@ManageCategoryActivity, "Category renamed.", android.widget.Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            android.widget.Toast.makeText(this@ManageCategoryActivity, "Unable to rename category.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this@ManageCategoryActivity, "Unable to rename category.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        btnBack.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteCategoryDialog(category: String) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.dialogTitle)?.text = "Delete Category"
        view.findViewById<TextView>(R.id.confirmMessage)?.text = "Are you sure you want to delete this category? All items in this category will remain, but the category will be removed."
        view.findViewById<Button>(R.id.btnCancel)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDelete)?.setOnClickListener {
            val sId = storeId ?: return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "delete_category_safe",
                        buildJsonObject {
                            put("p_store_id", sId)
                            put("p_category_name", category)
                        }
                    )
                    fetchCategories()
                    android.widget.Toast.makeText(this@ManageCategoryActivity, "Category and items deleted.", android.widget.Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    try {
                        @Serializable
                        data class MinimalCategoryRow(val id: String)
                        val cats = SupabaseProvider.client.postgrest["categories"].select {
                            filter { eq("store_id", sId); eq("name", category) }
                            limit(1)
                        }.decodeList<MinimalCategoryRow>()
                        val catId = cats.firstOrNull()?.id
                        if (!catId.isNullOrBlank()) {
                            // Dissociate products from category
                            SupabaseProvider.client.postgrest["products"].update(
                                buildJsonObject { put("category_id", kotlinx.serialization.json.JsonNull) }
                            ) {
                                filter { eq("store_id", sId); eq("category_id", catId) }
                            }
                            // Delete the category row
                            SupabaseProvider.client.postgrest["categories"].delete {
                                filter { eq("id", catId); eq("store_id", sId) }
                            }
                            fetchCategories()
                            android.widget.Toast.makeText(this@ManageCategoryActivity, "Category deleted.", android.widget.Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            android.widget.Toast.makeText(this@ManageCategoryActivity, "Unable to delete category.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this@ManageCategoryActivity, "Unable to delete category.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
    }
}