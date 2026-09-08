package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R

class DocumentAdapter(private var items: List<Document>) :
    RecyclerView.Adapter<DocumentAdapter.DocViewHolder>() {

    class DocViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDocName)
        val tvVersion: TextView = view.findViewById(R.id.tvVersionBadge)
        val tvMeta: TextView = view.findViewById(R.id.tvDocMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvDocStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document_row, parent, false)
        return DocViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocViewHolder, position: Int) {
        val doc = items[position]
        holder.tvName.text = doc.fileName
        holder.tvVersion.text = "v${doc.version}"
        holder.tvMeta.text = doc.documentCategory.replace("_", " ").replaceFirstChar { it.uppercase() }
        holder.tvStatus.text = doc.reviewStatus.replace("_", " ").replaceFirstChar { it.uppercase() }

        val badgeRes = when (doc.reviewStatus) {
            "approved" -> R.drawable.badge_status_completed
            "under_review" -> R.drawable.badge_status_pending
            "rejected" -> R.drawable.badge_status_overdue
            else -> R.drawable.badge_status_open
        }
        holder.tvStatus.setBackgroundResource(badgeRes)
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Document>) {
        items = newItems
        notifyDataSetChanged()
    }
}