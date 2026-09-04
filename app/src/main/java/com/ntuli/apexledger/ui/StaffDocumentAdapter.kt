package com.ntuli.apexledger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.ntuli.apexledger.R

class StaffDocumentAdapter(
    private var items: List<Document>,
    private val onApprove: (Document) -> Unit,
    private val onReject: (Document) -> Unit
) : RecyclerView.Adapter<StaffDocumentAdapter.StaffDocViewHolder>() {

    class StaffDocViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDocName)
        val tvClient: TextView = view.findViewById(R.id.tvClientName)
        val tvMeta: TextView = view.findViewById(R.id.tvDocMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvDocStatus)
        val reviewActionsRow: View = view.findViewById(R.id.reviewActionsRow)
        val btnApprove: MaterialButton = view.findViewById(R.id.btnApprove)
        val btnReject: MaterialButton = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffDocViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document_staff_row, parent, false)
        return StaffDocViewHolder(view)
    }

    override fun onBindViewHolder(holder: StaffDocViewHolder, position: Int) {
        val doc = items[position]
        holder.tvName.text = doc.fileName
        holder.tvClient.text = doc.clientName.ifEmpty { "Unknown client" }
        holder.tvMeta.text = doc.documentCategory.replace("_", " ").replaceFirstChar { it.uppercase() }
        holder.tvStatus.text = doc.reviewStatus.replace("_", " ").replaceFirstChar { it.uppercase() }

        val badgeRes = when (doc.reviewStatus) {
            "approved" -> R.drawable.badge_status_completed
            "under_review" -> R.drawable.badge_status_pending
            "rejected" -> R.drawable.badge_status_overdue
            else -> R.drawable.badge_status_open
        }
        holder.tvStatus.setBackgroundResource(badgeRes)

        // Only show approve/reject actions for documents still awaiting a decision
        val needsReview = doc.reviewStatus == "pending" || doc.reviewStatus == "under_review"
        holder.reviewActionsRow.visibility = if (needsReview) View.VISIBLE else View.GONE

        holder.btnApprove.setOnClickListener { onApprove(doc) }
        holder.btnReject.setOnClickListener { onReject(doc) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Document>) {
        items = newItems
        notifyDataSetChanged()
    }
}