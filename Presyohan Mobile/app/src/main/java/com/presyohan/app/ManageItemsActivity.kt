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
import com.google.firebase.firestore.FirebaseFirestore
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_items)

        val storeId = intent.getStringExtra("storeId")
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
                            // Delete from Firestore
                            val storeId = intent.getStringExtra("storeId")
                            if (!storeId.isNullOrBlank()) {
                                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                db.collection("stores").document(storeId)
                                    .collection("products").document(item.id)
                                    .delete()
                            }
                            // Check if the category is now empty and delete it from Firestore
                            val categoryToCheck = item.category
                            if (categoryToCheck.isNotBlank() && allItems.none { it.category == categoryToCheck }) {
                                val storeId = intent.getStringExtra("storeId")
                                if (!storeId.isNullOrBlank()) {
                                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    db.collection("stores").document(storeId)
                                        .collection("categories")
                                        .whereEqualTo("name", categoryToCheck)
                                        .get().addOnSuccessListener { querySnapshot ->
                                            if (!querySnapshot.isEmpty) {
                                                querySnapshot.documents[0].reference.delete()
                                            }
                                        }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ManageItems", "Delete error: "+e.localizedMessage)
                            android.widget.Toast.makeText(this@ManageItemsActivity, "Delete error: "+e.localizedMessage, android.widget.Toast.LENGTH_LONG).show()
                        }
                        dialog.dismiss()
                    }
                }
                dialog.show()
            } catch (e: Exception) {
                android.util.Log.e("ManageItems", "Dialog error: "+e.localizedMessage)
                android.widget.Toast.makeText(this@ManageItemsActivity, "Dialog error: "+e.localizedMessage, android.widget.Toast.LENGTH_LONG).show()
            }
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val filterCategory = intent.getStringExtra("filterCategory")
        if (!filterCategory.isNullOrBlank()) {
            selectedCategory = filterCategory
            categoryLabel.text = filterCategory.replaceFirstChar { it.uppercase() }
        }

        // Fetch items and categories from Firestore
        val db = FirebaseFirestore.getInstance()
        db.collection("stores").document(storeId).collection("products").get()
            .addOnSuccessListener { result ->
                try {
                    allItems.clear()
                    categories.clear()
                    val categorySet = mutableSetOf<String>()
                    for (doc in result) {
                        val name = doc.getString("name") ?: ""
                        val description = doc.getString("description") ?: ""
                        val price = doc.getDouble("price") ?: 0.0
                        val unit = doc.getString("units") ?: "" // Fix: use 'unit' for ManageItemData
                        val category = doc.getString("category") ?: ""
                        allItems.add(ManageItemData(doc.id, name, unit, price, description, category))
                        if (category.isNotBlank()) categorySet.add(category)
                    }
                    allItems.sortBy { it.name.lowercase() }
                    categories.add("All")
                    categories.addAll(categorySet.sortedBy { it.lowercase() })
                    updateFilter()

                    // Remove spinner logic for category selection
                    // categorySpinner.adapter = spinnerAdapter
                    // categorySpinner.setSelection(0)
                    // categorySpinner.onItemSelectedListener = ...
                    // categoryDrawerButton.setOnClickListener { categorySpinner.performClick() }
                    categoryDrawerButton.setOnClickListener { showCategoryDialog() }
                    // Set label to selected category or ALL ITEMS
                    if (!selectedCategory.isNullOrBlank()) {
                        categoryLabel.text = selectedCategory!!.replaceFirstChar { it.uppercase() }
                    } else {
                        categoryLabel.text = "ALL ITEMS"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ManageItems", "Fetch error: ${e.localizedMessage}")
                    android.widget.Toast.makeText(this, "Error loading items: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                android.widget.Toast.makeText(this, "Failed to fetch items: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                finish()
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
                    val storeId = intent.getStringExtra("storeId")
                    if (storeId.isNullOrBlank()) {
                        android.widget.Toast.makeText(this@ManageItemsActivity, "No store ID provided. Cannot save.", android.widget.Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        return@setOnClickListener
                    }
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val batch = db.batch()
                    // Save all items in allItems to Firestore
                    for (item in allItems) {
                        val docRef = db.collection("stores").document(storeId).collection("products").document(item.id)
                        val data = hashMapOf(
                            "name" to item.name,
                            "description" to item.description,
                            "price" to item.price,
                            "units" to item.unit,
                            "category" to item.category
                        )
                        batch.set(docRef, data)
                    }
                    batch.commit().addOnSuccessListener {
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
                    }.addOnFailureListener { e ->
                        android.widget.Toast.makeText(this@ManageItemsActivity, "Failed to save items: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                        dialog.dismiss()
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
}