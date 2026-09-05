package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.ui.TicketAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StaffDashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: TicketAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_dashboard)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvDepartmentBadge = findViewById<TextView>(R.id.tvDepartmentBadge)
        val rv = findViewById<RecyclerView>(R.id.rvAllTickets)
        val btnViewAllTickets = findViewById<Button>(R.id.btnViewAllTickets)
        val btnReviewDocuments = findViewById<Button>(R.id.btnReviewDocuments)
        val btnClientMessages = findViewById<Button>(R.id.btnClientMessages)
        val btnReports = findViewById<Button>(R.id.btnReports)
        val btnAdminPanel = findViewById<Button>(R.id.btnAdminPanel)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        tvDate.text = sdf.format(Date())

        adapter = TicketAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val fullName = doc.getString("full_name") ?: auth.currentUser?.email
                    val role = doc.getString("role") ?: "accountant"
                    val department = doc.getString("department") ?: ""

                    tvWelcome.text = "Welcome back, $fullName!"

                    if (department.isNotEmpty()) {
                        tvDepartmentBadge.text = department
                        tvDepartmentBadge.visibility = android.view.View.VISIBLE
                    }

                    btnAdminPanel.visibility = if (role == "admin") android.view.View.VISIBLE else android.view.View.GONE
                    btnReports.visibility = if (role == "admin" || role == "manager") android.view.View.VISIBLE else android.view.View.GONE
                }
        }

        setStat(R.id.statMyClients, "My Clients", "0")
        setStat(R.id.statOpenTickets, "Open Tickets", "0")
        setStat(R.id.statUnreadMessages, "Unread Messages", "0")
        setStat(R.id.statOverdue, "Overdue", "0")

        colorStatBar(R.id.statMyClients, R.color.primary)
        colorStatBar(R.id.statOpenTickets, R.color.warning)
        colorStatBar(R.id.statUnreadMessages, R.color.purple)
        colorStatBar(R.id.statOverdue, R.color.danger)

        loadAssignedClientsTicketsAndMessages()

        btnViewAllTickets.setOnClickListener {
            startActivity(Intent(this, StaffTicketsActivity::class.java))
        }
        btnReviewDocuments.setOnClickListener {
            startActivity(Intent(this, StaffDocumentsActivity::class.java))
        }
        btnClientMessages.setOnClickListener {
            startActivity(Intent(this, StaffMessagesActivity::class.java))
        }
        btnReports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        btnAdminPanel.setOnClickListener {
            startActivity(Intent(this, AdminPanelActivity::class.java))
        }
        btnLogout.setOnClickListener {
            com.ntuli.apexledger.util.AuditLogger.logEvent("User", "Logout", "Manual logout")
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadAssignedClientsTicketsAndMessages()
    }

    private fun loadAssignedClientsTicketsAndMessages() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereEqualTo("assignedAccountantId", uid)
            .get()
            .addOnSuccessListener { clientsResult ->
                val clientIds = clientsResult.documents.map { it.id }
                setStat(R.id.statMyClients, "My Clients", clientIds.size.toString())

                if (clientIds.isEmpty()) {
                    adapter.updateData(emptyList())
                    setStat(R.id.statOpenTickets, "Open Tickets", "0")
                    setStat(R.id.statOverdue, "Overdue", "0")
                    setStat(R.id.statUnreadMessages, "Unread Messages", "0")
                    return@addOnSuccessListener
                }

                val batches = clientIds.chunked(10)
                val allOpenTickets = mutableListOf<Ticket>()
                var overdueCount = 0
                var unreadCount = 0
                var completedBatches = 0

                for (batch in batches) {
                    db.collection("tickets")
                        .whereIn("clientId", batch)
                        .whereIn("status", listOf("open", "in_progress", "awaiting_documents"))
                        .get()
                        .addOnSuccessListener { result ->
                            val tickets = result.documents.map { d ->
                                Ticket(
                                    ticketId = d.id,
                                    ticketNumber = d.getString("ticketNumber") ?: d.id,
                                    serviceType = d.getString("serviceType") ?: "general",
                                    status = d.getString("status") ?: "open",
                                    priority = d.getString("priority") ?: "medium",
                                    description = d.getString("description") ?: "",
                                    dueDate = d.getString("dueDate") ?: ""
                                )
                            }
                            allOpenTickets.addAll(tickets)
                            completedBatches++
                            if (completedBatches == batches.size) {
                                adapter.updateData(allOpenTickets)
                                setStat(R.id.statOpenTickets, "Open Tickets", allOpenTickets.size.toString())
                            }
                        }

                    db.collection("tickets")
                        .whereIn("clientId", batch)
                        .whereEqualTo("status", "overdue")
                        .get()
                        .addOnSuccessListener { result ->
                            overdueCount += result.size()
                            setStat(R.id.statOverdue, "Overdue", overdueCount.toString())
                        }

                    db.collection("threads")
                        .whereIn("clientId", batch)
                        .get()
                        .addOnSuccessListener { threadsResult ->
                            // Unread here is approximated as threads with a message and no staff
                            // reply flagged yet; a proper unread-per-user tracker would need a
                            // 'readBy' field on messages, which we haven't modeled yet.
                            unreadCount += threadsResult.size()
                            setStat(R.id.statUnreadMessages, "Unread Messages", unreadCount.toString())
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