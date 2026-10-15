package com.ntuli.apexledger

import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.ntuli.apexledger.ui.ChatMessage
import com.ntuli.apexledger.ui.MessageAdapter
import com.ntuli.apexledger.util.DocumentUploader
import com.ntuli.apexledger.util.Feedback
import com.ntuli.apexledger.util.Labels
import com.ntuli.apexledger.util.NotificationHelper

class ConversationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MessageAdapter
    private lateinit var threadId: String

    // Kept so we can stop listening when the screen closes.
    private var messagesListener: ListenerRegistration? = null
    private var attaching = false

    private val timeFormat = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())

    private val urgentKeywords = listOf("urgent", "deadline", "penalty", "asap", "overdue")

    private val pickAttachment = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) attach(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        threadId = intent.getStringExtra("threadId") ?: return
        val subject = intent.getStringExtra("subject") ?: "Conversation"

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = subject
        toolbar.setNavigationOnClickListener { finish() }
        resolveCounterpartName(subject, toolbar)

        val rv = findViewById<RecyclerView>(R.id.rvMessages)
        val etBody = findViewById<EditText>(R.id.etMessageBody)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)

        val uid = auth.currentUser?.uid ?: return
        adapter = MessageAdapter(emptyList(), uid)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadMessages()

        btnSend.setOnClickListener {
            val text = etBody.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etBody.text.clear()
            }
        }

        findViewById<ImageButton>(R.id.btnAttach).setOnClickListener {
            if (!attaching) pickAttachment.launch(arrayOf("application/pdf", "image/*"))
        }
    }

    /** Title: "Chatting with <accountant or client>", subtitle: subject and service. */
    private fun resolveCounterpartName(subject: String, toolbar: MaterialToolbar) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("threads").document(threadId).get().addOnSuccessListener { thread ->
            val clientId = thread.getString("clientId") ?: return@addOnSuccessListener
            val service = thread.getString("serviceType") ?: ""
            toolbar.subtitle = listOf(thread.getString("subject") ?: subject, if (service.isNotEmpty()) Labels.service(service) else "")
                .filter { it.isNotEmpty() }.joinToString("  •  ")

            if (uid == clientId) {
                val name = thread.getString("accountantName") ?: ""
                if (name.isNotEmpty()) {
                    toolbar.title = "Chatting with $name"
                } else {
                    // Older conversations: fall back to the client's own accountant
                    db.collection("users").document(clientId).get().addOnSuccessListener { client ->
                        val accId = client.getString("assignedAccountantId")
                        if (!accId.isNullOrEmpty()) {
                            db.collection("users").document(accId).get().addOnSuccessListener { acc ->
                                acc.getString("full_name")?.let { toolbar.title = "Chatting with $it" }
                            }
                        }
                    }
                }
            } else {
                db.collection("users").document(clientId).get().addOnSuccessListener { client ->
                    client.getString("full_name")?.let { toolbar.title = "Chatting with $it" }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
        messagesListener = null
    }

    private fun loadMessages() {
        val uid = auth.currentUser?.uid ?: return

        messagesListener = db.collection("threads").document(threadId)
            .collection("messages")
            .orderBy("sentTimestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { result, _ ->
                if (result != null) {
                    val messages = result.documents.map { d ->
                        ChatMessage(
                            senderId = d.getString("senderId") ?: "",
                            messageBody = d.getString("messageBody") ?: "",
                            isUrgent = d.getBoolean("isUrgent") ?: false,
                            readBy = (d.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            timeLabel = d.getTimestamp("sentTimestamp")?.toDate()
                                ?.let { timeFormat.format(it) } ?: "",
                            attachmentDocId = d.getString("attachmentDocId") ?: "",
                            attachmentName = d.getString("attachmentName") ?: ""
                        )
                    }
                    adapter.updateData(messages)
                    if (messages.isNotEmpty()) {
                        findViewById<RecyclerView>(R.id.rvMessages).scrollToPosition(messages.size - 1)
                    }

                    for (doc in result.documents) {
                        val readBy = doc.get("readBy") as? List<*> ?: emptyList<String>()
                        if (uid !in readBy) {
                            doc.reference.update("readBy", FieldValue.arrayUnion(uid))
                        }
                    }
                }
            }
    }

    /** Uploads the picked PDF/photo as a document, then posts it in the chat. */
    private fun attach(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val btn = findViewById<ImageButton>(R.id.btnAttach)
        attaching = true
        btn.alpha = 0.4f
        Feedback.show(this, "Preparing your attachment...")

        fun done() { attaching = false; btn.alpha = 1f }

        DocumentUploader.prepare(this, uri) { picked, error ->
            if (picked == null) { done(); Feedback.show(this, error ?: "Could not read that file"); return@prepare }
            db.collection("threads").document(threadId).get().addOnCompleteListener { t ->
                val clientId = (if (t.isSuccessful) t.result?.getString("clientId") else null)
                if (clientId == null) { done(); Feedback.show(this, "Could not open this conversation. Try again."); return@addOnCompleteListener }
                DocumentUploader.upload(this, db, picked, clientId, uid) { docId, uploadError ->
                    done()
                    if (docId == null) {
                        Feedback.show(this, "Attachment failed: ${uploadError ?: "unknown error"}")
                    } else {
                        val etBody = findViewById<EditText>(R.id.etMessageBody)
                        val text = etBody.text.toString().trim()
                        etBody.text.clear()
                        sendMessage(text, docId, picked.name)
                    }
                }
            }
        }
    }

    private fun sendMessage(text: String, attachmentDocId: String = "", attachmentName: String = "") {
        val uid = auth.currentUser?.uid ?: return
        val body = text.ifEmpty { "Sent an attachment" }
        val isUrgent = urgentKeywords.any { body.lowercase().contains(it) }
        val preview = if (attachmentDocId.isNotEmpty()) "Attachment: $attachmentName" else body

        val messageData = hashMapOf<String, Any>(
            "senderId" to uid,
            "messageBody" to body,
            "isUrgent" to isUrgent,
            "readBy" to listOf(uid),
            "sentTimestamp" to com.google.firebase.Timestamp.now()
        )
        if (attachmentDocId.isNotEmpty()) {
            messageData["attachmentDocId"] = attachmentDocId
            messageData["attachmentName"] = attachmentName
        }

        db.collection("threads").document(threadId)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                db.collection("threads").document(threadId)
                    .update(mapOf("lastMessagePreview" to preview, "isUrgent" to isUrgent, "hiddenBy" to emptyList<String>()))

                // Notify the other side: staff -> the client; client -> the accountant this conversation is with
                db.collection("threads").document(threadId).get()
                    .addOnSuccessListener { threadDoc ->
                        val clientId = threadDoc.getString("clientId") ?: return@addOnSuccessListener

                        if (uid != clientId) {
                            NotificationHelper.notify(
                                recipientUid = clientId,
                                type = if (isUrgent) "urgent_message" else "new_message",
                                title = if (isUrgent) "Urgent message" else "New message",
                                body = preview.take(80),
                                targetId = threadId
                            )
                        } else {
                            val threadAccountant = threadDoc.getString("accountantId") ?: ""
                            val notifyAccountant = { accountantId: String ->
                                NotificationHelper.notify(
                                    recipientUid = accountantId,
                                    type = if (isUrgent) "urgent_message" else "new_message",
                                    title = if (isUrgent) "Urgent message from client" else "New message from client",
                                    body = preview.take(80),
                                    targetId = threadId
                                )
                            }
                            if (threadAccountant.isNotEmpty()) {
                                notifyAccountant(threadAccountant)
                            } else {
                                db.collection("users").document(clientId).get()
                                    .addOnSuccessListener { clientDoc ->
                                        val accountantId = clientDoc.getString("assignedAccountantId")
                                        if (!accountantId.isNullOrEmpty()) notifyAccountant(accountantId)
                                    }
                            }
                        }
                    }
            }
    }
}
