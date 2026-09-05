package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class StaffTicketAdapter(
    private var items: List<Ticket>,
    private val statusOptions: List<String>,
    private val onStatusChanged: (Ticket, String) -> Unit,
    private val onSetDueDate: (Ticket) -> Unit
) : RecyclerView.Adapter<StaffTicketAdapter.StaffTicketViewHolder>() {

    inner class StaffTicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvTicketNumber)
        val tvClient: TextView = view.findViewById(R.id.tvClientName)
        val tvService: TextView = view.findViewById(R.id.tvServiceType)
        val tvDueDate: TextView = view.findViewById(R.id.tvDueDate)
        val btnSetDueDate: TextView = view.findViewById(R.id.btnSetDueDate)
        val tvPriority: TextView = view.findViewById(R.id.tvPriorityBadge)
        val spinnerStatus: Spinner = view.findViewById(R.id.spinnerStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffTicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket_staff_row, parent, false)
        return StaffTicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: StaffTicketViewHolder, position: Int) {
        val ticket = items[position]
        holder.tvNumber.text = ticket.ticketNumber
        holder.tvClient.text = ticket.clientName.ifEmpty { "Unknown client" }
        holder.tvService.text = ticket.serviceType.replace("_", " ").replaceFirstChar { it.uppercase() }
        holder.tvDueDate.text = if (ticket.dueDate.isNotEmpty()) "Due ${ticket.dueDate}" else "No due date set"
        if (com.ntuli.apexledger.util.DateUtils.isOverdue(ticket.dueDate, ticket.status)) {
            holder.tvDueDate.text = "Overdue — was due ${ticket.dueDate}"
            holder.tvDueDate.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.danger_dark))
        } else {
            holder.tvDueDate.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.gray_400))
        }

        holder.tvPriority.text = ticket.priority.replaceFirstChar { it.uppercase() }

        holder.btnSetDueDate.setOnClickListener { onSetDueDate(ticket) }

        val priorityBadge = when (ticket.priority) {
            "high" -> R.drawable.badge_status_overdue
            "low" -> R.drawable.badge_status_completed
            else -> R.drawable.badge_status_pending
        }
        holder.tvPriority.setBackgroundResource(priorityBadge)

        val spinnerAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_dropdown_item,
            statusOptions.map { it.replace("_", " ").replaceFirstChar { c -> c.uppercase() } }
        )
        holder.spinnerStatus.adapter = spinnerAdapter

        val currentIndex = statusOptions.indexOf(ticket.status).let { if (it >= 0) it else 0 }
        holder.spinnerStatus.setSelection(currentIndex, false)

        holder.spinnerStatus.onItemSelectedListener = null
        holder.spinnerStatus.post {
            holder.spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val newStatus = statusOptions[pos]
                    if (newStatus != ticket.status) {
                        onStatusChanged(ticket, newStatus)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Ticket>) {
        items = newItems
        notifyDataSetChanged()
    }
}