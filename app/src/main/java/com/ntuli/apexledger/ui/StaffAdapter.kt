package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R
import com.ntuli.apexledger.util.Labels

class StaffAdapter(
    private var items: List<StaffMember>,
    private val currentUid: String = "",
    private val onToggleActive: (StaffMember) -> Unit = {}
) : RecyclerView.Adapter<StaffAdapter.StaffViewHolder>() {

    class StaffViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStaffName)
        val tvEmail: TextView = view.findViewById(R.id.tvStaffEmail)
        val tvRole: TextView = view.findViewById(R.id.tvStaffRole)
        val tvDepartment: TextView = view.findViewById(R.id.tvStaffDepartment)
        val tvStatus: TextView = view.findViewById(R.id.tvStaffStatus)
        val btnToggle: TextView = view.findViewById(R.id.btnToggleActive)
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
        holder.tvRole.text = Labels.title(staff.role)
        holder.tvDepartment.text = staff.department.substringBefore("(").trim().ifEmpty { "—" }

        if (staff.active) {
            holder.tvStatus.text = "Active"
            holder.tvStatus.setBackgroundResource(R.drawable.badge_status_completed)
            holder.btnToggle.text = "Deactivate"
        } else {
            holder.tvStatus.text = "Inactive"
            holder.tvStatus.setBackgroundResource(R.drawable.badge_status_overdue)
            holder.btnToggle.text = "Activate"
        }

        // An admin cannot lock themselves out
        val isSelf = staff.uid == currentUid
        holder.btnToggle.visibility = if (isSelf) View.GONE else View.VISIBLE
        holder.btnToggle.setOnClickListener { onToggleActive(staff) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<StaffMember>) {
        items = newItems
        notifyDataSetChanged()
    }
}
