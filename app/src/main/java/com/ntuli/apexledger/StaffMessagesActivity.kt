package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.MessageThread
import com.ntuli.apexledger.ui.StaffThreadAdapter
import com.ntuli.apexledger.util.Feedback
import com.ntuli.apexledger.util.NotificationHelper
import com.ntuli.apexledger.util.StaffData

class StaffMessagesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffThreadAdapter

    // Clients this accountant can start a NEW conversation with
    private var pickableClientIds: List<String> = emptyList()
    private var pickableRoles: Map<String, String> = emptyMap()
    private var pickableClientService: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_messages)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvStaffThreads)
        val etSubject = findViewById<EditText>(R.id.etSubject)
        val spinnerClient = findViewById<Spinner>(R.id.spinnerClient)
        val etNewMessageBody = findViewById<EditText>(R.id.etNewMessageBody)
        val btnStartConversation = findViewById<MaterialButton>(R.id.btnStartConversation)

        adapter = StaffThreadAdapter(emptyList(), onSelectionChanged = { count -> updateChatBar(count) }) { thread ->
            val intent = Intent(this, ConversationActivity::class.java)
            intent.putExtra("threadId", thread.threadId)
            intent.putExtra("subject", thread.subject)
            startActivity(intent)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        setupChatSelection()

        btnStartConversation.setOnClickListener {
            val subject = etSubject.text.toString().trim()
            val firstMessage = etNewMessageBody.text.toString().trim()

            if (pickableClientIds.isEmpty()) {
                Feedback.show(this, "There is no client left to start a conversation with")
                return@setOnClickListener
            }
            if (subject.isEmpty()) {
                Feedback.show(this, "Please enter a subject")
                return@setOnClickListener
            }

            val clientId = pickableClientIds[spinnerClient.selectedItemPosition]
            createNewThread(clientId, pickableClientService[clientId] ?: "", subject, firstMessage)
            etSubject.text.clear()
            etNewMessageBody.text.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Loads the conversations, then lists only the assigned clients who are not in them yet. */
    private fun refresh() {
        val uid = auth.currentUser?.uid ?: return
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val spinnerClient = findViewById<Spinner>(R.id.spinnerClient)
        val btnStart = findViewById<MaterialButton>(R.id.btnStartConversation)

        StaffData.loadThreads(db, uid) { res ->
            val threads = res.docs.filter { d ->
                (d.get("hiddenBy") as? List<*>)?.contains(uid) != true
            }.map { d ->
                MessageThread(
                    threadId = d.id,
                    clientId = d.getString("clientId") ?: "",
                    subject = d.getString("subject") ?: "No subject",
                    lastMessagePreview = d.getString("lastMessagePreview") ?: "",
                    isUrgent = d.getBoolean("isUrgent") ?: false,
                    clientName = res.clientNames[d.getString("clientId")] ?: "Unknown client",
                    serviceType = d.getString("serviceType") ?: ""
                )
            }
            tvEmptyState.visibility = if (threads.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            adapter.updateData(threads)
            com.ntuli.apexledger.util.Unread.mark(db, uid, threads) { adapter.updateData(it) }

            // People already in the Conversations box are removed from the picker
            val inConversations = threads.map { it.clientId }.toSet()
            fun fill(docs: List<com.google.firebase.firestore.DocumentSnapshot>, emptyText: String) {
                val free = docs.filter { it.id !in inConversations && it.id != uid && it.getBoolean("active") != false }
                pickableClientIds = free.map { it.id }
                pickableClientService = free.associate { it.id to (it.getString("servicePreference") ?: "") }
                pickableRoles = free.associate { it.id to (it.getString("role") ?: "client") }
                val names = free.map {
                    val role = it.getString("role") ?: "client"
                    val n = it.getString("full_name") ?: "Unknown"
                    if (role == "client") n else "$n (${com.ntuli.apexledger.util.Labels.title(role)})"
                }
                val shown = if (names.isNotEmpty()) names else listOf(emptyText)
                spinnerClient.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shown)
                spinnerClient.isEnabled = names.isNotEmpty()
                btnStart.isEnabled = names.isNotEmpty()
            }
            db.collection("users").document(uid).get().addOnSuccessListener { me ->
                if (me.getString("role") == "admin") {
                    // An administrator can message everyone: every client and every staff member
                    db.collection("users").get().addOnSuccessListener { all ->
                        fill(all.documents.sortedBy { it.getString("full_name")?.lowercase() ?: "" }, "Everyone already has a conversation")
                    }
                } else {
                    db.collection("users").whereEqualTo("assignedAccountantId", uid).get()
                        .addOnSuccessListener { clients ->
                            fill(clients.documents, if (clients.isEmpty) "You have no assigned clients" else "All your clients already have a conversation")
                        }
                }
            }
        }
    }

    private fun createNewThread(clientId: String, serviceType: String, subject: String, firstMessage: String) {
        val uid = auth.currentUser?.uid ?: return
        val urgentKeywords = listOf("urgent", "deadline", "penalty", "asap", "overdue")
        val isUrgent = urgentKeywords.any { firstMessage.lowercase().contains(it) }

        db.collection("users").document(uid).get().addOnCompleteListener { meTask ->
            val myName = (if (meTask.isSuccessful) meTask.result?.getString("full_name") else null) ?: "Your accountant"
            val threadData = hashMapOf(
                "clientId" to clientId,
                "subject" to subject,
                "serviceType" to serviceType,
                "accountantId" to uid,
                "accountantName" to myName,
                "lastMessagePreview" to firstMessage,
                "isUrgent" to isUrgent,
                "createdDate" to com.google.firebase.Timestamp.now()
            )
            db.collection("threads").add(threadData)
                .addOnSuccessListener { ref ->
                    if (firstMessage.isNotEmpty()) {
                        db.collection("threads").document(ref.id).collection("messages").add(
                            hashMapOf(
                                "senderId" to uid,
                                "messageBody" to firstMessage,
                                "isUrgent" to isUrgent,
                                "readBy" to listOf(uid),
                                "sentTimestamp" to com.google.firebase.Timestamp.now()
                            )
                        )
                        NotificationHelper.notify(
                            recipientUid = clientId,
                            type = if (isUrgent) "urgent_message" else "new_message",
                            title = if (pickableRoles[clientId] ?: "client" != "client") {
                                if (isUrgent) "Urgent message from $myName" else "New message from $myName"
                            } else if (isUrgent) "Urgent message from your accountant" else "New message from your accountant",
                            body = "$subject — ${firstMessage.take(80)}",
                            targetId = ref.id
                        )
                    }
                    val intent = Intent(this, ConversationActivity::class.java)
                    intent.putExtra("threadId", ref.id)
                    intent.putExtra("subject", subject)
                    startActivity(intent)
                }
                .addOnFailureListener { e -> Feedback.show(this, "Failed to start the conversation: ${e.message}") }
        }
    }

    // ---------------------------------------------------------------- delete chats
    private fun setupChatSelection() {
        findViewById<TextView>(R.id.btnSelectChats).setOnClickListener { adapter.startSelection() }
        findViewById<TextView>(R.id.btnChatSelectAll).setOnClickListener { adapter.selectAll() }
        findViewById<TextView>(R.id.btnChatCancel).setOnClickListener { adapter.cancelSelection() }
        findViewById<TextView>(R.id.btnChatDelete).setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            com.ntuli.apexledger.util.ChatActions.confirmDelete(this, db, uid, adapter.selectedList()) {
                adapter.cancelSelection()
                onResume()
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.selectionMode) {
                    adapter.cancelSelection()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun updateChatBar(count: Int) {
        val selecting = adapter.selectionMode
        findViewById<android.view.View>(R.id.rowChatSelection).visibility = if (selecting) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<android.view.View>(R.id.btnSelectChats).visibility = if (selecting) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<TextView>(R.id.tvChatSelectedCount).text = "$count selected"
        findViewById<TextView>(R.id.btnChatDelete).alpha = if (count > 0) 1f else 0.4f
    }
}
