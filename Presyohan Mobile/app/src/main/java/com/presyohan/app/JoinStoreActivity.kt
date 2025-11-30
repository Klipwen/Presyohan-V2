package com.presyohan.app

import com.presyohan.app.R
import com.presyohan.app.SupabaseProvider
import com.presyohan.app.SupabaseAuthService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.navigation.NavigationView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import android.widget.ImageView
import com.presyohan.app.NotificationActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.content.Intent

@Serializable
data class StoreByInviteCodeRow(
    val store_id: String,
    val name: String,
    val branch: String? = null,
    val type: String? = null,
    val invite_code_created_at: String? = null
)

@Serializable
data class UserStoreSummaryRow(
    val store_id: String,
    val name: String,
    val role: String
)

@Serializable
data class NotificationIdRow(
    val id: String
)

class JoinStoreActivity : AppCompatActivity() {

    private lateinit var storeCodeInput: EditText
    private lateinit var joinButton: Button
    private lateinit var loadingOverlay: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_store)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeCodeInput = findViewById(R.id.inputStoreCode)
        joinButton = findViewById(R.id.buttonRequestJoin)
        val backButton = findViewById<Button?>(R.id.buttonBack)
        backButton?.setOnClickListener { finish() }

        // Initialize Drawer Layout
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        HeaderUtils.updateHeader(this, navigationView)

        // Make menuIcon open drawer
        findViewById<ImageView>(R.id.menuIcon).setOnClickListener {
            drawerLayout.open()
        }

        // --- NAVIGATION LISTENER ADDED HERE ---
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> {
                    // Navigate to StoreActivity
                    val intent = Intent(this, StoreActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    val intent = Intent(this, NotificationActivity::class.java)
                    startActivity(intent)
                    drawerLayout.close()
                    true
                }
                R.id.nav_logout -> {
                    showLogoutDialog()
                    true
                }
                else -> false
            }
        }

        // Make notifIcon open NotificationActivity
        findViewById<ImageView>(R.id.notifIcon).setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        joinButton.setOnClickListener {
            val storeCode = storeCodeInput.text.toString().trim()

            if (storeCode.isEmpty()) {
                Toast.makeText(this, "Please enter a store code.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    val supaUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                    if (supaUserId == null) {
                        runOnUiThread {
                            Toast.makeText(this@JoinStoreActivity, "Not signed in. Please log in and try again.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Use RPC to get store by invite code (includes expiry check)
                    val storeResponse = try {
                        SupabaseProvider.client.postgrest.rpc(
                            "get_store_by_invite_code",
                            buildJsonObject { put("p_invite_code", storeCode) }
                        ).decodeList<StoreByInviteCodeRow>()
                    } catch (e: Exception) {
                        android.util.Log.e("JoinStoreActivity", "Lookup invite code failed: ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this@JoinStoreActivity, "Invalid or expired store code.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    if (storeResponse.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this@JoinStoreActivity, "Invalid or expired store code. Please request a new one or approach the store owner for help.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val store = storeResponse[0]

                    // Check if user is already a member of this store (best-effort)
                    val isAlreadyMember = try {
                        val userStores = SupabaseProvider.client.postgrest.rpc("get_user_stores")
                            .decodeList<UserStoreSummaryRow>()
                        userStores.any { it.store_id == store.store_id }
                    } catch (e: Exception) {
                        android.util.Log.w("JoinStoreActivity", "Membership check failed (continuing): ${e.message}")
                        false
                    }
                    if (isAlreadyMember) {
                        runOnUiThread {
                            Toast.makeText(this@JoinStoreActivity, "You're already a member of this store.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Check for existing pending join request (best-effort; skip if RLS blocks)
                    val existingNotifications = try {
                        SupabaseProvider.client.postgrest["notifications"]
                            .select(Columns.list("id")) {
                                filter {
                                    eq("sender_user_id", supaUserId)
                                    eq("store_id", store.store_id)
                                    eq("type", "join_request")
                                    eq("read", false)
                                }
                            }
                            .decodeList<NotificationIdRow>()
                    } catch (e: Exception) {
                        android.util.Log.w("JoinStoreActivity", "Duplicate request check failed (continuing): ${e.message}")
                        emptyList()
                    }

                    if (existingNotifications.isNotEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this@JoinStoreActivity, "You already have a pending join request for this store.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Send join request using RPC (server determines owner internally)
                    try {
                        SupabaseProvider.client.postgrest.rpc(
                            "send_join_request",
                            buildJsonObject {
                                put("p_store_id", store.store_id)
                            }
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("JoinStoreActivity", "send_join_request failed: ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this@JoinStoreActivity, "Unable to send request. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Create a pending notification for the requester (best-effort)
                    try {
                        SupabaseProvider.client.postgrest["notifications"].insert(
                            buildJsonObject {
                                put("receiver_user_id", supaUserId)
                                put("sender_user_id", supaUserId)
                                put("store_id", store.store_id)
                                put("type", "join_pending")
                                put("title", "Join Request")
                                put("message", "You requested to join ${store.name}")
                                put("read", false)
                            }
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("JoinStoreActivity", "Unable to create pending notification: ${e.message}")
                        // Non-blocking: continue even if notification insert fails
                    }

                    runOnUiThread {
                        Toast.makeText(this@JoinStoreActivity, "Join request sent successfully!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@JoinStoreActivity, com.presyohan.app.StoreActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }

                } catch (e: Exception) {
                    android.util.Log.e("JoinStoreActivity", "Join request failed: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this@JoinStoreActivity, "Unable to send join request. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun showLogoutDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
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
                    val intent = Intent(this@JoinStoreActivity, LoginActivity::class.java)
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
