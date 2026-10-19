package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.ntuli.apexledger.util.StatCards
import com.ntuli.apexledger.util.DashRows
import com.ntuli.apexledger.util.Labels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.jvm.java

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var ticketAdapter: TicketAdapter


    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        com.ntuli.apexledger.util.NotificationCenter.observe(this) { count ->
            val badge = findViewById<TextView>(R.id.tvBellBadge)
            badge.text = if (count > 9) "9+" else count.toString()
            badge.visibility = if (count > 0) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    override fun onStop() {
        super.onStop()
        com.ntuli.apexledger.util.NotificationCenter.stopObserving(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        askNotificationPermission()
        com.ntuli.apexledger.util.PullRefresh.attach(this) { recreate() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val rvRecentTickets = findViewById<RecyclerView>(R.id.rvRecentTickets)

        toolbar.setNavigationIcon(R.drawable.ic_menu_hamburger)
        navigationView.setCheckedItem(R.id.nav_dashboard)
        navigationView.menu.findItem(R.id.nav_logout)?.let { item ->
            val red = android.text.SpannableString(item.title)
            red.setSpan(
                android.text.style.ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(this, R.color.danger)),
                0, red.length, 0
            )
            item.title = red
        }
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
                    val fullName = doc.getString("fullName") ?: auth.currentUser?.email
                    val role = doc.getString("role") ?: "client"
                    tvWelcome.text = "Welcome back, $fullName!"
                    tvRole.text = "Client Portal"
                    tvRole.visibility = android.view.View.VISIBLE
                    findViewById<TextView>(R.id.tvAvatar).text = StatCards.initials(fullName)
                    loadAssignedAccountant(doc.getString("assignedAccountantId"))
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
        StatCards.decorate(findViewById(R.id.statDocuments), R.drawable.ic_stat_document, R.color.primary)
        StatCards.decorate(findViewById(R.id.statMessages), R.drawable.ic_stat_mail, R.color.warning)
        StatCards.decorate(findViewById(R.id.statTickets), R.drawable.ic_stat_ticket, R.color.success)
        StatCards.decorate(findViewById(R.id.statOverdue), R.drawable.ic_stat_warning, R.color.danger)

        findViewById<View>(R.id.qaRequest).setOnClickListener {
            startActivity(Intent(this, TicketsActivity::class.java).putExtra("openNew", true))
        }
        findViewById<View>(R.id.qaUpload).setOnClickListener { startActivity(Intent(this, DocumentsActivity::class.java)) }
        findViewById<View>(R.id.qaMessage).setOnClickListener { startActivity(Intent(this, MessagesActivity::class.java)) }
        findViewById<View>(R.id.qaTickets).setOnClickListener { startActivity(Intent(this, TicketsActivity::class.java)) }

        findViewById<TextView>(R.id.btnViewAllClient).setOnClickListener {
            startActivity(Intent(this, TicketsActivity::class.java))
        }

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

        findViewById<android.view.View>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
    }

    /** Next three due dates, nearest first. Hidden when nothing is due. */
    private fun renderComingUp(tickets: List<Ticket>) {
        val card = findViewById<View>(R.id.cardComingUp)
        val upcoming = tickets
            .filter { it.dueDate.isNotEmpty() && it.status !in listOf("completed", "cancelled") }
            .sortedBy { it.dueDate }
            .take(3)
        if (upcoming.isEmpty()) { card.visibility = View.GONE; return }
        val container = DashRows.section(card, "Coming Up")
        for (t in upcoming) {
            val chip = DashRows.dueChip(t.dueDate)
            DashRows.addRow(
                container, t.ticketNumber, Labels.service(t.serviceType),
                chip?.first, chip?.second ?: R.drawable.badge_status_open, chip?.third ?: R.color.navy
            )
        }
        card.visibility = View.VISIBLE
    }

    /**
     * Action Needed lists only what is still outstanding. An item disappears as soon as the client
     * does what was asked:
     *  - "Documents needed": gone once a document for that service is uploaded after the request
     *  - "Rejected": gone once a corrected version replaces the rejected document
     */
    private fun renderActionNeeded() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("tickets").whereEqualTo("clientId", uid).get().addOnCompleteListener { tTask ->
            db.collection("documents").whereEqualTo("clientId", uid).get().addOnCompleteListener { dTask ->
                val card = findViewById<View>(R.id.cardActionNeeded)
                val tickets = if (tTask.isSuccessful) tTask.result.documents else emptyList()
                val allDocs = if (dTask.isSuccessful) dTask.result.documents.filter { it.getBoolean("uploadComplete") != false } else emptyList()

                // Only the newest version of each document counts
                val superseded = allDocs.flatMap { d ->
                    (d.get("previousVersionIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                }.toSet()
                val latest = allDocs.filter { it.id !in superseded }

                val rejected = latest.count { it.getString("reviewStatus") == "rejected" }

                val stillNeeded = tickets.filter { it.getString("status") == "awaiting_documents" }.filter { t ->
                    val service = t.getString("serviceType") ?: ""
                    // when the accountant asked for the documents
                    val since = (t.getTimestamp("statusChangedAt") ?: t.getTimestamp("createdDate"))?.seconds ?: 0L
                    val uploadedSince = allDocs.any { d ->
                        d.getString("documentCategory") == service &&
                                (d.getTimestamp("uploadTimestamp")?.seconds ?: 0L) > since
                    }
                    !uploadedSince
                }

                if (stillNeeded.isEmpty() && rejected == 0) { card.visibility = View.GONE; return@addOnCompleteListener }
                val container = DashRows.section(card, "Action Needed", R.color.danger_dark)
                val toDocs = { startActivity(Intent(this, DocumentsActivity::class.java)) }
                for (t in stillNeeded) {
                    DashRows.addRow(
                        container, "Documents needed for ${t.getString("ticketNumber") ?: "your ticket"}",
                        "Your accountant is waiting for your ${Labels.service(t.getString("serviceType") ?: "other")} documents.",
                        "Upload", R.drawable.bg_button_primary_small, R.color.on_primary, toDocs
                    )
                }
                if (rejected > 0) {
                    DashRows.addRow(
                        container, "$rejected document${if (rejected == 1) " was" else "s were"} rejected",
                        "Please review the feedback and upload a corrected copy.",
                        "Review", R.drawable.bg_button_primary_small, R.color.on_primary, toDocs
                    )
                }
                card.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // coming back from Documents (or anywhere) re-checks what is still outstanding
        renderActionNeeded()
        com.ntuli.apexledger.util.HolidayCard.show(this, findViewById(R.id.cardHolidays))
    }

    /** Shows the accountant assigned to this client (name, duty, email). */
    private fun loadAssignedAccountant(accountantId: String?) {
        val tvName = findViewById<TextView>(R.id.tvAccountantName)
        val tvMeta = findViewById<TextView>(R.id.tvAccountantMeta)
        val tvAvatar = findViewById<TextView>(R.id.tvAccountantAvatar)
        if (accountantId.isNullOrEmpty()) {
            tvName.text = "Not assigned yet"
            tvMeta.text = "An administrator will assign your accountant shortly."
            return
        }
        db.collection("users").document(accountantId).get()
            .addOnSuccessListener { acc ->
                val name = acc.getString("fullName") ?: "Your accountant"
                val duty = (acc.getString("department") ?: "").substringBefore("(").trim()
                val email = acc.getString("email") ?: ""
                tvName.text = name
                tvAvatar.text = StatCards.initials(name)
                tvMeta.text = listOf(if (duty.isNotEmpty()) "$duty Duty" else "", email)
                    .filter { it.isNotEmpty() }.joinToString(" • ")
            }
            .addOnFailureListener {
                tvName.text = "Accountant assigned"
                tvMeta.text = ""
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
                        dueDate = doc.getString("dueDate") ?: "",
                        createdLabel = com.ntuli.apexledger.util.DateUtils.created(doc.getTimestamp("createdDate")),
                        accountantName = doc.getString("accountantName") ?: ""
                    )
                }

                ticketAdapter.updateData(allTickets.take(5))
                findViewById<TextView>(R.id.tvNoTickets).visibility =
                    if (allTickets.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                setStat(R.id.statTickets, "Active Tickets", allTickets.count { it.status !in listOf("completed", "cancelled") }.toString())

                renderComingUp(allTickets)

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