package com.ntuli.apexledger.ui

data class ChatMessage(
    val senderId: String = "",
    val messageBody: String = "",
    val isUrgent: Boolean = false
)