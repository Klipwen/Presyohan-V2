package com.presyohan.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import android.widget.ImageView

class AddItemActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_item)

        fun showAddCategoryDialog(onCategoryAdded: (String) -> Unit) {
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

        val spinner = findViewById<android.widget.Spinner>(R.id.spinnerItemCategory)
        val priceEditText = findViewById<android.widget.EditText>(R.id.inputItemPrice)
        val priceDisplay = findViewById<TextView>(R.id.priceDisplay)
        priceDisplay.visibility = View.GONE // Hide by default
        priceDisplay.textSize = 18f
        priceDisplay.setTextColor(resources.getColor(R.color.presyo_orange, null))

        val storeId = intent.getStringExtra("storeId")
        val categories = mutableListOf("Add Category")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spinner.adapter = adapter

        // Load categories from Firestore
        if (storeId != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("stores").document(storeId)
                .collection("categories")
                .get()
                .addOnSuccessListener { result ->
                    for (doc in result) {
                        val name = doc.getString("name")
                        if (name != null && !categories.contains(name)) {
                            categories.add(name)
                        }
                    }
                    adapter.notifyDataSetChanged()
                }
        }

        // Show dialog on touch if only 'Add Category' exists
        spinner.setOnTouchListener { v, event ->
            if (categories.size == 1) {
                showAddCategoryDialog { newCategory ->
                    categories.add(newCategory)
                    adapter.notifyDataSetChanged()
                    spinner.setSelection(categories.indexOf(newCategory))
                }
                true // consume the touch event
            } else {
                false
            }
        }

        var isSpinnerInitialized = false
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true
                    return
                }
                if (position == 0 && categories.size > 1) { // Only show dialog if there are other categories
                    showAddCategoryDialog { newCategory ->
                        categories.add(newCategory)
                        adapter.notifyDataSetChanged()
                        spinner.setSelection(categories.indexOf(newCategory))
                    }
                    // Reset selection to previous valid category if needed
                    spinner.setSelection(1)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

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
                android.widget.Toast.makeText(this, "Please fill out all fields", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = priceText.toDoubleOrNull() ?: 0.0
            val storeId = intent.getStringExtra("storeId") ?: return@setOnClickListener
            val storeName = intent.getStringExtra("storeName") ?: "Store"

            val productData = hashMapOf(
                "name" to itemName,
                "description" to description,
                "price" to price,
                "category" to category,
                "units" to units
            )

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("stores").document(storeId)
                .collection("products")
                .add(productData)
                .addOnSuccessListener {
                    android.widget.Toast.makeText(this, "Product added!", android.widget.Toast.LENGTH_SHORT).show()
                    val intent = android.content.Intent(this, HomeActivity::class.java)
                    intent.putExtra("storeId", storeId)
                    intent.putExtra("storeName", storeName)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    android.widget.Toast.makeText(this, "Failed to add product.", android.widget.Toast.LENGTH_SHORT).show()
                }
        }

        val btnAddMultipleItems = findViewById<android.widget.Button>(R.id.btnAddMultipleItems)
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
        btnAddMultipleItems.setOnClickListener {
            val intent = android.content.Intent(this, AddMultipleItemsActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
        }

        // Set real store name
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

        // Set real user name and email in navigation drawer header
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
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