package com.ntuli.apexledger.ui

data class PermissionRow(
    val permission: String,
    val admin: Boolean,
    val manager: Boolean,
    val accountant: Boolean,
    val client: Boolean
)