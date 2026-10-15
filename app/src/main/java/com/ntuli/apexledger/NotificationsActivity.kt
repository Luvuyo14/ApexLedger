package com.ntuli.apexledger

import android.content.Intent
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
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvNotifications)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        adapter = NotificationAdapter(
            emptyList(),
            onClick = { notification ->
                markAsRead(notification)
                openTarget(notification)
            },
            onSelectionChanged = { count -> updateSelectionBar(count) }
        )
        loadMyRole()
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener { markAllRead() }
        findViewById<TextView>(R.id.btnSelectMode).setOnClickListener { adapter.startSelection() }
        findViewById<TextView>(R.id.btnSelectAll).setOnClickListener { adapter.selectAll() }
        findViewById<TextView>(R.id.btnCancelSelect).setOnClickListener { adapter.cancelSelection() }
        findViewById<TextView>(R.id.btnDeleteSelected).setOnClickListener { confirmDelete() }

        // Back first leaves selection mode, then closes the screen
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

        loadNotifications(tvEmptyState)
    }

    private fun updateSelectionBar(count: Int) {
        val selecting = adapter.selectionMode
        findViewById<View>(R.id.rowSelection).visibility = if (selecting) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowActions).visibility = if (selecting) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvSelectedCount).text = "$count selected"
        findViewById<TextView>(R.id.btnDeleteSelected).alpha = if (count > 0) 1f else 0.4f
    }

    private fun confirmDelete() {
        val ids = adapter.selectedList()
        if (ids.isEmpty()) {
            com.ntuli.apexledger.util.Feedback.show(this, "Please select the notifications you want to delete")
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (ids.size == 1) "Delete this notification?" else "Delete ${ids.size} notifications?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // Firestore allows up to 500 changes in one batch
                var remaining = (ids.size + 499) / 500
                var failed = false
                ids.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(db.collection("notifications").document(it)) }
                    batch.commit().addOnCompleteListener { task ->
                        if (!task.isSuccessful) failed = true
                        remaining--
                        if (remaining == 0) {
                            if (failed) {
                                com.ntuli.apexledger.util.Feedback.show(this, "Failed to delete some notifications. Check your connection and the Firestore rules.")
                            } else {
                                com.ntuli.apexledger.util.Feedback.show(this, if (ids.size == 1) "Notification deleted" else "${ids.size} notifications deleted")
                            }
                            adapter.cancelSelection()
                            loadNotifications(findViewById(R.id.tvEmptyState))
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun markAllRead() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    com.ntuli.apexledger.util.Feedback.show(this, "You are all caught up")
                    return@addOnSuccessListener
                }
                val batch = db.batch()
                result.documents.forEach { batch.update(it.reference, "isRead", true) }
                batch.commit().addOnSuccessListener {
                    com.ntuli.apexledger.util.Feedback.show(this, "All notifications marked as read")
                    loadNotifications(findViewById(R.id.tvEmptyState))
                }
            }
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
            .get()
            .addOnFailureListener { e ->
                // Previously this failed silently (missing composite index) and
                // the screen just looked empty. Now the real reason is shown.
                tvEmptyState.visibility = View.VISIBLE
                tvEmptyState.text = "Couldn't load notifications: ${e.message}"
            }
            .addOnSuccessListener { result ->
                val notifications = result.documents.sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }.take(50).map { d ->
                    AppNotification(
                        notificationId = d.id,
                        type = d.getString("type") ?: "general",
                        title = d.getString("title") ?: "",
                        body = d.getString("body") ?: "",
                        isRead = d.getBoolean("isRead") ?: false,
                        timestampLabel = d.getTimestamp("createdAt")?.toDate()?.let { sdf.format(it) } ?: "",
                        targetId = d.getString("targetId") ?: ""
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

    private var myRole = "client"

    private fun loadMyRole() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { myRole = it.getString("role") ?: "client" }
    }

    /** Takes the person to the message, document or ticket the notification is about. */
    private fun openTarget(n: AppNotification) {
        val staff = myRole != "client"
        when {
            n.type.contains("message") && n.targetId.isNotEmpty() ->
                startActivity(Intent(this, ConversationActivity::class.java).putExtra("threadId", n.targetId))
            n.type.contains("document") && n.targetId.isNotEmpty() ->
                startActivity(Intent(this, DocumentViewerActivity::class.java).putExtra("docId", n.targetId))
            n.type.contains("ticket") ->
                startActivity(Intent(this, if (staff) StaffTicketsActivity::class.java else TicketsActivity::class.java))
        }
    }
}
