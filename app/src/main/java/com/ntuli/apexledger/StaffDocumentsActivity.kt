package com.ntuli.apexledger

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.Document
import com.ntuli.apexledger.ui.StaffDocumentAdapter
import com.ntuli.apexledger.util.AccountantRouter
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.DocumentUploader
import com.ntuli.apexledger.util.Feedback
import com.ntuli.apexledger.util.Labels
import com.ntuli.apexledger.util.NotificationHelper
import com.ntuli.apexledger.util.StaffData

/**
 * Document Management for staff.
 * - Administrators see every document; an accountant sees the documents routed to their service.
 * - Administrators and accountants can upload a document on behalf of a client.
 */
class StaffDocumentsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffDocumentAdapter

    private val statusFilterOptions = listOf("All statuses", "pending", "under_review", "approved", "rejected")
    private val categories = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit", "other")
    private var allDocuments: List<Document> = emptyList()
    private var selectedFilter = "All statuses"

    private var myRole = ""
    private var myName = ""
    private var clientIds: List<String> = emptyList()
    private var picked: DocumentUploader.Picked? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            findViewById<TextView>(R.id.tvStaffDropTitle).text = "Reading file..."
            DocumentUploader.prepare(this, uri) { p, error ->
                runOnUiThread {
                    if (p == null) {
                        picked = null
                        findViewById<TextView>(R.id.tvStaffDropTitle).text = "Tap to choose a file"
                        Feedback.show(this, "Could not read that file: ${error ?: "unknown error"}")
                    } else {
                        picked = p
                        findViewById<TextView>(R.id.tvStaffDropTitle).text = p.name
                        findViewById<TextView>(R.id.tvStaffDropHint).text = "${p.file.length() / 1024} KB - ready to upload"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_documents)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

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
            statusFilterOptions.map { if (it == "All statuses") it else Labels.status(it) }
        )
        spinnerFilterStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedFilter = statusFilterOptions[pos]
                applyFilter(tvEmptyState)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        setupUploadCard()
        loadDocuments(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadDocuments(findViewById(R.id.tvEmptyState))
    }

    // ----------------------------------------------------------- upload on behalf of a client
    private fun setupUploadCard() {
        val uid = auth.currentUser?.uid ?: return
        val card = findViewById<View>(R.id.cardStaffUpload)
        val toggle = findViewById<TextView>(R.id.btnToggleUpload)
        val spinnerClient = findViewById<Spinner>(R.id.spinnerUploadClient)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerUploadCategory)
        val btnUpload = findViewById<MaterialButton>(R.id.btnStaffUpload)

        spinnerCategory.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, categories.map { Labels.service(it) }
        )

        db.collection("users").document(uid).get().addOnSuccessListener { me ->
            myRole = me.getString("role") ?: ""
            myName = me.getString("full_name") ?: ""
            // Managers can review but not upload (see the Permissions Matrix)
            if (myRole != "admin" && myRole != "accountant") return@addOnSuccessListener
            toggle.visibility = View.VISIBLE

            db.collection("users").whereEqualTo("role", "client").get().addOnSuccessListener { clients ->
                val active = clients.documents.filter { it.getBoolean("active") != false }
                    .sortedBy { it.getString("full_name")?.lowercase() ?: "" }
                clientIds = active.map { it.id }
                val names = active.map { it.getString("full_name") ?: "Unknown client" }
                spinnerClient.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    if (names.isEmpty()) listOf("No clients yet") else names
                )
            }
        }

        toggle.setOnClickListener {
            val open = card.visibility != View.VISIBLE
            card.visibility = if (open) View.VISIBLE else View.GONE
            toggle.text = if (open) "Close" else "+ Upload"
        }
        findViewById<View>(R.id.staffDropzone).setOnClickListener {
            pickFile.launch(arrayOf("application/pdf", "image/*"))
        }

        btnUpload.setOnClickListener {
            val file = picked
            if (clientIds.isEmpty()) { Feedback.show(this, "There is no client to upload for yet"); return@setOnClickListener }
            if (file == null) { Feedback.show(this, "Please choose a file first"); return@setOnClickListener }

            val clientId = clientIds[spinnerClient.selectedItemPosition]
            val category = categories[spinnerCategory.selectedItemPosition]
            btnUpload.isEnabled = false
            btnUpload.text = "Uploading..."

            fun finish(accId: String, accName: String) {
                DocumentUploader.upload(
                    this, db, file, clientId, uid,
                    category = category, source = "staff", accountantId = accId, accountantName = accName
                ) { docId, error ->
                    runOnUiThread {
                        btnUpload.isEnabled = true
                        btnUpload.text = "Upload Securely"
                        if (error != null || docId == null) {
                            Feedback.show(this, "Upload failed: ${error ?: "unknown error"}")
                        } else {
                            Feedback.show(this, "${file.name} uploaded")
                            AuditLogger.logEvent("User", "Upload Document", "${file.name} for client $clientId ($category)")
                            NotificationHelper.notify(
                                recipientUid = clientId, type = "document_upload",
                                title = "A document was added to your account",
                                body = "${file.name} was uploaded by ${myName.ifEmpty { "your accountant" }}.",
                                targetId = docId
                            )
                            picked = null
                            findViewById<TextView>(R.id.tvStaffDropTitle).text = "Tap to choose a file"
                            findViewById<TextView>(R.id.tvStaffDropHint).text = "PDF, JPG, PNG • max 10MB"
                            card.visibility = View.GONE
                            toggle.text = "+ Upload"
                            loadDocuments(findViewById(R.id.tvEmptyState))
                        }
                    }
                }
            }

            if (myRole == "accountant") {
                finish(uid, myName)   // an accountant uploading is the one handling it
            } else {
                AccountantRouter.pick(db, clientId, category, "tickets") { pick -> finish(pick?.id ?: "", pick?.name ?: "") }
            }
        }
    }

    // ----------------------------------------------------------- list
    private fun loadDocuments(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return
        StaffData.loadDocuments(db, uid) { res ->
            allDocuments = res.docs.filter { it.getBoolean("uploadComplete") != false }.map { d ->
                Document(
                    documentId = d.id,
                    fileName = d.getString("fileName") ?: "Untitled",
                    documentCategory = d.getString("documentCategory") ?: "other",
                    accountantName = d.getString("accountantName") ?: "",
                    reviewStatus = d.getString("reviewStatus") ?: "pending",
                    clientName = res.clientNames[d.getString("clientId")] ?: "Unknown client",
                    version = (d.getLong("version") ?: 1L).toInt(),
                    dateLabel = d.getTimestamp("uploadTimestamp")?.toDate()
                        ?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(it) } ?: ""
                )
            }.sortedByDescending { it.dateLabel }
            findViewById<TextView>(R.id.tvDocsTitle).text = "All Documents (${allDocuments.size})"
            applyFilter(tvEmptyState)
        }
    }

    private fun applyFilter(tvEmptyState: TextView) {
        val filtered = if (selectedFilter == "All statuses") allDocuments
        else allDocuments.filter { it.reviewStatus == selectedFilter }
        adapter.updateData(filtered)
        tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateReviewStatus(doc: Document, newStatus: String) {
        db.collection("documents").document(doc.documentId)
            .update("reviewStatus", newStatus)
            .addOnSuccessListener {
                Feedback.show(this, "${doc.fileName} marked $newStatus")
                AuditLogger.logEvent("User", "Document Review", "${doc.fileName} → $newStatus (client: ${doc.clientName})")

                db.collection("documents").document(doc.documentId).get().addOnSuccessListener { d ->
                    val clientId = d.getString("clientId")
                    if (clientId != null) {
                        NotificationHelper.notify(
                            recipientUid = clientId,
                            type = "document_review",
                            title = "Document $newStatus",
                            body = "${doc.fileName} was $newStatus by your accountant.",
                            targetId = doc.documentId
                        )
                    }
                }
                loadDocuments(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e -> Feedback.show(this, "Failed to update: ${e.message}") }
    }
}
