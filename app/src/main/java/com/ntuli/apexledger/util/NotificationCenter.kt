package com.ntuli.apexledger.util

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ntuli.apexledger.NotificationsActivity
import com.ntuli.apexledger.R

/**
 * Watches the signed-in person's unread notifications in real time (free: a normal Firestore listener).
 * - keeps a live unread count that the bell badge shows
 * - raises a phone notification when a new one arrives while the app is running or in the background
 */
object NotificationCenter {
    private const val CHANNEL_ID = "apex_updates"

    private lateinit var app: Application
    private var registration: ListenerRegistration? = null
    private var firstSnapshot = true
    var unreadCount = 0
        private set
    private val observers = mutableMapOf<Any, (Int) -> Unit>()

    fun init(application: Application) {
        app = application
        createChannel()
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            registration?.remove()
            registration = null
            unreadCount = 0
            notifyObservers()
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            firstSnapshot = true
            registration = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("recipientUid", uid)
                .whereEqualTo("isRead", false)
                .addSnapshotListener { snap, error ->
                    if (error != null || snap == null) return@addSnapshotListener
                    unreadCount = snap.size()
                    notifyObservers()
                    if (!firstSnapshot) {
                        for (change in snap.documentChanges) {
                            if (change.type == DocumentChange.Type.ADDED && !change.document.metadata.hasPendingWrites()) {
                                showSystemNotification(
                                    change.document.id.hashCode(),
                                    change.document.getString("title") ?: "APEXLedger",
                                    change.document.getString("body") ?: "",
                                    change.document.getString("type") ?: "",
                                    change.document.getString("targetId") ?: ""
                                )
                            }
                        }
                    }
                    firstSnapshot = false
                }
        }
    }

    fun observe(owner: Any, callback: (Int) -> Unit) {
        observers[owner] = callback
        callback(unreadCount)
    }

    fun stopObserving(owner: Any) {
        observers.remove(owner)
    }

    private fun notifyObservers() {
        observers.values.toList().forEach { it(unreadCount) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New messages, ticket updates and document reviews"
            }
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun showSystemNotification(id: Int, title: String, body: String, type: String = "", targetId: String = "") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = when {
            type.contains("message") && targetId.isNotEmpty() ->
                Intent(app, com.ntuli.apexledger.ConversationActivity::class.java).putExtra("threadId", targetId)
            type.contains("document") && targetId.isNotEmpty() ->
                Intent(app, com.ntuli.apexledger.DocumentViewerActivity::class.java).putExtra("docId", targetId)
            else -> Intent(app, NotificationsActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(app, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try { NotificationManagerCompat.from(app).notify(id, n) } catch (_: SecurityException) { }
    }
}
