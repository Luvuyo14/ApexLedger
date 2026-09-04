package com.ntuli.apexledger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ntuli.apexledger.ui.AuditLogAdapter
import com.ntuli.apexledger.ui.AuditLogEntry
import com.ntuli.apexledger.ui.PermissionAdapter
import com.ntuli.apexledger.ui.PermissionRow
import com.ntuli.apexledger.ui.ServiceStatus
import com.ntuli.apexledger.ui.ServiceStatusAdapter
import com.ntuli.apexledger.ui.StaffAdapter
import com.ntuli.apexledger.ui.StaffMember
import com.ntuli.apexledger.util.AuditLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var staffAdapter: StaffAdapter
    private lateinit var auditAdapter: AuditLogAdapter

    private val roles = listOf("accountant", "manager", "admin")
    private val departments = listOf(
        "Taxation (accountant)", "Payroll (accountant)", "Bookkeeping (accountant)",
        "Advisory (accountant)", "Audit (accountant)", "Administration"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_panel)

        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        setupTabs()
        setupUserManagementSection()
        setupPermissionsSection()
        setupAuditLogSection()
        setupSystemHealthSection()
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val sectionUserManagement = findViewById<android.view.View>(R.id.sectionUserManagement)
        val sectionPermissions = findViewById<android.view.View>(R.id.sectionPermissions)
        val sectionAuditLog = findViewById<android.view.View>(R.id.sectionAuditLog)
        val sectionSystemHealth = findViewById<android.view.View>(R.id.sectionSystemHealth)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                sectionUserManagement.visibility = if (tab.position == 0) android.view.View.VISIBLE else android.view.View.GONE
                sectionPermissions.visibility = if (tab.position == 1) android.view.View.VISIBLE else android.view.View.GONE
                sectionAuditLog.visibility = if (tab.position == 2) android.view.View.VISIBLE else android.view.View.GONE
                sectionSystemHealth.visibility = if (tab.position == 3) android.view.View.VISIBLE else android.view.View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupUserManagementSection() {
        val etStaffName = findViewById<EditText>(R.id.etStaffName)
        val etStaffEmail = findViewById<EditText>(R.id.etStaffEmail)
        val etStaffPassword = findViewById<EditText>(R.id.etStaffPassword)
        val spinnerRole = findViewById<Spinner>(R.id.spinnerRole)
        val spinnerDepartment = findViewById<Spinner>(R.id.spinnerDepartment)
        val btnCreateStaff = findViewById<MaterialButton>(R.id.btnCreateStaff)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val rvStaff = findViewById<RecyclerView>(R.id.rvStaff)

        spinnerRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        spinnerDepartment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, departments)

        staffAdapter = StaffAdapter(emptyList())
        rvStaff.layoutManager = LinearLayoutManager(this)
        rvStaff.adapter = staffAdapter

        loadStaffList()

        btnCreateStaff.setOnClickListener {
            val name = etStaffName.text.toString().trim()
            val email = etStaffEmail.text.toString().trim()
            val password = etStaffPassword.text.toString()
            val role = roles[spinnerRole.selectedItemPosition]
            val department = departments[spinnerDepartment.selectedItemPosition]

            if (name.isEmpty() || email.isEmpty() || password.length < 8) {
                tvStatus.text = "Fill in name, email, and an 8+ character password"
                return@setOnClickListener
            }

            createStaffAccount(name, email, password, role, department, tvStatus)
        }
    }

    private fun createStaffAccount(
        name: String, email: String, password: String,
        role: String, department: String, tvStatus: TextView
    ) {
        val defaultApp = FirebaseApp.getInstance()
        val secondaryAppName = "StaffCreation-${System.currentTimeMillis()}"
        val secondaryApp = FirebaseApp.initializeApp(this, defaultApp.options, secondaryAppName)
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val newUid = result.user?.uid
                if (newUid == null) {
                    tvStatus.text = "Failed: could not get new user ID"
                    secondaryApp.delete()
                    return@addOnSuccessListener
                }

                val profile = hashMapOf(
                    "full_name" to name,
                    "email" to email,
                    "role" to role,
                    "department" to department,
                    "created_at" to com.google.firebase.Timestamp.now()
                )

                db.collection("users").document(newUid).set(profile)
                    .addOnSuccessListener {
                        tvStatus.setTextColor(0xFF2E7D32.toInt())
                        tvStatus.text = "Staff account created for $name"
                        AuditLogger.logEvent("Admin", "Create Staff Account", "Created $role account for $name ($email)")
                        loadStaffList()
                    }
                    .addOnFailureListener { e ->
                        tvStatus.setTextColor(0xFFD32F2F.toInt())
                        tvStatus.text = "Profile save failed: ${e.message}"
                    }

                secondaryAuth.signOut()
                secondaryApp.delete()
            }
            .addOnFailureListener { e ->
                tvStatus.setTextColor(0xFFD32F2F.toInt())
                tvStatus.text = "Failed to create account: ${e.message}"
                secondaryApp.delete()
            }
    }

    private fun loadStaffList() {
        db.collection("users")
            .whereIn("role", listOf("accountant", "manager", "admin"))
            .get()
            .addOnSuccessListener { result ->
                val staff = result.documents.map { d ->
                    StaffMember(
                        uid = d.id,
                        fullName = d.getString("full_name") ?: "Unnamed",
                        email = d.getString("email") ?: "",
                        role = d.getString("role") ?: "accountant"
                    )
                }
                staffAdapter.updateData(staff)
            }
    }

    private fun setupPermissionsSection() {
        val rvPermissions = findViewById<RecyclerView>(R.id.rvPermissions)
        rvPermissions.layoutManager = LinearLayoutManager(this)

        val permissions = listOf(
            PermissionRow("View own tickets / documents", admin = true, manager = true, accountant = true, client = true),
            PermissionRow("Create service tickets", admin = true, manager = false, accountant = true, client = false),
            PermissionRow("Update ticket status", admin = true, manager = true, accountant = true, client = false),
            PermissionRow("Upload documents", admin = true, manager = false, accountant = true, client = true),
            PermissionRow("Review / approve documents", admin = true, manager = true, accountant = true, client = false),
            PermissionRow("Send secure messages", admin = true, manager = true, accountant = true, client = true),
            PermissionRow("View management reports", admin = true, manager = true, accountant = false, client = false)
        )

        rvPermissions.adapter = PermissionAdapter(permissions)
    }

    private fun setupAuditLogSection() {
        val rvAuditLog = findViewById<RecyclerView>(R.id.rvAuditLog)
        val tvAuditEmptyState = findViewById<TextView>(R.id.tvAuditEmptyState)
        rvAuditLog.layoutManager = LinearLayoutManager(this)
        auditAdapter = AuditLogAdapter(emptyList())
        rvAuditLog.adapter = auditAdapter

        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

        db.collection("audit_log")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(40)
            .get()
            .addOnSuccessListener { result ->
                val entries = result.documents.map { d ->
                    val ts = d.getTimestamp("timestamp")
                    AuditLogEntry(
                        userType = d.getString("userType") ?: "User",
                        action = d.getString("action") ?: "",
                        details = d.getString("details") ?: "",
                        timestampLabel = ts?.toDate()?.let { sdf.format(it) } ?: ""
                    )
                }
                auditAdapter.updateData(entries)
                tvAuditEmptyState.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
    }

    private fun setupSystemHealthSection() {
        setStat(R.id.statStorageUsed, "Storage Used", "—")
        setStat(R.id.statActiveClientsHealth, "Active Clients", "0")
        setStat(R.id.statActiveStaff, "Active Staff", "0")
        setStat(R.id.statDocsPending, "Docs Pending Review", "0")
        setStat(R.id.statFailedLogins, "Failed Logins Today", "0")
        setStat(R.id.statAuditEvents, "Audit Events Today", "0")

        colorStatBar(R.id.statStorageUsed, R.color.purple)
        colorStatBar(R.id.statActiveClientsHealth, R.color.primary)
        colorStatBar(R.id.statActiveStaff, R.color.success)
        colorStatBar(R.id.statDocsPending, R.color.warning)
        colorStatBar(R.id.statFailedLogins, R.color.danger)
        colorStatBar(R.id.statAuditEvents, R.color.primary)

        // Active clients / staff counts
        db.collection("users").whereEqualTo("role", "client").get()
            .addOnSuccessListener { setStat(R.id.statActiveClientsHealth, "Active Clients", it.size().toString()) }

        db.collection("users").whereIn("role", listOf("accountant", "manager", "admin")).get()
            .addOnSuccessListener { setStat(R.id.statActiveStaff, "Active Staff", it.size().toString()) }

        // Documents pending review
        db.collection("documents").whereEqualTo("reviewStatus", "pending").get()
            .addOnSuccessListener { setStat(R.id.statDocsPending, "Docs Pending Review", it.size().toString()) }

        // Failed logins today and audit events today (from audit_log, filtered client-side by date)
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        db.collection("audit_log")
            .whereGreaterThanOrEqualTo("timestamp", com.google.firebase.Timestamp(startOfToday))
            .get()
            .addOnSuccessListener { result ->
                setStat(R.id.statAuditEvents, "Audit Events Today", result.size().toString())
                val failedLogins = result.documents.count { it.getString("action") == "Failed Login" }
                setStat(R.id.statFailedLogins, "Failed Logins Today", failedLogins.toString())
            }

        // Service status list — reflects what this app actually depends on
        val rvServiceStatus = findViewById<RecyclerView>(R.id.rvServiceStatus)
        rvServiceStatus.layoutManager = LinearLayoutManager(this)
        rvServiceStatus.adapter = ServiceStatusAdapter(
            listOf(
                ServiceStatus("Firestore Database", "Operational", true),
                ServiceStatus("Firebase Storage", "Operational", true),
                ServiceStatus("Authentication Service", "Operational", true)
            )
        )
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