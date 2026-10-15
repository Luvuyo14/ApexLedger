package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class MessageAdapter(
    private var items: List<ChatMessage>,
    private val currentUid: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SENT = 1
        const val TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].senderId == currentUid) TYPE_SENT else TYPE_RECEIVED
    }

    class BubbleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBody: TextView = view.findViewById(R.id.tvMessageBody)
        val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
        val tvAttachment: TextView = view.findViewById(R.id.tvAttachment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutRes = if (viewType == TYPE_SENT) R.layout.item_message_sent else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return BubbleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        val h = holder as BubbleViewHolder
        h.tvBody.text = message.messageBody
        h.tvTime.text = message.timeLabel
        if (message.attachmentDocId.isNotEmpty()) {
            h.tvAttachment.visibility = View.VISIBLE
            h.tvAttachment.text = "Attachment: ${message.attachmentName}  (tap to open)"
            h.tvAttachment.paintFlags = h.tvAttachment.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            h.tvAttachment.setOnClickListener {
                val ctx = h.itemView.context
                ctx.startActivity(
                    android.content.Intent(ctx, com.ntuli.apexledger.DocumentViewerActivity::class.java)
                        .putExtra("docId", message.attachmentDocId)
                )
            }
        } else {
            h.tvAttachment.visibility = View.GONE
        }
        h.tvTime.visibility = if (message.timeLabel.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ChatMessage>) {
        items = newItems
        notifyDataSetChanged()
    }
}