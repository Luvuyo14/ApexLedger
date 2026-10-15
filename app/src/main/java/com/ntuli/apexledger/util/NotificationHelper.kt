package com.ntuli.apexledger.util

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Writes in-app notification documents to Firestore. Call this from
 * anywhere a notification-worthy event happens (ticket status change,
 * document review, new message, etc.) targeting the user who should
 * see it.
 */
object NotificationHelper {
    fun notify(recipientUid: String, type: String, title: String, body: String, targetId: String = "") {
        val notification = hashMapOf(
            "recipientUid" to recipientUid,
            "type" to type,
            "title" to title,
            "body" to body,
            "targetId" to targetId,
            "isRead" to false,
            "createdAt" to Timestamp.now()
        )
        FirebaseFirestore.getInstance().collection("notifications").add(notification)
    }
}