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
    private val onStatusChanged: (Ticket, String) -> Unit
) : RecyclerView.Adapter<StaffTicketAdapter.StaffTicketViewHolder>() {

    inner class StaffTicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvTicketNumber)
        val tvClient: TextView = view.findViewById(R.id.tvClientName)
        val tvService: TextView = view.findViewById(R.id.tvServiceType)
        val tvDueDate: TextView = view.findViewById(R.id.tvDueDate)
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
        holder.tvPriority.text = ticket.priority.replaceFirstChar { it.uppercase() }

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

        // Avoid firing the listener on initial bind/setSelection by tracking position
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