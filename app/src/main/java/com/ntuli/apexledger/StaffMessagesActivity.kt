package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.MessageThread
import com.ntuli.apexledger.ui.StaffThreadAdapter

class StaffMessagesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffThreadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_messages)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvStaffThreads)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        adapter = StaffThreadAdapter(emptyList()) { thread ->
            val intent = Intent(this, ConversationActivity::class.java)
            intent.putExtra("threadId", thread.threadId)
            intent.putExtra("subject", thread.subject)
            startActivity(intent)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadAssignedThreads(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadAssignedThreads(findViewById(R.id.tvEmptyState))
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
                                // Show most recently created/updated threads first isn't tracked
                                // yet at the thread level, so we keep natural Firestore order here.
                                adapter.updateData(fetched)
                                tvEmptyState.visibility = if (fetched.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                            }
                        }
                }
            }
    }
}