package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.MessageThread
import com.ntuli.apexledger.ui.ThreadAdapter
import com.ntuli.apexledger.util.NotificationHelper

class MessagesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ThreadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvThreads)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val etSubject = findViewById<EditText>(R.id.etSubject)
        val etNewMessageBody = findViewById<EditText>(R.id.etNewMessageBody)
        val btnStartConversation = findViewById<MaterialButton>(R.id.btnStartConversation)

        adapter = ThreadAdapter(emptyList()) { thread ->
            val intent = Intent(this, ConversationActivity::class.java)
            intent.putExtra("threadId", thread.threadId)
            intent.putExtra("subject", thread.subject)
            startActivity(intent)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnStartConversation.setOnClickListener {
            val subject = etSubject.text.toString().trim()
            val firstMessage = etNewMessageBody.text.toString().trim()

            if (subject.isEmpty()) {
                Toast.makeText(this, "Please enter a subject", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createNewThread(subject, firstMessage)
            etSubject.text.clear()
            etNewMessageBody.text.clear()
        }

        loadThreads(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadThreads(findViewById(R.id.tvEmptyState))
    }

    private fun loadThreads(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("threads")
            .whereEqualTo("clientId", uid)
            .get()
            .addOnSuccessListener { result ->
                val threads = result.documents.map { d ->
                    MessageThread(
                        threadId = d.id,
                        subject = d.getString("subject") ?: "No subject",
                        lastMessagePreview = d.getString("lastMessagePreview") ?: "",
                        isUrgent = d.getBoolean("isUrgent") ?: false
                    )
                }
                adapter.updateData(threads)
                tvEmptyState.visibility = if (threads.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
    }

    private fun createNewThread(subject: String, firstMessage: String) {
        val uid = auth.currentUser?.uid ?: return
        val urgentKeywords = listOf("urgent", "deadline", "penalty", "asap", "overdue")
        val isUrgent = urgentKeywords.any { firstMessage.lowercase().contains(it) }

        val threadData = hashMapOf(
            "clientId" to uid,
            "subject" to subject,
            "lastMessagePreview" to firstMessage,
            "isUrgent" to isUrgent,
            "createdDate" to com.google.firebase.Timestamp.now()
        )
        db.collection("threads").add(threadData)
            .addOnSuccessListener { ref ->
                if (firstMessage.isNotEmpty()) {
                    val messageData = hashMapOf(
                        "senderId" to uid,
                        "messageBody" to firstMessage,
                        "isUrgent" to isUrgent,
                        "readBy" to listOf(uid),
                        "sentTimestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("threads").document(ref.id)
                        .collection("messages")
                        .add(messageData)

                    // Notify the assigned accountant about the new thread
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { clientDoc ->
                            val accountantId = clientDoc.getString("assignedAccountantId")
                            if (!accountantId.isNullOrEmpty()) {
                                NotificationHelper.notify(
                                    recipientUid = accountantId,
                                    type = if (isUrgent) "urgent_message" else "new_message",
                                    title = if (isUrgent) "Urgent message from client" else "New message from client",
                                    body = "$subject — ${firstMessage.take(80)}"
                                )
                            }
                        }
                }
                val intent = Intent(this, ConversationActivity::class.java)
                intent.putExtra("threadId", ref.id)
                intent.putExtra("subject", subject)
                startActivity(intent)
            }
    }
}