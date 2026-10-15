package com.ntuli.apexledger.util

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Decides which accountant handles a service for a client.
 *  1. the client's own accountant, if that accountant handles this service
 *  2. otherwise the accountant who handled this client's last ticket/conversation for the service
 *  3. otherwise an accountant who handles the service (spread across clients so one person is not overloaded)
 *  4. if nobody handles the service, the client's own accountant (or nobody - an admin assigns later)
 */
object AccountantRouter {
    data class Pick(val id: String, val name: String)

    fun duty(serviceType: String) = when (serviceType) {
        "tax_return" -> "Taxation"
        "payroll" -> "Payroll"
        "bookkeeping" -> "Bookkeeping"
        "advisory" -> "Advisory"
        "audit" -> "Audit"
        else -> ""
    }

    /** [source] is "tickets" or "threads": where to look for the client's previous accountant. */
    fun pick(db: FirebaseFirestore, clientId: String, serviceType: String, source: String, onDone: (Pick?) -> Unit) {
        db.collection("users").document(clientId).get().addOnCompleteListener { cTask ->
            val assigned = (if (cTask.isSuccessful) cTask.result?.getString("assignedAccountantId") else null) ?: ""
            db.collection("users").whereEqualTo("role", "accountant").get().addOnCompleteListener { aTask ->
                val active: List<DocumentSnapshot> =
                    if (aTask.isSuccessful) aTask.result.documents.filter { it.getBoolean("active") != false } else emptyList()
                val keyword = duty(serviceType)
                val matching = active.filter { keyword.isNotEmpty() && (it.getString("department") ?: "").startsWith(keyword) }
                fun pickOf(d: DocumentSnapshot) = Pick(d.id, d.getString("full_name") ?: "Accountant")

                matching.firstOrNull { it.id == assigned }?.let { onDone(pickOf(it)); return@addOnCompleteListener }

                db.collection(source)
                    .whereEqualTo("clientId", clientId)
                    .whereEqualTo("serviceType", serviceType)
                    .get()
                    .addOnCompleteListener { hTask ->
                        val previousId = if (hTask.isSuccessful) {
                            hTask.result.documents
                                .sortedByDescending { it.getTimestamp("createdDate")?.seconds ?: 0L }
                                .mapNotNull { it.getString("accountantId") }
                                .firstOrNull { id -> matching.any { it.id == id } }
                        } else null

                        val chosen: DocumentSnapshot? = matching.firstOrNull { it.id == previousId }
                            ?: matching.sortedBy { it.id }.let { list ->
                                if (list.isEmpty()) null else list[Math.abs(clientId.hashCode()) % list.size]
                            }
                            ?: active.firstOrNull { it.id == assigned }
                        onDone(chosen?.let { pickOf(it) })
                    }
            }
        }
    }
}
