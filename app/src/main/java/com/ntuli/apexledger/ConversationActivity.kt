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
import com.google.firebase.firestore.Query
import com.ntuli.apexledger.ui.ChatMessage
import com.ntuli.apexledger.ui.MessageAdapter
import com.ntuli.apexledger.util.NotificationHelper

class ConversationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MessageAdapter
    private lateinit var threadId: String

    // Keywords per your website's business rule for auto-flagging urgency
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

    private fun loadMessages() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("threads").document(threadId)
            .collection("messages")
            .orderBy("sentTimestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { result, _ ->
                if (result != null) {
                    val messages = result.documents.map { d ->
                        ChatMessage(
                            senderId = d.getString("senderId") ?: "",
                            messageBody = d.getString("messageBody") ?: "",
                            isUrgent = d.getBoolean("isUrgent") ?: false,
                            readBy = (d.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    }
                    adapter.updateData(messages)
                    findViewById<RecyclerView>(R.id.rvMessages).scrollToPosition(messages.size - 1)

                    // Mark any messages not sent by us as read, so unread counts
                    // elsewhere in the app (staff dashboard, client dashboard) stay accurate.
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

                // Notify the other party in this thread (whoever didn't just send this message).
                db.collection("threads").document(threadId).get()
                    .addOnSuccessListener { threadDoc ->
                        val clientId = threadDoc.getString("clientId") ?: return@addOnSuccessListener
                        val recipientUid = if (uid == clientId) null else clientId
                        // If the sender IS the client, we'd need to know which accountant to notify;
                        // that requires looking up assignedAccountantId on the client's user doc.
                        if (recipientUid != null) {
                            NotificationHelper.notify(
                                recipientUid = recipientUid,
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