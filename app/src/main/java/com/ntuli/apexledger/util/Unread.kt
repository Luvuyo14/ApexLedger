package com.ntuli.apexledger.util

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ntuli.apexledger.ui.MessageThread

/** Marks threads whose latest message was sent by someone else and not yet read by [uid]. */
object Unread {
    fun mark(db: FirebaseFirestore, uid: String, threads: List<MessageThread>, onDone: (List<MessageThread>) -> Unit) {
        if (threads.isEmpty()) { onDone(threads); return }
        val result = threads.toMutableList()
        var remaining = threads.size
        threads.forEachIndexed { i, t ->
            db.collection("threads").document(t.threadId).collection("messages")
                .orderBy("sentTimestamp", Query.Direction.DESCENDING).limit(1).get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val m = task.result.documents.firstOrNull()
                        if (m != null) {
                            val readBy = (m.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            if (m.getString("senderId") != uid && uid !in readBy) result[i] = t.copy(hasUnread = true)
                        }
                    }
                    remaining--
                    if (remaining == 0) onDone(result)
                }
        }
    }
}
