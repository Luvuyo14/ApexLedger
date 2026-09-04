package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class PermissionAdapter(private val items: List<PermissionRow>) :
    RecyclerView.Adapter<PermissionAdapter.PermissionViewHolder>() {

    class PermissionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPermissionName)
        val tvAdmin: TextView = view.findViewById(R.id.tvAdminMark)
        val tvManager: TextView = view.findViewById(R.id.tvManagerMark)
        val tvAccountant: TextView = view.findViewById(R.id.tvAccountantMark)
        val tvClient: TextView = view.findViewById(R.id.tvClientMark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_permission_row, parent, false)
        return PermissionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        val row = items[position]
        holder.tvName.text = row.permission
        setMark(holder.tvAdmin, row.admin)
        setMark(holder.tvManager, row.manager)
        setMark(holder.tvAccountant, row.accountant)
        setMark(holder.tvClient, row.client)
    }

    private fun setMark(view: TextView, allowed: Boolean) {
        if (allowed) {
            view.text = "✓"
            view.setBackgroundResource(R.drawable.badge_status_completed)
        } else {
            view.text = "✕"
            view.setBackgroundResource(R.drawable.badge_status_overdue)
        }
    }

    override fun getItemCount() = items.size
}