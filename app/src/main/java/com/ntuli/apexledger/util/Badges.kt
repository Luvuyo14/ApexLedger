package com.ntuli.apexledger.util

import com.ntuli.apexledger.R

object Badges {
    fun status(status: String): Int = when (status) {
        "completed" -> R.drawable.badge_status_completed
        "in_progress" -> R.drawable.badge_status_pending
        "awaiting_documents" -> R.drawable.badge_status_purple
        "under_review" -> R.drawable.badge_status_pending
        "cancelled" -> R.drawable.badge_status_cancelled
        else -> R.drawable.badge_status_open
    }

    fun priority(priority: String): Int = when (priority) {
        "urgent" -> R.drawable.badge_status_overdue
        "high" -> R.drawable.badge_priority_high
        "low" -> R.drawable.badge_status_completed
        else -> R.drawable.badge_status_pending
    }
}
