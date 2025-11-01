package com.presyohan.app

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.presyohan.app.adapter.Store
import com.presyohan.app.adapter.StoreAdapter
 
import io.github.jan.supabase.auth.auth
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class StoreActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StoreAdapter
    private val stores = mutableListOf<Store>()

    private val createStoreLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // After returning from CreateStoreActivity, refresh list
        fetchStores()
    }

    @Serializable
    data class StoreMemberRow(val store_id: String, val user_id: String, val role: String)

    @Serializable
    data class StoreRow(val id: String, val name: String, val branch: String? = null, val type: String? = null)

    // Minimal shape to avoid user_id/member_user_id mismatch; RLS filters rows for current user
    @Serializable
    data class StoreMemberLite(val store_id: String, val role: String)

    // Result shape for get_user_stores RPC
    @Serializable
    data class UserStoreRow(val store_id: String, val name: String, val branch: String? = null, val role: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener(this)

        // Open drawer when menu icon is clicked
        findViewById<ImageView>(R.id.menuIcon).setOnClickListener {
            drawerLayout.open()
        }

        // Add Store button logic
        findViewById<ImageButton>(R.id.addStoreButton).setOnClickListener {
            showStoreChoiceDialog()
        }

        recyclerView = findViewById(R.id.recyclerViewStores)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StoreAdapter(stores, emptyMap(), { store, anchor ->
            showStoreMenu(store, anchor)
        }, { store ->
            // On store card tap, open HomeActivity
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
        })
        recyclerView.adapter = adapter

        // Set user name in drawer header
        val headerView = navigationView.getHeaderView(0)
        val userNameText = headerView.findViewById<TextView>(R.id.drawerUserName)
        val userEmailText = headerView.findViewById<TextView>(R.id.drawerUserEmail)
        val supaUser = SupabaseProvider.client.auth.currentUserOrNull()
        userNameText.text = "User"
        userEmailText.text = supaUser?.email ?: ""
        lifecycleScope.launch {
            val name = SupabaseAuthService.getDisplayName() ?: "User"
            userNameText.text = name
        }

        checkUserStore()
        fetchStores()

        // Removed Google Play Services client setup; logout handled via Supabase only

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
                            eq("recipient_user_id", userIdNotif)
                            eq("unread", true)
                            eq("status", "Pending")
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
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences("presyo_prefs", MODE_PRIVATE)
            .edit().putString("last_screen", "store").apply()
        fetchStores()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.nav_logout) {
            lifecycleScope.launch {
                try {
                    SupabaseAuthService.signOut()
                } catch (_: Exception) { }
                val intent = Intent(this@StoreActivity, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
            return true
        }
        if (item.itemId == R.id.nav_notifications) {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
            drawerLayout.closeDrawer(android.view.Gravity.START)
            return true
        }
        return false
    }

    override fun onBackPressed() {
        // Exit the app when back is pressed from Store screen
        finishAffinity()
    }

    private fun checkUserStore() {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return
        db.collection("stores")
            .whereArrayContains("members", userId)
            .get()
            .addOnSuccessListener { documents ->
                // No dialog here
            }
            .addOnFailureListener {
                // No dialog here
            }
    }

    private fun showStoreChoiceDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_choice, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCreate = view.findViewById<Button>(R.id.btnCreateStore)
        val btnJoin = view.findViewById<Button>(R.id.btnJoinStore)

        btnCreate.setOnClickListener {
            createStoreLauncher.launch(Intent(this, CreateStoreActivity::class.java))
            dialog.dismiss()
        }

        btnJoin.setOnClickListener {
            startActivity(Intent(this, com.presyohan.app.JoinStoreActivity::class.java))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun fetchStores() {
        val client = SupabaseProvider.client
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val noStoreLabel = findViewById<TextView>(R.id.noStoreLabel)
        lifecycleScope.launch {
            try {
                // Server-side resolution of memberships and stores via resilient RPC
                val rows = client.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                
                Log.d("StoreActivity", "RPC get_user_stores returned ${rows.size} rows")
                if (rows.isNotEmpty()) {
                    Log.d("StoreActivity", "First store: id=${rows[0].store_id}, name='${rows[0].name}', role='${rows[0].role}'")
                }

                if (rows.isEmpty()) {
                    Log.d("StoreActivity", "No stores found, showing empty state")
                    adapter.updateStores(emptyList(), emptyMap())
                    noStoreLabel.visibility = View.VISIBLE
                    showStoreChoiceDialog()
                    return@launch
                }

                val roles = rows.associate { it.store_id to it.role }
                val fetchedStores = rows.map { r ->
                    Store(r.store_id, r.name, r.branch ?: "", "")
                }

                val sortedStores = fetchedStores.sortedWith(compareBy(
                    { val role = roles[it.id]?.lowercase(); when (role) { "owner" -> 0; "manager" -> 1; else -> 2 } },
                    { it.name.lowercase() }
                ))

                Log.d("StoreActivity", "Updating adapter with ${sortedStores.size} stores")
                adapter.updateStores(sortedStores, roles)
                noStoreLabel.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("StoreActivity", "Error fetching stores via RPC: ${e.message}", e)
                adapter.updateStores(emptyList(), emptyMap())
                noStoreLabel.visibility = View.VISIBLE
            }
        }
    }

    private fun showInviteStaffDialog(store: Store, inviteCode: String?, expiryMillis: Long?) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_invite_staff, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val codeText = view.findViewById<TextView>(R.id.storeCodeText)
        val copyBtn = view.findViewById<ImageView>(R.id.btnCopyCode)
        val generateBtn = view.findViewById<Button>(R.id.btnGenerateCode)
        // Remove expiryLabel logic
        fun updateCodeUI(code: String?, showExpiry: Boolean) {
            codeText.text = code ?: ""
            // No expiry label
        }
        // Initial UI state
        updateCodeUI(inviteCode, inviteCode != null && expiryMillis != null)

        // Copy code to clipboard
        copyBtn.setOnClickListener {
            val code = codeText.text.toString()
            if (code.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Store Code", code)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Invite code copied.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Generate code button logic
        generateBtn.setOnClickListener {
            val newCode = (100000..999999).random().toString()
            val now = System.currentTimeMillis()
            val updates = mapOf(
                "inviteCode" to newCode,
                "inviteCodeCreatedAt" to now
            )
            val storeDoc = db.collection("stores").document(store.id)
            storeDoc.update(updates).addOnSuccessListener {
                updateCodeUI(newCode, true)
                android.widget.Toast.makeText(this, "Invite code updated.", android.widget.Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                storeDoc.set(updates, com.google.firebase.firestore.SetOptions.merge()).addOnSuccessListener {
                    updateCodeUI(newCode, true)
                    android.widget.Toast.makeText(this, "Invite code updated.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Populate role spinner with permission options only
        val roleSpinner = view.findViewById<android.widget.Spinner>(R.id.roleSpinner)
        val permissions = listOf("View only prices", "Manage prices")
        val permissionAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, permissions)
        roleSpinner.adapter = permissionAdapter

        // Back button
        view.findViewById<Button>(R.id.btnBack).setOnClickListener {
            dialog.dismiss()
        }

        // Invite button (implement your invite logic here)
        view.findViewById<Button>(R.id.btnInvite).setOnClickListener {
            val email = view.findViewById<android.widget.EditText>(R.id.usernameInput).text.toString().trim()
            val selectedPermission = roleSpinner.selectedItem.toString().trim().lowercase()
            val role = when (selectedPermission) {
                "manage prices" -> "manager"
                "view only prices" -> "sales staff"
                else -> "sales staff"
            }
            if (email.isEmpty()) {
                android.widget.Toast.makeText(this, "Enter an email address.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Allow inviting by email regardless of code
            // Look up userId by email
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val userDoc = querySnapshot.documents[0]
                        val staffUserId = userDoc.id
                        // Check if user is already a staff member
                        db.collection("stores").document(store.id)
                            .collection("members")
                            .document(staffUserId)
                            .get()
                            .addOnSuccessListener { memberDoc ->
                                if (memberDoc.exists()) {
                                    android.widget.Toast.makeText(this, "${userDoc.getString("name") ?: email} is already your store staff", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    // Check for existing pending invitation (if deleted, there will be none, so allow invite)
                                    db.collection("users").document(staffUserId)
                                        .collection("notifications")
                                        .whereEqualTo("type", "Store Invitation")
                                        .whereEqualTo("storeName", store.name)
                                        .get()
                                        .addOnSuccessListener { notifSnapshot ->
                                            val hasPending = notifSnapshot.any { it.getString("status") == "Pending" }
                                            val hasAccepted = notifSnapshot.any { it.getString("status") == "Accepted" }
                                            if (hasAccepted) {
                                                android.widget.Toast.makeText(this, "Already a staff member.", android.widget.Toast.LENGTH_SHORT).show()
                                            } else if (hasPending) {
                                                android.widget.Toast.makeText(this, "Invitation already sent.", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                // No pending or accepted invitation (either rejected or deleted) — allow invite
                                                val senderName = SupabaseAuthService.getDisplayNameImmediate()
                                                val senderId = SupabaseProvider.client.auth.currentUserOrNull()?.id.orEmpty()
                                                val notification = hashMapOf(
                                                    "type" to "Store Invitation",
                                                    "status" to "Pending",
                                                    "sender" to senderName,
                                                    "senderId" to senderId,
                                                    "storeName" to store.name,
                                                    "role" to role,
                                                    "timestamp" to System.currentTimeMillis(),
                                                    "message" to "$senderName invited you to join their store, ${store.name} as $role."
                                                )
                                                db.collection("users").document(staffUserId)
                                                    .collection("notifications")
                                                    .add(notification)
                                                // Do NOT add to store members here; only add when invitation is accepted
                                                android.widget.Toast.makeText(this, "Invitation sent.", android.widget.Toast.LENGTH_SHORT).show()
                                                dialog.dismiss()
                                            }
                                        }
                                }
                            }
                    } else {
                        android.widget.Toast.makeText(this, "User not found.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    android.widget.Toast.makeText(this, "Unable to send invitation.", android.widget.Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    // Helper to show invite staff dialog with Firestore-based code logic
    private fun showInviteStaffWithCode(store: Store) {
        val storeDoc = db.collection("stores").document(store.id)
        storeDoc.get().addOnSuccessListener { doc ->
            val code = doc.getString("inviteCode")
            val createdAt = doc.getLong("inviteCodeCreatedAt") ?: 0L
            val now = System.currentTimeMillis()
            val expiryMillis = createdAt + 24 * 60 * 60 * 1000
            if (code != null && now < expiryMillis) {
                // Use existing code
                showInviteStaffDialog(store, code, expiryMillis)
            } else {
                // No code or expired: show empty, require user to generate
                showInviteStaffDialog(store, null, null)
            }
        }
    }

    private fun showStoreMenu(store: Store, anchor: View) {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (userId != null) {
            db.collection("stores").document(store.id)
                .collection("members").get().addOnSuccessListener { membersSnapshot ->
                    val owners = membersSnapshot.documents.filter { it.getString("role") == "owner" }
                    val isOwner = membersSnapshot.documents.any { it.id == userId && it.getString("role") == "owner" }
                    if (!isOwner) {
                        showStoreMenuEmployee(store)
                        return@addOnSuccessListener
                    }
                    val dialog = Dialog(this)
                    val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_menu, null)
                    dialog.setContentView(view)
                    dialog.setCancelable(true)
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                    view.findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
                        val intent = Intent(this, ManageStoreActivity::class.java)
                        intent.putExtra("storeId", store.id)
                        startActivity(intent)
                        dialog.dismiss()
                    }
                    view.findViewById<ImageView>(R.id.btnInviteStaff).setOnClickListener {
                        dialog.dismiss()
                        showInviteStaffWithCode(store)
                    }
                    val btnDelete = view.findViewById<ImageView>(R.id.btnDelete)
                    if (owners.size > 1) {
                        // Change to leave store
                        btnDelete.setImageResource(R.drawable.icon_logout) // Use a leave icon
                        btnDelete.contentDescription = "Leave Store"
                        btnDelete.setColorFilter(resources.getColor(R.color.red, null))
                        val labelDelete = view.findViewById<TextView>(R.id.labelDelete)
                        labelDelete.text = "Leave"
                        labelDelete.setTextColor(resources.getColor(R.color.red, null))
                        btnDelete.setOnClickListener {
                            val confirmDialog = Dialog(this)
                            val confirmView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
                            confirmDialog.setContentView(confirmView)
                            confirmDialog.setCancelable(true)
                            confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                            confirmView.findViewById<TextView>(R.id.dialogTitle).text = "Leave Store"
                            confirmView.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to leave this store? You will lose access to its products."
                            confirmView.findViewById<Button>(R.id.btnCancel).setOnClickListener { confirmDialog.dismiss() }
                            confirmView.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                                db.collection("users").document(userId)
                                    .update("stores", com.google.firebase.firestore.FieldValue.arrayRemove(store.id))
                                    .addOnSuccessListener {
                                        db.collection("stores").document(store.id)
                                            .collection("members").document(userId)
                                            .delete()
                                            .addOnSuccessListener {
                                                android.widget.Toast.makeText(this, "Left store.", android.widget.Toast.LENGTH_SHORT).show()
                                                fetchStores()
                                            }
                                    }
                                confirmDialog.dismiss()
                                dialog.dismiss()
                            }
                            confirmDialog.show()
                        }
                    } else {
                        // Only 1 owner, allow delete
                        btnDelete.setImageResource(R.drawable.icon_delete)
                        btnDelete.contentDescription = "Delete Store"
                        btnDelete.setOnClickListener {
                            val confirmDialog = Dialog(this)
                            val confirmView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
                            confirmDialog.setContentView(confirmView)
                            confirmDialog.setCancelable(true)
                            confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                            confirmView.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to delete this store? This action cannot be undone."
                            confirmView.findViewById<Button>(R.id.btnCancel).setOnClickListener { confirmDialog.dismiss() }
                            confirmView.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                                deleteStoreAndSubcollections(store.id) { success ->
                                    if (success) {
                                        db.collection("users").document(userId)
                                            .update("stores", com.google.firebase.firestore.FieldValue.arrayRemove(store.id))
                                            .addOnSuccessListener {
                                                android.widget.Toast.makeText(this, "Store deleted.", android.widget.Toast.LENGTH_SHORT).show()
                                                fetchStores()
                                            }
                                            .addOnFailureListener {
                                                android.widget.Toast.makeText(this, "Store deleted. User update failed.", android.widget.Toast.LENGTH_SHORT).show()
                                                fetchStores()
                                            }
                                    } else {
                                        android.widget.Toast.makeText(this, "Unable to delete store.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                confirmDialog.dismiss()
                                dialog.dismiss()
                            }
                            confirmDialog.show()
                        }
                    }
                    dialog.show()
                }
        }
    }

    private fun showStoreMenuEmployee(store: Store) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_menu_employee, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.textStoreName).text = store.name
        // Set access message based on role
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (userId != null) {
            db.collection("stores").document(store.id)
                .collection("members").document(userId)
                .get().addOnSuccessListener { memberDoc ->
                    val role = memberDoc.getString("role") ?: ""
                    val accessText = when (role) {
                        "manager" -> "You can manage prices and view products in this store"
                        else -> "You can only view price on this store"
                    }
                    view.findViewById<TextView>(R.id.textAccessLevel).text = accessText
                }
        }
        // Set up view members and leave store actions as needed
        // Example:
        view.findViewById<ImageView>(R.id.btnViewMembers).setOnClickListener {
            // Show members dialog using dialog_members.xml
            db.collection("stores").document(store.id)
                .get().addOnSuccessListener { storeDoc ->
                    val storeName = storeDoc.getString("name") ?: "Store"
                    // Fetch all members
                    db.collection("stores").document(store.id)
                        .collection("members").get().addOnSuccessListener { membersSnapshot ->
                            val members = mutableListOf<Pair<String, String>>() // Pair<name, role>
                            var ownerName: String? = null
                            var fetchedCount = 0
                            for (doc in membersSnapshot) {
                                val role = doc.getString("role") ?: ""
                                val userId = doc.id
                                db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
                                    val name = userDoc.getString("name") ?: "Unknown"
                                    if (role == "owner") {
                                        ownerName = name
                                    } else {
                                        members.add(Pair(name, role))
                                    }
                                    fetchedCount++
                                    if (fetchedCount == membersSnapshot.size()) {
                                        // Sort members: manager first, then sales staff
                                        val sortedMembers = members.sortedWith(compareBy({ if (it.second == "manager") 0 else 1 }, { it.first.lowercase() }))
                                        val dialog = Dialog(this)
                                        val dialogView = layoutInflater.inflate(R.layout.dialog_members, null)
                                        // Set store name
                                        dialogView.findViewById<TextView>(R.id.dialogStoreName).text = storeName
                                        // Set owner name
                                        dialogView.findViewById<TextView>(R.id.dialogOwnerName).text = ownerName ?: ""
                                        // Set members list
                                        val membersList = dialogView.findViewById<LinearLayout>(R.id.membersList)
                                        membersList.removeAllViews()
                                        for (member in sortedMembers) {
                                            val memberView = TextView(this)
                                            memberView.text = member.first
                                            memberView.textSize = 15f
                                            memberView.setPadding(0, 0, 0, 4)
                                            memberView.setTextColor(resources.getColor(R.color.black, null))
                                            membersList.addView(memberView)
                                        }
                                        dialog.setContentView(dialogView)
                                        dialog.setCancelable(true)
                                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                                        dialog.show()
                                    }
                                }
                            }
                        }
                }
            dialog.dismiss()
        }
        view.findViewById<ImageView>(R.id.btnLeaveStore).setOnClickListener {
            // Show confirmation dialog before leaving store
            val confirmDialog = Dialog(this)
            val confirmView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
            confirmDialog.setContentView(confirmView)
            confirmDialog.setCancelable(true)
            confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            confirmView.findViewById<TextView>(R.id.dialogTitle).text = "Leave Store"
            confirmView.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to leave this store? You will lose access to its products."
            confirmView.findViewById<Button>(R.id.btnCancel).setOnClickListener { confirmDialog.dismiss() }
            confirmView.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    db.collection("users").document(userId)
                        .update("stores", com.google.firebase.firestore.FieldValue.arrayRemove(store.id))
                        .addOnSuccessListener {
                            db.collection("stores").document(store.id)
                                .collection("members").document(userId)
                                .delete()
                                .addOnSuccessListener {
                                    android.widget.Toast.makeText(this, "Left store.", android.widget.Toast.LENGTH_SHORT).show()
                                    fetchStores()
                                }
                        }
                }
                confirmDialog.dismiss()
                dialog.dismiss()
            }
            confirmDialog.show()
        }
        dialog.show()
    }

    // Add a helper function to recursively delete all subcollections of a store
    private fun deleteStoreAndSubcollections(storeId: String, onComplete: (Boolean) -> Unit) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val storeRef = db.collection("stores").document(storeId)
        // List of subcollections to delete
        val subcollections = listOf("products", "categories", "members")
        val batch = db.batch()
        var pending = subcollections.size
        var failed = false
        for (sub in subcollections) {
            storeRef.collection(sub).get().addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                pending--
                if (pending == 0 && !failed) {
                    // After all subcollections are deleted, delete the store doc
                    batch.delete(storeRef)
                    batch.commit().addOnSuccessListener { onComplete(true) }
                        .addOnFailureListener { onComplete(false) }
                }
            }.addOnFailureListener {
                failed = true
                onComplete(false)
            }
        }
    }


}
