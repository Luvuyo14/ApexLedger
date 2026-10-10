package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
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
import com.ntuli.apexledger.ui.StaffThreadAdapter
import com.ntuli.apexledger.util.NotificationHelper

class StaffMessagesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffThreadAdapter

    private var assignedClientIds: List<String> = emptyList()
    private var assignedClientNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_messages)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvStaffThreads)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val etSubject = findViewById<EditText>(R.id.etSubject)
        val spinnerClient = findViewById<Spinner>(R.id.spinnerClient)
        val etNewMessageBody = findViewById<EditText>(R.id.etNewMessageBody)
        val btnStartConversation = findViewById<MaterialButton>(R.id.btnStartConversation)

        adapter = StaffThreadAdapter(emptyList()) { thread ->
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
            if (assignedClientIds.isEmpty()) {
                Toast.makeText(this, "You have no assigned clients yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedClientId = assignedClientIds[spinnerClient.selectedItemPosition]
            createNewThread(selectedClientId, subject, firstMessage)
            etSubject.text.clear()
            etNewMessageBody.text.clear()
        }

        loadAssignedClientsForSpinner(spinnerClient)
        loadAssignedThreads(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadAssignedThreads(findViewById(R.id.tvEmptyState))
    }

    private fun loadAssignedClientsForSpinner(spinnerClient: Spinner) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users")
            .whereEqualTo("assignedAccountantId", uid)
            .get()
            .addOnSuccessListener { result ->
                assignedClientIds = result.documents.map { it.id }
                assignedClientNames = result.documents.map { it.getString("full_name") ?: "Unknown client" }

                val displayNames = if (assignedClientNames.isEmpty()) listOf("No assigned clients") else assignedClientNames
                spinnerClient.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)
            }
    }

    private fun loadAssignedThreads(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereEqualTo("assignedAccountantId", uid)
            .get()
            .addOnSuccessListener { clientsResult ->
                val clientDocs = clientsResult.documents
                val clientIds = clientDocs.map { it.id }
                val clientNames = clientDocs.associate { it.id to (it.getString("full_name") ?: "Unknown client") }

                if (clientIds.isEmpty()) {
                    adapter.updateData(emptyList())
                    tvEmptyState.visibility = android.view.View.VISIBLE
                    return@addOnSuccessListener
                }

                val batches = clientIds.chunked(10)
                val fetched = mutableListOf<MessageThread>()
                var completedBatches = 0

                for (batch in batches) {
                    db.collection("threads")
                        .whereIn("clientId", batch)
                        .get()
                        .addOnSuccessListener { result ->
                            val threads = result.documents.map { d ->
                                MessageThread(
                                    threadId = d.id,
                                    clientId = d.getString("clientId") ?: "",
                                    subject = d.getString("subject") ?: "No subject",
                                    lastMessagePreview = d.getString("lastMessagePreview") ?: "",
                                    isUrgent = d.getBoolean("isUrgent") ?: false,
                                    clientName = clientNames[d.getString("clientId")] ?: "Unknown client"
                                )
                            }
                            fetched.addAll(threads)
                            completedBatches++
                            if (completedBatches == batches.size) {
                                adapter.updateData(fetched)
                                tvEmptyState.visibility = if (fetched.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                            }
                        }
                }
            }
    }

    private fun createNewThread(clientId: String, subject: String, firstMessage: String) {
        val uid = auth.currentUser?.uid ?: return
        val urgentKeywords = listOf("urgent", "deadline", "penalty", "asap", "overdue")
        val isUrgent = urgentKeywords.any { firstMessage.lowercase().contains(it) }

        val threadData = hashMapOf(
            "clientId" to clientId,
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

                    // Notify the client about the new thread
                    NotificationHelper.notify(
                        recipientUid = clientId,
                        type = if (isUrgent) "urgent_message" else "new_message",
                        title = if (isUrgent) "Urgent message from your accountant" else "New message from your accountant",
                        body = "$subject — ${firstMessage.take(80)}"
                    )
                }
                val intent = Intent(this, ConversationActivity::class.java)
                intent.putExtra("threadId", ref.id)
                intent.putExtra("subject", subject)
                startActivity(intent)
            }
    }
}