package com.presyohan.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import coil.load
import coil.transform.CircleCropTransformation
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
    val sender_user_id: String? = null,
    val store_id: String? = null,
    val type: String? = null,
    val title: String? = null,
    val message: String? = null,
    val read: Boolean = false,
    val created_at: String = ""
)

@Serializable
data class UserStoreLiteRow(
    val store_id: String,
    val name: String,
    val branch: String? = null,
    val type: String? = null,
    val role: String
)

class NotificationActivity : AppCompatActivity() {
    private var allNotifications = mutableListOf<Notification>()
    private lateinit var loadingOverlay: android.view.View
    private lateinit var adapter: NotificationAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_down,
                R.anim.stay
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_down, R.anim.stay)
        }
        setContentView(R.layout.activity_notification)
        loadingOverlay = LoadingOverlayHelper.attach(this)



        // Back button closes the activity
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewNotifications)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = NotificationAdapter(
            onAccept = { notification -> handleAccept(notification) },
            onReject = { notification -> handleReject(notification) },
            onCancel = { notification -> handleCancel(notification) },
            onViewStore = { notification -> handleViewStore(notification) }
        )
        recyclerView.adapter = adapter

        findViewById<TextView>(R.id.chipAll).setOnClickListener { updateChipSelection(R.id.chipAll) }
        findViewById<TextView>(R.id.chipJoin).setOnClickListener { updateChipSelection(R.id.chipJoin) }
        findViewById<TextView>(R.id.chipInvites).setOnClickListener { updateChipSelection(R.id.chipInvites) }
        findViewById<TextView>(R.id.chipSuki).setOnClickListener { updateChipSelection(R.id.chipSuki) }
        findViewById<TextView>(R.id.chipSystem).setOnClickListener { updateChipSelection(R.id.chipSystem) }

        // Initialize sliding tab indicator layout on startup
        findViewById<View>(R.id.tabContainer).post {
            val defaultChip = findViewById<TextView>(activeTabId) ?: return@post
            val indicator = findViewById<View>(R.id.tabIndicator) ?: return@post
            val parentView = defaultChip.parent as? View ?: return@post
            val params = indicator.layoutParams
            params.width = defaultChip.width
            params.height = defaultChip.height
            indicator.layoutParams = params
            indicator.x = defaultChip.x + parentView.left
            indicator.y = defaultChip.y + parentView.top
        }

        // Add swipe-to-delete with confirmation
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val notification = adapter.currentList[position]
                
                // Show confirmation dialog using reusable template (NO RED rule strictly applied)
                val dialog = Dialog(this@NotificationActivity)
                val view = layoutInflater.inflate(R.layout.dialog_reusable_template, null)
                dialog.setContentView(view)
                dialog.setCancelable(true)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                view.findViewById<TextView>(R.id.dialogTitle).text = "Delete Notification"
                view.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to delete this notification? This action cannot be undone."

                val btnNegative = view.findViewById<android.widget.Button>(R.id.btnNegative)
                val btnPositive = view.findViewById<android.widget.Button>(R.id.btnPositive)
                btnNegative.text = "Cancel"
                btnPositive.text = "Delete"

                btnNegative.setOnClickListener {
                    adapter.notifyItemChanged(position)
                    dialog.dismiss()
                }
                btnPositive.setOnClickListener {
                    LoadingOverlayHelper.show(loadingOverlay)
                    lifecycleScope.launch {
                        try {
                            SupabaseProvider.client.postgrest.rpc(
                                "delete_notification",
                                buildJsonObject {
                                    put("p_notification_id", notification.id)
                                }
                            )
                            allNotifications.removeAll { it.id == notification.id }
                            applyTabFilter(getSelectedTabName())
                            dialog.dismiss()
                        } catch (e: Exception) {
                            android.util.Log.e("NotificationActivity", "Failed to delete notification", e)
                            Toast.makeText(this@NotificationActivity, "Failed to delete: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
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

                val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}".toRegex()
                val userIdsToResolve = mutableSetOf<String>()
                for (row in response) {
                    row.sender_user_id?.let { userIdsToResolve.add(it) }
                    row.receiver_user_id.let { userIdsToResolve.add(it) }
                    row.message?.let { msg ->
                        uuidRegex.findAll(msg).forEach { match ->
                            userIdsToResolve.add(match.value)
                        }
                    }
                }

                val resolvedNames = mutableMapOf<String, String>()
                if (userIdsToResolve.isNotEmpty()) {
                    try {
                        @Serializable
                        data class UserProfileLite(val id: String, val name: String?)
                        
                        val profiles = SupabaseProvider.client.postgrest["app_users"]
                            .select(Columns.list("id", "name")) {
                                filter {
                                    isIn("id", userIdsToResolve.toList())
                                }
                            }
                            .decodeList<UserProfileLite>()
                        
                        for (p in profiles) {
                            p.name?.takeIf { it.isNotBlank() }?.let { resolvedNames[p.id] = it }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NotificationActivity", "Failed to resolve user names", e)
                    }
                }

                val cleanMessage: (String?) -> String = { msg ->
                    if (msg.isNullOrEmpty()) ""
                    else {
                        var temp: String = msg
                        uuidRegex.findAll(msg).forEach { match ->
                            val uuid = match.value
                            val name = resolvedNames[uuid] ?: "a user"
                            temp = temp.replace(uuid, name)
                        }
                        temp
                    }
                }

                allNotifications.clear()
                
                for (row in response) {
                    val timestamp = try {
                        java.time.Instant.parse(row.created_at).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    
                    val cleanedMsg = cleanMessage(row.message)
                    val cleanedRow = row.copy(message = cleanedMsg)
                    val (type, status, parsedSender, storeName, role) = parseNotificationInfo(cleanedRow)
                    
                    var sender = parsedSender ?: "Unknown"
                    if (uuidRegex.matches(sender)) {
                        sender = resolvedNames[sender] ?: "a user"
                    }
                    
                    allNotifications.add(Notification(
                        id = row.id,
                        type = type ?: "",
                        status = status ?: "",
                        sender = sender,
                        senderId = row.sender_user_id,
                        storeName = storeName,
                        role = role,
                        timestamp = timestamp,
                        message = cleanedMsg,
                        isNew = !row.read,
                        storeId = row.store_id
                    ))
                }
                
                processAndFormatNotifications()
                
                applyTabFilter(getSelectedTabName())
                
                markAllNotificationsAsRead()
            } catch (e: Exception) {
                android.util.Log.e("NotificationActivity", "Failed to load notifications", e)
                Toast.makeText(this@NotificationActivity, "Failed to load: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun applyTabFilter(category: String) {
        val filtered = when (category) {
            "Join Requests" -> allNotifications.filter { it.type == "Join Request" }
            "Store Invites" -> allNotifications.filter { it.type == "Store Invitation" || it.type == "Store Invitation Sent" }
            "Suki" -> allNotifications.filter { it.type == "Suki Request" }
            "System" -> allNotifications.filter {
                it.type != "Join Request" && it.type != "Store Invitation" && it.type != "Store Invitation Sent" && it.type != "Suki Request"
            }
            else -> allNotifications
        }
        adapter.submitList(filtered.toList())
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }
    
    private fun parseNotificationInfo(row: NotificationFullRow): List<String?> {
        val dbType = row.type.orEmpty()
        val type = when (dbType) {
            "join_request", "join_pending", "join_accepted", "join_rejected", "join_canceled" -> "Join Request"
            "invite_pending" -> "Store Invitation Sent"
            "store_invitation", "invitation_accepted", "invitation_rejected", "invite_canceled" -> "Store Invitation"
            "suki_request_sent", "suki_request_received", "suki_accepted", "suki_rejected", "suki_request_canceled" -> "Suki Request"
            "excel_export" -> "Export Complete"
            "member_left" -> "Staff Left Store"
            "member_joined" -> "Staff Joined Store"
            "member_removed" -> "Removed Staff"
            "role_changed" -> "Role Changed"
            "store_deleted" -> "Store Deleted"
            "store_visibility_changed" -> "Updated Store Status"
            "copy_price_complete" -> "Copy Price Complete"
            else -> dbType.ifEmpty { null }
        }
        
        val status = when (dbType) {
            "join_request", "join_pending", "store_invitation", "suki_request_sent", "suki_request_received", "invite_pending" -> "Pending"
            "join_accepted", "invitation_accepted", "suki_accepted" -> "Accepted"
            "join_rejected", "invitation_rejected", "suki_rejected" -> "Declined"
            "suki_request_canceled", "join_canceled", "invite_canceled" -> "Canceled"
            else -> ""
        }
        
        val msg = row.message.orEmpty()
        val sender = extractSenderFromMessage(msg) ?: row.sender_user_id ?: "Unknown"
        val storeName = extractStoreNameFromMessage(msg)
        val role = extractRoleFromMessage(msg)
        
        return listOf(type, status, sender, storeName, role)
    }
    
    private fun extractSenderFromMessage(message: String?): String? {
        if (message.isNullOrEmpty()) return null
        val patterns = listOf(
            "^(.+?)\\s+invited you to join".toRegex(),
            "^(.+?)\\s+wants to join".toRegex(),
            "^(.+?)\\s+joined\\s+".toRegex(),
            "^(.+?)\\s+left\\s+".toRegex(),
            "^(.+?)\\s+requested to connect".toRegex(),
            "^The Suking Tindahan request from\\s+(.+?)\\s+was".toRegex()
        )
        for (regex in patterns) {
            val match = regex.find(message)
            if (match != null) {
                val candidate = match.groupValues.getOrNull(1)?.trim()
                if (!candidate.isNullOrEmpty()) return candidate
            }
        }
        val emailRegex = "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})".toRegex()
        return emailRegex.find(message)?.value
    }
    
    private fun extractStoreNameFromMessage(message: String?): String? {
        if (message.isNullOrEmpty()) return null
        val cleanMsg = message.trim()
        val patterns = listOf(
            "invited you to join\\s+(.*?)\\s+as".toRegex(RegexOption.IGNORE_CASE),
            "invited\\s+.+?\\s+to join on your store\\s+(.*?)\\s+as".toRegex(RegexOption.IGNORE_CASE),
            "join store,\\s*(.*?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE),
            "join your store,\\s*(.*?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE),
            "join\\s+(.*?)\\s+as\\s+(?:a\\s+)?(?:Manager|Sales-Staff|employee|staff|owner)".toRegex(RegexOption.IGNORE_CASE),
            "^(.+?)\\s+accepted your request".toRegex(RegexOption.IGNORE_CASE),
            "^(.+?)\\s+declined your request".toRegex(RegexOption.IGNORE_CASE),
            "partner with\\s+(.*?)\\s+as".toRegex(RegexOption.IGNORE_CASE),
            "partnership request to\\s+(.*?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE),
            "to join\\s+(.*?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE),
            "join your store\\s+(.*?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val match = regex.find(cleanMsg)
            if (match != null) {
                val candidate = match.groupValues.lastOrNull()?.trim()
                if (!candidate.isNullOrEmpty()) {
                    return candidate.removeSuffix(".").removeSuffix(",").trim()
                }
            }
        }
        return null
    }
    
    private fun extractRoleFromMessage(message: String?): String? {
        if (message.isNullOrEmpty()) return null
        val lower = message.lowercase()
        return when {
            lower.contains("manager") -> "manager"
            lower.contains("employee") || lower.contains("sales staff") || lower.contains("sales-staff") || lower.contains("staff") -> "employee"
            else -> null
        }
    }

    private fun processAndFormatNotifications() {
        val processedList = mutableListOf<Notification>()
        
        // 1. Identify and remove duplicates:
        val toDiscard = mutableSetOf<String>()
        
        for (i in 0 until allNotifications.size) {
            val A = allNotifications[i]
            if (A.id in toDiscard) continue
            
            if ((A.type == "Store Invitation" || A.type == "Store Invitation Sent") && (A.status == "Accepted" || A.status == "Declined")) {
                val duplicateIndex = allNotifications.indexOfFirst { B ->
                    B.id != A.id && B.id !in toDiscard &&
                    B.storeId == A.storeId && B.senderId == A.senderId &&
                    (B.type == "Store Invitation" || B.type == "Store Invitation Sent") &&
                    (B.message.startsWith("You invited") || B.message.startsWith("You canceled"))
                }
                
                if (duplicateIndex != -1) {
                    val B = allNotifications[duplicateIndex]
                    toDiscard.add(B.id)
                }
            }
        }
        
        val filteredList = allNotifications.filter { it.id !in toDiscard }
        
        // 2. Format the messages to follow the design specifications:
        for (n in filteredList) {
            val formattedMsg = when (n.type) {
                "Store Invitation", "Store Invitation Sent" -> {
                    val roleDisplay = if (n.role == "manager") "Manager" else "Sales Staff"
                    val roleMemberDisplay = if (n.role == "manager") "Manager" else "Sales Staff member"
                    val store = n.storeName ?: "Store"
                    val senderName = n.sender.takeIf { it != "Unknown" } ?: "A user"
                    
                    when (n.status) {
                        "Pending" -> {
                            if (n.type == "Store Invitation Sent") {
                                if (n.message.startsWith("You invited")) {
                                    "You invited $senderName to join on your store $store as a $roleDisplay. Please wait for their response"
                                } else {
                                    n.message
                                }
                            } else {
                                "$store invited you to join their store team as a $roleDisplay."
                            }
                        }
                        "Accepted" -> {
                            val isInvitee = n.message.startsWith("You accepted") || n.message.contains("You accepted")
                            if (isInvitee) {
                                if (n.role == "manager") {
                                    "You accepted the invitation as a store Manager for $store. You can now manage and update store prices."
                                } else {
                                    "You accepted the invitation as a Sales Staff for $store. You are now part of their team."
                                }
                            } else {
                                if (n.message.contains("accepted your invitation") || n.message.startsWith("You invited")) {
                                    "$senderName accepted your invitation as a $roleMemberDisplay. They are now part of your $store team."
                                } else {
                                    n.message
                                }
                            }
                        }
                        "Declined" -> {
                            val isInvitee = n.message.startsWith("You declined") || n.message.contains("You declined")
                            if (isInvitee) {
                                "You declined the invitation from $store to join their team as a $roleDisplay."
                            } else {
                                if (n.message.contains("declined your invitation") || n.message.startsWith("You invited")) {
                                    "$senderName declined your invitation as a $roleDisplay. You may invite them again anytime."
                                } else {
                                    n.message
                                }
                            }
                        }
                        "Canceled" -> {
                            val isOwner = n.message.startsWith("You canceled") || n.type == "Store Invitation Sent"
                            if (isOwner) {
                                if (n.message.startsWith("You canceled") || n.message.startsWith("You invited")) {
                                    "You canceled the invitation to $senderName to join your store $store as a $roleDisplay."
                                } else {
                                    n.message
                                }
                            } else {
                                "The invitation from $store to join their store team was canceled."
                            }
                        }
                        else -> n.message
                    }
                }
                else -> n.message
            }
            
            processedList.add(n.copy(message = formattedMsg))
        }
        
        allNotifications.clear()
        allNotifications.addAll(processedList)
    }

    private fun markAllNotificationsAsRead() {
        lifecycleScope.launch {
            try {
                val allIds = allNotifications.filter { it.isNew }.map { it.id }
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
                    runOnUiThread {
                        allNotifications = allNotifications.map { it.copy(isNew = false) }.toMutableList()
                        applyTabFilter(getSelectedTabName())
                    }
                }
            } catch (e: Exception) {
                // Silently fail - not critical
            }
        }
    }

    private fun updateNotificationInPlace(notificationId: String, newStatus: String, newMessage: String) {
        val index = allNotifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            val oldItem = allNotifications[index]
            allNotifications[index] = oldItem.copy(
                status = newStatus,
                message = newMessage,
                isNew = false
            )
            applyTabFilter(getSelectedTabName())
        }
    }

    private fun handleAccept(notification: Notification) {
        if (notification.type == "Join Request") {
            showManageJoinRequestDialog(notification)
            return
        }
        
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                if (notification.type == "Suki Request") {
                    val requesterId = notification.senderId ?: return@launch
                    val storeId = notification.storeId ?: return@launch

                    // Update suki relationships to active
                    SupabaseProvider.client.postgrest["suki_relationships"].update(
                        buildJsonObject { put("status", "active") }
                    ) {
                        filter {
                            eq("user_id", requesterId)
                            eq("store_id", storeId)
                        }
                    }

                    // Update local notification status
                    val newMsg = "You accepted ${notification.sender}'s request. They can now view your public prices as a Suki."
                    updateNotificationInPlace(notification.id, "Accepted", newMsg)

                    // Update DB notification
                    SupabaseProvider.client.postgrest["notifications"].update(
                        buildJsonObject {
                            put("type", "suki_accepted")
                            put("message", newMsg)
                        }
                    ) {
                        filter { eq("id", notification.id) }
                    }

                    // Let other owners know who accepted the request
                    try {
                        val handlerProfile = SupabaseAuthService.getUserProfile()
                        val handlerName = handlerProfile?.name ?: "An owner"
                        SupabaseProvider.client.postgrest["notifications"].update(
                            buildJsonObject {
                                put("type", "suki_accepted")
                                put("message", "$handlerName accepted ${notification.sender}'s request. They can now view public prices as a Suki.")
                            }
                        ) {
                            filter {
                                eq("store_id", storeId)
                                eq("type", "suki_request_received")
                                eq("sender_user_id", requesterId)
                                neq("id", notification.id)
                            }
                        }
                    } catch (_: Exception) {}

                    // Insert decision notification for requester (Flow C)
                    SupabaseProvider.client.postgrest["notifications"].insert(
                        buildJsonObject {
                            put("receiver_user_id", requesterId)
                            put("sender_user_id", SupabaseProvider.client.auth.currentUserOrNull()?.id ?: "")
                            put("store_id", storeId)
                            put("type", "suki_accepted")
                            put("title", "Suki Request Accepted")
                            put("message", "${notification.storeName ?: "QSOS"} accepted your request! You are now partnered as a Suking Tindahan and can view their prices.")
                            put("read", false)
                        }
                    )
                    Toast.makeText(this@NotificationActivity, "Suki request accepted", Toast.LENGTH_SHORT).show()
                    LoadingOverlayHelper.hide(loadingOverlay)
                } else {
                    // Store Invitation accepted
                    val proceedAccept = {
                        lifecycleScope.launch {
                            try {
                                SupabaseProvider.client.postgrest.rpc(
                                    "handle_store_invitation",
                                    buildJsonObject {
                                        put("p_notification_id", notification.id)
                                        put("p_action", "accept")
                                    }
                                )
                                val newMsg = "You accepted the invitation to join the ${notification.storeName ?: "store"} team. You can now manage and update store prices."
                                updateNotificationInPlace(notification.id, "Accepted", newMsg)
                                Toast.makeText(this@NotificationActivity, "Invitation accepted", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@NotificationActivity, "Failed to accept invitation", Toast.LENGTH_SHORT).show()
                            } finally {
                                LoadingOverlayHelper.hide(loadingOverlay)
                            }
                        }
                    }

                    val currentUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                    val storeId = notification.storeId
                    if (currentUserId != null && !storeId.isNullOrBlank()) {
                        val isSuki = try {
                            val sukiList = SupabaseProvider.client.postgrest["suki_relationships"]
                                .select {
                                    filter {
                                        eq("user_id", currentUserId)
                                        eq("store_id", storeId)
                                        eq("status", "active")
                                    }
                                }
                                .decodeList<SukiRelationshipRow>()
                            sukiList.isNotEmpty()
                        } catch (e: Exception) {
                            false
                        }

                        if (isSuki) {
                            LoadingOverlayHelper.hide(loadingOverlay)
                            val roleName = if (notification.role == "manager") "Manager" else "Sales Staff"
                            val promptMessage = "You are currently a Suki of this store. Accepting this invitation to join the team as a $roleName will make you a store team member and you will no longer be a Suki. Do you want to proceed?"
                            
                            ReusableDialogHelper.showCustomDialog(
                                context = this@NotificationActivity,
                                title = "Confirm Invitation",
                                message = promptMessage,
                                positiveButtonText = "Proceed",
                                positiveAction = {
                                    LoadingOverlayHelper.show(loadingOverlay)
                                    proceedAccept()
                                },
                                negativeButtonText = "Cancel",
                                negativeAction = {}
                            )
                        } else {
                            proceedAccept()
                        }
                    } else {
                        proceedAccept()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@NotificationActivity, "Failed to accept invitation", Toast.LENGTH_SHORT).show()
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
    
    private fun handleReject(notification: Notification) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                if (notification.type == "Join Request") {
                    SupabaseProvider.client.postgrest.rpc(
                        "handle_join_request",
                        buildJsonObject {
                            put("p_notification_id", notification.id)
                            put("p_action", "reject")
                        }
                    )
                    val newMsg = "You declined ${notification.sender}'s request to join ${notification.storeName ?: "the store"}."
                    updateNotificationInPlace(notification.id, "Declined", newMsg)
                    Toast.makeText(this@NotificationActivity, "Join request rejected", Toast.LENGTH_SHORT).show()
                } else if (notification.type == "Suki Request") {
                    val requesterId = notification.senderId ?: return@launch
                    val storeId = notification.storeId ?: return@launch

                    // Delete the pending suki relationship
                    SupabaseProvider.client.postgrest["suki_relationships"].delete {
                        filter {
                            eq("user_id", requesterId)
                            eq("store_id", storeId)
                        }
                    }

                    // Update local notification status
                    val newMsg = "You declined the Suking Tindahan request from ${notification.sender}."
                    updateNotificationInPlace(notification.id, "Declined", newMsg)

                    // Update DB notification
                    SupabaseProvider.client.postgrest["notifications"].update(
                        buildJsonObject {
                            put("type", "suki_rejected")
                            put("message", newMsg)
                        }
                    ) {
                        filter { eq("id", notification.id) }
                    }

                    // Let other owners know who declined the request
                    try {
                        val handlerProfile = SupabaseAuthService.getUserProfile()
                        val handlerName = handlerProfile?.name ?: "An owner"
                        SupabaseProvider.client.postgrest["notifications"].update(
                            buildJsonObject {
                                put("type", "suki_rejected")
                                put("message", "$handlerName declined the Suking Tindahan request from ${notification.sender}.")
                            }
                        ) {
                            filter {
                                eq("store_id", storeId)
                                eq("type", "suki_request_received")
                                eq("sender_user_id", requesterId)
                                neq("id", notification.id)
                            }
                        }
                    } catch (_: Exception) {}

                    // Insert decision notification for requester (Flow C)
                    SupabaseProvider.client.postgrest["notifications"].insert(
                        buildJsonObject {
                            put("receiver_user_id", requesterId)
                            put("sender_user_id", SupabaseProvider.client.auth.currentUserOrNull()?.id ?: "")
                            put("store_id", storeId)
                            put("type", "suki_rejected")
                            put("title", "Suki Request Rejected")
                            put("message", "${notification.storeName ?: "QSOS"} declined your request to partner as a Suking Tindahan. You can try again later.")
                            put("read", false)
                        }
                    )
                    Toast.makeText(this@NotificationActivity, "Suki request rejected", Toast.LENGTH_SHORT).show()
                } else {
                    // Store invitation reject
                    SupabaseProvider.client.postgrest.rpc(
                        "handle_store_invitation",
                        buildJsonObject {
                            put("p_notification_id", notification.id)
                            put("p_action", "reject")
                        }
                    )
                    val newMsg = "You declined the invitation to join ${notification.storeName ?: "the store"}."
                    updateNotificationInPlace(notification.id, "Declined", newMsg)
                    Toast.makeText(this@NotificationActivity, "Invitation rejected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationActivity", "Failed to reject invitation/request", e)
                Toast.makeText(this@NotificationActivity, "Failed to reject: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun handleCancel(notification: Notification) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                if (notification.type == "Suki Request") {
                    SupabaseProvider.client.postgrest.rpc(
                        "cancel_suki_request",
                        buildJsonObject {
                            put("p_notification_id", notification.id)
                        }
                    )
                    val newMsg = "You canceled the partnership request to ${notification.storeName ?: "QSOS"}."
                    updateNotificationInPlace(notification.id, "Canceled", newMsg)
                    Toast.makeText(this@NotificationActivity, "Partnership request canceled", Toast.LENGTH_SHORT).show()
                } else if (notification.type == "Join Request") {
                    SupabaseProvider.client.postgrest.rpc(
                        "cancel_join_request",
                        buildJsonObject {
                            put("p_notification_id", notification.id)
                        }
                    )
                    val newMsg = "You canceled your request to join store, ${notification.storeName ?: "QSOS"}."
                    updateNotificationInPlace(notification.id, "Canceled", newMsg)
                    Toast.makeText(this@NotificationActivity, "Join request canceled", Toast.LENGTH_SHORT).show()
                } else {
                    // Store Invitation cancel
                    SupabaseProvider.client.postgrest.rpc(
                        "cancel_store_invitation",
                        buildJsonObject {
                            put("p_notification_id", notification.id)
                        }
                    )
                    val newMsg = "You canceled the invitation to ${notification.sender} to join your store ${notification.storeName ?: "QSOS"}."
                    updateNotificationInPlace(notification.id, "Canceled", newMsg)
                    Toast.makeText(this@NotificationActivity, "Invitation canceled", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationActivity", "Failed to cancel request", e)
                Toast.makeText(this@NotificationActivity, "Failed to cancel: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
    
    private fun handleViewStore(notification: Notification) {
        if (notification.type == "excel_export" || notification.message.contains("Excel file") || notification.message.contains("exported")) {
            try {
                val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open downloads folder", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (notification.message.contains("status to public") || notification.message.contains("status to private")) {
            val currentUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id
            val isOwner = currentUserId != null && notification.senderId != currentUserId
            if (isOwner) {
                val intent = Intent(this, ManageStoreActivity::class.java)
                intent.putExtra("storeId", notification.storeId)
                startActivity(intent)
                return
            }
        }

        val storeId = notification.storeId
        val storeName = notification.storeName

        if (storeId != null) {
            val intent = Intent(this@NotificationActivity, HomeActivity::class.java)
            intent.putExtra("storeId", storeId)
            if (storeName != null) intent.putExtra("storeName", storeName)
            startActivity(intent)
            return
        }

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
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val storeName = notification.storeName ?: "Store"
        val layoutOptionSalesStaff = view.findViewById<View>(R.id.layoutOptionSalesStaff)
        val layoutOptionManager = view.findViewById<View>(R.id.layoutOptionManager)
        val imgOptionSalesStaff = view.findViewById<ImageView>(R.id.imgOptionSalesStaff)
        val imgOptionManager = view.findViewById<ImageView>(R.id.imgOptionManager)
        val tvOptionSalesStaff = view.findViewById<TextView>(R.id.tvOptionSalesStaff)
        val tvOptionManager = view.findViewById<TextView>(R.id.tvOptionManager)
        val staffAvatar = view.findViewById<ImageView>(R.id.staffAvatar)
        val staffName = view.findViewById<TextView>(R.id.staffName)
        val staffDetails = view.findViewById<TextView>(R.id.staffDetails)

        val btnBack = view.findViewById<Button>(R.id.btnBack)
        val btnAccept = view.findViewById<Button>(R.id.btnAccept)

        staffName.text = notification.sender
        staffDetails.text = "Loading..."

        var selectedRole = "employee"

        val selectSalesStaff = {
            selectedRole = "employee"
            layoutOptionSalesStaff.setBackgroundResource(R.drawable.bg_card_selected_orange)
            imgOptionSalesStaff.setImageResource(R.drawable.ic_radio_checked_orange)
            tvOptionSalesStaff.setTextColor(getColor(R.color.presyo_orange))

            layoutOptionManager.setBackgroundResource(R.drawable.bg_card_unselected)
            imgOptionManager.setImageResource(R.drawable.ic_radio_unchecked)
            tvOptionManager.setTextColor(getColor(R.color.presyo_darkblue))
        }

        val selectManager = {
            selectedRole = "manager"
            layoutOptionSalesStaff.setBackgroundResource(R.drawable.bg_card_unselected)
            imgOptionSalesStaff.setImageResource(R.drawable.ic_radio_unchecked)
            tvOptionSalesStaff.setTextColor(getColor(R.color.presyo_darkblue))

            layoutOptionManager.setBackgroundResource(R.drawable.bg_card_selected_orange)
            imgOptionManager.setImageResource(R.drawable.ic_radio_checked_orange)
            tvOptionManager.setTextColor(getColor(R.color.presyo_orange))
        }

        layoutOptionSalesStaff.setOnClickListener { selectSalesStaff() }
        layoutOptionManager.setOnClickListener { selectManager() }

        // Mutable name resolved after DB fetch — used in accept message
        var resolvedName = notification.sender.takeIf { it.isNotBlank() && !it.contains("-") } ?: "the user"

        // Fetch sender's name, user code and avatar from database
        lifecycleScope.launch {
            try {
                @Serializable
                data class UserCodeCheck(val name: String?, val user_code: String?, val avatar_url: String?)
                val profile = SupabaseProvider.client.postgrest["app_users"]
                    .select(Columns.list("name", "user_code", "avatar_url")) {
                        filter { eq("id", notification.senderId ?: "") }
                    }
                    .decodeList<UserCodeCheck>()
                    .firstOrNull()
                
                runOnUiThread {
                    if (profile != null) {
                        val displayName = profile.name?.takeIf { it.isNotBlank() }
                            ?: notification.sender.takeIf { it.isNotBlank() && !it.contains("-") }
                            ?: "Unknown"
                        resolvedName = displayName
                        staffName.text = displayName
                        staffDetails.text = profile.user_code ?: "NO-CODE"
                        if (!profile.avatar_url.isNullOrBlank()) {
                            staffAvatar.load(profile.avatar_url) {
                                crossfade(true)
                                transformations(CircleCropTransformation())
                                error(R.drawable.avatar_default)
                                fallback(R.drawable.avatar_default)
                            }
                        }
                    } else {
                        staffDetails.text = "NO-CODE"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    staffDetails.text = "NO-CODE"
                }
            }
        }

        btnBack.setOnClickListener { dialog.dismiss() }
        btnAccept.setOnClickListener {
            LoadingOverlayHelper.show(loadingOverlay)
            dialog.dismiss()
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "handle_join_request",
                        buildJsonObject {
                            put("p_notification_id", notification.id)
                            put("p_action", "accept")
                            put("p_role", selectedRole)
                        }
                    )

                    val newMsg = "You accepted $resolvedName's request to join $storeName as $selectedRole."
                    runOnUiThread {
                        updateNotificationInPlace(notification.id, "Accepted", newMsg)
                        Toast.makeText(this@NotificationActivity, "Join request accepted", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotificationActivity", "Failed to accept join request", e)
                    runOnUiThread {
                        Toast.makeText(this@NotificationActivity, "Failed to accept request: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
        dialog.show()
    }

    private var activeTabId: Int = R.id.chipAll

    private fun animateTabIndicator(targetChipId: Int) {
        val targetChip = findViewById<TextView>(targetChipId) ?: return
        val indicator = findViewById<View>(R.id.tabIndicator) ?: return
        val parentView = targetChip.parent as? View ?: return

        activeTabId = targetChipId

        val parentLeft = parentView.left
        val parentTop = parentView.top

        if (targetChip.width == 0) {
            targetChip.post {
                val params = indicator.layoutParams
                params.width = targetChip.width
                params.height = targetChip.height
                indicator.layoutParams = params
                indicator.x = targetChip.x + parentLeft
                indicator.y = targetChip.y + parentTop
            }
            return
        }

        val startX = indicator.x
        val endX = targetChip.x + parentLeft

        val startWidth = indicator.width
        val endWidth = targetChip.width

        val startHeight = indicator.height
        val endHeight = targetChip.height

        indicator.y = targetChip.y + parentTop

        val animatorX = android.animation.ValueAnimator.ofFloat(startX, endX)
        animatorX.addUpdateListener { animation ->
            indicator.x = animation.animatedValue as Float
        }

        val animatorW = android.animation.ValueAnimator.ofInt(startWidth, endWidth)
        animatorW.addUpdateListener { animation ->
            val p = indicator.layoutParams
            p.width = animation.animatedValue as Int
            indicator.layoutParams = p
        }

        val animatorH = android.animation.ValueAnimator.ofInt(startHeight, endHeight)
        animatorH.addUpdateListener { animation ->
            val p = indicator.layoutParams
            p.height = animation.animatedValue as Int
            indicator.layoutParams = p
        }

        val animatorSet = android.animation.AnimatorSet()
        animatorSet.playTogether(animatorX, animatorW, animatorH)
        animatorSet.duration = 220
        animatorSet.interpolator = android.view.animation.DecelerateInterpolator()
        animatorSet.start()
    }

    private fun updateChipSelection(selectedChipId: Int) {
        val chips = listOf(
            R.id.chipAll to "All",
            R.id.chipJoin to "Join Requests",
            R.id.chipInvites to "Store Invites",
            R.id.chipSuki to "Suki",
            R.id.chipSystem to "System"
        )
        
        for ((id, _) in chips) {
            val chip = findViewById<TextView>(id) ?: continue
            if (id == selectedChipId) {
                chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.presyo_orange))
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.edittext_hint))
                chip.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
        
        animateTabIndicator(selectedChipId)
        applyTabFilter(getSelectedTabName())
    }

    private fun getSelectedTabName(): String {
        return when (activeTabId) {
            R.id.chipAll -> "All"
            R.id.chipJoin -> "Join Requests"
            R.id.chipInvites -> "Store Invites"
            R.id.chipSuki -> "Suki"
            R.id.chipSystem -> "System"
            else -> "All"
        }
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.stay,
                R.anim.slide_out_up
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.stay, R.anim.slide_out_up)
        }
    }
}
