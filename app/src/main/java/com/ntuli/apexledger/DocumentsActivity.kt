package com.ntuli.apexledger

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.ntuli.apexledger.ui.Document
import com.ntuli.apexledger.ui.DocumentAdapter
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.NotificationHelper

class DocumentsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var adapter: DocumentAdapter
    private lateinit var spinnerCategory: Spinner

    private val categories = listOf("tax_return", "payroll", "bookkeeping", "advisory", "other")
    private var pendingUri: Uri? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            Toast.makeText(this, "File selected. Tap 'Upload Securely' to continue.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documents)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        spinnerCategory = findViewById(R.id.spinnerDocCategory)
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        val uploadDropzone = findViewById<LinearLayout>(R.id.uploadDropzone)
        val btnUploadSecurely = findViewById<MaterialButton>(R.id.btnUploadSecurely)

        val rv = findViewById<RecyclerView>(R.id.rvDocuments)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        adapter = DocumentAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        uploadDropzone.setOnClickListener {
            pickFileLauncher.launch("application/pdf,image/*")
        }

        btnUploadSecurely.setOnClickListener {
            val uri = pendingUri
            if (uri == null) {
                Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show()
            } else {
                uploadDocument(uri, categories[spinnerCategory.selectedItemPosition])
            }
        }

        loadDocuments(tvEmptyState)
    }

    private fun loadDocuments(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("documents")
            .whereEqualTo("clientId", uid)
            .get()
            .addOnSuccessListener { result ->
                // Only show the latest version per category: a document that
                // is itself referenced in another document's previousVersionIds
                // is a superseded version and should be hidden from the main list.
                val allDocs = result.documents
                val supersededIds = allDocs.flatMap { d ->
                    (d.get("previousVersionIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                }.toSet()

                val latestDocs = allDocs.filter { it.id !in supersededIds }

                val docs = latestDocs.map { d ->
                    Document(
                        documentId = d.id,
                        fileName = d.getString("fileName") ?: "Untitled",
                        documentCategory = d.getString("documentCategory") ?: "other",
                        reviewStatus = d.getString("reviewStatus") ?: "pending",
                        version = (d.getLong("version") ?: 1L).toInt()
                    )
                }
                adapter.updateData(docs)
                tvEmptyState.visibility = if (docs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
    }

    private fun uploadDocument(uri: Uri, category: String) {
        val uid = auth.currentUser?.uid ?: return

        val mimeType = contentResolver.getType(uri) ?: ""
        val isAllowedType = mimeType == "application/pdf" || mimeType.startsWith("image/")
        if (!isAllowedType) {
            Toast.makeText(this, "Only PDF and image files are allowed", Toast.LENGTH_LONG).show()
            return
        }

        var fileSizeBytes = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                fileSizeBytes = cursor.getLong(sizeIndex)
            }
        }
        val maxSizeBytes = 10 * 1024 * 1024 // 10 MB
        if (fileSizeBytes > maxSizeBytes) {
            Toast.makeText(this, "File is too large. Maximum size is 10MB", Toast.LENGTH_LONG).show()
            return
        }

        val fileName = uri.lastPathSegment ?: "document_${System.currentTimeMillis()}"
        val storageRef = storage.reference.child("documents/$uid/$fileName")

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()

        // Check if this client already has a latest document in this category,
        // so we know whether this upload is version 1 or a new version of an
        // existing document.
        db.collection("documents")
            .whereEqualTo("clientId", uid)
            .whereEqualTo("documentCategory", category)
            .get()
            .addOnSuccessListener { existingResult ->
                val existingDocs = existingResult.documents
                val supersededIds = existingDocs.flatMap { d ->
                    (d.get("previousVersionIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                }.toSet()
                val currentLatest = existingDocs.firstOrNull { it.id !in supersededIds }

                val newVersion = ((currentLatest?.getLong("version") ?: 0L).toInt()) + 1
                val previousIds = if (currentLatest != null) {
                    val priorChain = (currentLatest.get("previousVersionIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    priorChain + currentLatest.id
                } else {
                    emptyList()
                }

                storageRef.putFile(uri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                            val docData = hashMapOf(
                                "clientId" to uid,
                                "fileName" to fileName,
                                "filePath" to downloadUrl.toString(),
                                "documentCategory" to category,
                                "reviewStatus" to "pending",
                                "version" to newVersion,
                                "previousVersionIds" to previousIds,
                                "uploadTimestamp" to com.google.firebase.Timestamp.now()
                            )
                            db.collection("documents").add(docData)
                                .addOnSuccessListener {
                                    val message = if (newVersion > 1) {
                                        "Upload complete! This is version $newVersion."
                                    } else {
                                        "Upload complete!"
                                    }
                                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                                    AuditLogger.logEvent(
                                        "Client", "Upload Document",
                                        "$fileName (version $newVersion, category: $category)"
                                    )

                                    // Notify the assigned accountant that a new/updated document needs review
                                    db.collection("users").document(uid).get()
                                        .addOnSuccessListener { clientDoc ->
                                            val accountantId = clientDoc.getString("assignedAccountantId")
                                            if (!accountantId.isNullOrEmpty()) {
                                                NotificationHelper.notify(
                                                    recipientUid = accountantId,
                                                    type = "document_upload",
                                                    title = "New document to review",
                                                    body = "$fileName was uploaded (version $newVersion)."
                                                )
                                            }
                                        }

                                    pendingUri = null
                                    loadDocuments(findViewById(R.id.tvEmptyState))
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }
}