package com.ntuli.apexledger.ui

data class AuditLogEntry(
    val userType: String = "User",
    val action: String = "",
    val details: String = "",
    val timestampLabel: String = ""
)