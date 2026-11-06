package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

enum class NotificationStatus { UNREAD, READ }

class Notification : Identifiable() {
    var recipientId: String = ""
    var title: String = ""
    var message: String = ""
    var status: NotificationStatus = NotificationStatus.UNREAD
    var readAt: Long? = null
    var createdAt: Long = System.currentTimeMillis()
}
