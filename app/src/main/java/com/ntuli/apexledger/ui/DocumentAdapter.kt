package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ntuli.apexledger.R
import com.ntuli.apexledger.util.Labels

class DocumentAdapter(private var items: List<Document>) :
    RecyclerView.Adapter<DocumentAdapter.DocViewHolder>() {

    class DocViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDocName)
        val tvVersion: TextView = view.findViewById(R.id.tvVersionBadge)
        val tvMeta: TextView = view.findViewById(R.id.tvDocMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvDocStatus)
        val tvFileType: TextView = view.findViewById(R.id.tvFileType)
        val tvAccountant: TextView = view.findViewById(R.id.tvDocAccountant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document_row, parent, false)
        return DocViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocViewHolder, position: Int) {
        val doc = items[position]
        holder.tvName.text = doc.fileName
        holder.itemView.setOnClickListener {
            val ctx = holder.itemView.context
            ctx.startActivity(
                android.content.Intent(ctx, com.ntuli.apexledger.DocumentViewerActivity::class.java)
                    .putExtra("docId", doc.documentId)
            )
        }
        com.ntuli.apexledger.util.FileTypes.apply(holder.tvFileType, doc.fileName)
        // Which accountant (and which kind) this document was routed to
        val duty = com.ntuli.apexledger.util.Labels.service(doc.documentCategory)
        holder.tvAccountant.text =
            if (doc.accountantName.isNotEmpty()) "Assigned to ${doc.accountantName}  •  $duty"
            else "Not assigned to an accountant yet"
        holder.tvVersion.text = "v${doc.version}"
        holder.tvMeta.text = listOf(Labels.service(doc.documentCategory), doc.dateLabel).filter { it.isNotEmpty() }.joinToString(" • ")
        holder.tvStatus.text = Labels.status(doc.reviewStatus)

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