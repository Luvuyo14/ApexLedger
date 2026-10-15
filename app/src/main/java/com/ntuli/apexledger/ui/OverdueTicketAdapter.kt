package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R
import com.ntuli.apexledger.util.DateUtils

class OverdueTicketAdapter(private var items: List<Ticket>) :
    RecyclerView.Adapter<OverdueTicketAdapter.OverdueViewHolder>() {

    class OverdueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvTicketNumber)
        val tvService: TextView = view.findViewById(R.id.tvTicketService)
        val tvMeta: TextView = view.findViewById(R.id.tvTicketMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvPriority: TextView = view.findViewById(R.id.tvPriorityBadge)
        val tvDue: TextView = view.findViewById(R.id.tvDueText)
        val tvChip: TextView = view.findViewById(R.id.tvOverdueChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OverdueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket_row, parent, false)
        return OverdueViewHolder(view)
    }

    override fun onBindViewHolder(holder: OverdueViewHolder, position: Int) {
        val ticket = items[position]
        holder.tvNumber.text = ticket.ticketNumber
        holder.tvService.text = ticket.clientName.ifEmpty { "Unknown client" }
        holder.tvMeta.text = if (ticket.accountantName.isNotEmpty()) "Accountant: ${ticket.accountantName}" else ""
        holder.tvMeta.visibility = if (ticket.accountantName.isEmpty()) View.GONE else View.VISIBLE

        // Red "96d Overdue" badge, as on the website
        holder.tvStatus.text = "${DateUtils.daysOverdue(ticket.dueDate)}d Overdue"
        holder.tvStatus.setBackgroundResource(R.drawable.badge_status_overdue)

        holder.tvPriority.visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.tvCreated).visibility = View.GONE
        holder.tvChip.visibility = View.GONE
        holder.tvDue.text = "Due ${DateUtils.display(ticket.dueDate)}"
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Ticket>) {
        items = newItems
        notifyDataSetChanged()
    }
}
