package com.ntuli.apexledger.util

import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

/**
 * Generates website-style ticket numbers: TAX-2026-0001, PAY-2026-0002 ...
 * Uses a counter document per prefix+year (counters/ticket_TAX_2026) inside a
 * transaction so numbers stay sequential. If Firestore rules do not allow the
 * counters collection, it falls back to a unique 4-digit suffix so ticket
 * creation never fails.
 */
object TicketNumbers {
    fun generate(db: FirebaseFirestore, serviceType: String, onReady: (String) -> Unit) {
        val prefix = Labels.ticketPrefix(serviceType)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val ref = db.collection("counters").document("ticket_${prefix}_$year")

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val next = (snap.getLong("last") ?: 0L) + 1
            tx.set(ref, mapOf("last" to next))
            next
        }.addOnSuccessListener { n ->
            onReady("$prefix-$year-${n.toString().padStart(4, '0')}")
        }.addOnFailureListener {
            val suffix = (System.currentTimeMillis() % 10000).toString().padStart(4, '0')
            onReady("$prefix-$year-$suffix")
        }
    }
}
