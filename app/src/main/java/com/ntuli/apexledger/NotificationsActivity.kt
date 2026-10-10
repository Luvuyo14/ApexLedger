package com.ntuli.apexledger

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ntuli.apexledger.ui.AppNotification
import com.ntuli.apexledger.ui.NotificationAdapter
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvNotifications)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        adapter = NotificationAdapter(emptyList()) { notification ->
            markAsRead(notification)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadNotifications(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadNotifications(findViewById(R.id.tvEmptyState))
    }

    private fun loadNotifications(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnFailureListener { e ->
                // Previously this failed silently (missing composite index) and
                // the screen just looked empty. Now the real reason is shown.
                tvEmptyState.visibility = View.VISIBLE
                tvEmptyState.text = "Couldn't load notifications: ${e.message}"
            }
            .addOnSuccessListener { result ->
                val notifications = result.documents.map { d ->
                    AppNotification(
                        notificationId = d.id,
                        type = d.getString("type") ?: "general",
                        title = d.getString("title") ?: "",
                        body = d.getString("body") ?: "",
                        isRead = d.getBoolean("isRead") ?: false,
                        timestampLabel = d.getTimestamp("createdAt")?.toDate()?.let { sdf.format(it) } ?: ""
                    )
                }
                adapter.updateData(notifications)
                tvEmptyState.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun markAsRead(notification: AppNotification) {
        if (notification.isRead) return
        db.collection("notifications").document(notification.notificationId)
            .update("isRead", true)
            .addOnSuccessListener {
                loadNotifications(findViewById(R.id.tvEmptyState))
            }
    }
}