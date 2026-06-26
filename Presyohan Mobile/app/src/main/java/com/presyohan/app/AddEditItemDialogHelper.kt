package com.presyohan.app

import android.app.Dialog
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AddEditItemDialogHelper {

    @Serializable
    data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)

    @Serializable
    data class NewCategoryRow(val category_id: String, val store_id: String, val name: String)

    @Serializable
    data class MinimalProductReturn(val product_id: String)

    @Serializable
    data class StoreBranchRow(val branch: String?)

    fun showAddOrEditItemDialog(
        activity: AppCompatActivity,
        storeId: String,
        storeName: String,
        productData: EditProductData? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_add_edit_item, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set Dialog Width to 90% of Screen
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Bind Views
        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val dialogTitle = view.findViewById<TextView>(R.id.dialogTitle)
        val dialogStoreText = view.findViewById<TextView>(R.id.dialogStoreText)
        val dialogStoreBranchText = view.findViewById<TextView>(R.id.dialogStoreBranchText)
        val btnTopAction = view.findViewById<Button>(R.id.btnTopAction)

        val inputName = view.findViewById<TextInputEditText>(R.id.inputItemName)
        val inputDescription = view.findViewById<TextInputEditText>(R.id.inputItemDescription)
        val spinnerCategory = view.findViewById<AutoCompleteTextView>(R.id.spinnerItemCategory)
        val inputPrice = view.findViewById<TextInputEditText>(R.id.inputItemPrice)
        val inputUnit = view.findViewById<TextInputEditText>(R.id.inputItemUnit)
        val cbMakePublic = view.findViewById<CheckBox>(R.id.cbMakePublic)
        val buttonDone = view.findViewById<Button>(R.id.buttonDone)

        // Set store name
        dialogStoreText.text = storeName

        // Load store branch name
        activity.lifecycleScope.launch {
            try {
                val result = SupabaseProvider.client.postgrest["stores"].select(Columns.list("branch")) {
                    filter { eq("id", storeId) }
                    limit(1)
                }.decodeList<StoreBranchRow>()
                val branch = result.firstOrNull()?.branch ?: ""
                dialogStoreBranchText.text = branch
            } catch (e: Exception) {
                Log.e("AddEditItemDialog", "Failed to load store branch: ${e.localizedMessage}")
                dialogStoreBranchText.text = ""
            }
        }

        // Setup Categories Spinner
        val categories = mutableListOf<String>()
        val categoryIdMap = mutableMapOf<String, String>()
        val categoryAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, categories)
        spinnerCategory.setAdapter(categoryAdapter)

        fun loadCategories(selectedCategoryName: String? = null) {
            activity.lifecycleScope.launch {
                try {
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
                    if (!categories.contains("Add Category")) {
                        categories.add("Add Category")
                    }
                    categoryAdapter.notifyDataSetChanged()

                    if (selectedCategoryName != null) {
                        val index = categories.indexOfFirst { it.trim().equals(selectedCategoryName.trim(), ignoreCase = true) }
                        if (index >= 0) {
                            spinnerCategory.setText(categories[index], false)
                        } else if (selectedCategoryName.isNotBlank()) {
                            spinnerCategory.setText(selectedCategoryName, false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AddEditItemDialog", "Failed to load categories: ${e.localizedMessage}")
                }
            }
        }

        // Setup dialog modes
        val isEditMode = productData != null && productData.id.isNotBlank()
        if (isEditMode) {
            dialogTitle.text = "Edit Item"
            btnTopAction.text = "Manage Items"
            btnTopAction.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(activity, ManageItemsActivity::class.java)
                intent.putExtra("storeId", storeId)
                intent.putExtra("storeName", storeName)
                activity.startActivity(intent)
            }

            // Prefill product details
            productData?.let {
                inputName.setText(it.name)
                inputDescription.setText(it.description)
                inputPrice.setText(if (it.price == 0.0) "" else String.format("%.2f", it.price))
                inputUnit.setText(it.unit)
                cbMakePublic.isChecked = it.isPublic
                loadCategories(it.category)
            }
        } else {
            dialogTitle.text = "Add Item"
            btnTopAction.text = "+ Multiple Items"
            btnTopAction.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(activity, AddMultipleItemsActivity::class.java)
                intent.putExtra("storeId", storeId)
                intent.putExtra("storeName", storeName)
                activity.startActivity(intent)
            }
            cbMakePublic.isChecked = true // Default check in Add mode as shown in screenshot
            if (productData != null) {
                loadCategories(productData.category)
            } else {
                loadCategories()
            }
        }

        // Handle Add Category selection
        spinnerCategory.setOnItemClickListener { _, _, position, _ ->
            val selected = categoryAdapter.getItem(position) ?: ""
            if (selected == "Add Category") {
                showAddCategoryDialog(activity, storeId, categories, categoryIdMap, categoryAdapter, spinnerCategory)
            }
        }

        btnBack.setOnClickListener { dialog.dismiss() }

        buttonDone.setOnClickListener {
            val nameVal = inputName.text.toString().trim()
            val descriptionVal = inputDescription.text.toString().trim().ifBlank { null }
            val priceValText = inputPrice.text.toString().trim()
            val unitVal = inputUnit.text.toString().trim()
            val selectedCategory = spinnerCategory.text.toString().trim()
            val isPublicVal = cbMakePublic.isChecked

            if (nameVal.isEmpty() || priceValText.isEmpty() || unitVal.isEmpty() ||
                selectedCategory.isEmpty() || selectedCategory == "Choose category..." || selectedCategory == "Add Category") {
                Toast.makeText(activity, "Please complete all fields and select a category.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val priceVal = priceValText.toDoubleOrNull() ?: 0.0

            activity.lifecycleScope.launch {
                try {
                    val catName = selectedCategory.uppercase()
                    var categoryId = categoryIdMap[catName]
                    if (categoryId.isNullOrBlank()) {
                        val inserted = SupabaseProvider.client.postgrest.rpc(
                            "add_category",
                            buildJsonObject {
                                put("p_store_id", storeId)
                                put("p_name", catName)
                            }
                        ).decodeList<NewCategoryRow>()
                        categoryId = inserted.firstOrNull()?.category_id
                        val normalized = inserted.firstOrNull()?.name ?: catName
                        if (!categoryId.isNullOrBlank()) {
                            categoryIdMap[normalized] = categoryId
                        }
                    }

                    if (isEditMode) {
                        val productId = productData!!.id
                        val updatePayload = buildJsonObject {
                            put("name", nameVal)
                            if (descriptionVal != null) {
                                put("description", descriptionVal)
                            } else {
                                put("description", JsonNull)
                            }
                            put("price", priceVal)
                            put("unit", unitVal)
                            if (categoryId != null) {
                                put("category_id", categoryId)
                            } else {
                                put("category_id", JsonNull)
                            }
                            put("is_public", isPublicVal)
                        }
                        SupabaseProvider.client.postgrest["products"].update(updatePayload) {
                            filter { eq("id", productId); eq("store_id", storeId) }
                        }
                        Toast.makeText(activity, "Product updated.", Toast.LENGTH_SHORT).show()
                    } else {
                        val result = SupabaseProvider.client.postgrest.rpc(
                            "add_product",
                            buildJsonObject {
                                put("p_store_id", storeId)
                                put("p_category_id", categoryId)
                                put("p_name", nameVal)
                                put("p_description", descriptionVal ?: "")
                                put("p_price", JsonPrimitive(priceVal))
                                put("p_unit", unitVal)
                            }
                        ).decodeList<MinimalProductReturn>().firstOrNull()

                        if (result != null) {
                            // Update is_public column
                            SupabaseProvider.client.postgrest["products"].update(
                                buildJsonObject {
                                    put("is_public", isPublicVal)
                                }
                            ) {
                                filter { eq("id", result.product_id); eq("store_id", storeId) }
                            }
                        }
                        Toast.makeText(activity, "Product added.", Toast.LENGTH_SHORT).show()
                    }

                    dialog.dismiss()
                    onComplete?.invoke()

                } catch (e: Exception) {
                    Log.e("AddEditItemDialog", "Operation failed: ${e.localizedMessage}")
                    Toast.makeText(activity, "Unable to save product.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showAddCategoryDialog(
        activity: AppCompatActivity,
        storeId: String,
        categories: MutableList<String>,
        categoryIdMap: MutableMap<String, String>,
        adapter: ArrayAdapter<String>,
        spinner: AutoCompleteTextView
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_new_category, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        val input = view.findViewById<android.widget.EditText>(R.id.inputCategory)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        view.findViewById<TextView>(R.id.title)?.let {
            it.text = "Add Category"
        }

        if (btnAdd == null || btnBack == null || input == null) {
            Toast.makeText(activity, "Failed to initialize category dialog.", Toast.LENGTH_SHORT).show()
            return
        }

        btnAdd.setOnClickListener {
            val categoryRaw = input.text.toString().trim()
            val category = categoryRaw.uppercase()
            if (category.isNotEmpty()) {
                activity.lifecycleScope.launch {
                    try {
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
                        val addIndex = categories.indexOf("Add Category")
                        val insertIndex = if (addIndex >= 0) addIndex else categories.size
                        if (categories.none { it.equals(normalizedName, ignoreCase = true) }) {
                            categories.add(insertIndex, normalizedName)
                        }
                        adapter.notifyDataSetChanged()
                        spinner.setText(normalizedName, false)
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
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

data class EditProductData(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val unit: String,
    val category: String,
    val isPublic: Boolean
)
