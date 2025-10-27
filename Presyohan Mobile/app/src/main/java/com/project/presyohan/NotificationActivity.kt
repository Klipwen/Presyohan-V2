package com.project.presyohan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.project.presyohan.Notification
import com.project.presyohan.adapter.NotificationAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import android.app.Dialog
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope

class NotificationActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private var notificationListener: ListenerRegistration? = null
    private val notifications = mutableListOf<Notification>()
    private val notificationDocIds = mutableListOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        // Google Sign-In setup for logout
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val menuIcon = findViewById<ImageView>(R.id.menuIcon)
        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(Gravity.START)
        }
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> {
                    val intent = Intent(this, StoreActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawer(Gravity.START)
                    true
                }
                R.id.nav_logout -> {
                    lifecycleScope.launch {
                        try {
                            SupabaseAuthService.signOut()
                        } catch (_: Exception) { }
                        val intent = Intent(this@NotificationActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    true
                }
                R.id.nav_notifications -> {
                    drawerLayout.closeDrawer(Gravity.START)
                    true
                }
                else -> false
            }
        }

        // Back button closes the activity
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewNotifications)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = NotificationAdapter(notifications,
            onAccept = { notification -> handleAccept(notification) },
            onReject = { notification -> handleReject(notification) },
            onViewStore = { notification -> handleViewStore(notification) }
        )
        recyclerView.adapter = adapter

        // Add swipe-to-delete with confirmation
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val notification = notifications[position]
                val docId = notificationDocIds[position]
                // Show confirmation dialog
                val dialog = Dialog(this@NotificationActivity)
                val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
                dialog.setContentView(view)
                dialog.setCancelable(true)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                view.findViewById<TextView>(R.id.dialogTitle).text = "Delete Notification"
                view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to delete this notification? This action cannot be undone."
                view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
                    dialog.dismiss()
                    adapter.notifyItemChanged(position) // Restore item
                }
                view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                    // Delete from Firestore
                    val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                    if (userId != null) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("users").document(userId)
                            .collection("notifications")
                            .document(docId)
                            .delete()
                            .addOnSuccessListener {
                                dialog.dismiss()
                            }
                            .addOnFailureListener {
                                android.widget.Toast.makeText(this@NotificationActivity, "Failed to delete notification", android.widget.Toast.LENGTH_SHORT).show()
                                adapter.notifyItemChanged(position)
                                dialog.dismiss()
                            }
                    } else {
                        adapter.notifyItemChanged(position)
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        // Set real user name and email in navigation drawer header (Supabase)
        val navigationViewHeader = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val headerView = navigationViewHeader.getHeaderView(0)
        val userNameText = headerView.findViewById<TextView>(R.id.drawerUserName)
        val userEmailText = headerView.findViewById<TextView>(R.id.drawerUserEmail)
        userNameText.text = "User"
        val supaUser = SupabaseProvider.client.auth.currentUserOrNull()
        userEmailText.text = supaUser?.email ?: ""
        lifecycleScope.launch {
            val name = SupabaseAuthService.getDisplayName() ?: "User"
            userNameText.text = name
        }

        if (userId != null) {
            // Mark all unread notifications as read
            db.collection("users").document(userId)
                .collection("notifications")
                .whereEqualTo("unread", true)
                .get()
                .addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.update("unread", false)
                    }
                }
            notificationListener = db.collection("users").document(userId)
                .collection("notifications")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    notifications.clear()
                    notificationDocIds.clear()
                    snapshot?.forEach { doc ->
                        val type = doc.getString("type") ?: ""
                        val status = doc.getString("status") ?: "Pending"
                        val sender = doc.getString("sender") ?: ""
                        val senderId = doc.getString("senderId")
                        val storeName = doc.getString("storeName")
                        val role = doc.getString("role")
                        val timestampAny = doc.get("timestamp")
                        val timestamp = when (timestampAny) {
                            is Number -> timestampAny.toLong()
                            is String -> {
                                // Try to parse as long, then as date string
                                timestampAny.toLongOrNull() ?: try {
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(timestampAny)?.time ?: System.currentTimeMillis()
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }
                            }
                            else -> System.currentTimeMillis()
                        }
                        val message = doc.getString("message") ?: ""
                        notifications.add(Notification(type, status, sender, senderId, storeName, role, timestamp, message))
                        notificationDocIds.add(doc.id)
                    }
                    notifications.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationListener?.remove()
    }

    private fun handleAccept(notification: Notification) {
        if (notification.type == "Join Request") {
            // Show dialog for owner to select role
            showManageJoinRequestDialog(notification)
            return
        }
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val docIndex = notifications.indexOf(notification)
        if (docIndex == -1) return
        val docId = notificationDocIds[docIndex]
        // Update notification status to 'Accepted'
        db.collection("users").document(userId)
            .collection("notifications").document(docId)
            .update("status", "Accepted")
            .addOnSuccessListener {
                // Add store to user's stores array
                db.collection("stores").whereEqualTo("name", notification.storeName).get()
                    .addOnSuccessListener { storeSnapshot ->
                        if (!storeSnapshot.isEmpty) {
                            val storeId = storeSnapshot.documents[0].id
                            db.collection("users").document(userId)
                                .update("stores", com.google.firebase.firestore.FieldValue.arrayUnion(storeId))
                            db.collection("stores").document(storeId)
                                .collection("members").document(userId)
                                .set(mapOf("role" to (notification.role ?: "sales staff")), com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
                // Notify sender
                db.collection("users").whereEqualTo("name", notification.sender).get()
                    .addOnSuccessListener { senderSnapshot ->
                        if (!senderSnapshot.isEmpty) {
                            val senderId = senderSnapshot.documents[0].id
                            val senderNotif = hashMapOf(
                                "type" to "Store Invitation",
                                "status" to "Accepted",
                                "sender" to ((SupabaseProvider.client.auth.currentUserOrNull()?.userMetadata?.get("name") as? String)
                                    ?: (SupabaseProvider.client.auth.currentUserOrNull()?.email ?: "")),
                                "senderId" to userId,
                                "storeName" to notification.storeName,
                                "timestamp" to System.currentTimeMillis(),
                                "message" to "${((SupabaseProvider.client.auth.currentUserOrNull()?.userMetadata?.get("name") as? String)
                                    ?: (SupabaseProvider.client.auth.currentUserOrNull()?.email ?: ""))} accepted your invitation to join ${notification.storeName}."
                            )
                            db.collection("users").document(senderId)
                                .collection("notifications")
                                .add(senderNotif)
                        }
                    }
                // Notify invited user (self)
                val selfNotif = hashMapOf(
                    "type" to "Store Invitation",
                    "status" to "Accepted",
                    "sender" to notification.sender,
                    "senderId" to notification.senderId,
                    "storeName" to notification.storeName,
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "You accepted ${notification.sender}'s invitation to join ${notification.storeName}."
                )
                db.collection("users").document(userId)
                    .collection("notifications")
                    .add(selfNotif)
            }
    }
    private fun handleReject(notification: Notification) {
        if (notification.type == "Join Request") {
            // Notify requester of rejection
            val db = FirebaseFirestore.getInstance()
            val userId = notification.senderId ?: return
            val ownerId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return
            val storeName = notification.storeName ?: "Store"
            val requesterNotif = hashMapOf(
                "type" to "Join Request",
                "status" to "Declined",
                "sender" to ((SupabaseProvider.client.auth.currentUserOrNull()?.userMetadata?.get("name") as? String)
                    ?: (SupabaseProvider.client.auth.currentUserOrNull()?.email ?: "")),
                "senderId" to ownerId,
                "storeName" to storeName,
                "role" to null,
                "timestamp" to System.currentTimeMillis(),
                "message" to "Your request to join $storeName was declined."
            )
            db.collection("users").document(userId)
                .collection("notifications").add(requesterNotif)
            // Update notification status to Declined for owner
            val docIndex = notifications.indexOf(notification)
            if (docIndex != -1) {
                val docId = notificationDocIds[docIndex]
                db.collection("users").document(ownerId)
                    .collection("notifications").document(docId)
                    .update("status", "Declined")
            }
            return
        }
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val docIndex = notifications.indexOf(notification)
        if (docIndex == -1) return
        val docId = notificationDocIds[docIndex]
        db.collection("users").document(userId)
            .collection("notifications").document(docId)
            .update("status", "Declined")
            .addOnSuccessListener {
                // Notify sender
                db.collection("users").whereEqualTo("name", notification.sender).get()
                    .addOnSuccessListener { senderSnapshot ->
                        if (!senderSnapshot.isEmpty) {
                            val senderId = senderSnapshot.documents[0].id
                            val senderNotif = hashMapOf(
                                "type" to "Store Invitation",
                                "status" to "Declined",
                                "sender" to ((SupabaseProvider.client.auth.currentUserOrNull()?.userMetadata?.get("name") as? String)
                                    ?: (SupabaseProvider.client.auth.currentUserOrNull()?.email ?: "")),
                                "senderId" to userId,
                                "storeName" to notification.storeName,
                                "timestamp" to System.currentTimeMillis(),
                                "message" to "${((SupabaseProvider.client.auth.currentUserOrNull()?.userMetadata?.get("name") as? String)
                                    ?: (SupabaseProvider.client.auth.currentUserOrNull()?.email ?: ""))} declined your invitation to join ${notification.storeName}."
                            )
                            db.collection("users").document(senderId)
                                .collection("notifications")
                                .add(senderNotif)
                        }
                    }
                // Notify invited user (self)
                val selfNotif = hashMapOf(
                    "type" to "Store Invitation",
                    "status" to "Declined",
                    "sender" to notification.sender,
                    "senderId" to notification.senderId,
                    "storeName" to notification.storeName,
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "You declined ${notification.sender}'s invitation to join ${notification.storeName}."
                )
                db.collection("users").document(userId)
                    .collection("notifications")
                    .add(selfNotif)
            }
    }
    private fun handleViewStore(notification: Notification) {
        // Open HomeActivity for the store referenced in the notification
        val storeName = notification.storeName ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return
        db.collection("stores").whereEqualTo("name", storeName).get()
            .addOnSuccessListener { storeSnapshot ->
                if (!storeSnapshot.isEmpty) {
                    val storeDoc = storeSnapshot.documents[0]
                    val storeId = storeDoc.id
                    // Check if user is a member
                    db.collection("stores").document(storeId)
                        .collection("members").document(userId)
                        .get()
                        .addOnSuccessListener { memberDoc ->
                            if (memberDoc.exists()) {
                                val intent = android.content.Intent(this, com.project.presyohan.HomeActivity::class.java)
                                intent.putExtra("storeId", storeId)
                                intent.putExtra("storeName", storeName)
                                startActivity(intent)
                            } else {
                                android.widget.Toast.makeText(this, "You are not a member of this store.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            android.widget.Toast.makeText(this, "Failed to check membership.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                } else {
                    android.widget.Toast.makeText(this, "Store not found or no longer exists.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                android.widget.Toast.makeText(this, "Failed to open store.", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    private fun showManageJoinRequestDialog(notification: Notification) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_manage_join_request, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val storeName = notification.storeName ?: "Store"
        view.findViewById<TextView>(R.id.dialogTitle).text = storeName + " | Join Request"
        view.findViewById<TextView>(R.id.dialogMessage).text = "${notification.sender} requested to join your store"

        val radioSales = view.findViewById<android.widget.RadioButton>(R.id.radioSales)
        val radioManager = view.findViewById<android.widget.RadioButton>(R.id.radioManager)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        val btnAccept = view.findViewById<Button>(R.id.btnAccept)

        btnBack.setOnClickListener { dialog.dismiss() }
        btnAccept.setOnClickListener {
            val selectedRole = if (radioManager.isChecked) "manager" else "sales staff"
            // Add user to store with selected role
            val db = FirebaseFirestore.getInstance()
            db.collection("stores").whereEqualTo("name", notification.storeName).get()
                .addOnSuccessListener { storeSnapshot ->
                    if (!storeSnapshot.isEmpty) {
                        val storeId = storeSnapshot.documents[0].id
                        val userId = notification.senderId ?: return@addOnSuccessListener
                        db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
                            val name = userDoc.getString("name") ?: ""
                            db.collection("stores").document(storeId)
                                .collection("members").document(userId)
                                .set(mapOf("role" to selectedRole, "name" to name), com.google.firebase.firestore.SetOptions.merge())
                            db.collection("users").document(userId)
                                .update("stores", com.google.firebase.firestore.FieldValue.arrayUnion(storeId))
                            // Update notification status to Accepted for owner
                            val ownerId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return@addOnSuccessListener
                            val docIndex = notifications.indexOf(notification)
                            if (docIndex != -1) {
                                val docId = notificationDocIds[docIndex]
                                db.collection("users").document(ownerId)
                                    .collection("notifications").document(docId)
                                    .update("status", "Accepted", "role", selectedRole)
                            }
                            // Notify requester
                            val requesterNotif = hashMapOf(
                                "type" to "Join Request",
                                "status" to "Accepted",
                                "sender" to ((SupabaseProvider.client.auth.currentUserOrNull()?.userMetadata?.get("name") as? String)
                                    ?: (SupabaseProvider.client.auth.currentUserOrNull()?.email ?: "")),
                                "senderId" to ownerId,
                                "storeName" to notification.storeName,
                                "role" to selectedRole,
                                "timestamp" to System.currentTimeMillis(),
                                "message" to "Your request to join $storeName was accepted."
                            )
                            db.collection("users").document(userId)
                                .collection("notifications").add(requesterNotif)
                            dialog.dismiss()
                        }
                    }
                }
        }
        dialog.show()
    }
}