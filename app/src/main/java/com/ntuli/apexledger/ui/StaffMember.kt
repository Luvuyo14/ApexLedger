package com.ntuli.apexledger.ui

data class StaffMember(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "accountant",
    val department: String = "",
    val active: Boolean = true
)
