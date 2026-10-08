package com.ntuli.apexledger

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import com.ntuli.apexledger.ui.ClientAdapter
import com.ntuli.apexledger.ui.ClientListItem
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
    private lateinit var clientAdapter: ClientAdapter

    private val roles = listOf("accountant", "manager", "admin")
    private val departments = listOf(
        "Taxation (accountant)", "Payroll (accountant)", "Bookkeeping (accountant)",
        "Advisory (accountant)", "Audit (accountant)", "Administration"
    )

    private var clientIds: List<String> = emptyList()
    private var clientNames: List<String> = emptyList()
    private var accountantIds: List<String> = emptyList()
    private var accountantNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_panel)

        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        setupTabs()
        setupUserManagementSection()
        setupClientAssignmentSection()
        setupPermissionsSection()
        setupAuditLogSection()
        setupSystemHealthSection()
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val sectionUserManagement = findViewById<View>(R.id.sectionUserManagement)
        val sectionPermissions = findViewById<View>(R.id.sectionPermissions)
        val sectionAuditLog = findViewById<View>(R.id.sectionAuditLog)
        val sectionSystemHealth = findViewById<View>(R.id.sectionSystemHealth)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                sectionUserManagement.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                sectionPermissions.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                sectionAuditLog.visibility = if (tab.position == 2) View.VISIBLE else View.GONE
                sectionSystemHealth.visibility = if (tab.position == 3) View.VISIBLE else View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ---------------------------------------------------------------
    // User management
    // ---------------------------------------------------------------

    private fun setupUserManagementSection() {
        val etStaffName = findViewById<EditText>(R.id.etStaffName)
        val etStaffEmail = findViewById<EditText>(R.id.etStaffEmail)
        val etStaffPassword = findViewById<EditText>(R.id.etStaffPassword)
        val tvErrorStaffName = findViewById<TextView>(R.id.tvErrorStaffName)
        val tvErrorStaffEmail = findViewById<TextView>(R.id.tvErrorStaffEmail)
        val tvErrorStaffPassword = findViewById<TextView>(R.id.tvErrorStaffPassword)
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
            etStaffName.setBackgroundResource(R.drawable.bg_input_field)
            etStaffEmail.setBackgroundResource(R.drawable.bg_input_field)
            etStaffPassword.setBackgroundResource(R.drawable.bg_input_field)
            tvErrorStaffName.visibility = View.GONE
            tvErrorStaffEmail.visibility = View.GONE
            tvErrorStaffPassword.visibility = View.GONE
            tvStatus.text = ""

            val name = etStaffName.text.toString().trim()
            val email = etStaffEmail.text.toString().trim()
            val password = etStaffPassword.text.toString()
            val role = roles[spinnerRole.selectedItemPosition]
            val department = departments[spinnerDepartment.selectedItemPosition]

            var hasError = false
            if (name.isEmpty()) {
                etStaffName.setBackgroundResource(R.drawable.bg_input_field_error)
                tvErrorStaffName.text = "Full name is required"
                tvErrorStaffName.visibility = View.VISIBLE
                hasError = true
            }
            if (email.isEmpty()) {
                etStaffEmail.setBackgroundResource(R.drawable.bg_input_field_error)
                tvErrorStaffEmail.text = "Email is required"
                tvErrorStaffEmail.visibility = View.VISIBLE
                hasError = true
            }
            if (password.length < 8) {
                etStaffPassword.setBackgroundResource(R.drawable.bg_input_field_error)
                tvErrorStaffPassword.text = "Password must be at least 8 characters"
                tvErrorStaffPassword.visibility = View.VISIBLE
                hasError = true
            }
            if (hasError) return@setOnClickListener

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
                        loadAccountantsForSpinner()
                    }
                    .addOnFailureListener { e ->
                        tvStatus.setTextColor(0xFFD32F2F.toInt())
                        tvStatus.text = "Profile save failed: ${e.message}"
                    }

                secondaryAuth.signOut()
                secondaryApp.delete()
            }
            .addOnFailureListener { e ->
                findViewById<EditText>(R.id.etStaffEmail).setBackgroundResource(R.drawable.bg_input_field_error)
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
                findViewById<TextView>(R.id.tvStaffEmptyState).visibility =
                    if (staff.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    // ---------------------------------------------------------------
    // Client -> accountant assignment (override / fallback path)
    // ---------------------------------------------------------------

    private fun setupClientAssignmentSection() {
        val spinnerClient = findViewById<Spinner>(R.id.spinnerAssignClient)
        val spinnerAccountant = findViewById<Spinner>(R.id.spinnerAssignAccountant)
        val btnAssign = findViewById<MaterialButton>(R.id.btnAssignClient)
        val rvClients = findViewById<RecyclerView>(R.id.rvClients)

        clientAdapter = ClientAdapter(emptyList())
        rvClients.layoutManager = LinearLayoutManager(this)
        rvClients.adapter = clientAdapter

        loadClientsForSpinnerAndList()
        loadAccountantsForSpinner()

        btnAssign.setOnClickListener {
            if (clientIds.isEmpty() || accountantIds.isEmpty()) {
                Toast.makeText(this, "Need at least one client and one accountant", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clientId = clientIds[spinnerClient.selectedItemPosition]
            val clientName = clientNames[spinnerClient.selectedItemPosition]
            val accountantId = accountantIds[spinnerAccountant.selectedItemPosition]
            val accountantName = accountantNames[spinnerAccountant.selectedItemPosition]

            db.collection("users").document(clientId)
                .update("assignedAccountantId", accountantId)
                .addOnSuccessListener {
                    Toast.makeText(this, "$clientName assigned to $accountantName", Toast.LENGTH_SHORT).show()
                    AuditLogger.logEvent(
                        "Admin", "Assign Client",
                        "$clientName assigned to accountant $accountantName"
                    )
                    loadClientsForSpinnerAndList()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun loadClientsForSpinnerAndList() {
        db.collection("users")
            .whereEqualTo("role", "client")
            .get()
            .addOnSuccessListener { result ->
                clientIds = result.documents.map { it.id }
                clientNames = result.documents.map { it.getString("full_name") ?: "Unnamed client" }

                val displayNames = if (clientNames.isEmpty()) listOf("No clients yet") else clientNames
                findViewById<Spinner>(R.id.spinnerAssignClient).adapter =
                    ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)

                val accountantIdToName = accountantIds.zip(accountantNames).toMap()

                val clientListItems = result.documents.map { d ->
                    val accId = d.getString("assignedAccountantId") ?: ""
                    ClientListItem(
                        uid = d.id,
                        fullName = d.getString("full_name") ?: "Unnamed client",
                        email = d.getString("email") ?: "",
                        assignedAccountantName = accountantIdToName[accId] ?: ""
                    )
                }
                clientAdapter.updateData(clientListItems)
                findViewById<TextView>(R.id.tvClientsEmptyState).visibility =
                    if (clientListItems.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun loadAccountantsForSpinner() {
        db.collection("users")
            .whereEqualTo("role", "accountant")
            .get()
            .addOnSuccessListener { result ->
                accountantIds = result.documents.map { it.id }
                accountantNames = result.documents.map { it.getString("full_name") ?: "Unnamed accountant" }

                val displayNames = if (accountantNames.isEmpty()) listOf("No accountants yet") else accountantNames
                findViewById<Spinner>(R.id.spinnerAssignAccountant).adapter =
                    ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)

                loadClientsForSpinnerAndList()
            }
    }

    // ---------------------------------------------------------------
    // Permissions matrix (static reference)
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // Audit log
    // ---------------------------------------------------------------

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
                tvAuditEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    // ---------------------------------------------------------------
    // System health
    // ---------------------------------------------------------------

    private fun setupSystemHealthSection() {
        setStat(R.id.statStorageUsed, "Storage Used", "Not available")
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

        db.collection("users").whereEqualTo("role", "client").get()
            .addOnSuccessListener { setStat(R.id.statActiveClientsHealth, "Active Clients", it.size().toString()) }

        db.collection("users").whereIn("role", listOf("accountant", "manager", "admin")).get()
            .addOnSuccessListener { setStat(R.id.statActiveStaff, "Active Staff", it.size().toString()) }

        db.collection("documents").whereEqualTo("reviewStatus", "pending").get()
            .addOnSuccessListener { setStat(R.id.statDocsPending, "Docs Pending Review", it.size().toString()) }

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

        val rvServiceStatus = findViewById<RecyclerView>(R.id.rvServiceStatus)
        rvServiceStatus.layoutManager = LinearLayoutManager(this)
        fetchGcpServiceStatus()
    }

    /**
     * Checks Google Cloud's public, unauthenticated incidents feed for any
     * ONGOING incident (no "end" timestamp) affecting Firestore or Cloud
     * Storage. Firebase Authentication incidents are tracked on a separate
     * dashboard (status.firebase.google.com) with no confirmed public JSON
     * feed, so that row points to the dashboard instead of guessing.
     */
    private fun fetchGcpServiceStatus() {
        Thread {
            var firestoreDisrupted: Boolean? = null
            var storageDisrupted: Boolean? = null

            try {
                val url = java.net.URL("https://status.cloud.google.com/incidents.json")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"

                if (connection.responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val incidents = org.json.JSONArray(body)
                    firestoreDisrupted = false
                    storageDisrupted = false

                    for (i in 0 until incidents.length()) {
                        val incident = incidents.getJSONObject(i)
                        val isOngoing = !incident.has("end") || incident.isNull("end") ||
                                incident.optString("end", "").isEmpty()
                        if (!isOngoing) continue

                        val affectedProducts = incident.optJSONArray("affected_products") ?: continue
                        for (j in 0 until affectedProducts.length()) {
                            val name = affectedProducts.getJSONObject(j).optString("title", "")
                            if (name.contains("Firestore", ignoreCase = true)) firestoreDisrupted = true
                            if (name.contains("Cloud Storage", ignoreCase = true)) storageDisrupted = true
                        }
                    }
                }
            } catch (e: Exception) {
                // Network failure or parse error: leave both null (unknown)
                // rather than guessing a status.
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updateServiceStatusList(firestoreDisrupted, storageDisrupted)
            }
        }.start()
    }

    private fun updateServiceStatusList(firestoreDisrupted: Boolean?, storageDisrupted: Boolean?) {
        val rvServiceStatus = findViewById<RecyclerView>(R.id.rvServiceStatus)
        val services = listOf(
            ServiceStatus(
                "Firestore Database",
                when (firestoreDisrupted) {
                    true -> "Disrupted"
                    false -> "Operational"
                    null -> "Unknown (check failed)"
                },
                firestoreDisrupted == false
            ),
            ServiceStatus(
                "Firebase Storage",
                when (storageDisrupted) {
                    true -> "Disrupted"
                    false -> "Operational"
                    null -> "Unknown (check failed)"
                },
                storageDisrupted == false
            ),
            ServiceStatus(
                "Authentication Service",
                "See status.firebase.google.com",
                true
            )
        )
        rvServiceStatus.adapter = ServiceStatusAdapter(services)
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