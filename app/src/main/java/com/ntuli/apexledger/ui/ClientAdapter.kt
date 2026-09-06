package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class ClientAdapter(private var items: List<ClientListItem>) :
    RecyclerView.Adapter<ClientAdapter.ClientViewHolder>() {

    class ClientViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvClientName)
        val tvEmail: TextView = view.findViewById(R.id.tvClientEmail)
        val tvAccountant: TextView = view.findViewById(R.id.tvAssignedAccountant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_client_row, parent, false)
        return ClientViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        val client = items[position]
        holder.tvName.text = client.fullName
        holder.tvEmail.text = client.email

        if (client.assignedAccountantName.isEmpty()) {
            holder.tvAccountant.text = "Unassigned"
            holder.tvAccountant.setBackgroundResource(R.drawable.badge_status_overdue)
        } else {
            holder.tvAccountant.text = client.assignedAccountantName
            holder.tvAccountant.setBackgroundResource(R.drawable.badge_status_completed)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ClientListItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}