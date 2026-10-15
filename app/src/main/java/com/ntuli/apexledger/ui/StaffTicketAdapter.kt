package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R
import com.ntuli.apexledger.util.Badges
import com.ntuli.apexledger.util.DateUtils
import com.ntuli.apexledger.util.Labels

class StaffTicketAdapter(
    private var items: List<Ticket>,
    private val statusOptions: List<String>,
    private val onStatusChanged: (Ticket, String) -> Unit,
    private val onSetDueDate: (Ticket) -> Unit,
    private val onEditNotes: (Ticket) -> Unit = {}
) : RecyclerView.Adapter<StaffTicketAdapter.StaffTicketViewHolder>() {

    inner class StaffTicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvTicketNumber)
        val tvCreated: TextView = view.findViewById(R.id.tvCreated)
        val tvClient: TextView = view.findViewById(R.id.tvClientName)
        val tvService: TextView = view.findViewById(R.id.tvServiceType)
        val tvDueDate: TextView = view.findViewById(R.id.tvDueDate)
        val tvOverdue: TextView = view.findViewById(R.id.tvOverdueChip)
        val btnSetDueDate: TextView = view.findViewById(R.id.btnSetDueDate)
        val btnNotes: TextView = view.findViewById(R.id.btnInternalNotes)
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
        holder.tvCreated.text = if (ticket.createdLabel.isNotEmpty()) "Created ${ticket.createdLabel}" else ""
        holder.tvCreated.visibility = if (ticket.createdLabel.isNotEmpty()) View.VISIBLE else View.GONE
        holder.tvClient.text = ticket.clientName.ifEmpty { "Unknown client" }
        holder.tvService.text = Labels.service(ticket.serviceType)

        holder.tvDueDate.text =
            if (ticket.dueDate.isNotEmpty()) DateUtils.display(ticket.dueDate) else "No due date set"
        val chip = if (ticket.status == "completed" || ticket.status == "cancelled") null
            else com.ntuli.apexledger.util.DashRows.dueChip(ticket.dueDate)
        if (chip == null) {
            holder.tvOverdue.visibility = View.GONE
        } else {
            holder.tvOverdue.text = chip.first
            holder.tvOverdue.setBackgroundResource(chip.second)
            holder.tvOverdue.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, chip.third))
            holder.tvOverdue.visibility = View.VISIBLE
        }

        holder.tvPriority.text = Labels.priority(ticket.priority)
        holder.tvPriority.setBackgroundResource(Badges.priority(ticket.priority))

        holder.btnSetDueDate.setOnClickListener { onSetDueDate(ticket) }
        holder.btnNotes.text = if (ticket.internalNotes.isNotEmpty()) "Internal notes ●" else "Internal notes"
        holder.btnNotes.setOnClickListener { onEditNotes(ticket) }

        val spinnerAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_dropdown_item,
            statusOptions.map { Labels.status(it) }
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
