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
import com.ntuli.apexledger.util.Labels
import java.util.Calendar
import com.ntuli.apexledger.util.NotificationHelper

class StaffTicketsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffTicketAdapter

    private val statusOptions = listOf("open", "in_progress", "awaiting_documents", "under_review", "completed", "cancelled")
    private val statusFilterOptions = listOf("All statuses") + statusOptions
    private val priorityOptions = listOf("All priorities", "low", "medium", "high", "urgent")

    private var allTickets: List<Ticket> = emptyList()
    private var selectedStatusFilter = "All statuses"
    private var selectedPriorityFilter = "All priorities"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_tickets)
        com.ntuli.apexledger.util.PullRefresh.attach(this) { onResume() }

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
            onSetDueDate = { ticket -> showDatePicker(ticket) },
            onEditNotes = { ticket -> showNotesDialog(ticket) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        spinnerFilterStatus.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            statusFilterOptions.map { if (it == "All statuses") it else Labels.status(it) }
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
            priorityOptions.map { if (it == "All priorities") it else Labels.priority(it) }
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

        com.ntuli.apexledger.util.StaffData.loadTickets(db, uid) { res ->
            if (res.docs.isEmpty()) {
                allTickets = emptyList()
                applyFilters(tvEmptyState)
                return@loadTickets
            }
            // Internal notes live in a staff-only collection so clients can never read them
            val clientIds = res.docs.mapNotNull { it.getString("clientId") }.distinct()
            val batches = clientIds.chunked(10)
            val notes = mutableMapOf<String, String>()
            var done = 0
            for (batch in batches) {
                db.collection("ticket_notes").whereIn("clientId", batch).get().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        task.result.documents.forEach { notes[it.id] = it.getString("notes") ?: "" }
                    }
                    done++
                    if (done == batches.size) {
                        allTickets = res.docs.map { d ->
                            Ticket(
                                ticketId = d.id,
                                ticketNumber = d.getString("ticketNumber") ?: d.id,
                                serviceType = d.getString("serviceType") ?: "general",
                                status = d.getString("status") ?: "open",
                                priority = d.getString("priority") ?: "medium",
                                description = d.getString("description") ?: "",
                                dueDate = d.getString("dueDate") ?: "",
                                createdLabel = com.ntuli.apexledger.util.DateUtils.created(d.getTimestamp("createdDate")),
                                internalNotes = notes[d.id] ?: "",
                                clientId = d.getString("clientId") ?: "",
                                clientName = res.clientNames[d.getString("clientId")] ?: "Unknown client"
                            )
                        }.sortedByDescending { it.ticketNumber }
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
            .update("status", newStatus, "statusChangedAt", com.google.firebase.Timestamp.now())
            .addOnSuccessListener {
                com.ntuli.apexledger.util.Feedback.show(this, "Ticket ${ticket.ticketNumber} updated to ${Labels.status(newStatus)}")
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
                                body = "Your ticket status changed to ${Labels.status(newStatus)}.",
                                targetId = ticket.ticketId
                            )
                        }
                    }

                loadAssignedTickets(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e ->
                com.ntuli.apexledger.util.Feedback.show(this, "Failed to update: ${e.message}")
            }
    }

    private fun showDatePicker(ticket: Ticket) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formatted = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                checkDueDate(ticket, formatted, year, month + 1)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Asks the Nager.Date public-holiday API whether the chosen day is a weekend or a South African
     * public holiday, and offers the next working day if it is.
     */
    private fun checkDueDate(ticket: Ticket, date: String, year: Int, month: Int) {
        // a date in December may be pushed into next year, so load both years
        val years = if (month == 12) listOf(year, year + 1) else listOf(year)

        com.ntuli.apexledger.util.HolidayApi.forYears(this, years) { holidays, error ->
            if (holidays == null) {
                // offline or the service is down: save the date anyway and say that the check was skipped
                com.ntuli.apexledger.util.Feedback.show(this, "Public holiday check skipped ($error). Date saved.")
                updateTicketDueDate(ticket, date)
                return@forYears
            }

            val check = com.ntuli.apexledger.util.WorkingDays.check(date, holidays)
            if (check == null || !check.isProblem) {
                updateTicketDueDate(ticket, date)
                return@forYears
            }

            val shown = com.ntuli.apexledger.util.DateUtils.display(date)
            val suggested = com.ntuli.apexledger.util.DateUtils.display(check.suggestedDate)
            android.app.AlertDialog.Builder(this)
                .setTitle("Due date warning")
                .setMessage("$shown is ${check.reason}.\n\nThe next working day is $suggested.")
                .setPositiveButton("Use $suggested") { _, _ -> updateTicketDueDate(ticket, check.suggestedDate) }
                .setNeutralButton("Keep $shown") { _, _ -> updateTicketDueDate(ticket, date) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateTicketDueDate(ticket: Ticket, dueDate: String) {
        db.collection("tickets").document(ticket.ticketId)
            .update("dueDate", dueDate)
            .addOnSuccessListener {
                com.ntuli.apexledger.util.Feedback.show(this, "Due date set to $dueDate")
                AuditLogger.logEvent(
                    "User", "Set Ticket Due Date",
                    "${ticket.ticketNumber} → $dueDate (client: ${ticket.clientName})"
                )
                loadAssignedTickets(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e ->
                com.ntuli.apexledger.util.Feedback.show(this, "Failed to update: ${e.message}")
            }
    }

    private fun showNotesDialog(ticket: Ticket) {
        val input = android.widget.EditText(this)
        input.setText(ticket.internalNotes)
        input.hint = "Notes visible to staff only"
        input.minLines = 3
        input.gravity = android.view.Gravity.TOP
        input.setPadding(48, 32, 48, 16)

        android.app.AlertDialog.Builder(this)
            .setTitle("Internal notes — ${ticket.ticketNumber}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val notes = input.text.toString().trim()
                db.collection("ticket_notes").document(ticket.ticketId)
                    .set(mapOf("clientId" to ticket.clientId, "notes" to notes))
                    .addOnSuccessListener {
                        com.ntuli.apexledger.util.Feedback.show(this, "Notes saved")
                        AuditLogger.logEvent("User", "Update Ticket Notes", ticket.ticketNumber)
                        loadAssignedTickets(findViewById(R.id.tvEmptyState))
                    }
                    .addOnFailureListener { e ->
                        com.ntuli.apexledger.util.Feedback.show(this, "Failed to save: ${e.message}")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
