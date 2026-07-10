package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R
import com.presyohan.app.Notification
import com.presyohan.app.SupabaseProvider
import io.github.jan.supabase.auth.auth

class NotificationAdapter(
    private val onAccept: (Notification) -> Unit = {},
    private val onReject: (Notification) -> Unit = {},
    private val onCancel: (Notification) -> Unit = {},
    private val onViewStore: (Notification) -> Unit = {}
) : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_card, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = getItem(position)
        holder.bind(notification, onAccept, onReject, onCancel, onViewStore)
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textType: TextView = itemView.findViewById(R.id.textType)
        private val textStatus: TextView = itemView.findViewById(R.id.textStatus)
        private val textTimestamp: TextView = itemView.findViewById(R.id.textTimestamp)
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val layoutActions: View = itemView.findViewById(R.id.layoutActions)
        private val btnAccept: TextView = itemView.findViewById(R.id.btnAccept)
        private val btnReject: TextView = itemView.findViewById(R.id.btnReject)
        private val btnCancel: TextView = itemView.findViewById(R.id.btnCancel)
        private val btnViewStore: TextView = itemView.findViewById(R.id.btnViewStore)
        private val dotSeparator: TextView = itemView.findViewById(R.id.dotSeparator)
        private val viewOrangeDot: View = itemView.findViewById(R.id.viewOrangeDot)
        private val layoutDivider: View = itemView.findViewById(R.id.layoutDivider)
        private val layoutIconContainer: View = itemView.findViewById(R.id.layoutIconContainer)
        private val imgNotificationIcon: android.widget.ImageView = itemView.findViewById(R.id.imgNotificationIcon)

        private fun getFriendlyTimeString(timestampMillis: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestampMillis
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                seconds < 60 -> "now"
                minutes == 1L -> "1 min ago"
                minutes < 60 -> "$minutes mins ago"
                hours == 1L -> "1 hour ago"
                hours < 24 -> "$hours hours ago"
                days == 1L -> "yesterday"
                else -> java.text.SimpleDateFormat("MM/dd/yy", java.util.Locale.US).format(java.util.Date(timestampMillis))
            }
        }

        fun bind(
            notification: Notification,
            onAccept: (Notification) -> Unit,
            onReject: (Notification) -> Unit,
            onCancel: (Notification) -> Unit,
            onViewStore: (Notification) -> Unit
        ) {
            // Map type to a user-friendly title matching the design spec
            textType.text = when (notification.type) {
                "Join Request" -> "Join Request"
                "Store Invitation" -> "Store Invitation"
                "Store Invitation Sent" -> "Store Invitation Sent"
                "Suki Request" -> if (notification.status == "Accepted") "New Suki" else "Suki Request"
                // System notification types — already mapped from DB in parseNotificationInfo
                "Export Complete", "excel_export" -> "Export Complete"
                "Staff Left Store", "member_left" -> "Staff Left Store"
                "Staff Joined Store", "member_joined" -> "Staff Joined Store"
                "Removed Staff", "member_removed" -> "Removed Staff"
                "Role Updated", "role_changed", "role_change" -> "Role Updated"
                "Store Deleted", "store_deleted" -> "Store Deleted"
                "Updated Store Status", "store_visibility_changed" -> "Updated Store Status"
                "Clone Price Complete", "clone_price_complete" -> "Clone Price Complete"
                "Suking Tindahan Connected" -> "Suking Tindahan Connected"
                "You Have Been Removed" -> "You Have Been Removed"
                "You Have Left" -> "You Have Left"
                else -> when {
                    notification.message.contains("left your") || notification.message.contains("left the") || notification.message.contains("has left") -> "Staff Left Store"
                    notification.message.contains("removed") && notification.message.contains("from") -> "Removed Staff"
                    notification.message.contains("deleted") || notification.message.contains("Deleted") -> "Store Deleted"
                    notification.message.contains("Clone Price") || notification.message.contains("pricelist") -> "Clone Price Complete"
                    notification.message.contains("status to public") || notification.message.contains("status to private") -> "Updated Store Status"
                    notification.message.contains("partnered") || notification.message.contains("Suking Tindahan connected") -> "Suking Tindahan Connected"
                    notification.message.contains("promoted") || notification.message.contains("role has been") || notification.message.contains("role changed") -> "Role Updated"
                    notification.message.contains("removed from") || notification.message.contains("no longer") -> "You Have Been Removed"
                    notification.message.contains("You left") -> "You Have Left"
                    notification.message.contains("Excel") || notification.message.contains("exported") -> "Export Complete"
                    notification.type.isNotBlank() -> notification.type
                    else -> "Notification"
                }
            }
            textTimestamp.text = getFriendlyTimeString(notification.timestamp)
            textMessage.text = notification.message

            // Dynamically configure Notification Icon and Background Color matching the notification type
            val iconRes = when (notification.type) {
                "Join Request", "Store Invitation", "Store Invitation Sent", "Staff Joined Store", "Staff Left Store", "Removed Staff", "Role Updated" -> R.drawable.icon_profile
                "Suki Request", "Suking Tindahan Connected" -> R.drawable.icon_store
                "Export Complete", "Clone Price Complete" -> R.drawable.icon_pricelist
                "Store Deleted" -> R.drawable.icon_delete
                else -> R.drawable.icon_notification
            }
            imgNotificationIcon.setImageResource(iconRes)

            val bgTint = "#FFEADB"
            val iconTint = "#FB8500"

            layoutIconContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(bgTint))
            imgNotificationIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(iconTint))

            // Orange dot indicator shows when new/unread, toggles off when seen
            viewOrangeDot.visibility = if (notification.isNew) View.VISIBLE else View.GONE

            // Reset visibility
            textStatus.visibility = View.GONE
            layoutActions.visibility = View.GONE
            btnAccept.visibility = View.GONE
            btnReject.visibility = View.GONE
            btnCancel.visibility = View.GONE
            btnViewStore.visibility = View.GONE
            dotSeparator.visibility = View.VISIBLE
            layoutDivider.visibility = View.GONE

            val currentUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id

            when (notification.status) {
                "Pending" -> {
                    val isSukiRequest = notification.type == "Suki Request"
                    val isJoinRequest = notification.type == "Join Request"

                    if (isSukiRequest) {
                        layoutActions.visibility = View.VISIBLE
                        // Sender is the requester (Flow A)
                        val isRequester = notification.senderId == currentUserId
                        if (isRequester) {
                            btnCancel.visibility = View.VISIBLE
                            btnCancel.text = "Cancel Request"
                            btnCancel.setOnClickListener { onCancel(notification) }
                        } else {
                            // Manager receives request (Flow B)
                            btnAccept.visibility = View.VISIBLE
                            btnReject.visibility = View.VISIBLE
                            btnAccept.setOnClickListener { onAccept(notification) }
                            btnReject.setOnClickListener { onReject(notification) }
                        }
                    } else if (isJoinRequest) {
                        val isRequester = (currentUserId != null && notification.senderId == currentUserId) ||
                                          notification.message.startsWith("Your request") ||
                                          notification.message.startsWith("You requested")
                        layoutActions.visibility = View.VISIBLE
                        if (isRequester) {
                            btnCancel.visibility = View.VISIBLE
                            btnCancel.text = "Cancel Request"
                            btnCancel.setOnClickListener { onCancel(notification) }
                        } else {
                            btnAccept.visibility = View.VISIBLE
                            btnReject.visibility = View.VISIBLE
                            btnAccept.setOnClickListener { onAccept(notification) }
                            btnReject.setOnClickListener { onReject(notification) }
                        }
                    } else if (notification.type == "Store Invitation") {
                        // Store Invitation pending
                        val isOwnerInviter = notification.message.startsWith("You invited")
                        layoutActions.visibility = View.VISIBLE
                        if (isOwnerInviter) {
                            btnCancel.visibility = View.VISIBLE
                            btnCancel.text = "Cancel Invitation"
                            btnCancel.setOnClickListener { onCancel(notification) }
                        } else {
                            btnAccept.visibility = View.VISIBLE
                            btnReject.visibility = View.VISIBLE
                            btnAccept.setOnClickListener { onAccept(notification) }
                            btnReject.setOnClickListener { onReject(notification) }
                        }
                    }
                }
                "Accepted" -> {
                    textStatus.visibility = View.VISIBLE
                    textStatus.text = "Accepted"
                    textStatus.setTextColor(itemView.context.getColor(R.color.presyo_teal))
                }
                "Declined", "Rejected" -> {
                    textStatus.visibility = View.VISIBLE
                    textStatus.text = "Declined"
                    textStatus.setTextColor(itemView.context.getColor(R.color.presyo_orange))
                }
                "Canceled" -> {
                    textStatus.visibility = View.VISIBLE
                    textStatus.text = "Canceled"
                    textStatus.setTextColor(itemView.context.getColor(R.color.presyo_orange))
                }
            }

            // ── Dynamic Link Action Binding ──
            btnViewStore.visibility = View.GONE
            if (notification.type == "excel_export" || notification.message.contains("Excel file") || notification.message.contains("exported")) {
                btnViewStore.visibility = View.VISIBLE
                btnViewStore.text = "Open File >"
                btnViewStore.setTextColor(itemView.context.getColor(R.color.presyo_teal))
                btnViewStore.setOnClickListener { onViewStore(notification) }
            } else if (notification.message.contains("status to public") || notification.message.contains("status to private")) {
                val isOwner = currentUserId != null && notification.senderId != currentUserId
                if (isOwner) {
                    btnViewStore.visibility = View.VISIBLE
                    btnViewStore.text = "Store Settings >"
                    btnViewStore.setTextColor(itemView.context.getColor(R.color.presyo_teal))
                    btnViewStore.setOnClickListener { onViewStore(notification) }
                } else {
                    btnViewStore.visibility = View.VISIBLE
                    btnViewStore.text = "View Store >"
                    btnViewStore.setTextColor(itemView.context.getColor(R.color.presyo_teal))
                    btnViewStore.setOnClickListener { onViewStore(notification) }
                }
            } else if (notification.type == "Suki Request") {
                val isRequester = currentUserId != null && notification.senderId == currentUserId
                val isAccepted = notification.status == "Accepted"
                val shouldShow = isAccepted || !isRequester
                if (shouldShow && !notification.storeId.isNullOrBlank()) {
                    btnViewStore.visibility = View.VISIBLE
                    btnViewStore.text = "View Store >"
                    btnViewStore.setTextColor(itemView.context.getColor(R.color.presyo_teal))
                    btnViewStore.setOnClickListener { onViewStore(notification) }
                }
            } else if (notification.status == "Accepted" || 
                notification.message.contains("connected") || 
                notification.message.contains("partnered") || 
                notification.message.contains("joined") || 
                notification.message.contains("role") ||
                notification.message.contains("promoted")) {
                
                if (!notification.storeId.isNullOrBlank()) {
                    btnViewStore.visibility = View.VISIBLE
                    btnViewStore.text = "View Store >"
                    btnViewStore.setTextColor(itemView.context.getColor(R.color.presyo_teal))
                    btnViewStore.setOnClickListener { onViewStore(notification) }
                }
            }

            // Sync layout divider visibility with layout actions container
            layoutDivider.visibility = layoutActions.visibility
        }
    }
}

class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
    override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
        return oldItem == newItem
    }
}