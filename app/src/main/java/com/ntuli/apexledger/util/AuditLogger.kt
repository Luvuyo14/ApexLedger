package com.ntuli.apexledger.util

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Lightweight helper for writing audit trail events to Firestore.
 * Call AuditLogger.logEvent(...) from any Activity after a significant
 * action (login, logout, ticket/document changes, etc.) to build up
 * the Admin Panel's Audit Log history.
 */
object AuditLogger {
    fun logEvent(userType: String, action: String, details: String = "") {
        val entry = hashMapOf(
            "userType" to userType,
            "action" to action,
            "details" to details,
            "timestamp" to Timestamp.now()
        )
        FirebaseFirestore.getInstance().collection("audit_log").add(entry)
    }
}