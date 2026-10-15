package com.ntuli.apexledger

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
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
import com.ntuli.apexledger.util.NotificationHelper

class ConversationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MessageAdapter
    private lateinit var threadId: String

    // Kept so we can stop listening when the screen closes.
    private var messagesListener: ListenerRegistration? = null

    private val timeFormat = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())

    private val urgentKeywords = listOf("urgent", "deadline", "penalty", "asap", "overdue")

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
    }

    /** Title becomes "<subject> with <other person>". */
    private fun resolveCounterpartName(subject: String, toolbar: MaterialToolbar) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("threads").document(threadId).get().addOnSuccessListener { thread ->
            val clientId = thread.getString("clientId") ?: return@addOnSuccessListener
            if (uid == clientId) {
                db.collection("users").document(clientId).get().addOnSuccessListener { client ->
                    val accId = client.getString("assignedAccountantId")
                    if (!accId.isNullOrEmpty()) {
                        db.collection("users").document(accId).get().addOnSuccessListener { acc ->
                            acc.getString("full_name")?.let { toolbar.title = "$subject with $it" }
                        }
                    }
                }
            } else {
                db.collection("users").document(clientId).get().addOnSuccessListener { client ->
                    client.getString("full_name")?.let { toolbar.title = "$subject with $it" }
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
                                ?.let { timeFormat.format(it) } ?: ""
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

    private fun sendMessage(text: String) {
        val uid = auth.currentUser?.uid ?: return
        val isUrgent = urgentKeywords.any { text.lowercase().contains(it) }

        val messageData = hashMapOf(
            "senderId" to uid,
            "messageBody" to text,
            "isUrgent" to isUrgent,
            "readBy" to listOf(uid),
            "sentTimestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("threads").document(threadId)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                db.collection("threads").document(threadId)
                    .update(
                        mapOf(
                            "lastMessagePreview" to text,
                            "isUrgent" to isUrgent
                        )
                    )

                // Notify the other party: if staff sent it, notify the client;
                // if the client sent it, notify their assigned accountant.
                db.collection("threads").document(threadId).get()
                    .addOnSuccessListener { threadDoc ->
                        val clientId = threadDoc.getString("clientId") ?: return@addOnSuccessListener

                        if (uid != clientId) {
                            NotificationHelper.notify(
                                recipientUid = clientId,
                                type = if (isUrgent) "urgent_message" else "new_message",
                                title = if (isUrgent) "Urgent message" else "New message",
                                body = text.take(80)
                            )
                        } else {
                            db.collection("users").document(clientId).get()
                                .addOnSuccessListener { clientDoc ->
                                    val accountantId = clientDoc.getString("assignedAccountantId")
                                    if (!accountantId.isNullOrEmpty()) {
                                        NotificationHelper.notify(
                                            recipientUid = accountantId,
                                            type = if (isUrgent) "urgent_message" else "new_message",
                                            title = if (isUrgent) "Urgent message from client" else "New message from client",
                                            body = text.take(80)
                                        )
                                    }
                                }
                        }
                    }
            }
    }
}