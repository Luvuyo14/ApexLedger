package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.Ticket
import com.ntuli.apexledger.ui.TicketAdapter

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var ticketAdapter: TicketAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val tvRole = findViewById<TextView>(R.id.tvRole)
        val rvRecentTickets = findViewById<RecyclerView>(R.id.rvRecentTickets)

        // Hamburger icon to open drawer
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_menu_overflow_material)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Ticket list setup
        ticketAdapter = TicketAdapter(emptyList())
        rvRecentTickets.layoutManager = LinearLayoutManager(this)
        rvRecentTickets.adapter = ticketAdapter

        val uid = auth.currentUser?.uid

        // Load user profile
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val fullName = doc.getString("full_name") ?: auth.currentUser?.email
                    val role = doc.getString("role") ?: "client"
                    tvWelcome.text = "Welcome, $fullName"
                    tvRole.text = "Role: $role"
                }
        }

        // Load recent tickets belonging to this user
        if (uid != null) {
            db.collection("tickets")
                .whereEqualTo("clientId", uid)
                .limit(5)
                .get()
                .addOnSuccessListener { result ->
                    val tickets = result.documents.map { doc ->
                        Ticket(
                            ticketNumber = doc.getString("ticketNumber") ?: doc.id,
                            serviceType = doc.getString("serviceType") ?: "general",
                            status = doc.getString("status") ?: "open"
                        )
                    }
                    ticketAdapter.updateData(tickets)
                }
        }

        // Load stat counts
        if (uid != null) {
            loadStatCount("documents", "clientId", uid, R.id.statDocuments, "Documents")
            loadStatCount("tickets", "clientId", uid, R.id.statTickets, "Active Tickets")

            db.collection("tickets")
                .whereEqualTo("clientId", uid)
                .whereEqualTo("status", "completed")
                .get()
                .addOnSuccessListener { }

            db.collection("messages")
                .whereEqualTo("recipientId", uid)
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener { result ->
                    setStat(R.id.statMessages, "Unread Messages", result.size().toString())
                }

            db.collection("tickets")
                .whereEqualTo("clientId", uid)
                .whereEqualTo("status", "overdue")
                .get()
                .addOnSuccessListener { result ->
                    setStat(R.id.statOverdue, "Overdue", result.size().toString())
                }
        }

        // Drawer navigation
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { /* already here */ }
                R.id.nav_documents -> startActivity(Intent(this, DocumentsActivity::class.java))
                R.id.nav_messages -> Toast.makeText(this, "Messages screen coming next", Toast.LENGTH_SHORT).show()
                R.id.nav_tickets -> Toast.makeText(this, "Tickets screen coming next", Toast.LENGTH_SHORT).show()
                R.id.nav_profile -> Toast.makeText(this, "Profile screen coming next", Toast.LENGTH_SHORT).show()
                R.id.nav_logout -> {
                    auth.signOut()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
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

    private fun setStat(includeId: Int, label: String, value: String) {
        val container = findViewById<android.view.View>(includeId)
        container.findViewById<TextView>(R.id.tvStatLabel).text = label
        container.findViewById<TextView>(R.id.tvStatValue).text = value
    }
}