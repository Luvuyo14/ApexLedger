package com.ntuli.apexledger.util

import android.app.Activity
import android.app.AlertDialog
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object ChatActions {
    /**
     * "Delete" removes the chats from MY list only. The other person keeps their copy and the
     * conversation comes back for both people if either of them sends a new message.
     */
    fun confirmDelete(activity: Activity, db: FirebaseFirestore, uid: String, ids: List<String>, onDone: () -> Unit) {
        if (ids.isEmpty()) {
            Feedback.show(activity, "Please select the chats you want to delete")
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(if (ids.size == 1) "Delete this chat?" else "Delete ${ids.size} chats?")
            .setMessage(
                "They will be removed from your list. The other person keeps their copy, and a chat " +
                    "comes back if either of you sends a new message."
            )
            .setPositiveButton("Delete") { _, _ ->
                var remaining = (ids.size + 499) / 500
                var failed = false
                ids.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { id ->
                        batch.update(db.collection("threads").document(id), "hiddenBy", FieldValue.arrayUnion(uid))
                    }
                    batch.commit().addOnCompleteListener { task ->
                        if (!task.isSuccessful) failed = true
                        remaining--
                        if (remaining == 0) {
                            Feedback.show(
                                activity,
                                if (failed) "Failed to delete some chats. Check your connection and try again."
                                else if (ids.size == 1) "Chat deleted" else "${ids.size} chats deleted"
                            )
                            onDone()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
