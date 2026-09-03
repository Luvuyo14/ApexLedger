package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class TicketAdapter(private var items: List<Ticket>) :
    RecyclerView.Adapter<TicketAdapter.TicketViewHolder>() {

    class TicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvTicketNumber)
        val tvService: TextView = view.findViewById(R.id.tvTicketService)
        val tvMeta: TextView = view.findViewById(R.id.tvTicketMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket_row, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = items[position]
        holder.tvNumber.text = ticket.ticketNumber
        holder.tvService.text = ticket.serviceType.replace("_", " ").replaceFirstChar { it.uppercase() }

        val metaParts = mutableListOf<String>()
        if (ticket.clientName.isNotEmpty()) metaParts.add(ticket.clientName)
        if (ticket.dueDate.isNotEmpty()) metaParts.add("Due ${ticket.dueDate}")
        holder.tvMeta.text = metaParts.joinToString(" • ")
        holder.tvMeta.visibility = if (metaParts.isEmpty()) View.GONE else View.VISIBLE

        holder.tvStatus.text = ticket.status.replace("_", " ").replaceFirstChar { it.uppercase() }

        val badgeRes = when (ticket.status) {
            "completed" -> R.drawable.badge_status_completed
            "in_progress", "awaiting_documents", "under_review" -> R.drawable.badge_status_pending
            "cancelled" -> R.drawable.badge_status_overdue
            else -> R.drawable.badge_status_open
        }
        holder.tvStatus.setBackgroundResource(badgeRes)
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Ticket>) {
        items = newItems
        notifyDataSetChanged()
    }
}