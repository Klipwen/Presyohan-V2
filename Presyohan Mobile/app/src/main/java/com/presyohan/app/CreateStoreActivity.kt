package com.presyohan.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.content.Intent
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth

class CreateStoreActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_store)

        // Drawer and menu logic
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val menuIcon = findViewById<android.widget.ImageView>(R.id.menuIcon)
        menuIcon.setOnClickListener {
            drawerLayout.open()
        }
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> {
                    // Navigate to StoreActivity
                    val intent = Intent(this, StoreActivity::class.java)
                    startActivity(intent)
                    drawerLayout.close()
                    true
                }
                R.id.nav_notifications -> {
                    val intent = Intent(this, NotificationActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawer(android.view.Gravity.START)
                    true
                }
                // Handle other menu items if needed
                else -> false
            }
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

        // Set real user name and email in navigation drawer header
        val headerView = navigationView.getHeaderView(0)
        val userNameText = headerView.findViewById<TextView>(R.id.drawerUserName)
        val userEmailText = headerView.findViewById<TextView>(R.id.drawerUserEmail)
        val supaUser = SupabaseProvider.client.auth.currentUserOrNull()
        userEmailText.text = supaUser?.email ?: ""
        userNameText.text = "User"
        lifecycleScope.launch {
            val name = SupabaseAuthService.getDisplayName() ?: "User"
            userNameText.text = name
        }

        val storeNameEditText = findViewById<EditText>(R.id.inputItemName)
        val storeBranchEditText = findViewById<EditText>(R.id.inputStoreBranch)
        val storeTypeSpinner = findViewById<Spinner>(R.id.spinnerStoreType)
        val otherStoreTypeEditText = findViewById<EditText>(R.id.editOtherStoreType)
        val backBtn = findViewById<Button>(R.id.buttonBack)
        val createBtn = findViewById<Button>(R.id.buttonCreate)

        // Store type options
        val storeTypes = listOf(
            "--Select store type--",
            "Grocery",
            "Pharmacy",
            "Laundry",
            "Other (specify)"
        )

        // Custom spinner adapter using spinner_store_type.xml
        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_store_type,
            R.id.spinnerText,
            storeTypes
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.setTextColor(resources.getColor(R.color.presyo_orange, null))
                view.setBackgroundColor(resources.getColor(android.R.color.white, null))
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.spinner_store_type)
        storeTypeSpinner.adapter = adapter

        // Show/hide 'Other' field
        storeTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (storeTypes[position] == "Other (specify)") {
                    otherStoreTypeEditText.visibility = View.VISIBLE
                } else {
                    otherStoreTypeEditText.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        backBtn.setOnClickListener { finish() }
        createBtn.setOnClickListener {
            val name = storeNameEditText.text.toString().trim()
            val branch = storeBranchEditText.text.toString().trim()
            val selectedType = storeTypeSpinner.selectedItem.toString()
            val type = if (selectedType == "Other (specify)") {
                otherStoreTypeEditText.text.toString().trim()
            } else {
                selectedType
            }
            if (name.isEmpty() || branch.isEmpty() || type.isEmpty() || selectedType == "--Select store type--") {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val db = FirebaseFirestore.getInstance()
            val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return@setOnClickListener
            val members: HashMap<String, String> = hashMapOf(userId to "owner")
            val storeData: HashMap<String, Any> = hashMapOf(
                "name" to name,
                "branch" to branch,
                "type" to type,
                "members" to members
            )
            db.collection("stores").add(storeData).addOnSuccessListener { docRef ->
                // Add user to members subcollection with role 'owner' and name
                db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
                    val name = userDoc.getString("name") ?: ""
                    val memberData = hashMapOf("role" to "owner", "name" to name)
                    docRef.collection("members").document(userId).set(memberData)
                }
                val userRef = db.collection("users").document(userId)
                userRef.update("stores", com.google.firebase.firestore.FieldValue.arrayUnion(docRef.id))
                    .addOnFailureListener {
                        // Fallback: create the array if it doesn't exist
                        userRef.set(mapOf("stores" to listOf(docRef.id)), com.google.firebase.firestore.SetOptions.merge())
                    }
                Toast.makeText(this, "Store created!", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to create store.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}