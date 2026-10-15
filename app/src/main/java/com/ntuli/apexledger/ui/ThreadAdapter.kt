package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class ThreadAdapter(
    private var items: List<MessageThread>,
    private val onSelectionChanged: (Int) -> Unit = {},
    private val onClick: (MessageThread) -> Unit
) : RecyclerView.Adapter<ThreadAdapter.ThreadViewHolder>() {

    var selectionMode = false
        private set
    private val selectedIds = mutableSetOf<String>()

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
        selectedIds.addAll(items.map { it.threadId })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun selectedList(): List<String> = selectedIds.toList()

    private fun toggle(t: MessageThread, forceOn: Boolean = false) {
        if (forceOn || t.threadId !in selectedIds) selectedIds.add(t.threadId) else selectedIds.remove(t.threadId)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    class ThreadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvThreadSubject)
        val tvPreview: TextView = view.findViewById(R.id.tvThreadPreview)
        val tvUrgent: TextView = view.findViewById(R.id.tvUrgentPill)
        val tvAvatar: TextView = view.findViewById(R.id.tvThreadAvatar)
        val tvMeta: TextView = view.findViewById(R.id.tvThreadMeta)
        val tvUnread: TextView = view.findViewById(R.id.tvUnreadBadge)
        val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_thread_row, parent, false)
        return ThreadViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) {
        val thread = items[position]
        holder.tvSubject.text = thread.subject
        holder.tvPreview.text = thread.lastMessagePreview
        holder.tvUrgent.visibility = if (thread.isUrgent) View.VISIBLE else View.GONE
        holder.tvAvatar.text = com.ntuli.apexledger.util.StatCards.initials(thread.subject)
        val metaParts = listOf(
            if (thread.serviceType.isNotEmpty()) com.ntuli.apexledger.util.Labels.service(thread.serviceType) else "",
            if (thread.accountantName.isNotEmpty()) "Chatting with ${thread.accountantName}" else ""
        ).filter { it.isNotEmpty() }
        holder.tvMeta.text = metaParts.joinToString("  •  ")
        holder.tvMeta.visibility = if (metaParts.isEmpty()) View.GONE else View.VISIBLE
        holder.tvUnread.visibility = if (thread.hasUnread) View.VISIBLE else View.GONE
        holder.tvPreview.setTypeface(null, if (thread.hasUnread) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        holder.checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = thread.threadId in selectedIds
        holder.itemView.setOnClickListener { if (selectionMode) toggle(thread) else onClick(thread) }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) startSelection()
            toggle(thread, forceOn = true)
            true
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<MessageThread>) {
        items = newItems
        selectedIds.retainAll(newItems.map { it.threadId }.toSet())
        notifyDataSetChanged()
    }
}