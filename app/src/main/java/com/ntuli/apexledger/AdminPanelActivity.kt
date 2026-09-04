package com.ntuli.apexledger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.ui.StaffAdapter
import com.ntuli.apexledger.ui.StaffMember

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StaffAdapter

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

        adapter = StaffAdapter(emptyList())
        rvStaff.layoutManager = LinearLayoutManager(this)
        rvStaff.adapter = adapter

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

    /**
     * Creates a new staff Firebase Auth account WITHOUT switching the current
     * admin's session. We do this by spinning up a temporary secondary
     * FirebaseApp instance, using it only for this one signup call, then
     * discarding it. The admin's own FirebaseAuth.getInstance() session
     * (tied to the default FirebaseApp) is never touched.
     */
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
                        loadStaffList()
                    }
                    .addOnFailureListener { e ->
                        tvStatus.setTextColor(0xFFD32F2F.toInt())
                        tvStatus.text = "Profile save failed: ${e.message}"
                    }

                // Sign out of the secondary app's session immediately, then clean it up.
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
                adapter.updateData(staff)
            }
    }
}