package com.project.presyohan

import android.os.Bundle
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Button

class ManageCategoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManageCategoryAdapter
    private var storeId: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_category)

        storeId = intent.getStringExtra("storeId")
        if (storeId.isNullOrBlank()) {
            finish()
            return
        }

        recyclerView = findViewById(R.id.recyclerViewCategories)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val textStoreName = findViewById<TextView>(R.id.textStoreName)
        val textStoreBranch = findViewById<TextView>(R.id.textStoreBranch)
        db.collection("stores").document(storeId!!).get().addOnSuccessListener { doc ->
            textStoreName.text = doc.getString("name") ?: "Store Name"
            textStoreBranch.text = doc.getString("branch") ?: "Branch Name"
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        adapter = ManageCategoryAdapter(emptyList(),
            onViewItems = { category ->
                val intent = android.content.Intent(this, ManageItemsActivity::class.java)
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
        val storeId = storeId ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("stores").document(storeId).collection("categories").get().addOnSuccessListener { snapshot ->
            val categories = snapshot.documents.mapNotNull { it.getString("name") }
            // Fetch item counts for each category
            db.collection("stores").document(storeId).collection("products").get().addOnSuccessListener { productsSnapshot ->
                val counts = mutableMapOf<String, Int>()
                for (cat in categories) counts[cat] = 0
                for (doc in productsSnapshot) {
                    val cat = doc.getString("category")
                    if (cat != null && counts.containsKey(cat)) {
                        counts[cat] = counts[cat]!! + 1
                    }
                }
                adapter.updateCategories(categories, counts)
            }
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
            val storeId = storeId ?: return@setOnClickListener
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            // Find the category doc by name
            db.collection("stores").document(storeId).collection("categories")
                .whereEqualTo("name", oldCategory).get().addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty) return@addOnSuccessListener
                    val docRef = querySnapshot.documents[0].reference
                    docRef.delete().addOnSuccessListener {
                        db.collection("stores").document(storeId).collection("categories")
                            .add(mapOf("name" to newCategory)).addOnSuccessListener {
                                // Update all products with oldCategory to newCategory
                                db.collection("stores").document(storeId).collection("products")
                                    .whereEqualTo("category", oldCategory)
                                    .get().addOnSuccessListener { snapshot ->
                                        val batch = db.batch()
                                        for (doc in snapshot.documents) {
                                            batch.update(doc.reference, "category", newCategory)
                                        }
                                        batch.commit().addOnSuccessListener {
                                            fetchCategories()
                                            android.widget.Toast.makeText(this, "Category renamed!", android.widget.Toast.LENGTH_SHORT).show()
                                            dialog.dismiss()
                                        }
                                    }
                            }
                    }
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
            val storeId = storeId ?: return@setOnClickListener
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            // Find the category doc by name
            db.collection("stores").document(storeId).collection("categories")
                .whereEqualTo("name", category).get().addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty) return@addOnSuccessListener
                    val docRef = querySnapshot.documents[0].reference
                    // Delete all items in this category
                    db.collection("stores").document(storeId).collection("products")
                        .whereEqualTo("category", category).get().addOnSuccessListener { productsSnapshot ->
                            val batch = db.batch()
                            for (doc in productsSnapshot.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.delete(docRef)
                            batch.commit().addOnSuccessListener {
                                fetchCategories()
                                android.widget.Toast.makeText(this, "Category and all items deleted!", android.widget.Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        }
                }
        }
        dialog.show()
    }
} 