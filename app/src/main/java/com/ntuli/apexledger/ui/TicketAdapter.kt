package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R
import com.ntuli.apexledger.util.Badges
import com.ntuli.apexledger.util.DateUtils
import com.ntuli.apexledger.util.Labels

class TicketAdapter(private var items: List<Ticket>) :
    RecyclerView.Adapter<TicketAdapter.TicketViewHolder>() {

    class TicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvTicketNumber)
        val tvService: TextView = view.findViewById(R.id.tvTicketService)
        val tvMeta: TextView = view.findViewById(R.id.tvTicketMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvPriority: TextView = view.findViewById(R.id.tvPriorityBadge)
        val tvDue: TextView = view.findViewById(R.id.tvDueText)
        val tvOverdue: TextView = view.findViewById(R.id.tvOverdueChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket_row, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = items[position]
        holder.tvNumber.text = ticket.ticketNumber
        holder.tvService.text = Labels.service(ticket.serviceType)

        holder.tvMeta.text = ticket.clientName
        holder.tvMeta.visibility = if (ticket.clientName.isEmpty()) View.GONE else View.VISIBLE

        holder.tvStatus.text = Labels.status(ticket.status)
        holder.tvStatus.setBackgroundResource(Badges.status(ticket.status))

        holder.tvPriority.text = Labels.priority(ticket.priority)
        holder.tvPriority.setBackgroundResource(Badges.priority(ticket.priority))

        holder.tvDue.text = if (ticket.dueDate.isNotEmpty()) DateUtils.display(ticket.dueDate) else "No due date"
        holder.tvOverdue.visibility =
            if (DateUtils.isOverdue(ticket.dueDate, ticket.status)) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Ticket>) {
        items = newItems
        notifyDataSetChanged()
    }
}
