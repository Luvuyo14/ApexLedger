package com.ntuli.apexledger.util

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Lightweight helper for writing audit trail events to Firestore.
 * Every entry records the signed-in user's ID (actorUid) so the security rules
 * can reject entries written in someone else's name.
 */
object AuditLogger {
    fun logEvent(userType: String, action: String, details: String = "") {
        val entry = hashMapOf(
            "actorUid" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "userType" to userType,
            "action" to action,
            "details" to details.take(500),
            "timestamp" to Timestamp.now()
        )
        FirebaseFirestore.getInstance().collection("audit_log").add(entry)
    }
}