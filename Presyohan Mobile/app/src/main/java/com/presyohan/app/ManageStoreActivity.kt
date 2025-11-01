package com.presyohan.app

import android.app.Dialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.button.MaterialButton
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout

class ManageStoreActivity : AppCompatActivity() {
    private lateinit var inputStoreName: EditText
    private lateinit var inputBranchName: EditText
    private lateinit var spinnerStoreType: Spinner
    private lateinit var btnDone: MaterialButton
    private lateinit var btnBack: ImageView
    private lateinit var storeText: TextView
    private lateinit var storeBranchText: TextView
    private lateinit var headerLabel: TextView
    private lateinit var inputCustomType: EditText

    private val storeTypes = arrayOf("Laundry Shop", "Car Wash", "Water Refilling Station", "Other")
    private var storeId: String? = null
    private var storeName: String? = null
    private var branchName: String? = null
    private var storeType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_store)

        storeId = intent.getStringExtra("storeId")
        if (storeId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        inputStoreName = findViewById(R.id.inputStoreName)
        inputBranchName = findViewById(R.id.inputBranchName)
        spinnerStoreType = findViewById(R.id.spinnerStoreType)
        btnDone = findViewById(R.id.btnDone)
        btnBack = findViewById(R.id.btnBack)
        storeText = findViewById(R.id.storeText)
        storeBranchText = findViewById(R.id.storeBranchText)
        headerLabel = findViewById(R.id.headerLabel)

        // Set up spinner before fetching Firestore
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, storeTypes)
        spinnerStoreType.adapter = adapter

        inputCustomType = findViewById(R.id.inputCustomType)
        if (inputCustomType == null) {
            Toast.makeText(this, "Layout error: inputCustomType not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Fetch current store info
        val db = FirebaseFirestore.getInstance()
        db.collection("stores").document(storeId!!).get().addOnSuccessListener { doc ->
            storeName = doc.getString("name") ?: ""
            branchName = doc.getString("branch") ?: ""
            storeType = doc.getString("type") ?: storeTypes[0]
            inputStoreName.setText(storeName)
            inputBranchName.setText(branchName)
            val typeIndex = storeTypes.indexOfFirst { it.equals(storeType, ignoreCase = true) }
            if (typeIndex >= 0) {
                spinnerStoreType.setSelection(typeIndex)
                inputCustomType.visibility = View.GONE
            } else {
                spinnerStoreType.setSelection(storeTypes.size - 1) // 'Other'
                inputCustomType.visibility = View.VISIBLE
                inputCustomType.setText(storeType)
            }
            storeText.text = storeName
            storeBranchText.text = branchName
        }

        spinnerStoreType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (storeTypes[position] == "Other") {
                    inputCustomType.visibility = View.VISIBLE
                } else {
                    inputCustomType.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnBack.setOnClickListener { finish() }

        btnDone.setOnClickListener {
            val newName = inputStoreName.text.toString().trim()
            val newBranch = inputBranchName.text.toString().trim()
            val selectedType = spinnerStoreType.selectedItem?.toString() ?: storeTypes[0]
            val newType = if (selectedType == "Other") inputCustomType.text.toString().trim() else selectedType
            if (newName.isEmpty() || newBranch.isEmpty() || (selectedType == "Other" && newType.isEmpty())) {
                Toast.makeText(this, "Complete all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Show confirmation dialog
            val dialog = Dialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            view.findViewById<TextView>(R.id.dialogTitle).text = "Confirm Changes?"
            view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to update the store details?"
            view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
            view.findViewById<Button>(R.id.btnDelete).apply {
                text = "Update"
                setOnClickListener {
                    // Update Firestore
                    db.collection("stores").document(storeId!!)
                        .update(mapOf(
                            "name" to newName,
                            "branch" to newBranch,
                            "type" to newType
                        ))
                        .addOnSuccessListener {
                            Toast.makeText(this@ManageStoreActivity, "Store updated.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@ManageStoreActivity, "Unable to update store.", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        }
                }
            }
            dialog.show()
        }

        val btnAddItem = findViewById<Button>(R.id.btnAddItem)
        btnAddItem.setOnClickListener {
            val intent = android.content.Intent(this, AddItemActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
        }

        val btnManageMembers = findViewById<Button>(R.id.btnManageMembers)
        btnManageMembers.setOnClickListener {
            val intent = android.content.Intent(this, ManageMembersActivity::class.java)
            intent.putExtra("storeId", storeId)
            startActivity(intent)
        }

        val btnManageCategory = findViewById<Button>(R.id.btnManageCategory)
        btnManageCategory.setOnClickListener {
            val intent = android.content.Intent(this, ManageCategoryActivity::class.java)
            intent.putExtra("storeId", storeId)
            startActivity(intent)
        }
    }

    // Extension function for dp to px
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}