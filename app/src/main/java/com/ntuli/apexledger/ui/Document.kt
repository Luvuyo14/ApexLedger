package com.ntuli.apexledger.ui

data class Document(
    val documentId: String = "",
    val fileName: String = "",
    val documentCategory: String = "other",
    val reviewStatus: String = "pending",
    val clientName: String = ""
)