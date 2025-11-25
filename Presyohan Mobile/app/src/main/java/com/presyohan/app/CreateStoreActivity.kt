package com.presyohan.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
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
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CreateStoreActivity : AppCompatActivity() {
    private lateinit var loadingOverlay: android.view.View
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_store)
        loadingOverlay = LoadingOverlayHelper.attach(this)

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
                R.id.nav_logout -> {
                    showLogoutDialog()
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
        val userIdNotif = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (notifDot != null && userIdNotif != null) {
            lifecycleScope.launch {
                try {
                    val rows = SupabaseProvider.client.postgrest["notifications"].select {
                        filter {
                            eq("receiver_user_id", userIdNotif)
                            eq("read", false)
                        }
                        limit(1)
                    }.decodeList<com.presyohan.app.HomeActivity.NotificationRow>()
                    notifDot.visibility = if (rows.isNotEmpty()) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    notifDot.visibility = View.GONE
                    android.util.Log.e("SupabaseNotif", "Error loading notif badge", e)
                }
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
                Toast.makeText(this, "Please complete all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val client = SupabaseProvider.client
            val uid = client.auth.currentUserOrNull()?.id
            if (uid == null) {
                Toast.makeText(this, "Not signed in. Please log in and try again.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create store via Supabase RPC. This inserts into public.stores and
            // public.store_members with the caller as 'owner'.
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    android.util.Log.d("CreateStore", "Starting store creation with name: $name, branch: $branch, type: $type")
                    android.util.Log.d("CreateStore", "User ID: $uid")

                    val payload = buildJsonObject {
                        put("p_name", name)
                        put("p_branch", branch)
                        put("p_type", type)
                    }

                    android.util.Log.d("CreateStore", "Calling create_store RPC with payload: $payload")
                    client.postgrest.rpc("create_store", payload)
                    android.util.Log.d("CreateStore", "RPC call successful")

                    // Resolve newly created store id by querying accessible stores
                    // Prefer role=owner and matching name/branch; fallback to newest store by name/branch
                    @kotlinx.serialization.Serializable
                    data class UserStoreRow(
                        val store_id: String,
                        val name: String,
                        val branch: String? = null,
                        val type: String? = null,
                        val role: String
                    )
                    @kotlinx.serialization.Serializable
                    data class StoreRow(val id: String, val name: String, val branch: String? = null)

                    var newStoreId: String? = null
                    try {
                        val rows = client.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                        newStoreId = rows.firstOrNull {
                            it.role == "owner" &&
                            it.name.equals(name, ignoreCase = true) &&
                            ((it.branch ?: "").equals(branch, ignoreCase = true))
                        }?.store_id
                    } catch (_: Exception) { /* fallback below */ }

                    if (newStoreId.isNullOrBlank()) {
                        try {
                            val stores = client.postgrest["stores"].select {
                                filter {
                                    eq("name", name)
                                    if (branch.isNotEmpty()) eq("branch", branch)
                                }
                                order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                                limit(1)
                            }.decodeList<StoreRow>()
                            newStoreId = stores.firstOrNull()?.id
                        } catch (_: Exception) { }
                    }

                    if (!newStoreId.isNullOrBlank()) {
                        Toast.makeText(this@CreateStoreActivity, "Store created successfully.", Toast.LENGTH_SHORT).show()
                        val goHome = Intent(this@CreateStoreActivity, HomeActivity::class.java)
                        goHome.putExtra("storeId", newStoreId)
                        goHome.putExtra("storeName", name)
                        // Clear back stack so user lands in new store cleanly
                        goHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(goHome)
                        finish()
                    } else {
                        // Fallback: complete without redirect if id couldn’t be resolved
                        Toast.makeText(this@CreateStoreActivity, "Store created. Opening stores list...", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CreateStore", "Store creation failed", e)
                    // Professional, user-friendly fallback message without debug details
                    Toast.makeText(this@CreateStoreActivity, "Couldn’t create the store. Please try again.", Toast.LENGTH_LONG).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun showLogoutDialog() {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogTitle).text = "Log Out?"
        view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to log out of Presyohan?"

        view.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<android.widget.Button>(R.id.btnDelete).apply {
            text = "Log Out"
            setOnClickListener {
                lifecycleScope.launch {
                    try {
                        SupabaseAuthService.signOut()
                    } catch (_: Exception) { }
                    val intent = Intent(this@CreateStoreActivity, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
