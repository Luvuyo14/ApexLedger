package com.ntuli.apexledger.util

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException

/** Used by the chat screen to attach a PDF/photo. Same storage method as the Documents screen. */
object DocumentUploader {
    private const val CHUNK_BYTES = 512 * 1024
    const val MAX_BYTES = 10L * 1024 * 1024

    class Picked(val file: File, val name: String, val mimeType: String)

    /** Copies the picked file into the app cache (while the permission is fresh) and checks it. */
    fun prepare(activity: Activity, uri: Uri, onDone: (Picked?, String?) -> Unit) {
        Thread {
            var name = "attachment"
            try {
                activity.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (n >= 0) name = c.getString(n) ?: name
                    }
                }
            } catch (_: Exception) { }

            var mime = activity.contentResolver.getType(uri) ?: ""
            if (mime != "application/pdf" && !mime.startsWith("image/")) {
                mime = when (name.substringAfterLast('.', "").lowercase()) {
                    "pdf" -> "application/pdf"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    else -> ""
                }
            }
            var picked: Picked? = null
            var error: String? = null
            if (mime.isEmpty()) {
                error = "Only PDF, JPG and PNG files can be attached"
            } else {
                val out = File(activity.cacheDir, "attach_${System.currentTimeMillis()}.tmp")
                try {
                    val input = activity.contentResolver.openInputStream(uri)
                        ?: throw IOException("the app that holds this file would not share it")
                    input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    when {
                        out.length() == 0L -> throw IOException("the file is empty (0 bytes)")
                        out.length() > MAX_BYTES -> throw IOException("the file is larger than 10MB")
                    }
                    picked = Picked(out, name, mime)
                } catch (e: Throwable) {
                    out.delete()
                    error = "Could not read that file: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            activity.runOnUiThread { onDone(picked, error) }
        }.start()
    }

    /** Saves the file as a document for [clientId] and returns its id. */
    fun upload(
        activity: Activity, db: FirebaseFirestore, picked: Picked, clientId: String, uploaderUid: String,
        category: String = "other", source: String = "message",
        accountantId: String = "", accountantName: String = "", reviewStatus: String? = null,
        onDone: (String?, String?) -> Unit
    ) {
        Thread {
            val bytes = try { picked.file.readBytes() } catch (e: Exception) { null }
            activity.runOnUiThread {
                if (bytes == null) { onDone(null, "The file is no longer available"); return@runOnUiThread }
                val chunkCount = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
                val docRef = db.collection("documents").document()
                val fromClient = uploaderUid == clientId
                val data = hashMapOf(
                    "clientId" to clientId,
                    "uploadedBy" to uploaderUid,
                    "source" to source,
                    "fileName" to picked.name,
                    "filePath" to "",
                    "storageType" to "firestore",
                    "chunkCount" to chunkCount,
                    "mimeType" to picked.mimeType,
                    "documentCategory" to category,
                    "accountantId" to accountantId,
                    "accountantName" to accountantName,
                    "fileSizeBytes" to bytes.size.toLong(),
                    "reviewStatus" to (reviewStatus ?: (if (fromClient) "pending" else "approved")),
                    "version" to 1,
                    "previousVersionIds" to emptyList<String>(),
                    "uploadComplete" to false,
                    "uploadTimestamp" to Timestamp.now()
                )
                docRef.set(data)
                    .addOnFailureListener { e -> onDone(null, describe(e)) }
                    .addOnSuccessListener {
                        writeChunks(db, docRef, bytes, 0, chunkCount) { error ->
                            if (error != null) {
                                docRef.delete()
                                onDone(null, describe(error))
                            } else {
                                docRef.update("uploadComplete", true).addOnCompleteListener {
                                    picked.file.delete()
                                    onDone(docRef.id, null)
                                }
                            }
                        }
                    }
            }
        }.start()
    }

    private fun writeChunks(
        db: FirebaseFirestore, docRef: com.google.firebase.firestore.DocumentReference, bytes: ByteArray,
        from: Int, total: Int, onDone: (Exception?) -> Unit
    ) {
        if (from >= total) { onDone(null); return }
        val to = minOf(from + 3, total)
        val batch = db.batch()
        for (i in from until to) {
            val start = i * CHUNK_BYTES
            val end = minOf(start + CHUNK_BYTES, bytes.size)
            val b64 = android.util.Base64.encodeToString(bytes, start, end - start, android.util.Base64.NO_WRAP)
            batch.set(docRef.collection("chunks").document(i.toString().padStart(4, '0')), hashMapOf("index" to i, "data" to b64))
        }
        batch.commit()
            .addOnSuccessListener { writeChunks(db, docRef, bytes, to, total, onDone) }
            .addOnFailureListener { e -> onDone(e) }
    }

    private fun describe(e: Exception): String {
        val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name
        return when (code) {
            "PERMISSION_DENIED" -> "PERMISSION_DENIED - publish the updated Firestore rules (documents and chunks)"
            "UNAVAILABLE" -> "cannot reach the server. Check your internet connection"
            null -> e.message ?: "unknown error"
            else -> "$code - ${e.message}"
        }
    }
}
