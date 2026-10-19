package com.ntuli.apexledger.util

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Loads the tickets / conversations a staff member should see:
 *  - admins and managers: everything
 *  - accountants: items routed to them (accountantId) plus older items from their assigned clients
 */
object StaffData {
    class Result(
        val docs: List<DocumentSnapshot>,
        val clientNames: Map<String, String>,
        val assignedClientIds: List<String>,
        val role: String
    )

    fun loadTickets(db: FirebaseFirestore, uid: String, onDone: (Result) -> Unit) = load(db, uid, "tickets", onDone)
    fun loadThreads(db: FirebaseFirestore, uid: String, onDone: (Result) -> Unit) = load(db, uid, "threads", onDone)
    fun loadDocuments(db: FirebaseFirestore, uid: String, onDone: (Result) -> Unit) = load(db, uid, "documents", onDone)

    private fun load(db: FirebaseFirestore, uid: String, collection: String, onDone: (Result) -> Unit) {
        db.collection("users").document(uid).get().addOnCompleteListener { rTask ->
            val role = (if (rTask.isSuccessful) rTask.result?.getString("role") else null) ?: "accountant"
            db.collection("users").get().addOnCompleteListener { cTask ->
                val everyone = if (cTask.isSuccessful) cTask.result.documents else emptyList()
                val clients = everyone.filter { it.getString("role") == "client" }
                // names for clients AND staff, so conversations with staff show a proper name
                val names = everyone.associate { it.id to (it.getString("fullName") ?: "Unknown") }
                val assigned = clients.filter { it.getString("assignedAccountantId") == uid }.map { it.id }

                if (role == "admin" || role == "manager") {
                    db.collection(collection).get().addOnCompleteListener { t ->
                        onDone(Result(if (t.isSuccessful) t.result.documents else emptyList(), names, assigned, role))
                    }
                    return@addOnCompleteListener
                }

                val merged = LinkedHashMap<String, DocumentSnapshot>()
                val batches = assigned.chunked(10)
                // +1 routed to me, +1 sent to me directly (an admin messaging a staff member)
                var pending = 2 + batches.size
                fun finished() {
                    pending--
                    if (pending == 0) onDone(Result(merged.values.toList(), names, assigned, role))
                }

                db.collection(collection).whereEqualTo("accountantId", uid).get().addOnCompleteListener { t ->
                    if (t.isSuccessful) t.result.documents.forEach { merged[it.id] = it }
                    finished()
                }
                db.collection(collection).whereEqualTo("clientId", uid).get().addOnCompleteListener { t ->
                    if (t.isSuccessful) t.result.documents.forEach { merged[it.id] = it }
                    finished()
                }
                // Older items (created before routing existed) belong to the client's assigned accountant
                for (batch in batches) {
                    db.collection(collection).whereIn("clientId", batch).get().addOnCompleteListener { t ->
                        if (t.isSuccessful) {
                            t.result.documents
                                .filter { it.getString("accountantId").isNullOrEmpty() }
                                .forEach { merged.putIfAbsent(it.id, it) }
                        }
                        finished()
                    }
                }
            }
        }
    }
}
