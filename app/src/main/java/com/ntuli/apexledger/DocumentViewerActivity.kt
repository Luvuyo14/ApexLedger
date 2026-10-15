package com.ntuli.apexledger

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.Badges
import com.ntuli.apexledger.util.DocumentFiles
import com.ntuli.apexledger.util.FileTypes
import com.ntuli.apexledger.util.Feedback
import com.ntuli.apexledger.util.Labels
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Shows one document's details and lets the person open it or save it to the phone. */
class DocumentViewerActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var docId = ""
    private var fileName = "document"
    private var mimeType = ""
    private var chunkCount = 0
    private var legacyUrl = ""
    private var cachedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_viewer)
        db = FirebaseFirestore.getInstance()
        docId = intent.getStringExtra("docId") ?: run { finish(); return }

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnOpen).setOnClickListener { prepare { file -> openFile(file) } }
        findViewById<MaterialButton>(R.id.btnDownload).setOnClickListener { prepare { file -> saveFile(file) } }
        loadDetails()
    }

    private fun loadDetails() {
        db.collection("documents").document(docId).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) { Feedback.show(this, "This document no longer exists"); finish(); return@addOnSuccessListener }
                fileName = d.getString("fileName") ?: "document"
                mimeType = d.getString("mimeType") ?: guessMime(fileName)
                chunkCount = (d.getLong("chunkCount") ?: 0L).toInt()
                legacyUrl = d.getString("filePath") ?: ""
                val status = d.getString("reviewStatus") ?: "pending"

                findViewById<TextView>(R.id.tvFileName).text = fileName
                FileTypes.apply(findViewById(R.id.tvFileType), fileName)
                findViewById<TextView>(R.id.tvStatusBadge).apply {
                    text = Labels.status(status)
                    setBackgroundResource(
                        when (status) {
                            "approved" -> R.drawable.badge_status_completed
                            "rejected" -> R.drawable.badge_status_overdue
                            "under_review" -> R.drawable.badge_status_pending
                            else -> Badges.status("open")
                        }
                    )
                }
                findViewById<TextView>(R.id.tvCategory).text = Labels.service(d.getString("documentCategory") ?: "other")
                findViewById<TextView>(R.id.tvVersion).text = "v${(d.getLong("version") ?: 1L)}"
                findViewById<TextView>(R.id.tvSize).text = formatSize(d.getLong("fileSizeBytes") ?: 0L)
                findViewById<TextView>(R.id.tvUploaded).text = d.getTimestamp("uploadTimestamp")?.toDate()
                    ?.let { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(it) } ?: "-"

                val accName = d.getString("accountantName") ?: ""
                findViewById<TextView>(R.id.tvAssignedTo).text =
                    if (accName.isNotEmpty()) "$accName (${Labels.service(d.getString("documentCategory") ?: "other")})"
                    else "Not assigned yet"

                val clientId = d.getString("clientId") ?: ""
                db.collection("users").document(clientId).get().addOnSuccessListener { u ->
                    findViewById<TextView>(R.id.tvClient).text = u.getString("full_name") ?: "-"
                }

                // Small images show a preview straight away
                if (mimeType.startsWith("image/") && chunkCount in 1..6) {
                    prepare { file -> showPreview(file) }
                }
            }
            .addOnFailureListener { e ->
                Feedback.show(this, "Could not load the document: ${e.message}")
            }
    }

    /** Makes sure the file is on the phone, then runs [then]. */
    private fun prepare(then: (File) -> Unit) {
        cachedFile?.let { if (it.exists()) { then(it); return } }

        if (chunkCount == 0) {
            // Older documents were stored as a web link
            if (legacyUrl.startsWith("http")) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(legacyUrl)))
            } else {
                Feedback.show(this, "This document has no file attached")
            }
            return
        }

        val area = findViewById<View>(R.id.progressArea)
        val bar = findViewById<ProgressBar>(R.id.progressBar)
        val label = findViewById<TextView>(R.id.tvProgress)
        area.visibility = View.VISIBLE
        bar.progress = 0
        label.text = "Preparing file... 0%"
        setButtonsEnabled(false)

        DocumentFiles.fetch(this, db, docId, chunkCount, fileName,
            onProgress = { p -> bar.progress = p; label.text = "Preparing file... $p%" },
            onDone = { file, error ->
                area.visibility = View.GONE
                setButtonsEnabled(true)
                if (file == null) {
                    Feedback.show(this, "Could not download the file: ${error ?: "unknown error"}")
                } else {
                    cachedFile = file
                    then(file)
                }
            })
    }

    private fun openFile(file: File) {
        if (!DocumentFiles.open(this, file, mimeType)) {
            Feedback.show(this, "No app found to open this file. Use Save to Downloads instead.")
        } else {
            AuditLogger.logEvent("User", "View Document", fileName)
        }
    }

    private fun saveFile(file: File) {
        val where = DocumentFiles.saveToDownloads(this, file, fileName, mimeType)
        if (where == null) {
            Feedback.show(this, "Could not save the file to Downloads")
        } else {
            Feedback.show(this, "Saved to $where")
            AuditLogger.logEvent("User", "Download Document", fileName)
        }
    }

    private fun showPreview(file: File) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        var sample = 1
        while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp != null) {
            findViewById<ImageView>(R.id.ivPreview).apply { setImageBitmap(bmp); visibility = View.VISIBLE }
            findViewById<View>(R.id.tvFileType).visibility = View.GONE
        }
    }

    private fun setButtonsEnabled(on: Boolean) {
        findViewById<MaterialButton>(R.id.btnOpen).isEnabled = on
        findViewById<MaterialButton>(R.id.btnDownload).isEnabled = on
    }

    private fun guessMime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        bytes > 0 -> "$bytes B"
        else -> "-"
    }
}
