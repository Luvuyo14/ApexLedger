package com.ntuli.apexledger.ui

data class Ticket(
    val ticketId: String = "",
    val ticketNumber: String = "",
    val serviceType: String = "",
    val status: String = "open",
    val priority: String = "medium",
    val description: String = "",
    val clientName: String = "",
    val dueDate: String = "",
    val internalNotes: String = "",
    val accountantName: String = "",
    val clientId: String = "",
    val createdLabel: String = ""
)
