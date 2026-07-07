package com.presyohan.app

data class Notification(
    val id: String,
    val type: String, // e.g., "Join Request", "Store Invitation", "Suki Request"
    val status: String, // "Accepted", "Declined", "Pending", "Canceled"
    val sender: String, // e.g., "Caliph Juen"
    val senderId: String?, // sender's UID
    val storeName: String?, // e.g., "QSOS"
    val role: String?, // invited role
    val timestamp: Long, // epoch millis
    val message: String, // Description/message
    val isNew: Boolean = true, // To toggle the orange dot
    val storeId: String? = null
)