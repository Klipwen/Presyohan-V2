package com.presyohan.app

data class Notification(
    val type: String, // e.g., "Join Request", "Store Invitation"
    val status: String, // "Accepted", "Declined", "Pending"
    val sender: String, // e.g., "Caliph Juen"
    val senderId: String?, // sender's UID
    val storeName: String?, // e.g., "QSOS"
    val role: String?, // invited role (manager or sales staff)
    val timestamp: Long, // epoch millis
    val message: String // Description/message
)