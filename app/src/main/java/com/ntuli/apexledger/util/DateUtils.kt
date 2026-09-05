package com.ntuli.apexledger.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    /**
     * A ticket is considered overdue if it has a due date in the past
     * and hasn't reached a terminal state (completed/cancelled).
     * Dates are stored as "yyyy-MM-dd" strings, which compare correctly
     * with plain string comparison since the format is zero-padded and
     * most-significant-first.
     */
    fun isOverdue(dueDate: String, status: String): Boolean {
        if (dueDate.isEmpty()) return false
        if (status == "completed" || status == "cancelled") return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return dueDate < today
    }
}