package com.ntuli.apexledger

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.StaffTicketAdapter
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.util.AuditLogger
import java.util.Calendar
import com.ntuli.apexledger.util.NotificationHelper

class StaffTicketsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffTicketAdapter

    private val statusOptions = listOf("open", "in_progress", "awaiting_documents", "completed", "cancelled")
    private val statusFilterOptions = listOf("All statuses") + statusOptions
    private val priorityOptions = listOf("All priorities", "low", "medium", "high")

    private var allTickets: List<Ticket> = emptyList()
    private var selectedStatusFilter = "All statuses"
    private var selectedPriorityFilter = "All priorities"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_tickets)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvStaffTickets)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val spinnerFilterStatus = findViewById<Spinner>(R.id.spinnerFilterStatus)
        val spinnerFilterPriority = findViewById<Spinner>(R.id.spinnerFilterPriority)

        adapter = StaffTicketAdapter(
            emptyList(),
            statusOptions,
            onStatusChanged = { ticket, newStatus -> updateTicketStatus(ticket, newStatus) },
            onSetDueDate = { ticket -> showDatePicker(ticket) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        spinnerFilterStatus.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            statusFilterOptions.map { if (it == "All statuses") it else it.replace("_", " ").replaceFirstChar { c -> c.uppercase() } }
        )
        spinnerFilterStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedStatusFilter = statusFilterOptions[pos]
                applyFilters(tvEmptyState)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerFilterPriority.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            priorityOptions.map { if (it == "All priorities") it else it.replaceFirstChar { c -> c.uppercase() } }
        )
        spinnerFilterPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedPriorityFilter = priorityOptions[pos]
                applyFilters(tvEmptyState)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        loadAssignedTickets(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadAssignedTickets(findViewById(R.id.tvEmptyState))
    }

    private fun loadAssignedTickets(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereEqualTo("assignedAccountantId", uid)
            .get()
            .addOnSuccessListener { clientsResult ->
                val clientDocs = clientsResult.documents
                val clientIds = clientDocs.map { it.id }
                val clientNames = clientDocs.associate { it.id to (it.getString("full_name") ?: "Unknown client") }

                if (clientIds.isEmpty()) {
                    allTickets = emptyList()
                    applyFilters(tvEmptyState)
                    return@addOnSuccessListener
                }

                val batches = clientIds.chunked(10)
                val fetched = mutableListOf<Ticket>()
                var completedBatches = 0

                for (batch in batches) {
                    db.collection("tickets")
                        .whereIn("clientId", batch)
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
                                    clientName = clientNames[d.getString("clientId")] ?: "Unknown client"
                                )
                            }
                            fetched.addAll(tickets)
                            completedBatches++
                            if (completedBatches == batches.size) {
                                allTickets = fetched
                                applyFilters(tvEmptyState)
                            }
                        }
                }
            }
    }

    private fun applyFilters(tvEmptyState: TextView) {
        var filtered = allTickets
        if (selectedStatusFilter != "All statuses") {
            filtered = filtered.filter { it.status == selectedStatusFilter }
        }
        if (selectedPriorityFilter != "All priorities") {
            filtered = filtered.filter { it.priority == selectedPriorityFilter }
        }
        adapter.updateData(filtered)
        tvEmptyState.visibility = if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateTicketStatus(ticket: Ticket, newStatus: String) {
        db.collection("tickets").document(ticket.ticketId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Ticket ${ticket.ticketNumber} updated to ${newStatus.replace("_", " ")}", Toast.LENGTH_SHORT).show()
                AuditLogger.logEvent(
                    "User", "Update Ticket Status",
                    "${ticket.ticketNumber} → $newStatus (client: ${ticket.clientName})"
                )

                db.collection("tickets").document(ticket.ticketId).get()
                    .addOnSuccessListener { doc ->
                        val clientId = doc.getString("clientId")
                        if (clientId != null) {
                            NotificationHelper.notify(
                                recipientUid = clientId,
                                type = "ticket_update",
                                title = "Ticket ${ticket.ticketNumber} updated",
                                body = "Your ticket status changed to ${newStatus.replace("_", " ")}."
                            )
                        }
                    }

                loadAssignedTickets(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showDatePicker(ticket: Ticket) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formatted = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                updateTicketDueDate(ticket, formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateTicketDueDate(ticket: Ticket, dueDate: String) {
        db.collection("tickets").document(ticket.ticketId)
            .update("dueDate", dueDate)
            .addOnSuccessListener {
                Toast.makeText(this, "Due date set to $dueDate", Toast.LENGTH_SHORT).show()
                AuditLogger.logEvent(
                    "User", "Set Ticket Due Date",
                    "${ticket.ticketNumber} → $dueDate (client: ${ticket.clientName})"
                )
                loadAssignedTickets(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}