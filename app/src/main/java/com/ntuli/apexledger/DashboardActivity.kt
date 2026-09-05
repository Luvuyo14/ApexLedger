package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.ui.TicketAdapter
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var ticketAdapter: TicketAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val rvRecentTickets = findViewById<RecyclerView>(R.id.rvRecentTickets)

        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_menu_overflow_material)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        tvDate.text = sdf.format(Date())

        ticketAdapter = TicketAdapter(emptyList())
        rvRecentTickets.layoutManager = LinearLayoutManager(this)
        rvRecentTickets.adapter = ticketAdapter

        val uid = auth.currentUser?.uid

        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val fullName = doc.getString("full_name") ?: auth.currentUser?.email
                    val role = doc.getString("role") ?: "client"
                    tvWelcome.text = "Welcome back, $fullName!"
                    tvRole.text = "Role: $role"
                }
        }

        if (uid != null) {
            loadTicketsAndOverdue(uid)
            loadStatCount("documents", "clientId", uid, R.id.statDocuments, "Documents")
            loadUnreadMessagesCount(uid)
        }

        colorStatBar(R.id.statDocuments, R.color.primary)
        colorStatBar(R.id.statMessages, R.color.warning)
        colorStatBar(R.id.statTickets, R.color.success)
        colorStatBar(R.id.statOverdue, R.color.danger)

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { /* already here */ }
                R.id.nav_documents -> startActivity(Intent(this, DocumentsActivity::class.java))
                R.id.nav_messages -> startActivity(Intent(this, MessagesActivity::class.java))
                R.id.nav_tickets -> startActivity(Intent(this, TicketsActivity::class.java))
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_logout -> {
                    AuditLogger.logEvent("Client", "Logout", "Manual logout")
                    auth.signOut()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun loadTicketsAndOverdue(uid: String) {
        db.collection("tickets")
            .whereEqualTo("clientId", uid)
            .get()
            .addOnSuccessListener { result ->
                val allTickets = result.documents.map { doc ->
                    Ticket(
                        ticketId = doc.id,
                        ticketNumber = doc.getString("ticketNumber") ?: doc.id,
                        serviceType = doc.getString("serviceType") ?: "general",
                        status = doc.getString("status") ?: "open",
                        priority = doc.getString("priority") ?: "medium",
                        description = doc.getString("description") ?: "",
                        dueDate = doc.getString("dueDate") ?: ""
                    )
                }

                ticketAdapter.updateData(allTickets.take(5))
                setStat(R.id.statTickets, "Active Tickets", allTickets.count { it.status !in listOf("completed", "cancelled") }.toString())

                val overdueCount = allTickets.count { DateUtils.isOverdue(it.dueDate, it.status) }
                setStat(R.id.statOverdue, "Overdue", overdueCount.toString())
            }
    }

    private fun loadStatCount(collection: String, field: String, uid: String, includeId: Int, label: String) {
        db.collection(collection)
            .whereEqualTo(field, uid)
            .get()
            .addOnSuccessListener { result ->
                setStat(includeId, label, result.size().toString())
            }
    }

    private fun loadUnreadMessagesCount(uid: String) {
        setStat(R.id.statMessages, "Unread Messages", "0")
        var unreadCount = 0

        db.collection("threads")
            .whereEqualTo("clientId", uid)
            .get()
            .addOnSuccessListener { threadsResult ->
                for (threadDoc in threadsResult.documents) {
                    db.collection("threads").document(threadDoc.id)
                        .collection("messages")
                        .orderBy("sentTimestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { lastMsgResult ->
                            val lastMsg = lastMsgResult.documents.firstOrNull()
                            if (lastMsg != null) {
                                val readBy = lastMsg.get("readBy") as? List<*> ?: emptyList<String>()
                                val senderId = lastMsg.getString("senderId") ?: ""
                                if (senderId != uid && uid !in readBy) {
                                    unreadCount++
                                    setStat(R.id.statMessages, "Unread Messages", unreadCount.toString())
                                }
                            }
                        }
                }
            }
    }

    private fun setStat(includeId: Int, label: String, value: String) {
        val container = findViewById<android.view.View>(includeId)
        container.findViewById<TextView>(R.id.tvStatLabel).text = label
        container.findViewById<TextView>(R.id.tvStatValue).text = value
    }

    private fun colorStatBar(includeId: Int, colorRes: Int) {
        val container = findViewById<android.view.View>(includeId)
        container.findViewById<android.view.View>(R.id.statAccentBar)
            .setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }
}