package com.project.presyohan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FirebaseFirestore
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.content.Intent
import com.project.presyohan.NotificationActivity

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

        // Load categories from Firestore and set spinner
        val categories = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spinnerCategory.adapter = adapter
        if (storeId.isNotEmpty()) {
            FirebaseFirestore.getInstance()
                .collection("stores").document(storeId)
                .collection("categories")
                .get()
                .addOnSuccessListener { result ->
                    categories.clear()
                    for (doc in result) {
                        val name = doc.getString("name")
                        if (name != null && !categories.contains(name)) {
                            categories.add(name)
                        }
                    }
                    if (productCategory.isNotEmpty() && categories.none { it.trim().equals(productCategory.trim(), ignoreCase = true) }) {
                        categories.add(productCategory)
                    }
                    if (!categories.contains("Add Category")) {
                        categories.add("Add Category")
                    }
                    adapter.notifyDataSetChanged()
                    val normalizedCategories = categories.map { it.trim().lowercase() }
                    val normalizedProductCategory = productCategory.trim().lowercase()
                    val index = normalizedCategories.indexOf(normalizedProductCategory)
                    if (index >= 0) {
                        spinnerCategory.setSelection(index)
                    } else if (categories.isNotEmpty()) {
                        spinnerCategory.setSelection(0)
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("EditItemActivity", "Failed to load categories", e)
                }
        } else {
            android.util.Log.e("EditItemActivity", "storeId is empty! Cannot load categories.")
        }

        spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            var isFirst = true
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isFirst) {
                    isFirst = false
                    return
                }
                if (categories.isNotEmpty() && position == categories.size - 1) { // 'Add Category' selected
                    showAddCategoryDialog(storeId, categories, adapter, spinnerCategory)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Set store name in header
        val storeName = intent.getStringExtra("storeName") ?: "Store name"
        val storeNameText = findViewById<TextView>(R.id.storeText)
        val storeBranchText = findViewById<TextView>(R.id.storeBranchText)
        storeNameText.text = storeName
        if (storeId != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("stores").document(storeId).get().addOnSuccessListener { doc ->
                val branch = doc.getString("branch") ?: ""
                storeBranchText.text = branch
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
        val userIdNotif = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
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
            val newDescription = inputDescription.text.toString().trim()
            val newPrice = inputPrice.text.toString().toDoubleOrNull() ?: 0.0
            val newUnit = inputUnit.text.toString().trim()
            val newCategory = spinnerCategory.selectedItem?.toString() ?: ""
            val storeIdForUpdate = intent.getStringExtra("storeId") ?: return@setOnClickListener
            val productId = intent.getStringExtra("productId") ?: return@setOnClickListener
            if (newName.isEmpty() || newUnit.isEmpty() || newCategory.isEmpty() || newCategory == "Add Category") {
                android.widget.Toast.makeText(this, "Please fill out all fields", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updateData = hashMapOf(
                "name" to newName,
                "description" to newDescription,
                "price" to newPrice,
                "units" to newUnit,
                "category" to newCategory
            )
            FirebaseFirestore.getInstance()
                .collection("stores").document(storeIdForUpdate)
                .collection("products").document(productId)
                .update(updateData as Map<String, Any>)
                .addOnSuccessListener {
                    android.widget.Toast.makeText(this, "Product updated!", android.widget.Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    android.widget.Toast.makeText(this, "Failed to update product.", android.widget.Toast.LENGTH_SHORT).show()
                }
        }

        findViewById<Button>(R.id.btnManageItems).setOnClickListener {
            val storeId = intent.getStringExtra("storeId")
            val storeName = intent.getStringExtra("storeName")
            if (storeId.isNullOrBlank() || storeName.isNullOrBlank()) {
                android.widget.Toast.makeText(this, "Store information missing. Cannot manage items.", android.widget.Toast.LENGTH_LONG).show()
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
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        if (userId != null) {
            db.collection("users").document(userId).get().addOnSuccessListener { doc ->
                userNameText.text = doc.getString("name") ?: "User"
                userEmailText.text = doc.getString("email") ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
            }
        }
    }

    private fun showAddCategoryDialog(storeId: String, categories: MutableList<String>, adapter: ArrayAdapter<String>, spinner: Spinner) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val input = view.findViewById<EditText>(R.id.inputCategoryName)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val btnBack = view.findViewById<Button>(R.id.btnBack)

        btnAdd.setOnClickListener {
            val category = input.text.toString().trim()
            if (category.isNotEmpty()) {
                // Save to Firestore with auto-generated ID
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val categoryData = hashMapOf("name" to category)
                db.collection("stores").document(storeId)
                    .collection("categories").add(categoryData)
                    .addOnSuccessListener {
                        categories.add(category)
                        adapter.notifyDataSetChanged()
                        spinner.setSelection(categories.indexOf(category))
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

    private fun deleteCategoryIfEmpty(storeId: String, categoryName: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("stores").document(storeId)
            .collection("products")
            .whereEqualTo("category", categoryName)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    db.collection("stores").document(storeId)
                        .collection("categories").document(categoryName)
                        .delete()
                }
            }
    }
} 