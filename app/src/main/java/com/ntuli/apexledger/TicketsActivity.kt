package com.ntuli.apexledger

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.ui.TicketAdapter
import com.ntuli.apexledger.util.AuditLogger
import com.ntuli.apexledger.util.Labels
import com.ntuli.apexledger.util.TicketNumbers

class TicketsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: TicketAdapter

    private val serviceTypes = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit")
    private val priorities = listOf("low", "medium", "high", "urgent")

    // Same filter options as the website's client portal
    private val statusFilterOptions = listOf(
        "All statuses", "open", "in_progress", "awaiting_documents",
        "under_review", "completed", "cancelled"
    )
    private val priorityFilterOptions = listOf("All priorities") + priorities

    private var allTickets: List<Ticket> = emptyList()
    private var selectedStatus = "All statuses"
    private var selectedPriority = "All priorities"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvTickets)
        val fab = findViewById<FloatingActionButton>(R.id.fabNewTicket)
        val btnRequestService = findViewById<MaterialButton>(R.id.btnRequestService)
        val spinnerStatus = findViewById<Spinner>(R.id.spinnerFilterStatus)
        val spinnerPriority = findViewById<Spinner>(R.id.spinnerFilterPriority)

        adapter = TicketAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        spinnerStatus.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            statusFilterOptions.map { if (it == "All statuses") it else Labels.status(it) }
        )
        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedStatus = statusFilterOptions[pos]
                applyFilters()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        spinnerPriority.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            priorityFilterOptions.map { if (it == "All priorities") it else Labels.priority(it) }
        )
        spinnerPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedPriority = priorityFilterOptions[pos]
                applyFilters()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        fab.setOnClickListener { showNewTicketDialog() }
        btnRequestService.setOnClickListener { showNewTicketDialog() }

        loadTickets()

        if (intent.getBooleanExtra("openNew", false)) showNewTicketDialog()
    }

    override fun onResume() {
        super.onResume()
        loadTickets()
    }

    private fun loadTickets() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("tickets")
            .whereEqualTo("clientId", uid)
            .get()
            .addOnSuccessListener { result ->
                allTickets = result.documents.map { d ->
                    Ticket(
                        ticketId = d.id,
                        ticketNumber = d.getString("ticketNumber") ?: d.id,
                        serviceType = d.getString("serviceType") ?: "general",
                        status = d.getString("status") ?: "open",
                        priority = d.getString("priority") ?: "medium",
                        description = d.getString("description") ?: "",
                        dueDate = d.getString("dueDate") ?: "",
                        createdLabel = com.ntuli.apexledger.util.DateUtils.created(d.getTimestamp("createdDate")),
                        accountantName = d.getString("accountantName") ?: ""
                    )
                }.sortedByDescending { it.ticketNumber }
                applyFilters()
            }
    }

    private fun applyFilters() {
        var filtered = allTickets
        if (selectedStatus != "All statuses") filtered = filtered.filter { it.status == selectedStatus }
        if (selectedPriority != "All priorities") filtered = filtered.filter { it.priority == selectedPriority }
        adapter.updateData(filtered)
        findViewById<TextView>(R.id.tvTicketCount).text = "${filtered.size} ticket(s)"
        findViewById<TextView>(R.id.tvEmptyState).visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showNewTicketDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_new_ticket, null)
        val spinnerServiceType = view.findViewById<Spinner>(R.id.spinnerServiceType)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)

        spinnerServiceType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            serviceTypes.map { Labels.service(it) }
        )
        spinnerPriority.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            priorities.map { Labels.priority(it) }
        )
        spinnerPriority.setSelection(priorities.indexOf("medium"))

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Create") { _, _ ->
                val serviceType = serviceTypes[spinnerServiceType.selectedItemPosition]
                val priority = priorities[spinnerPriority.selectedItemPosition]
                val description = etDescription.text.toString().trim()
                if (description.isNotEmpty()) {
                    createTicket(serviceType, priority, description)
                } else {
                    com.ntuli.apexledger.util.Feedback.show(this, "Please describe your request")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createTicket(serviceType: String, priority: String, description: String) {
        val uid = auth.currentUser?.uid ?: return

        // Find the accountant who handles this service (the client's own one if they handle it)
        com.ntuli.apexledger.util.AccountantRouter.pick(db, uid, serviceType, "tickets") { pick ->
            TicketNumbers.generate(db, serviceType) { ticketNumber ->
                val ticketData = hashMapOf(
                    "clientId" to uid,
                    "ticketNumber" to ticketNumber,
                    "serviceType" to serviceType,
                    "priority" to priority,
                    "status" to "open",
                    "description" to description,
                    "dueDate" to "",
                    "accountantId" to (pick?.id ?: ""),
                    "accountantName" to (pick?.name ?: ""),
                    "createdDate" to com.google.firebase.Timestamp.now()
                )

                db.collection("tickets").add(ticketData)
                    .addOnSuccessListener { ticketRef ->
                        val who = pick?.name
                        com.ntuli.apexledger.util.Feedback.show(
                            this,
                            if (who != null) "Ticket $ticketNumber created and assigned to $who"
                            else "Ticket $ticketNumber created. An administrator will assign an accountant."
                        )
                        AuditLogger.logEvent(
                            "Client", "Create Ticket",
                            "$ticketNumber (${Labels.service(serviceType)}, $priority priority" + (if (who != null) ", assigned to $who)" else ")")
                        )
                        if (pick != null) {
                            com.ntuli.apexledger.util.NotificationHelper.notify(
                                recipientUid = pick.id,
                                type = "new_ticket",
                                title = "New service request",
                                body = "$ticketNumber - ${Labels.service(serviceType)}: ${description.take(60)}",
                                targetId = ticketRef.id
                            )
                        }
                        loadTickets()
                    }
                    .addOnFailureListener { e ->
                        com.ntuli.apexledger.util.Feedback.show(this, "Failed: ${e.message}")
                    }
            }
        }
    }
}
