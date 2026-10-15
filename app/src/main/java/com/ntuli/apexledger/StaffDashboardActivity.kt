package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.ui.TicketAdapter
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.DateUtils
import com.ntuli.apexledger.util.Labels
import com.ntuli.apexledger.util.StatCards
import com.ntuli.apexledger.util.DashRows
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StaffDashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: TicketAdapter

    private var unreadCount = 0
    private var isOrgWide = false
    private var staffRole = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_dashboard)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvAvatar = findViewById<TextView>(R.id.tvAvatar)
        val tvDepartmentBadge = findViewById<TextView>(R.id.tvDepartmentBadge)
        val rv = findViewById<RecyclerView>(R.id.rvAllTickets)

        toolbar.setNavigationIcon(R.drawable.ic_menu_hamburger)
        toolbar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        // Website sidebar look: "STAFF PORTAL" label, selected item highlighted,
        // admin-only entries hidden until we know the role, red Logout
        navigationView.getHeaderView(0).findViewById<TextView>(R.id.tvPortalLabel).text = "STAFF PORTAL"
        navigationView.setCheckedItem(R.id.nav_dashboard)
        val menu = navigationView.menu
        menu.findItem(R.id.nav_reports).isVisible = false
        menu.findItem(R.id.nav_admin_section).isVisible = false
        menu.findItem(R.id.nav_logout).let { item ->
            val red = android.text.SpannableString(item.title)
            red.setSpan(
                android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.danger)),
                0, red.length, 0
            )
            item.title = red
        }

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
                    tvAvatar.text = StatCards.initials(fullName)

                    isOrgWide = role == "admin" || role == "manager"
                    staffRole = role

                    // "Taxation (accountant)" -> "Taxation Duty" (same wording as the website)
                    val duty = department.substringBefore("(").trim()
                    if (duty.isNotEmpty() && role == "accountant") {
                        tvDepartmentBadge.text = "$duty Duty"
                        tvDepartmentBadge.visibility = android.view.View.VISIBLE
                    } else if (department.isNotEmpty()) {
                        tvDepartmentBadge.text = department
                        tvDepartmentBadge.visibility = android.view.View.VISIBLE
                    }
                    loadData()

                    menu.findItem(R.id.nav_admin_section).isVisible = role == "admin"
                    menu.findItem(R.id.nav_reports).isVisible = role == "admin" || role == "manager"
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
        StatCards.decorate(findViewById(R.id.statMyClients), R.drawable.ic_stat_people, R.color.primary)
        StatCards.decorate(findViewById(R.id.statOpenTickets), R.drawable.ic_stat_ticket, R.color.warning)
        StatCards.decorate(findViewById(R.id.statUnreadMessages), R.drawable.ic_stat_mail, R.color.purple)
        StatCards.decorate(findViewById(R.id.statOverdue), R.drawable.ic_stat_warning, R.color.danger)

        loadData()

        findViewById<TextView>(R.id.btnViewAllTickets).setOnClickListener {
            startActivity(Intent(this, StaffTicketsActivity::class.java))
        }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { /* already here */ }
                R.id.nav_documents -> startActivity(Intent(this, StaffDocumentsActivity::class.java))
                R.id.nav_messages -> startActivity(Intent(this, StaffMessagesActivity::class.java))
                R.id.nav_tickets -> startActivity(Intent(this, StaffTicketsActivity::class.java))
                R.id.nav_reports -> startActivity(Intent(this, ReportsActivity::class.java))
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_admin -> startActivity(Intent(this, AdminPanelActivity::class.java))
                R.id.nav_logout -> {
                    AuditLogger.logEvent("User", "Logout", "Manual logout")
                    auth.signOut()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        findViewById<View>(R.id.qaReview).setOnClickListener { startActivity(Intent(this, StaffDocumentsActivity::class.java)) }
        findViewById<View>(R.id.qaMessages).setOnClickListener { startActivity(Intent(this, StaffMessagesActivity::class.java)) }

        findViewById<android.view.View>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }


    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun showTickets(list: List<Ticket>) {
        adapter.updateData(list)
        findViewById<TextView>(R.id.tvNoTickets).visibility =
            if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun loadData() {
        if (staffRole.isEmpty()) {
            // role not known yet; the profile callback calls loadData() again
            return
        }
        if (isOrgWide) loadOrgWideStats() else loadAssignedClientsTicketsAndMessages()
    }

    /**
     * Admin / manager dashboard — same four cards as the website:
     * Active Clients, Open Tickets, Accountants, Overdue.
     */
    private fun loadOrgWideStats() {
        setStat(R.id.statMyClients, "Active Clients", "0")
        setStat(R.id.statOpenTickets, "Open Tickets", "0")
        setStat(R.id.statUnreadMessages, "Accountants", "0")
        setStat(R.id.statOverdue, "Overdue", "0")
        colorStatBar(R.id.statUnreadMessages, R.color.success)
        StatCards.decorate(findViewById(R.id.statUnreadMessages), R.drawable.ic_stat_briefcase, R.color.success)

        db.collection("users").get().addOnSuccessListener { users ->
            val clientNames = users.documents.associate { it.id to (it.getString("full_name") ?: "Unknown client") }
            setStat(R.id.statMyClients, "Active Clients", users.documents.count { it.getString("role") == "client" && it.getBoolean("active") != false }.toString())
            setStat(R.id.statUnreadMessages, "Accountants", users.documents.count { it.getString("role") == "accountant" && it.getBoolean("active") != false }.toString())

            db.collection("tickets").get().addOnSuccessListener { result ->
                val all = result.documents.map { d ->
                    Ticket(
                        ticketId = d.id,
                        ticketNumber = d.getString("ticketNumber") ?: d.id,
                        serviceType = d.getString("serviceType") ?: "general",
                        status = d.getString("status") ?: "open",
                        priority = d.getString("priority") ?: "medium",
                        description = d.getString("description") ?: "",
                        dueDate = d.getString("dueDate") ?: "",
                        clientName = clientNames[d.getString("clientId")] ?: "Unknown client"
                    ) to (d.getTimestamp("createdDate")?.seconds ?: 0L)
                }
                val open = all.map { it.first }.filter { it.status !in listOf("completed", "cancelled") }
                setStat(R.id.statOpenTickets, "Open Tickets", open.size.toString())
                setStat(R.id.statOverdue, "Overdue", open.count { DateUtils.isOverdue(it.dueDate, it.status) }.toString())
                showTickets(all.sortedByDescending { it.second }.map { it.first }.take(5))
                renderOverdueAlert(open.filter { DateUtils.isOverdue(it.dueDate, it.status) })
            }

            renderUnassigned(users.documents)
        }
    }

    /** Admin/manager: red card with the worst overdue tickets. Hidden when none. */
    private fun renderOverdueAlert(overdue: List<Ticket>) {
        val card = findViewById<View>(R.id.cardOverdueAlert)
        if (overdue.isEmpty()) { card.visibility = View.GONE; return }
        val container = DashRows.section(card, "Overdue Tickets (${overdue.size})", R.color.danger_dark)
        for (t in overdue.sortedByDescending { DateUtils.daysOverdue(it.dueDate) }.take(5)) {
            DashRows.addRow(
                container, t.ticketNumber, "${t.clientName} • ${Labels.service(t.serviceType)}",
                "${DateUtils.daysOverdue(t.dueDate)}d overdue", R.drawable.badge_status_overdue, R.color.danger_dark
            )
        }
        card.visibility = View.VISIBLE
    }

    /** Clients with no accountant. Admins get an Assign button that suggests the best accountant. */
    private fun renderUnassigned(users: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val card = findViewById<View>(R.id.cardUnassigned)
        val unassigned = users.filter {
            it.getString("role") == "client" && it.getBoolean("active") != false &&
                    it.getString("assignedAccountantId").isNullOrEmpty()
        }
        if (unassigned.isEmpty()) { card.visibility = View.GONE; return }

        val accountants = users.filter { it.getString("role") == "accountant" && it.getBoolean("active") != false }
        val clientCounts = users.filter { it.getString("role") == "client" }
            .groupingBy { it.getString("assignedAccountantId") ?: "" }.eachCount()

        val container = DashRows.section(card, "Clients Without an Accountant (${unassigned.size})")
        for (client in unassigned) {
            val name = client.getString("full_name") ?: "Unnamed client"
            val service = client.getString("servicePreference") ?: ""
            DashRows.addRow(
                container, name, if (service.isEmpty()) "No service chosen" else "Wants: ${Labels.service(service)}",
                if (staffRole == "admin") "Assign" else null,
                R.drawable.bg_button_primary_small, R.color.on_primary
            ) { showAssignDialog(client, accountants, clientCounts) }
        }
        card.visibility = View.VISIBLE
    }

    private fun showAssignDialog(
        client: com.google.firebase.firestore.DocumentSnapshot,
        accountants: List<com.google.firebase.firestore.DocumentSnapshot>,
        clientCounts: Map<String, Int>
    ) {
        if (accountants.isEmpty()) {
            com.ntuli.apexledger.util.Feedback.show(this, "No active accountants available")
            return
        }
        val keyword = when (client.getString("servicePreference")) {
            "tax_return" -> "Taxation"
            "payroll" -> "Payroll"
            "bookkeeping" -> "Bookkeeping"
            "advisory" -> "Advisory"
            "audit" -> "Audit"
            else -> ""
        }
        // Best first: same duty as the client's service, then fewest clients
        val ranked = accountants.sortedWith(
            compareByDescending<com.google.firebase.firestore.DocumentSnapshot> {
                keyword.isNotEmpty() && (it.getString("department") ?: "").startsWith(keyword)
            }.thenBy { clientCounts[it.id] ?: 0 }
        )
        val labels = ranked.map {
            val duty = (it.getString("department") ?: "").substringBefore("(").trim()
            val n = clientCounts[it.id] ?: 0
            val match = keyword.isNotEmpty() && (it.getString("department") ?: "").startsWith(keyword)
            "${it.getString("full_name") ?: "Accountant"} - $duty - $n client${if (n == 1) "" else "s"}" +
                    if (match) " (matches service)" else ""
        }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Assign ${client.getString("full_name") ?: "client"}")
            .setItems(labels) { _, which ->
                val acc = ranked[which]
                db.collection("users").document(client.id)
                    .update("assignedAccountantId", acc.id)
                    .addOnSuccessListener {
                        AuditLogger.logEvent(
                            "Admin", "Assign Client",
                            "${client.getString("full_name")} assigned to accountant ${acc.getString("full_name")}"
                        )
                        com.ntuli.apexledger.util.Feedback.show(this, "Assigned")
                        loadData()
                    }
                    .addOnFailureListener { e ->
                        com.ntuli.apexledger.util.Feedback.show(this, "Failed: ${e.message}")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Accountant: open tickets due within 7 days (or already overdue), nearest first. */
    private fun renderDueThisWeek(open: List<Ticket>) {
        val card = findViewById<View>(R.id.cardDueWeek)
        val soon = open.filter { (DateUtils.daysUntil(it.dueDate) ?: Long.MAX_VALUE) <= 7 }
            .sortedBy { it.dueDate }.take(5)
        if (soon.isEmpty()) { card.visibility = View.GONE; return }
        val container = DashRows.section(card, "Due This Week")
        for (t in soon) {
            val chip = DashRows.dueChip(t.dueDate)
            DashRows.addRow(
                container, t.ticketNumber, "${t.clientName} • ${Labels.service(t.serviceType)}",
                chip?.first, chip?.second ?: R.drawable.badge_status_open, chip?.third ?: R.color.navy
            )
        }
        card.visibility = View.VISIBLE
    }

    private fun loadAssignedClientsTicketsAndMessages() {
        val uid = auth.currentUser?.uid ?: return
        unreadCount = 0
        setStat(R.id.statUnreadMessages, "Unread Messages", "0")

        db.collection("users")
            .whereEqualTo("assignedAccountantId", uid)
            .get()
            .addOnSuccessListener { clientsResult ->
                val clientIds = clientsResult.documents.map { it.id }
                val clientNames = clientsResult.documents.associate { it.id to (it.getString("full_name") ?: "") }
                setStat(R.id.statMyClients, "My Clients", clientIds.size.toString())

                if (clientIds.isEmpty()) {
                    showTickets(emptyList())
                    renderDueThisWeek(emptyList())
                    setStat(R.id.statOpenTickets, "Open Tickets", "0")
                    setStat(R.id.statOverdue, "Overdue", "0")
                    setStat(R.id.statUnreadMessages, "Unread Messages", "0")
                    return@addOnSuccessListener
                }

                val batches = clientIds.chunked(10)
                val allOpenTickets = mutableListOf<Ticket>()
                var completedBatches = 0

                for (batch in batches) {
                    db.collection("tickets")
                        .whereIn("clientId", batch)
                        .whereIn("status", listOf("open", "in_progress", "awaiting_documents", "under_review"))
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
                                    dueDate = d.getString("dueDate") ?: "",
                                    clientName = clientNames[d.getString("clientId")] ?: ""
                                )
                            }
                            allOpenTickets.addAll(tickets)
                            completedBatches++
                            if (completedBatches == batches.size) {
                                showTickets(allOpenTickets.take(5))
                                renderDueThisWeek(allOpenTickets)
                                setStat(R.id.statOpenTickets, "Open Tickets", allOpenTickets.size.toString())

                                val overdueCount = allOpenTickets.count { DateUtils.isOverdue(it.dueDate, it.status) }
                                setStat(R.id.statOverdue, "Overdue", overdueCount.toString())
                            }
                        }

                    db.collection("threads")
                        .whereIn("clientId", batch)
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
                                            if (senderId != auth.currentUser?.uid && auth.currentUser?.uid !in readBy) {
                                                unreadCount++
                                                setStat(R.id.statUnreadMessages, "Unread Messages", unreadCount.toString())
                                            }
                                        }
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