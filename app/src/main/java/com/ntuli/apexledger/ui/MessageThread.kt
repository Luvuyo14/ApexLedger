package com.ntuli.apexledger.ui

data class MessageThread(
    val threadId: String = "",
    val clientId: String = "",
    val subject: String = "",
    val lastMessagePreview: String = "",
    val isUrgent: Boolean = false,
    val clientName: String = "",
    val hasUnread: Boolean = false,
    val accountantName: String = "",
    val serviceType: String = ""
)
