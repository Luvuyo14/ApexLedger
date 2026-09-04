package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class StaffAdapter(private var items: List<StaffMember>) :
    RecyclerView.Adapter<StaffAdapter.StaffViewHolder>() {

    class StaffViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStaffName)
        val tvEmail: TextView = view.findViewById(R.id.tvStaffEmail)
        val tvRole: TextView = view.findViewById(R.id.tvStaffRole)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_staff_row, parent, false)
        return StaffViewHolder(view)
    }

    override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
        val staff = items[position]
        holder.tvName.text = staff.fullName
        holder.tvEmail.text = staff.email
        holder.tvRole.text = staff.role.replaceFirstChar { it.uppercase() }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<StaffMember>) {
        items = newItems
        notifyDataSetChanged()
    }
}