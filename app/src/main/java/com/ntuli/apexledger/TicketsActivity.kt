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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.ui.TicketAdapter

class TicketsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: TicketAdapter

    private val serviceTypes = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit")

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

        adapter = TicketAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        fab.setOnClickListener { showNewTicketDialog() }

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
                        description = d.getString("description") ?: ""
                    )
                }
                adapter.updateData(tickets)
                tvEmptyState.visibility = if (tickets.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
    }

    private fun showNewTicketDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_new_ticket, null)
        val spinner = view.findViewById<Spinner>(R.id.spinnerServiceType)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, serviceTypes)

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Create") { _, _ ->
                val serviceType = serviceTypes[spinner.selectedItemPosition]
                val description = etDescription.text.toString().trim()
                if (description.isNotEmpty()) {
                    createTicket(serviceType, description)
                } else {
                    Toast.makeText(this, "Please describe your request", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createTicket(serviceType: String, description: String) {
        val uid = auth.currentUser?.uid ?: return
        val ticketNumber = "TCK-${System.currentTimeMillis()}"

        val ticketData = hashMapOf(
            "clientId" to uid,
            "ticketNumber" to ticketNumber,
            "serviceType" to serviceType,
            "priority" to "medium",
            "status" to "open",
            "description" to description,
            "createdDate" to com.google.firebase.Timestamp.now()
        )

        db.collection("tickets").add(ticketData)
            .addOnSuccessListener {
                Toast.makeText(this, "Ticket created!", Toast.LENGTH_SHORT).show()
                loadTickets(findViewById(R.id.tvEmptyState))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}