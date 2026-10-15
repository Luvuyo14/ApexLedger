package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class ThreadAdapter(
    private var items: List<MessageThread>,
    private val onClick: (MessageThread) -> Unit
) : RecyclerView.Adapter<ThreadAdapter.ThreadViewHolder>() {

    class ThreadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvThreadSubject)
        val tvPreview: TextView = view.findViewById(R.id.tvThreadPreview)
        val tvUrgent: TextView = view.findViewById(R.id.tvUrgentPill)
        val tvAvatar: TextView = view.findViewById(R.id.tvThreadAvatar)
        val tvUnread: TextView = view.findViewById(R.id.tvUnreadBadge)
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
        holder.tvUnread.visibility = if (thread.hasUnread) View.VISIBLE else View.GONE
        holder.tvPreview.setTypeface(null, if (thread.hasUnread) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        holder.itemView.setOnClickListener { onClick(thread) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<MessageThread>) {
        items = newItems
        notifyDataSetChanged()
    }
}