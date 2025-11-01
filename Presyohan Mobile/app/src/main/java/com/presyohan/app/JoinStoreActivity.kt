package com.presyohan.app

import com.presyohan.app.R
import com.presyohan.app.SupabaseProvider
import com.presyohan.app.SupabaseAuthService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.navigation.NavigationView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import android.widget.ImageView
import com.presyohan.app.NotificationActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class JoinStoreActivity : AppCompatActivity() {

    private lateinit var storeCodeInput: EditText
    private lateinit var joinButton: Button

    private val db = FirebaseFirestore.getInstance()
    // Identity is provided by Supabase session; no FirebaseAuth usage.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_store)

        storeCodeInput = findViewById(R.id.inputStoreCode)
        joinButton = findViewById(R.id.buttonRequestJoin)
        val backButton = findViewById<Button?>(R.id.buttonBack)
        backButton?.setOnClickListener { finish() }

        // Set real user name and email in navigation drawer header (Supabase)
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
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
        // Make menuIcon open drawer
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        findViewById<ImageView>(R.id.menuIcon).setOnClickListener {
            drawerLayout.open()
        }
        // Make notifIcon open NotificationActivity
        findViewById<ImageView>(R.id.notifIcon).setOnClickListener {
            val intent = android.content.Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        joinButton.setOnClickListener {
            val storeCode = storeCodeInput.text.toString().trim()

            if (storeCode.isEmpty()) {
                Toast.makeText(this, "Please enter a store code.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Find store by inviteCode in stores collection
            db.collection("stores")
                .whereEqualTo("inviteCode", storeCode)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty) {
                        Toast.makeText(this, "Invalid store code. Please request a new one or approach the store owner for help.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val storeDoc = querySnapshot.documents[0]
                    val storeId = storeDoc.id
                    val createdAt = storeDoc.getLong("inviteCodeCreatedAt") ?: 0L
                    val now = System.currentTimeMillis()
                    val expiryMillis = createdAt + 24 * 60 * 60 * 1000
                    if (now > expiryMillis) {
                        Toast.makeText(this, "Store code expired. Please request a new one or approach the store owner for help.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    // Check if user is already a member
                    val supaUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                    if (supaUserId == null) {
                        Toast.makeText(this, "Not signed in. Please log in and try again.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    db.collection("stores").document(storeId).collection("members")
                        .document(supaUserId).get()
                        .addOnSuccessListener { docSnapshot ->
                            if (docSnapshot.exists()) {
                                Toast.makeText(this, "You're already a member of this store.", Toast.LENGTH_SHORT).show()
                            } else {
                                // Find owner UID from 'members' map in store document
                                val membersMap = storeDoc.get("members") as? Map<*, *>
                                val ownerEntry = membersMap?.entries?.find { it.value == "owner" }
                                val ownerUid = ownerEntry?.key as? String
                                if (ownerUid == null) {
                                    Toast.makeText(this, "Store owner not found. Please contact support.", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }
                                // Check for existing pending join request from this user for this store
                                db.collection("users").document(ownerUid)
                                    .collection("notifications")
                                    .whereEqualTo("type", "Join Request")
                                    .whereEqualTo("senderId", supaUserId)
                                    .whereEqualTo("storeName", storeDoc.getString("name") ?: "Store")
                                    .whereEqualTo("status", "Pending")
                                    .get()
                                    .addOnSuccessListener { notifSnapshot ->
                                        if (!notifSnapshot.isEmpty) {
                                            Toast.makeText(this, "You already have a pending join request for this store.", Toast.LENGTH_SHORT).show()
                                            return@addOnSuccessListener
                                        }
                                        // Send join request notification to owner
                                        val senderNameOrEmail = SupabaseAuthService.getDisplayNameImmediate()
                                        val notif = hashMapOf(
                                            "type" to "Join Request",
                                            "status" to "Pending",
                                            "sender" to senderNameOrEmail,
                                            "senderId" to supaUserId,
                                            "storeName" to (storeDoc.getString("name") ?: "Store"),
                                            "role" to null,
                                            "timestamp" to System.currentTimeMillis(),
                                            "message" to "$senderNameOrEmail requested to join your store.",
                                            "unread" to true
                                        )
                                        db.collection("users").document(ownerUid)
                                            .collection("notifications")
                                            .add(notif)
                                            .addOnSuccessListener {
                                                // Add notification to current user (joinee) as well
                                                val selfNotif = hashMapOf(
                                                    "type" to "Join Request",
                                                    "status" to "Pending",
                                                    "sender" to senderNameOrEmail,
                                                    "senderId" to supaUserId,
                                                    "storeName" to (storeDoc.getString("name") ?: "Store"),
                                                    "role" to null,
                                                    "timestamp" to System.currentTimeMillis(),
                                                    "message" to "Your request to join ${(storeDoc.getString("name") ?: "Store")} was sent and is awaiting owner approval.",
                                                    "unread" to true
                                                )
                                                db.collection("users").document(supaUserId)
                                                    .collection("notifications")
                                                    .add(selfNotif)
                                                    .addOnSuccessListener {
                                                        // Go to store list (StoreActivity) while waiting for approval
                                                        val intent = android.content.Intent(this, com.presyohan.app.StoreActivity::class.java)
                                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        startActivity(intent)
                                                        finish()
                                                    }
                                                    .addOnFailureListener {
                                                        Toast.makeText(this, "Join request sent. Notification delivery failed.", Toast.LENGTH_SHORT).show()
                                                        val intent = android.content.Intent(this, com.presyohan.app.StoreActivity::class.java)
                                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        startActivity(intent)
                                                        finish()
                                                    }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(this, "Unable to send join request.", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Unable to check existing requests.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Unexpected error.", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Invalid or expired store code.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
