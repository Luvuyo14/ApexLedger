package com.ntuli.apexledger

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
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

class TicketsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: TicketAdapter

    private val serviceTypes = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit")
    private val priorities = listOf("low", "medium", "high")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvTickets)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)
        val fab = findViewById<FloatingActionButton>(R.id.fabNewTicket)
        val btnRequestService = findViewById<MaterialButton>(R.id.btnRequestService)

        adapter = TicketAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        fab.setOnClickListener { showNewTicketDialog() }
        btnRequestService.setOnClickListener { showNewTicketDialog() }

        loadTickets(tvEmptyState)
    }

    override fun onResume() {
        super.onResume()
        loadTickets(findViewById(R.id.tvEmptyState))
    }

    private fun loadTickets(tvEmptyState: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("tickets")
            .whereEqualTo("clientId", uid)
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
                adapter.updateData(tickets)
                tvEmptyState.visibility = if (tickets.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
    }

    private fun showNewTicketDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_new_ticket, null)
        val spinnerServiceType = view.findViewById<Spinner>(R.id.spinnerServiceType)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)

        spinnerServiceType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            serviceTypes.map { it.replace("_", " ").replaceFirstChar { c -> c.uppercase() } }
        )
        spinnerPriority.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            priorities.map { it.replaceFirstChar { c -> c.uppercase() } }
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
                    Toast.makeText(this, "Please describe your request", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createTicket(serviceType: String, priority: String, description: String) {
        val uid = auth.currentUser?.uid ?: return
        val ticketNumber = "TCK-${System.currentTimeMillis()}"

        val ticketData = hashMapOf(
            "clientId" to uid,
            "ticketNumber" to ticketNumber,
            "serviceType" to serviceType,
            "priority" to priority,
            "status" to "open",
            "description" to description,
            "dueDate" to "",
            "createdDate" to com.google.firebase.Timestamp.now()
        )

        db.collection("tickets").add(ticketData)
            .addOnSuccessListener {
                Toast.makeText(this, "Ticket created!", Toast.LENGTH_SHORT).show()
                AuditLogger.logEvent("Client", "Create Ticket", "$ticketNumber ($serviceType, $priority priority)")
                loadTickets(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}