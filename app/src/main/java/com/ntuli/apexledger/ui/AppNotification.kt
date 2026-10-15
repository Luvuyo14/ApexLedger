package com.ntuli.apexledger.ui

data class AppNotification(
    val notificationId: String = "",
    val type: String = "general",
    val title: String = "",
    val body: String = "",
    val isRead: Boolean = false,
    val timestampLabel: String = "",
    val targetId: String = ""
)