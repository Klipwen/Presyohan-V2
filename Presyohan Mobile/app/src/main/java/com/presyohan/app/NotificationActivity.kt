package com.presyohan.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
 
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.Notification
import com.presyohan.app.adapter.NotificationAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import android.app.Dialog
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import android.widget.Toast
import java.util.UUID
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class NotificationFullRow(
    val id: String,
    val receiver_user_id: String,
    val sender_user_id: String?,
    val store_id: String?,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val created_at: String
)

// Local summary shape for get_user_stores RPC to avoid type name collisions
@Serializable
data class UserStoreLiteRow(
    val store_id: String,
    val name: String,
    val branch: String? = null,
    val type: String? = null,
    val role: String
)

class NotificationActivity : AppCompatActivity() {
    private val notifications = mutableListOf<Notification>()
    private val allNotifications = mutableListOf<Notification>()
    private val notificationIds = mutableListOf<String>()
    private val notificationStoreIds = mutableListOf<String?>()
    private lateinit var loadingOverlay: android.view.View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)
        loadingOverlay = LoadingOverlayHelper.attach(this)

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
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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

        val tabs = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabsNotification)
        listOf("All", "Requests", "Invites", "System").forEach { tabs.addTab(tabs.newTab().setText(it)) }
        tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) { applyTabFilter(tab.text?.toString() ?: "All") }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) { applyTabFilter(tab.text?.toString() ?: "All") }
        })
        tabs.getTabAt(0)?.select()

        // Add swipe-to-delete with confirmation
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val notification = notifications[position]
                val notificationId = notificationIds[position]
                // Show confirmation dialog
                val dialog = Dialog(this@NotificationActivity)
                val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
                dialog.setContentView(view)
                dialog.setCancelable(true)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                view.findViewById<TextView>(R.id.dialogTitle).text = "Delete Notification"
                view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to delete this notification? This action cannot be undone."
                view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
                    adapter.notifyItemChanged(position)
                    dialog.dismiss()
                }
                view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                    LoadingOverlayHelper.show(loadingOverlay)
                    lifecycleScope.launch {
                        try {
                            SupabaseProvider.client.postgrest["notifications"]
                                .delete {
                                    filter {
                                        eq("id", notificationId)
                                        eq("receiver_user_id", SupabaseProvider.client.auth.currentUserOrNull()?.id ?: "")
                                    }
                                }
                            notifications.removeAt(position)
                            notificationIds.removeAt(position)
                            notificationStoreIds.removeAt(position)
                            adapter.notifyItemRemoved(position)
                            dialog.dismiss()
                        } catch (e: Exception) {
                            Toast.makeText(this@NotificationActivity, "Unable to delete notification.", Toast.LENGTH_SHORT).show()
                            adapter.notifyItemChanged(position)
                            dialog.dismiss()
                        }
                        LoadingOverlayHelper.hide(loadingOverlay)
                    }
                }
                dialog.show()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Load notifications; marking as read happens after loading completes
        loadNotifications()
    }

    private fun loadNotifications() {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return@launch
                
                val response = SupabaseProvider.client.postgrest["notifications"]
                    .select(Columns.list("id", "receiver_user_id", "sender_user_id", "store_id", "type", "title", "message", "read", "created_at")) {
                        filter {
                            eq("receiver_user_id", userId)
                        }
                        order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeList<NotificationFullRow>()

                notifications.clear()
                notificationIds.clear()
                notificationStoreIds.clear()
                
                for (row in response) {
                    // Convert timestamp from ISO string to epoch millis
                    val timestamp = try {
                        java.time.Instant.parse(row.created_at).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    
                    // Map notification types and extract info from message
                    val (type, status, sender, storeName, role) = parseNotificationInfo(row)
                    
                    notifications.add(Notification(
                        type = type ?: "",
                        status = status ?: "",
                        sender = sender ?: "",
                        senderId = row.sender_user_id ?: "",
                        storeName = storeName ?: "",
                        role = role ?: "",
                        timestamp = timestamp,
                        message = row.message
                    ))
                    notificationIds.add(row.id)
                    notificationStoreIds.add(row.store_id)
                }
                
                allNotifications.clear()
                allNotifications.addAll(notifications)
                runOnUiThread { applyTabFilter(findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabsNotification).getTabAt(0)?.text?.toString() ?: "All") }
                markAllNotificationsAsRead()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NotificationActivity, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun applyTabFilter(category: String) {
        notifications.clear()
        val filtered = when (category) {
            "Requests" -> allNotifications.filter { it.type.contains("Request", true) }
            "Invites" -> allNotifications.filter { it.type.contains("Invite", true) || it.type.contains("Invitation", true) }
            "System" -> allNotifications.filter { !(it.type.contains("Request", true) || it.type.contains("Invite", true) || it.type.contains("Invitation", true)) }
            else -> allNotifications
        }
        notifications.addAll(filtered)
        findViewById<RecyclerView>(R.id.recyclerViewNotifications).adapter?.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        // Reload to ensure latest and mark as read upon load
        loadNotifications()
    }
    
    private fun parseNotificationInfo(row: NotificationFullRow): List<String?> {
        val type = when (row.type) {
            "join_request" -> "Join Request"
            "join_pending" -> "Join Request"
            "join_accepted" -> "Join Request"
            "join_rejected" -> "Join Request"
            "store_invitation" -> "Store Invitation"
            "invitation_accepted" -> "Store Invitation"
            else -> row.type
        }
        
        val status = when (row.type) {
            "join_request", "join_pending", "store_invitation" -> "Pending"
            "join_accepted", "invitation_accepted" -> "Accepted"
            "join_rejected" -> "Declined"
            else -> "Pending"
        }
        
        // Extract sender name/email from message; fallback to sender_user_id
        val sender = extractSenderFromMessage(row.message) ?: row.sender_user_id ?: "Unknown"
        
        // Extract store name from message
        val storeName = extractStoreNameFromMessage(row.message)
        
        // Extract role from message
        val role = extractRoleFromMessage(row.message)
        
        return listOf(type, status, sender, storeName, role)
    }
    
    private fun extractSenderFromMessage(message: String): String? {
        // Try to extract a display name from common notification patterns
        // Patterns: "<Name> invited you to join ...", "<Name> wants to join ...",
        //           "<Name> joined ...", "<Name> left ..."
        val patterns = listOf(
            "^(.+?)\\s+invited you to join".toRegex(),
            "^(.+?)\\s+wants to join".toRegex(),
            "^(.+?)\\s+joined\\s+".toRegex(),
            "^(.+?)\\s+left\\s+".toRegex()
        )
        for (regex in patterns) {
            val match = regex.find(message)
            if (match != null) {
                val candidate = match.groupValues.getOrNull(1)?.trim()
                if (!candidate.isNullOrEmpty()) return candidate
            }
        }
        // Fallback: extract email from messages like "user@example.com invited you to join..."
        val emailRegex = "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})".toRegex()
        return emailRegex.find(message)?.value
    }
    
    private fun extractStoreNameFromMessage(message: String): String? {
        // Extract store name from messages like "...join StoreName as..."
        val joinPattern = "join\\s+([^\\s]+)\\s+".toRegex()
        return joinPattern.find(message)?.groupValues?.get(1)
    }
    
    private fun extractRoleFromMessage(message: String): String? {
        return when {
            message.contains("as manager") -> "manager"
            message.contains("as employee") -> "employee"
            else -> null
        }
    }

    private fun markAllNotificationsAsRead() {
        lifecycleScope.launch {
            try {
                val allIds = notificationIds.toList()
                if (allIds.isNotEmpty()) {
                    SupabaseProvider.client.postgrest.rpc(
                        "mark_notifications_read",
                        buildJsonObject {
                            put(
                                "p_notification_ids",
                                buildJsonArray {
                                    allIds.forEach { add(JsonPrimitive(it)) }
                                }
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                // Silently fail - not critical
            }
        }
    }

    private fun handleAccept(notification: Notification) {
        if (notification.type == "Join Request") {
            // Show dialog for owner to select role
            showManageJoinRequestDialog(notification)
            return
        }
        
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val notificationId = notificationIds[notifications.indexOf(notification)]

                SupabaseProvider.client.postgrest.rpc(
                    "handle_store_invitation",
                    buildJsonObject {
                        put("p_notification_id", notificationId)
                        put("p_action", "accept")
                    }
                )

                Toast.makeText(this@NotificationActivity, "Invitation accepted", Toast.LENGTH_SHORT).show()
                loadNotifications() // Refresh list
            } catch (e: Exception) {
                Toast.makeText(this@NotificationActivity, "Failed to accept invitation", Toast.LENGTH_SHORT).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }
    
    private fun handleReject(notification: Notification) {
        if (notification.type == "Join Request") {
            lifecycleScope.launch {
                try {
                    val notificationId = notificationIds[notifications.indexOf(notification)]

                    SupabaseProvider.client.postgrest.rpc(
                        "handle_join_request",
                        buildJsonObject {
                            put("p_notification_id", notificationId)
                            put("p_action", "reject")
                        }
                    )

                    Toast.makeText(this@NotificationActivity, "Join request rejected", Toast.LENGTH_SHORT).show()
                    loadNotifications() // Refresh list
                } catch (e: Exception) {
                    Toast.makeText(this@NotificationActivity, "Failed to reject request", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val notificationId = notificationIds[notifications.indexOf(notification)]

                SupabaseProvider.client.postgrest.rpc(
                    "handle_store_invitation",
                    buildJsonObject {
                        put("p_notification_id", notificationId)
                        put("p_action", "reject")
                    }
                )

                Toast.makeText(this@NotificationActivity, "Invitation rejected", Toast.LENGTH_SHORT).show()
                loadNotifications() // Refresh list
            } catch (e: Exception) {
                Toast.makeText(this@NotificationActivity, "Failed to reject invitation", Toast.LENGTH_SHORT).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }
    
    private fun handleViewStore(notification: Notification) {
        // Open HomeActivity for the store referenced in the notification
        val index = notifications.indexOf(notification)
        val storeId = if (index >= 0 && index < notificationStoreIds.size) notificationStoreIds[index] else null
        val storeName = notification.storeName

        // If we have the storeId from the notification, prefer using it directly
        if (storeId != null) {
            val intent = Intent(this@NotificationActivity, HomeActivity::class.java)
            intent.putExtra("storeId", storeId)
            if (storeName != null) intent.putExtra("storeName", storeName)
            startActivity(intent)
            return
        }

        // Fallback: resolve by store name from the user's stores
        val resolvedName = storeName ?: return
        lifecycleScope.launch {
            try {
                val userStores = SupabaseProvider.client.postgrest.rpc("get_user_stores")
                    .decodeList<UserStoreLiteRow>()

                val store = userStores.find { it.name == resolvedName }
                if (store != null) {
                    val intent = Intent(this@NotificationActivity, HomeActivity::class.java)
                    intent.putExtra("storeId", store.store_id)
                    intent.putExtra("storeName", resolvedName)
                    startActivity(intent)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@NotificationActivity, "You are not a member of this store.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NotificationActivity, "Unable to open store.", Toast.LENGTH_SHORT).show()
                }
            }
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
            val selectedRole = if (radioManager.isChecked) "manager" else "employee"
            
            lifecycleScope.launch {
                try {
                    val notificationId = notificationIds[notifications.indexOf(notification)]

                    SupabaseProvider.client.postgrest.rpc(
                        "handle_join_request",
                        buildJsonObject {
                            put("p_notification_id", notificationId)
                            put("p_action", "accept")
                            put("p_role", selectedRole)
                        }
                    )

                    runOnUiThread {
                        Toast.makeText(this@NotificationActivity, "Join request accepted", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadNotifications() // Refresh list
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@NotificationActivity, "Failed to accept request", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }
}