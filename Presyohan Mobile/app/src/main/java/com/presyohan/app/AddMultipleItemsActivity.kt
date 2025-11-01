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

class AddMultipleItemsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter
    private lateinit var buttonSelectCategory: AppCompatButton
    private lateinit var itemCounter: TextView
    private val categories = mutableListOf<CategoryWithItems>()
    private val categoryNames = mutableListOf<String>()
    private var storeId: String? = null
    private var storeName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_multiple_items)

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
                // If no items left, remove the category card and delete the category from Firestore
                if (items.isEmpty()) {
                    val storeId = storeId
                    if (storeId != null) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("stores").document(storeId)
                            .collection("categories").document(categoryName)
                            .delete()
                    }
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
                    saveAllItemsToFirestore()
                }
                dialog.show()
            } else {
                saveAllItemsToFirestore()
            }
        }
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            onBackPressed()
        }
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
        fetchCategoriesFromFirestore {
            showCategorySelectionDialog()
        }
    }

    private fun fetchCategoriesFromFirestore(onFetched: () -> Unit) {
        storeId?.let { id ->
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("stores").document(id)
                .collection("categories")
                .get()
                .addOnSuccessListener { result ->
                    categoryNames.clear()
                    for (doc in result) {
                        val name = doc.getString("name")
                        if (name != null && !categoryNames.contains(name)) {
                            categoryNames.add(name)
                        }
                    }
                    categoryNames.add("Add new Category")
                    onFetched()
                }
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

        btnAdd.setOnClickListener {
            val category = input.text.toString().trim()
            if (category.isNotEmpty()) {
                // Save to Firestore with auto-generated ID
                val storeId = intent.getStringExtra("storeId") ?: return@setOnClickListener
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val categoryData = hashMapOf("name" to category)
                db.collection("stores").document(storeId)
                    .collection("categories").add(categoryData)
                    .addOnSuccessListener {
                        onCategoryAdded(category)
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        input.error = "Failed to add category"
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

    private fun saveAllItemsToFirestore() {
        val storeId = storeId ?: return
        val storeName = storeName
        val allItems = mutableListOf<Map<String, Any>>()
        val categoriesToCheck = mutableListOf<String>()
        for (category in categories) {
            if (category.items.isEmpty()) {
                categoriesToCheck.add(category.categoryName)
            }
            for (item in category.items) {
                val name = item.name.trim()
                val unit = item.unit.trim()
                val price = item.price.trim()
                if (name.isBlank() || unit.isBlank() || price.isBlank()) {
                    Toast.makeText(this, "Complete all required fields for every item.", Toast.LENGTH_SHORT).show()
                    return
                }
                val data = hashMapOf(
                    "name" to name,
                    "units" to unit,
                    "price" to (price.toDoubleOrNull() ?: 0.0),
                    "description" to item.description.trim(),
                    "category" to category.categoryName
                )
                allItems.add(data)
            }
        }
        if (allItems.isEmpty()) {
            Toast.makeText(this, "No items to save.", Toast.LENGTH_SHORT).show()
            return
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val storeRef = db.collection("stores").document(storeId).collection("products")
        val batch = db.batch()
        for (item in allItems) {
            val newDoc = storeRef.document()
            batch.set(newDoc, item)
        }
        batch.commit().addOnSuccessListener {
            // After saving, delete any empty categories from Firestore
            for (categoryName in categoriesToCheck) {
                db.collection("stores").document(storeId)
                    .collection("categories").document(categoryName)
                    .delete()
            }
            Toast.makeText(this, "Items added.", Toast.LENGTH_SHORT).show()
            val intent = android.content.Intent(this, HomeActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Unable to add items.", Toast.LENGTH_SHORT).show()
        }
    }
}