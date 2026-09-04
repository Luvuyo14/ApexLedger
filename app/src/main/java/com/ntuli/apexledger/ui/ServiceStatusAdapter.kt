package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class ServiceStatusAdapter(private val items: List<ServiceStatus>) :
    RecyclerView.Adapter<ServiceStatusAdapter.ServiceViewHolder>() {

    class ServiceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.statusDot)
        val tvName: TextView = view.findViewById(R.id.tvServiceName)
        val tvLabel: TextView = view.findViewById(R.id.tvServiceStatusLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_status_row, parent, false)
        return ServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = items[position]
        holder.tvName.text = service.name
        holder.tvLabel.text = service.statusLabel

        val badgeRes = if (service.isHealthy) R.drawable.badge_status_completed else R.drawable.badge_status_overdue
        holder.dot.setBackgroundResource(badgeRes)
        holder.tvLabel.setBackgroundResource(badgeRes)
    }

    override fun getItemCount() = items.size
}