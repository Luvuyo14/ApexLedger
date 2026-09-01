package com.ntuli.apexledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.ntuli.apexledger.ui.Document
import com.ntuli.apexledger.ui.DocumentAdapter

class DocumentsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var adapter: DocumentAdapter

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) uploadDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documents)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvDocuments)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val fab = findViewById<FloatingActionButton>(R.id.fabUploadDoc)

        adapter = DocumentAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        fab.setOnClickListener {
            pickFileLauncher.launch("*/*")
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

    private fun uploadDocument(uri: Uri) {
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
                        "documentCategory" to "other",
                        "reviewStatus" to "pending",
                        "uploadTimestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("documents").add(docData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Upload complete!", Toast.LENGTH_SHORT).show()
                            loadDocuments(findViewById(R.id.tvEmptyState))
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}