package com.ntuli.apexledger.ui

data class ServiceStatus(
    val name: String,
    val statusLabel: String = "Operational",
    val isHealthy: Boolean = true
)