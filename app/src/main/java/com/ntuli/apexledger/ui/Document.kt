package com.ntuli.apexledger.ui

data class Document(
    val documentId: String = "",
    val fileName: String = "",
    val documentCategory: String = "other",
    val reviewStatus: String = "pending",
    val clientName: String = "",
    val version: Int = 1,
    val previousVersionIds: List<String> = emptyList(),
    val dateLabel: String = "",
    val accountantName: String = ""
)
