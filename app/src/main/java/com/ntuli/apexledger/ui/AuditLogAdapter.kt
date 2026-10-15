package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class AuditLogAdapter(private var items: List<AuditLogEntry>) :
    RecyclerView.Adapter<AuditLogAdapter.AuditViewHolder>() {

    class AuditViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUserType: TextView = view.findViewById(R.id.tvUserTypeBadge)
        val tvAction: TextView = view.findViewById(R.id.tvAuditAction)
        val tvTimestamp: TextView = view.findViewById(R.id.tvAuditTimestamp)
        val tvDetails: TextView = view.findViewById(R.id.tvAuditDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuditViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audit_log_row, parent, false)
        return AuditViewHolder(view)
    }

    override fun onBindViewHolder(holder: AuditViewHolder, position: Int) {
        val entry = items[position]
        holder.tvUserType.text = entry.userType
        holder.tvAction.text = entry.action
        holder.tvTimestamp.text = entry.timestampLabel
        holder.tvDetails.text = entry.details.ifEmpty { "—" }

        val failed = entry.action == "Failed Login"
        val ctx = holder.itemView.context
        holder.tvAction.setTextColor(
            androidx.core.content.ContextCompat.getColor(ctx, if (failed) R.color.danger_dark else R.color.navy)
        )
        if (failed) {
            holder.tvUserType.setBackgroundResource(R.drawable.badge_status_overdue)
            holder.tvUserType.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.danger_dark))
        } else {
            holder.tvUserType.setBackgroundResource(R.drawable.bg_pill_blue)
            holder.tvUserType.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.primary))
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<AuditLogEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}