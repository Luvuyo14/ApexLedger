package com.ntuli.apexledger.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream

/** Rebuilds a document from its Firestore pieces, then opens or saves it. */
object DocumentFiles {

    fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifEmpty { "document" }

    /** Downloads every piece in order and writes the finished file into the app cache. */
    fun fetch(
        context: Context, db: FirebaseFirestore, docId: String, chunkCount: Int, fileName: String,
        onProgress: (Int) -> Unit, onDone: (File?, String?) -> Unit
    ) {
        val dir = File(context.cacheDir, "docs").apply { mkdirs() }
        val file = File(dir, safeName(fileName))
        val out = try { FileOutputStream(file) } catch (e: Exception) { onDone(null, e.message); return }

        fun step(i: Int) {
            if (i >= chunkCount) {
                out.close()
                onDone(file, null)
                return
            }
            db.collection("documents").document(docId).collection("chunks")
                .document(i.toString().padStart(4, '0')).get()
                .addOnSuccessListener { snap ->
                    val data = snap.getString("data")
                    if (data == null) {
                        out.close(); file.delete()
                        onDone(null, "Part ${i + 1} of $chunkCount is missing")
                        return@addOnSuccessListener
                    }
                    try {
                        out.write(Base64.decode(data, Base64.NO_WRAP))
                    } catch (e: Exception) {
                        out.close(); file.delete()
                        onDone(null, e.message)
                        return@addOnSuccessListener
                    }
                    onProgress((i + 1) * 100 / chunkCount)
                    step(i + 1)
                }
                .addOnFailureListener { e ->
                    out.close(); file.delete()
                    onDone(null, e.message)
                }
        }
        step(0)
    }

    /** Opens the file in whatever app can show it (PDF viewer, gallery ...). */
    fun open(context: Context, file: File, mimeType: String): Boolean {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifEmpty { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Exception) { false }
    }

    /** Saves a copy into the phone's Downloads folder. Returns where it went, or null on failure. */
    fun saveToDownloads(context: Context, file: File, fileName: String, mimeType: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName(fileName))
                    put(MediaStore.Downloads.MIME_TYPE, mimeType.ifEmpty { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/APEXLedger")
                }
                val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
                "Downloads/APEXLedger"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                val target = File(dir, safeName(fileName))
                file.copyTo(target, overwrite = true)
                target.parent
            }
        } catch (e: Exception) {
            null
        }
    }
}
