package com.ntuli.apexledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.OverdueTicketAdapter
import com.ntuli.apexledger.ui.Ticket

class ReportsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var overdueAdapter: OverdueTicketAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

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

        loadReportData()
    }

    private fun loadReportData() {
        // Pull everything needed: all tickets, all clients (for name + assigned accountant),
        // and all accountants (for workload labels), then compute report numbers in memory.
        db.collection("users").whereEqualTo("role", "client").get()
            .addOnSuccessListener { clientsResult ->
                val clientNames = clientsResult.documents.associate { it.id to (it.getString("full_name") ?: "Unknown client") }
                val clientToAccountant = clientsResult.documents.associate { it.id to (it.getString("assignedAccountantId") ?: "") }

                db.collection("users").whereEqualTo("role", "accountant").get()
                    .addOnSuccessListener { accountantsResult ->
                        val accountantNames = accountantsResult.documents.associate { it.id to (it.getString("full_name") ?: "Unassigned") }

                        db.collection("tickets").get()
                            .addOnSuccessListener { ticketsResult ->
                                val tickets = ticketsResult.documents.map { d ->
                                    val clientId = d.getString("clientId") ?: ""
                                    Ticket(
                                        ticketId = d.id,
                                        ticketNumber = d.getString("ticketNumber") ?: d.id,
                                        serviceType = d.getString("serviceType") ?: "general",
                                        status = d.getString("status") ?: "open",
                                        priority = d.getString("priority") ?: "medium",
                                        description = d.getString("description") ?: "",
                                        dueDate = d.getString("dueDate") ?: "",
                                        clientName = clientNames[clientId] ?: "Unknown client"
                                    ) to clientToAccountant[clientId].orEmpty()
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
        val total = tickets.size

        val completed = tickets.count { it.status == "completed" }
        val open = tickets.count { it.status == "open" }
        val overdue = tickets.filter { it.status == "overdue" }
        val completionRate = if (total > 0) (completed * 100 / total) else 0

        setStat(R.id.statTotalTickets, "Total Tickets", total.toString())
        setStat(R.id.statCompletionRate, "Completion Rate", "$completionRate%")
        setStat(R.id.statOpenTickets, "Open Tickets", open.toString())
        setStat(R.id.statOverdueTickets, "Overdue Tickets", overdue.size.toString())

        overdueAdapter.updateData(overdue)
        findViewById<View>(R.id.tvOverdueEmptyState).visibility =
            if (overdue.isEmpty()) View.VISIBLE else View.GONE

        // Ticket completion breakdown by status
        val statusCounts = tickets.groupingBy { it.status }.eachCount()
        val statusColors = mapOf(
            "open" to R.color.info,
            "in_progress" to R.color.warning,
            "awaiting_documents" to R.color.purple,
            "completed" to R.color.success,
            "cancelled" to R.color.danger
        )
        renderBars(
            R.id.containerTicketCompletion,
            statusCounts.entries.sortedByDescending { it.value }.map { (status, count) ->
                Triple(
                    status.replace("_", " ").replaceFirstChar { it.uppercase() },
                    count,
                    statusColors[status] ?: R.color.gray_500
                )
            },
            total
        )

        // Workload by accountant: count open vs total tickets per accountant
        val byAccountant = ticketsWithAccountant.groupBy { it.second }
        val workloadRows = byAccountant.entries
            .filter { it.key.isNotEmpty() }
            .map { (accId, pairs) ->
                val name = accountantNames[accId] ?: "Unknown"
                Triple(name, pairs.size, R.color.primary)
            }
            .sortedByDescending { it.second }

        val maxWorkload = workloadRows.maxOfOrNull { it.second } ?: 1
        renderBars(R.id.containerWorkload, workloadRows, maxWorkload)
    }

    /**
     * Renders a simple set of label/value rows with a proportional colored
     * bar into the given container, using item_bar_row.xml. maxValue is used
     * as the denominator so each bar's width reflects its share of the total.
     */
    private fun renderBars(containerId: Int, rows: List<Triple<String, Int, Int>>, maxValue: Int) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()

        if (rows.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No data yet"
            empty.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.gray_500))
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
            val barParent = barFill.parent as LinearLayout
            val fillWeight = value.toFloat() / denominator.toFloat()
            val params = barFill.layoutParams as LinearLayout.LayoutParams
            params.weight = fillWeight
            barFill.layoutParams = params
            barFill.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, colorRes))

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
            .setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, colorRes))
    }
}