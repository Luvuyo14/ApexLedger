package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.MessageThread
import com.ntuli.apexledger.ui.ThreadAdapter

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
        val fab = findViewById<FloatingActionButton>(R.id.fabNewMessage)

        adapter = ThreadAdapter(emptyList()) { thread ->
            val intent = Intent(this, ConversationActivity::class.java)
            intent.putExtra("threadId", thread.threadId)
            intent.putExtra("subject", thread.subject)
            startActivity(intent)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        fab.setOnClickListener {
            createNewThread()
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

    private fun createNewThread() {
        val uid = auth.currentUser?.uid ?: return
        val threadData = hashMapOf(
            "clientId" to uid,
            "subject" to "New conversation",
            "lastMessagePreview" to "",
            "isUrgent" to false,
            "createdDate" to com.google.firebase.Timestamp.now()
        )
        db.collection("threads").add(threadData)
            .addOnSuccessListener { ref ->
                val intent = Intent(this, ConversationActivity::class.java)
                intent.putExtra("threadId", ref.id)
                intent.putExtra("subject", "New conversation")
                startActivity(intent)
            }
    }
}