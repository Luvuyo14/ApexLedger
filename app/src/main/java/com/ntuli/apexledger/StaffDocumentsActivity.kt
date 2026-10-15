package com.ntuli.apexledger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.Document
import com.ntuli.apexledger.ui.StaffDocumentAdapter
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.NotificationHelper

class StaffDocumentsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffDocumentAdapter

    private val statusFilterOptions = listOf("All statuses", "pending", "under_review", "approved", "rejected")
    private var allDocuments: List<Document> = emptyList()
    private var selectedFilter = "All statuses"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_documents)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvStaffDocuments)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val spinnerFilterStatus = findViewById<Spinner>(R.id.spinnerFilterStatus)

        adapter = StaffDocumentAdapter(
            emptyList(),
            onApprove = { doc -> updateReviewStatus(doc, "approved") },
            onReject = { doc -> updateReviewStatus(doc, "rejected") }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        spinnerFilterStatus.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            statusFilterOptions.map { if (it == "All statuses") it else com.ntuli.apexledger.util.Labels.status(it) }
        )
        spinnerFilterStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedFilter = statusFilterOptions[pos]
                applyFilter(tvEmptyState)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        loadAssignedDocuments(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadAssignedDocuments(findViewById(R.id.tvEmptyState))
    }

    private fun loadAssignedDocuments(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereEqualTo("assignedAccountantId", uid)
            .get()
            .addOnSuccessListener { clientsResult ->
                val clientDocs = clientsResult.documents
                val clientIds = clientDocs.map { it.id }
                val clientNames = clientDocs.associate { it.id to (it.getString("full_name") ?: "Unknown client") }

                if (clientIds.isEmpty()) {
                    allDocuments = emptyList()
                    applyFilter(tvEmptyState)
                    return@addOnSuccessListener
                }

                val batches = clientIds.chunked(10)
                val fetched = mutableListOf<Document>()
                var completedBatches = 0

                for (batch in batches) {
                    db.collection("documents")
                        .whereIn("clientId", batch)
                        .get()
                        .addOnSuccessListener { result ->
                            val docs = result.documents.filter { it.getBoolean("uploadComplete") != false }.map { d ->
                                Document(
                                    documentId = d.id,
                                    fileName = d.getString("fileName") ?: "Untitled",
                                    documentCategory = d.getString("documentCategory") ?: "other",
                                    reviewStatus = d.getString("reviewStatus") ?: "pending",
                                    clientName = clientNames[d.getString("clientId")] ?: "Unknown client",
                                    version = (d.getLong("version") ?: 1L).toInt(),
                                    dateLabel = d.getTimestamp("uploadTimestamp")?.toDate()
                                        ?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(it) } ?: ""
                                )
                            }
                            fetched.addAll(docs)
                            completedBatches++
                            if (completedBatches == batches.size) {
                                allDocuments = fetched
                                applyFilter(tvEmptyState)
                            }
                        }
                }
            }
    }

    private fun applyFilter(tvEmptyState: TextView) {
        val filtered = if (selectedFilter == "All statuses") {
            allDocuments
        } else {
            allDocuments.filter { it.reviewStatus == selectedFilter }
        }
        adapter.updateData(filtered)
        tvEmptyState.visibility = if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateReviewStatus(doc: Document, newStatus: String) {
        db.collection("documents").document(doc.documentId)
            .update("reviewStatus", newStatus)
            .addOnSuccessListener {
                com.ntuli.apexledger.util.Feedback.show(this, "${doc.fileName} marked $newStatus")
                AuditLogger.logEvent(
                    "User", "Document Review",
                    "${doc.fileName} → $newStatus (client: ${doc.clientName})"
                )

                db.collection("documents").document(doc.documentId).get()
                    .addOnSuccessListener { d ->
                        val clientId = d.getString("clientId")
                        if (clientId != null) {
                            NotificationHelper.notify(
                                recipientUid = clientId,
                                type = "document_review",
                                title = "Document $newStatus",
                                body = "${doc.fileName} was $newStatus by your accountant."
                            )
                        }
                    }

                loadAssignedDocuments(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e ->
                com.ntuli.apexledger.util.Feedback.show(this, "Failed to update: ${e.message}")
            }
    }
}