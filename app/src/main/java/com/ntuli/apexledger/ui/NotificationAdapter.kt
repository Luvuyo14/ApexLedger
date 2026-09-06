package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class NotificationAdapter(
    private var items: List<AppNotification>,
    private val onClick: (AppNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.unreadDot)
        val tvTitle: TextView = view.findViewById(R.id.tvNotificationTitle)
        val tvBody: TextView = view.findViewById(R.id.tvNotificationBody)
        val tvTime: TextView = view.findViewById(R.id.tvNotificationTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_row, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = items[position]
        holder.tvTitle.text = notification.title
        holder.tvBody.text = notification.body
        holder.tvTime.text = notification.timestampLabel
        holder.dot.visibility = if (notification.isRead) View.INVISIBLE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(notification) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<AppNotification>) {
        items = newItems
        notifyDataSetChanged()
    }
}