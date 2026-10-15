package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

/**
 * Notification list. Press and hold a notification to start choosing which ones to delete;
 * while choosing, tapping a row ticks or unticks it.
 */
class NotificationAdapter(
    private var items: List<AppNotification>,
    private val onClick: (AppNotification) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    var selectionMode = false
        private set
    private val selectedIds = mutableSetOf<String>()

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.unreadDot)
        val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
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
        holder.checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = notification.notificationId in selectedIds

        holder.itemView.setOnClickListener {
            if (selectionMode) toggle(notification) else onClick(notification)
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) startSelection()
            toggle(notification, forceOn = true)
            true
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<AppNotification>) {
        items = newItems
        // forget ticks for notifications that no longer exist
        selectedIds.retainAll(newItems.map { it.notificationId }.toSet())
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun startSelection() {
        selectionMode = true
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun cancelSelection() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(items.map { it.notificationId })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun selectedList(): List<String> = selectedIds.toList()

    fun totalCount() = items.size

    private fun toggle(n: AppNotification, forceOn: Boolean = false) {
        if (forceOn || n.notificationId !in selectedIds) selectedIds.add(n.notificationId)
        else selectedIds.remove(n.notificationId)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }
}
