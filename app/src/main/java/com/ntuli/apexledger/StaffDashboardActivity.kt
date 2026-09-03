package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.ui.TicketAdapter

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
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val rv = findViewById<RecyclerView>(R.id.rvAllTickets)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        adapter = TicketAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val fullName = doc.getString("full_name") ?: auth.currentUser?.email
                    val role = doc.getString("role") ?: "accountant"
                    tvWelcome.text = "Welcome, $fullName"
                    tvRole.text = "Role: $role"
                }
        }

        loadAllOpenTickets()

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun loadAllOpenTickets() {
        val uid = auth.currentUser?.uid ?: return

        // Step 1: find clients assigned to this accountant
        db.collection("users")
            .whereEqualTo("assignedAccountantId", uid)
            .get()
            .addOnSuccessListener { clientsResult ->
                val clientIds = clientsResult.documents.map { it.id }

                if (clientIds.isEmpty()) {
                    adapter.updateData(emptyList())
                    return@addOnSuccessListener
                }

                // Firestore whereIn supports max 10 values per query
                val batches = clientIds.chunked(10)
                val allTickets = mutableListOf<Ticket>()
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
                            allTickets.addAll(tickets)
                            completedBatches++
                            if (completedBatches == batches.size) {
                                adapter.updateData(allTickets)
                            }
                        }
                }
            }
    }
}