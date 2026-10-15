package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.DonutChartView
import com.ntuli.apexledger.ui.OverdueTicketAdapter
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.util.DateUtils
import com.ntuli.apexledger.util.Labels
import com.ntuli.apexledger.util.StatCards
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class ReportsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var overdueAdapter: OverdueTicketAdapter

    // Kept so Export CSV can share what's on screen
    private var exportRows: List<Ticket> = emptyList()
    private var activeAccountantIds: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { loadReportData(); loadSecuritySnapshot() }

        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rvOverdue = findViewById<RecyclerView>(R.id.rvOverdueTickets)
        overdueAdapter = OverdueTicketAdapter(emptyList())
        rvOverdue.layoutManager = LinearLayoutManager(this)
        rvOverdue.adapter = overdueAdapter

        setStat(R.id.statTotalTickets, "Total Tickets", "0")
        setStat(R.id.statCompletionRate, "Completion Rate", "0%")
        setStat(R.id.statOpenTickets, "Open Tickets", "0")
        setStat(R.id.statOverdueTickets, "Overdue Tickets", "0")

        colorStatBar(R.id.statTotalTickets, R.color.primary)
        colorStatBar(R.id.statCompletionRate, R.color.success)
        colorStatBar(R.id.statOpenTickets, R.color.warning)
        colorStatBar(R.id.statOverdueTickets, R.color.danger)
        StatCards.decorate(findViewById(R.id.statTotalTickets), R.drawable.ic_stat_ticket, R.color.primary)
        StatCards.decorate(findViewById(R.id.statCompletionRate), R.drawable.ic_stat_check, R.color.success)
        StatCards.decorate(findViewById(R.id.statOpenTickets), R.drawable.ic_stat_clock, R.color.warning)
        StatCards.decorate(findViewById(R.id.statOverdueTickets), R.drawable.ic_stat_warning, R.color.danger)

        findViewById<TextView>(R.id.tvReportDate).text =
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()) +
                    "  •  Workload, performance and compliance"

        findViewById<View>(R.id.btnExportCsv).setOnClickListener { shareCsv() }
        findViewById<View>(R.id.btnPrintReport).setOnClickListener { shareSummary() }

        loadReportData()
        loadSecuritySnapshot()
    }

    private fun loadReportData() {
        db.collection("users").whereEqualTo("role", "client").get()
            .addOnSuccessListener { clientsResult ->
                val clientNames = clientsResult.documents.associate { it.id to (it.getString("full_name") ?: "Unknown client") }
                val clientToAccountant = clientsResult.documents.associate { it.id to (it.getString("assignedAccountantId") ?: "") }

                db.collection("users").whereEqualTo("role", "accountant").get()
                    .addOnSuccessListener { accountantsResult ->
                        val accountantNames = accountantsResult.documents.associate { it.id to (it.getString("full_name") ?: "Unassigned") }
                        activeAccountantIds = accountantsResult.documents
                            .filter { it.getBoolean("active") != false }.map { it.id }.toSet()

                        db.collection("tickets").get()
                            .addOnSuccessListener { ticketsResult ->
                                val tickets = ticketsResult.documents.map { d ->
                                    val clientId = d.getString("clientId") ?: ""
                                    val accId = clientToAccountant[clientId].orEmpty()
                                    Ticket(
                                        ticketId = d.id,
                                        ticketNumber = d.getString("ticketNumber") ?: d.id,
                                        serviceType = d.getString("serviceType") ?: "general",
                                        status = d.getString("status") ?: "open",
                                        priority = d.getString("priority") ?: "medium",
                                        description = d.getString("description") ?: "",
                                        dueDate = d.getString("dueDate") ?: "",
                                        clientName = clientNames[clientId] ?: "Unknown client",
                                        accountantName = accountantNames[accId] ?: ""
                                    ) to accId
                                }

                                computeAndRender(tickets, accountantNames)
                            }
                    }
            }
    }

    private fun computeAndRender(
        ticketsWithAccountant: List<Pair<Ticket, String>>,
        accountantNames: Map<String, String>
    ) {
        val tickets = ticketsWithAccountant.map { it.first }
        exportRows = tickets
        val total = tickets.size

        val completed = tickets.count { it.status == "completed" }
        val open = tickets.count { it.status !in listOf("completed", "cancelled") }

        // BUG FIX: "overdue" is not a stored status — it is a due date in the past
        // on a ticket that isn't completed/cancelled (same rule the website uses).
        val overdue = tickets
            .filter { DateUtils.isOverdue(it.dueDate, it.status) }
            .sortedByDescending { DateUtils.daysOverdue(it.dueDate) }
        val completionRate = if (total > 0) (completed * 100 / total) else 0

        setStat(R.id.statTotalTickets, "Total Tickets", total.toString())
        setStat(R.id.statCompletionRate, "Completion Rate", "$completionRate%")
        setStat(R.id.statOpenTickets, "Open Tickets", open.toString())
        setStat(R.id.statOverdueTickets, "Overdue Tickets", overdue.size.toString())

        overdueAdapter.updateData(overdue)
        val card = findViewById<View>(R.id.cardOverdue)
        val tvOverdueTitle = findViewById<TextView>(R.id.tvOverdueTitle)
        val tvOverdueSub = findViewById<TextView>(R.id.tvOverdueSubtitle)
        val rvOverdue = findViewById<RecyclerView>(R.id.rvOverdueTickets)
        if (overdue.isEmpty()) {
            card.setBackgroundResource(R.drawable.bg_card_soft)
            tvOverdueTitle.text = "All tickets are on track"
            tvOverdueTitle.setTextColor(ContextCompat.getColor(this, R.color.success_dark))
            tvOverdueSub.text = "No ticket is past its due date."
            rvOverdue.visibility = View.GONE
        } else {
            card.setBackgroundResource(R.drawable.bg_card_alert)
            tvOverdueTitle.text = "${overdue.size} overdue ticket${if (overdue.size == 1) "" else "s"}"
            tvOverdueTitle.setTextColor(ContextCompat.getColor(this, R.color.danger_dark))
            tvOverdueSub.text = "These need attention first."
            rvOverdue.visibility = View.VISIBLE
        }

        // Ticket completion donut (percentage in the middle) + legend
        val statusCounts = tickets.groupingBy { it.status }.eachCount()
        val statusColors = mapOf(
            "open" to R.color.info,
            "in_progress" to R.color.warning,
            "awaiting_documents" to R.color.purple,
            "under_review" to R.color.primary,
            "completed" to R.color.success,
            "cancelled" to R.color.gray_400
        )
        val sorted = statusCounts.entries.sortedByDescending { it.value }
        val donut = findViewById<DonutChartView>(R.id.donutTicketCompletion)
        donut.setData(
            sorted.map { it.value.toFloat() },
            sorted.map { ContextCompat.getColor(this, statusColors[it.key] ?: R.color.gray_500) }
        )
        donut.setCenterText("$completionRate%", "completed")

        val legend = findViewById<LinearLayout>(R.id.containerTicketCompletion)
        legend.removeAllViews()
        for ((status, count) in sorted) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_legend_row, legend, false)
            row.findViewById<View>(R.id.viewDot).background.mutate().setTint(
                ContextCompat.getColor(this, statusColors[status] ?: R.color.gray_500)
            )
            row.findViewById<TextView>(R.id.tvLegendLabel).text = Labels.status(status)
            val pct = if (total > 0) count * 100 / total else 0
            row.findViewById<TextView>(R.id.tvLegendValue).text = "$count  ($pct%)"
            legend.addView(row)
        }

        // Workload by accountant: initials, open/completed counts, two-colour bar
        val byAccountant = ticketsWithAccountant.groupBy { it.second }
        val workload = accountantNames.keys
            .filter { it in activeAccountantIds || byAccountant.containsKey(it) }
            .map { accId ->
                val list = byAccountant[accId].orEmpty().map { it.first }
                Triple(accId, list.count { it.status !in listOf("completed", "cancelled") },
                    list.count { it.status == "completed" })
            }
            .sortedByDescending { it.second + it.third }
        val maxTotal = workload.maxOfOrNull { it.second + it.third }?.coerceAtLeast(1) ?: 1

        val container = findViewById<LinearLayout>(R.id.containerWorkload)
        container.removeAllViews()
        if (workload.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No accountants yet"
            empty.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
            empty.textSize = 13f
            container.addView(empty)
        }
        for ((accId, open, done) in workload) {
            val name = accountantNames[accId] ?: "Unknown"
            val row = LayoutInflater.from(this).inflate(R.layout.item_workload_row, container, false)
            row.findViewById<TextView>(R.id.tvWlAvatar).text = StatCards.initials(name)
            row.findViewById<TextView>(R.id.tvWlName).text = name
            row.findViewById<TextView>(R.id.tvWlTotal).text = (open + done).toString()
            row.findViewById<TextView>(R.id.tvWlSub).text = "$open open  •  $done completed"
            fun weight(id: Int, value: Float) {
                val v = row.findViewById<View>(id)
                (v.layoutParams as LinearLayout.LayoutParams).weight = value
                v.requestLayout()
            }
            weight(R.id.barOpen, open.toFloat())
            weight(R.id.barDone, done.toFloat())
            weight(R.id.barRest, (maxTotal - open - done).coerceAtLeast(0).toFloat())
            container.addView(row)
        }
    }

    /** "Failed login attempts in the last 30 days: N" — same as the website's Security Snapshot. */
    private fun loadSecuritySnapshot() {
        val cutoff = Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        db.collection("audit_log")
            .whereEqualTo("action", "Failed Login")
            .get()
            .addOnSuccessListener { result ->
                val count = result.documents.count { d ->
                    val ts = d.getTimestamp("timestamp")?.toDate()
                    ts != null && ts.after(cutoff)
                }
                findViewById<TextView>(R.id.tvSecuritySnapshot).text =
                    "Failed login attempts in the last 30 days: $count. Full event-level detail is available to Admins under Admin Panel → Audit Log."
            }
            .addOnFailureListener {
                findViewById<TextView>(R.id.tvSecuritySnapshot).text =
                    "Failed login attempts: unavailable. Full event-level detail is available to Admins under Admin Panel → Audit Log."
            }
    }

    private fun shareCsv() {
        if (exportRows.isEmpty()) {
            com.ntuli.apexledger.util.Feedback.show(this, "Nothing to export yet")
            return
        }
        val sb = StringBuilder("Ticket #,Client,Type,Priority,Status,Due Date,Accountant\n")
        for (t in exportRows) {
            sb.append(
                listOf(
                    t.ticketNumber, t.clientName, Labels.service(t.serviceType),
                    Labels.priority(t.priority), Labels.status(t.status),
                    DateUtils.display(t.dueDate), t.accountantName
                ).joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
            ).append("\n")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "APEXLedger ticket report")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(send, "Export CSV"))
    }

    private fun shareSummary() {
        val total = findViewById<View>(R.id.statTotalTickets).findViewById<TextView>(R.id.tvStatValue).text
        val rate = findViewById<View>(R.id.statCompletionRate).findViewById<TextView>(R.id.tvStatValue).text
        val overdue = findViewById<View>(R.id.statOverdueTickets).findViewById<TextView>(R.id.tvStatValue).text
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "APEXLedger manager report")
            putExtra(Intent.EXTRA_TEXT, "Manager Reports\nTotal tickets: $total\nCompletion rate: $rate\nOverdue tickets: $overdue")
        }
        startActivity(Intent.createChooser(send, "Share report"))
    }

    private fun renderBars(containerId: Int, rows: List<Triple<String, Int, Int>>, maxValue: Int) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()

        if (rows.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No data yet"
            empty.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
            empty.textSize = 13f
            container.addView(empty)
            return
        }

        val denominator = if (maxValue > 0) maxValue else 1

        for ((label, value, colorRes) in rows) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_bar_row, container, false)
            row.findViewById<TextView>(R.id.tvBarLabel).text = label
            row.findViewById<TextView>(R.id.tvBarValue).text = value.toString()

            val barFill = row.findViewById<View>(R.id.barFill)
            val params = barFill.layoutParams as LinearLayout.LayoutParams
            params.weight = value.toFloat() / denominator.toFloat()
            barFill.layoutParams = params
            barFill.setBackgroundColor(ContextCompat.getColor(this, colorRes))

            container.addView(row)
        }
    }

    private fun setStat(includeId: Int, label: String, value: String) {
        val container = findViewById<View>(includeId)
        container.findViewById<TextView>(R.id.tvStatLabel).text = label
        container.findViewById<TextView>(R.id.tvStatValue).text = value
    }

    private fun colorStatBar(includeId: Int, colorRes: Int) {
        val container = findViewById<View>(includeId)
        container.findViewById<View>(R.id.statAccentBar)
            .setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }
}
