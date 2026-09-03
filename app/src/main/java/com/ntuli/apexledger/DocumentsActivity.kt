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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            pickFileLauncher.launch("*/*")
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
                val docs = result.documents.map { d ->
                    Document(
                        fileName = d.getString("fileName") ?: "Untitled",
                        documentCategory = d.getString("documentCategory") ?: "other",
                        reviewStatus = d.getString("reviewStatus") ?: "pending"
                    )
                }
                adapter.updateData(docs)
                tvEmptyState.visibility = if (docs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
    }

    private fun uploadDocument(uri: Uri, category: String) {
        val uid = auth.currentUser?.uid ?: return
        val fileName = uri.lastPathSegment ?: "document_${System.currentTimeMillis()}"
        val storageRef = storage.reference.child("documents/$uid/$fileName")

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val docData = hashMapOf(
                        "clientId" to uid,
                        "fileName" to fileName,
                        "filePath" to downloadUrl.toString(),
                        "documentCategory" to category,
                        "reviewStatus" to "pending",
                        "uploadTimestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("documents").add(docData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Upload complete!", Toast.LENGTH_SHORT).show()
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