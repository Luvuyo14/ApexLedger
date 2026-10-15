package com.ntuli.apexledger.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {
    private val stored get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * A ticket is overdue if it has a due date in the past and has not reached
     * a terminal state. Dates are stored as "yyyy-MM-dd".
     */
    fun isOverdue(dueDate: String, status: String): Boolean {
        if (dueDate.isEmpty()) return false
        if (status == "completed" || status == "cancelled") return false
        val today = stored.format(Date())
        return dueDate < today
    }

    /** "2026-06-15" -> "15 Jun 2026" (same as the website). */
    fun display(dueDate: String): String {
        if (dueDate.isEmpty()) return ""
        return try {
            val d = stored.parse(dueDate) ?: return dueDate
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d)
        } catch (e: Exception) {
            dueDate
        }
    }

    fun daysOverdue(dueDate: String): Long {
        return try {
            val due = stored.parse(dueDate) ?: return 0
            val diff = Date().time - due.time
            maxOf(0L, TimeUnit.MILLISECONDS.toDays(diff))
        } catch (e: Exception) {
            0
        }
    }

    /** Whole days from today to the due date (negative = already past). Null if no date. */
    fun daysUntil(dueDate: String): Long? {
        if (dueDate.isEmpty()) return null
        return try {
            val due = stored.parse(dueDate) ?: return null
            val today = stored.parse(stored.format(Date())) ?: return null
            TimeUnit.MILLISECONDS.toDays(due.time - today.time)
        } catch (e: Exception) {
            null
        }
    }

    /** The day a record was created, e.g. "14 Oct 2026" (empty if unknown). */
    fun created(ts: com.google.firebase.Timestamp?): String =
        ts?.toDate()?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it) } ?: ""
}
