package com.project.presyohan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.presyohan.R
import com.project.presyohan.Notification
import com.project.presyohan.SupabaseProvider

class NotificationAdapter(
    private val notifications: List<Notification>,
    private val onAccept: (Notification) -> Unit = {},
    private val onReject: (Notification) -> Unit = {},
    private val onViewStore: (Notification) -> Unit = {}
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.notification_card, parent, false)
        return NotificationViewHolder(view)
    }

    override fun getItemCount(): Int = notifications.size

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification, onAccept, onReject, onViewStore)
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textType: TextView = itemView.findViewById(R.id.textType)
        private val textStatus: TextView = itemView.findViewById(R.id.textStatus)
        private val textDeclined: TextView = itemView.findViewById(R.id.textDeclined)
        private val textTimestamp: TextView = itemView.findViewById(R.id.textTimestamp)
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val layoutActions: View = itemView.findViewById(R.id.layoutActions)
        private val btnAccept: TextView = itemView.findViewById(R.id.btnAccept)
        private val btnReject: TextView = itemView.findViewById(R.id.btnReject)
        private val textViewStore: TextView = itemView.findViewById(R.id.textViewStore)
        private val dividerTypeStatus: View = itemView.findViewById(R.id.dividerTypeStatus)
        private val dotSeparator: TextView = itemView.findViewById(R.id.dotSeparator)

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
                else -> java.text.SimpleDateFormat("MM/dd/yy").format(java.util.Date(timestampMillis))
            }
        }

        fun bind(
            notification: Notification,
            onAccept: (Notification) -> Unit,
            onReject: (Notification) -> Unit,
            onViewStore: (Notification) -> Unit
        ) {
            textType.text = notification.type
            textTimestamp.text = getFriendlyTimeString(notification.timestamp)
            textMessage.text = notification.message

            // Reset visibility
            textStatus.visibility = View.GONE
            textDeclined.visibility = View.GONE
            layoutActions.visibility = View.GONE
            textViewStore.visibility = View.GONE
            dividerTypeStatus.visibility = View.GONE
            dotSeparator.visibility = View.GONE

            val currentUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id

            when (notification.status) {
                "Pending" -> {
                    // Only show Accept/Reject for join requests if current user is the owner
                    val isJoinRequest = notification.type == "Join Request"
                    val isOwner = currentUserId != null && notification.senderId != currentUserId
                    if (isJoinRequest && isOwner) {
                        layoutActions.visibility = View.VISIBLE
                        btnAccept.setOnClickListener { onAccept(notification) }
                        btnReject.setOnClickListener { onReject(notification) }
                    } else if (!isJoinRequest) {
                        layoutActions.visibility = View.VISIBLE
                        btnAccept.setOnClickListener { onAccept(notification) }
                        btnReject.setOnClickListener { onReject(notification) }
                    } else {
                        layoutActions.visibility = View.GONE
                    }
                    dividerTypeStatus.visibility = View.GONE
                    textStatus.visibility = View.GONE
                    textDeclined.visibility = View.GONE
                    dotSeparator.visibility = View.VISIBLE
                }
                "Accepted" -> {
                    textStatus.visibility = View.VISIBLE
                    textStatus.text = "Accepted"
                    textStatus.setTextColor(itemView.context.getColor(R.color.presyo_teal))
                    // Only show 'View store' if current user is NOT the sender
                    if (currentUserId != notification.senderId) {
                        textViewStore.visibility = View.VISIBLE
                        textViewStore.setOnClickListener { onViewStore(notification) }
                    }
                    dividerTypeStatus.visibility = View.VISIBLE
                    dotSeparator.visibility = View.VISIBLE
                }
                "Declined" -> {
                    textDeclined.visibility = View.VISIBLE
                    textDeclined.text = "Declined"
                    textDeclined.setTextColor(itemView.context.getColor(R.color.presyo_orange))
                    dividerTypeStatus.visibility = View.VISIBLE
                    dotSeparator.visibility = View.VISIBLE
                }
            }
        }
    }
}