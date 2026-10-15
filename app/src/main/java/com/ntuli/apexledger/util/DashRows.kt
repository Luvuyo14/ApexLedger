package com.ntuli.apexledger.util

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ntuli.apexledger.R

/** Small helpers for the extra dashboard cards (Coming Up, Action Needed, Due This Week ...). */
object DashRows {

    /** Prepares a card_dash_section include: sets the title, empties it and returns the row container. */
    fun section(card: View, title: String, titleColorRes: Int = R.color.navy): LinearLayout {
        val tv = card.findViewById<TextView>(R.id.tvSectionTitle)
        tv.text = title
        tv.setTextColor(ContextCompat.getColor(card.context, titleColorRes))
        val container = card.findViewById<LinearLayout>(R.id.containerSection)
        container.removeAllViews()
        return container
    }

    fun addRow(
        container: LinearLayout,
        title: String,
        sub: String,
        action: String? = null,
        actionBgRes: Int = R.drawable.bg_pill_blue,
        actionColorRes: Int = R.color.primary,
        onClick: (() -> Unit)? = null
    ) {
        val row = LayoutInflater.from(container.context).inflate(R.layout.item_dash_row, container, false)
        row.findViewById<TextView>(R.id.tvRowTitle).text = title
        val tvSub = row.findViewById<TextView>(R.id.tvRowSub)
        tvSub.text = sub
        tvSub.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        val tvAction = row.findViewById<TextView>(R.id.tvRowAction)
        if (action != null) {
            tvAction.text = action
            tvAction.setBackgroundResource(actionBgRes)
            tvAction.setTextColor(ContextCompat.getColor(container.context, actionColorRes))
            tvAction.visibility = View.VISIBLE
            if (onClick != null) tvAction.setOnClickListener { onClick() }
        }
        container.addView(row)
    }

    /** "Due in 3 days" / "Due today" / "Overdue by 5 days" with a matching colour. */
    fun dueChip(dueDate: String): Triple<String, Int, Int>? {
        val days = DateUtils.daysUntil(dueDate) ?: return null
        return when {
            days < 0 -> Triple("Overdue by ${-days} day${if (-days == 1L) "" else "s"}", R.drawable.badge_status_overdue, R.color.danger_dark)
            days == 0L -> Triple("Due today", R.drawable.badge_status_pending, R.color.navy)
            days == 1L -> Triple("Due tomorrow", R.drawable.badge_status_pending, R.color.navy)
            days <= 3L -> Triple("Due in $days days", R.drawable.badge_status_pending, R.color.navy)
            else -> Triple("Due in $days days", R.drawable.badge_status_open, R.color.navy)
        }
    }
}
