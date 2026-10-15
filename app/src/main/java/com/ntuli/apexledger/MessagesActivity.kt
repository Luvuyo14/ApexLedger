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

    private val serviceTypes = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit")

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ThreadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvThreads)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val etSubject = findViewById<EditText>(R.id.etSubject)
        val etNewMessageBody = findViewById<EditText>(R.id.etNewMessageBody)
        val btnStartConversation = findViewById<MaterialButton>(R.id.btnStartConversation)
        val spinnerService = findViewById<android.widget.Spinner>(R.id.spinnerNewMessageService)
        spinnerService.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            serviceTypes.map { com.ntuli.apexledger.util.Labels.service(it) }
        )

        adapter = ThreadAdapter(emptyList(), onSelectionChanged = { count -> updateChatBar(count) }) { thread ->
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

            if (subject.isEmpty()) {
                com.ntuli.apexledger.util.Feedback.show(this, "Please enter a subject")
                return@setOnClickListener
            }

            createNewThread(subject, firstMessage, serviceTypes[spinnerService.selectedItemPosition])
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
                val threads = result.documents.filter { d ->
                    (d.get("hiddenBy") as? List<*>)?.contains(uid) != true
                }.map { d ->
                    MessageThread(
                        threadId = d.id,
                        subject = d.getString("subject") ?: "No subject",
                        lastMessagePreview = d.getString("lastMessagePreview") ?: "",
                        isUrgent = d.getBoolean("isUrgent") ?: false,
                        accountantName = d.getString("accountantName") ?: "",
                        serviceType = d.getString("serviceType") ?: ""
                    )
                }
                tvEmptyState.visibility = if (threads.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                adapter.updateData(threads)
                com.ntuli.apexledger.util.Unread.mark(db, uid, threads) { adapter.updateData(it) }
            }
    }

    private fun createNewThread(subject: String, firstMessage: String, serviceType: String) {
        val uid = auth.currentUser?.uid ?: return
        val urgentKeywords = listOf("urgent", "deadline", "penalty", "asap", "overdue")
        val isUrgent = urgentKeywords.any { firstMessage.lowercase().contains(it) }

        // Route to the accountant who handles this service (the client's own one if they handle it)
        com.ntuli.apexledger.util.AccountantRouter.pick(db, uid, serviceType, "threads") { pick ->
            val threadData = hashMapOf(
                "clientId" to uid,
                "subject" to subject,
                "serviceType" to serviceType,
                "accountantId" to (pick?.id ?: ""),
                "accountantName" to (pick?.name ?: ""),
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
                    }
                    if (pick != null) {
                        NotificationHelper.notify(
                            recipientUid = pick.id,
                            type = if (isUrgent) "urgent_message" else "new_message",
                            title = if (isUrgent) "Urgent message from client" else "New message from client",
                            body = "$subject — ${firstMessage.take(80)}",
                            targetId = ref.id
                        )
                    } else {
                        com.ntuli.apexledger.util.Feedback.show(this, "No accountant handles this service yet. An administrator will pick it up.")
                    }
                    val intent = Intent(this, ConversationActivity::class.java)
                    intent.putExtra("threadId", ref.id)
                    intent.putExtra("subject", subject)
                    startActivity(intent)
                }
                .addOnFailureListener { e ->
                    com.ntuli.apexledger.util.Feedback.show(this, "Failed to start the conversation: ${e.message}")
                }
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
