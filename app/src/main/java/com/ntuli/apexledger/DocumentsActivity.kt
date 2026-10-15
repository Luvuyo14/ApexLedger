package com.ntuli.apexledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.ntuli.apexledger.ui.Document
import com.ntuli.apexledger.ui.DocumentAdapter
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.Labels
import com.ntuli.apexledger.util.NotificationHelper

class DocumentsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: DocumentAdapter
    private lateinit var spinnerCategory: Spinner

    private val categories = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit", "other")
    private var pendingUri: Uri? = null

    // Matches the "max 10MB" text shown in the upload dropzone
    private val maxFileSizeBytes = 10L * 1024 * 1024

    // The picked file is copied into the app's own cache straight away, while the permission is
    // fresh. That way a file the phone refuses to share is reported immediately, with the reason.
    private var pendingFile: java.io.File? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { /* some providers don't support it */ }
            copyPickedFile(uri)
        }
    }

    private fun copyPickedFile(uri: Uri) {
        var name = "document"
        var size = 0L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n) ?: name
                    if (s >= 0) size = c.getLong(s)
                }
            }
        } catch (_: Exception) { }

        findViewById<TextView>(R.id.tvDropTitle).text = "Reading $name..."
        Thread {
            var error: String? = null
            val out = java.io.File(cacheDir, "upload_${System.currentTimeMillis()}.tmp")
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("the app that holds this file would not share it")
                input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                if (out.length() == 0L) {
                    throw java.io.IOException(
                        "the file is empty (0 bytes). If it is stored in the cloud (Drive, Photos), " +
                                "open it once so it downloads to the phone, or pick it from Downloads instead"
                    )
                }
            } catch (e: Throwable) {
                error = (e.message ?: e.javaClass.simpleName)
                out.delete()
            }
            runOnUiThread {
                if (error != null) {
                    pendingFile = null
                    pendingUri = null
                    findViewById<TextView>(R.id.tvDropTitle).text = "Tap to upload a file"
                    com.ntuli.apexledger.util.Feedback.show(this, "Could not read that file: $error")
                } else {
                    pendingFile?.delete()
                    pendingFile = out
                    findViewById<TextView>(R.id.tvDropTitle).text = name
                    findViewById<TextView>(R.id.tvDropHint).text =
                        "${formatSize(out.length())} - tap Upload Securely to continue"
                }
            }
        }.start()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(java.util.Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documents)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        spinnerCategory = findViewById(R.id.spinnerDocCategory)
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories.map { Labels.service(it) })

        val uploadDropzone = findViewById<LinearLayout>(R.id.uploadDropzone)
        val btnUploadSecurely = findViewById<MaterialButton>(R.id.btnUploadSecurely)

        val rv = findViewById<RecyclerView>(R.id.rvDocuments)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        adapter = DocumentAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        uploadDropzone.setOnClickListener {
            pickFileLauncher.launch(arrayOf("application/pdf", "image/*"))
        }

        btnUploadSecurely.setOnClickListener {
            val uri = pendingUri
            if (uri == null || pendingFile == null) {
                com.ntuli.apexledger.util.Feedback.show(this, "Please select a file first")
            } else {
                uploadDocument(uri, categories[spinnerCategory.selectedItemPosition])
            }
        }

        loadDocuments(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadDocuments(findViewById(R.id.tvEmptyState))
    }

    private fun loadDocuments(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("documents")
            .whereEqualTo("clientId", uid)
            .get()
            .addOnSuccessListener { result ->
                val allDocs = result.documents
                val supersededIds = allDocs.flatMap { d ->
                    (d.get("previousVersionIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                }.toSet()

                val latestDocs = allDocs.filter { it.id !in supersededIds && it.getBoolean("uploadComplete") != false }

                val docs = latestDocs.map { d ->
                    Document(
                        documentId = d.id,
                        fileName = d.getString("fileName") ?: "Untitled",
                        documentCategory = d.getString("documentCategory") ?: "other",
                        accountantName = d.getString("accountantName") ?: "",
                        reviewStatus = d.getString("reviewStatus") ?: "pending",
                        version = (d.getLong("version") ?: 1L).toInt(),
                        dateLabel = d.getTimestamp("uploadTimestamp")?.toDate()
                            ?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(it) } ?: ""
                    )
                }
                adapter.updateData(docs)
                tvEmptyState.visibility = if (docs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener { e ->
                tvEmptyState.visibility = android.view.View.VISIBLE
                tvEmptyState.text = "Couldn't load documents: ${e.message}"
            }
    }

    private fun uploadDocument(uri: Uri, category: String) {
        val uid = auth.currentUser?.uid ?: return

        // Validate type (this is what refuses .txt files)
        var fileSizeBytes = 0L
        var displayName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (sizeIndex >= 0) fileSizeBytes = cursor.getLong(sizeIndex)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
            }
        }

        // Some file managers report a vague type (or none), so fall back to the file extension
        var mimeType = contentResolver.getType(uri) ?: ""
        if (mimeType != "application/pdf" && !mimeType.startsWith("image/")) {
            mimeType = when ((displayName ?: "").substringAfterLast('.', "").lowercase()) {
                "pdf" -> "application/pdf"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> ""
            }
        }
        if (mimeType.isEmpty()) {
            com.ntuli.apexledger.util.Feedback.show(this, "Only PDF, JPG and PNG files are allowed")
            return
        }

        if (fileSizeBytes > maxFileSizeBytes) {
            com.ntuli.apexledger.util.Feedback.show(this, "File is too large. Maximum size is 10MB")
            return
        }

        val fileName = displayName ?: "document_${System.currentTimeMillis()}"

        com.ntuli.apexledger.util.Feedback.show(this, "Uploading...")

        db.collection("documents")
            .whereEqualTo("clientId", uid)
            .whereEqualTo("documentCategory", category)
            .get()
            .addOnFailureListener { e ->
                com.ntuli.apexledger.util.Feedback.show(this, "Upload failed at step 1 (checking existing documents): ${describe(e)}")
            }
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

                saveToFirestore(
                    uri, uid, fileName, category, mimeType, fileSizeBytes,
                    newVersion, previousIds
                )
            }
    }

    /**
     * Free-plan storage: the file is cut into 512 KB pieces and saved as small documents under
     * documents/{id}/chunks. The parent document is created first, then the pieces, and it is
     * marked uploadComplete only when every piece has been written.
     */
    private fun saveToFirestore(
        uri: Uri, uid: String, fileName: String, category: String, mimeType: String,
        sizeFromProvider: Long, newVersion: Int, previousIds: List<String>
    ) {
        // A document goes to the accountant who handles its SERVICE (e.g. Tax Return -> Taxation),
        // not simply to the client's own accountant.
        com.ntuli.apexledger.util.AccountantRouter.pick(db, uid, category, "tickets") { pick ->
            saveToFirestore(uri, uid, fileName, category, mimeType, sizeFromProvider, newVersion, previousIds, pick)
        }
    }

    private fun saveToFirestore(
        uri: Uri, uid: String, fileName: String, category: String, mimeType: String,
        sizeFromProvider: Long, newVersion: Int, previousIds: List<String>,
        pick: com.ntuli.apexledger.util.AccountantRouter.Pick?
    ) {
        val btn = findViewById<MaterialButton>(R.id.btnUploadSecurely)
        btn.isEnabled = false
        btn.text = "Reading file..."

        Thread {
            val bytes = try {
                pendingFile?.takeIf { it.exists() }?.readBytes()
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (bytes == null || bytes.isEmpty()) {
                    resetUploadButton(btn)
                    com.ntuli.apexledger.util.Feedback.show(this, "The selected file is no longer available. Please pick it again.")
                    return@runOnUiThread
                }
                if (bytes.size > maxFileSizeBytes) {
                    resetUploadButton(btn)
                    com.ntuli.apexledger.util.Feedback.show(this, "File is too large. Maximum size is 10MB")
                    return@runOnUiThread
                }
                val chunkCount = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
                val docRef = db.collection("documents").document()
                val docData = hashMapOf(
                    "clientId" to uid,
                    "fileName" to fileName,
                    "filePath" to "",
                    "storageType" to "firestore",
                    "chunkCount" to chunkCount,
                    "mimeType" to mimeType,
                    "documentCategory" to category,
                    "accountantId" to (pick?.id ?: ""),
                    "accountantName" to (pick?.name ?: ""),
                    "fileSizeBytes" to bytes.size.toLong(),
                    "reviewStatus" to "pending",
                    "version" to newVersion,
                    "previousVersionIds" to previousIds,
                    "uploadComplete" to false,
                    "uploadTimestamp" to com.google.firebase.Timestamp.now()
                )
                btn.text = "Uploading 0%"
                val noResponse = Runnable {
                    com.ntuli.apexledger.util.Feedback.show(
                        this, "Upload failed: no response from the server. Check your internet connection and try again."
                    )
                    resetUploadButton(btn)
                }
                btn.postDelayed(noResponse, 30000)
                docRef.set(docData)
                    .addOnFailureListener { e ->
                        btn.removeCallbacks(noResponse)
                        resetUploadButton(btn)
                        com.ntuli.apexledger.util.Feedback.show(this, "Upload failed at step 2 (saving the file record): ${describe(e)}")
                    }
                    .addOnSuccessListener {
                        btn.removeCallbacks(noResponse)
                        writeChunks(docRef, bytes, 0, chunkCount, btn) { error ->
                            if (error != null) {
                                docRef.delete()
                                resetUploadButton(btn)
                                com.ntuli.apexledger.util.Feedback.show(this, "Upload failed at step 3 (saving the file data): ${describe(error)}")
                            } else {
                                docRef.update("uploadComplete", true)
                                    .addOnCompleteListener {
                                        resetUploadButton(btn)
                                        onUploadFinished(uid, fileName, category, newVersion, pick, docRef.id)
                                    }
                            }
                        }
                    }
            }
        }.start()
    }

    private fun writeChunks(
        docRef: com.google.firebase.firestore.DocumentReference, bytes: ByteArray,
        from: Int, total: Int, btn: MaterialButton, onDone: (Exception?) -> Unit
    ) {
        if (from >= total) { onDone(null); return }
        val to = minOf(from + 3, total)
        val batch = db.batch()
        for (i in from until to) {
            val start = i * CHUNK_BYTES
            val end = minOf(start + CHUNK_BYTES, bytes.size)
            val b64 = android.util.Base64.encodeToString(bytes, start, end - start, android.util.Base64.NO_WRAP)
            batch.set(
                docRef.collection("chunks").document(i.toString().padStart(4, '0')),
                hashMapOf("index" to i, "data" to b64)
            )
        }
        batch.commit()
            .addOnSuccessListener {
                btn.text = "Uploading ${to * 100 / total}%"
                writeChunks(docRef, bytes, to, total, btn, onDone)
            }
            .addOnFailureListener { e -> onDone(e) }
    }

    /** Turns a Firestore error into words a person can act on. */
    private fun describe(e: Exception): String {
        val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name
        return when (code) {
            "PERMISSION_DENIED" -> "PERMISSION_DENIED - the Firestore rules do not allow this yet. Publish the updated rules (documents and chunks) and try again."
            "UNAVAILABLE" -> "UNAVAILABLE - cannot reach the server. Check your internet connection."
            null -> e.message ?: "unknown error"
            else -> "$code - ${e.message}"
        }
    }

    private fun resetUploadButton(btn: MaterialButton) {
        btn.isEnabled = true
        btn.text = "Upload Securely"
    }

    private fun onUploadFinished(
        uid: String, fileName: String, category: String, newVersion: Int,
        pick: com.ntuli.apexledger.util.AccountantRouter.Pick?, docId: String
    ) {
        val assigned = if (pick != null) " Assigned to ${pick.name} (${Labels.service(category)})." else ""
        val message = (if (newVersion > 1) "Upload complete! This is version $newVersion." else "Upload complete!") + assigned
        com.ntuli.apexledger.util.Feedback.show(this, message)

        AuditLogger.logEvent(
            "Client", "Upload Document",
            "$fileName (version $newVersion, category: $category)"
        )

        if (pick != null) {
            NotificationHelper.notify(
                recipientUid = pick.id,
                type = "document_upload",
                title = "New ${Labels.service(category)} document",
                body = "$fileName was uploaded (version $newVersion).",
                targetId = docId
            )
        } else {
            com.ntuli.apexledger.util.Feedback.show(this, "No accountant handles ${Labels.service(category)} yet. An administrator will pick it up.")
        }

        pendingUri = null
        pendingFile?.delete()
        pendingFile = null
        findViewById<TextView>(R.id.tvDropTitle).text = "Tap to upload a file"
        findViewById<TextView>(R.id.tvDropHint).text = "PDF, JPG, PNG • max 10MB"
        loadDocuments(findViewById(R.id.tvEmptyState))
    }

    companion object {
        private const val CHUNK_BYTES = 512 * 1024
    }
}
