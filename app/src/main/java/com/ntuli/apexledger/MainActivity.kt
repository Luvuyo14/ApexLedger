package com.ntuli.apexledger

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntuli.apexledger.util.AuditLogger

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val tvErrorEmail = findViewById<TextView>(R.id.tvErrorEmail)
        val tvErrorPassword = findViewById<TextView>(R.id.tvErrorPassword)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnLogin.setOnClickListener {
            etEmail.setBackgroundResource(R.drawable.bg_input_field)
            etPassword.setBackgroundResource(R.drawable.bg_input_field)
            tvErrorEmail.visibility = android.view.View.GONE
            tvErrorPassword.visibility = android.view.View.GONE
            tvStatus.text = ""

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            var hasError = false
            if (email.isEmpty()) {
                etEmail.setBackgroundResource(R.drawable.bg_input_field_error)
                tvErrorEmail.text = "Email is required"
                tvErrorEmail.visibility = android.view.View.VISIBLE
                hasError = true
            }
            if (password.isEmpty()) {
                etPassword.setBackgroundResource(R.drawable.bg_input_field_error)
                tvErrorPassword.text = "Password is required"
                tvErrorPassword.visibility = android.view.View.VISIBLE
                hasError = true
            }
            if (hasError) return@setOnClickListener

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid == null) {
                        // Should not normally happen, but never guess a role when we
                        // don't even have a reliable uid. Fail safely.
                        auth.signOut()
                        showLoginError(etEmail, etPassword, tvStatus, "Something went wrong signing you in. Please try again.")
                        return@addOnSuccessListener
                    }

                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                // Auth account exists but has no matching Firestore profile —
                                // a partially created account. Never default this to "client".
                                auth.signOut()
                                showLoginError(
                                    etEmail, etPassword, tvStatus,
                                    "Your account setup is incomplete. Please contact support."
                                )
                                return@addOnSuccessListener
                            }

                            val role = doc.getString("role")
                            if (role.isNullOrEmpty()) {
                                // A role must always be present. Missing role is a data
                                // problem, not a reason to silently treat someone as a client.
                                auth.signOut()
                                showLoginError(
                                    etEmail, etPassword, tvStatus,
                                    "Your account is missing role information. Please contact support."
                                )
                                return@addOnSuccessListener
                            }

                            AuditLogger.logEvent(
                                if (role == "client") "Client" else "User", "Login"
                            )

                            val destination = when (role) {
                                "client" -> DashboardActivity::class.java
                                "accountant", "manager", "admin" -> StaffDashboardActivity::class.java
                                else -> null
                            }

                            if (destination == null) {
                                // Unrecognized role value — don't guess which dashboard is safe.
                                auth.signOut()
                                showLoginError(
                                    etEmail, etPassword, tvStatus,
                                    "Your account role is not recognized. Please contact support."
                                )
                                return@addOnSuccessListener
                            }

                            startActivity(Intent(this, destination))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            // Network/Firestore error reading the profile. Do NOT fall back
                            // to the client dashboard — that would hide a real problem from
                            // the user and could put a staff member on the wrong screen.
                            auth.signOut()
                            showLoginError(
                                etEmail, etPassword, tvStatus,
                                "Could not verify your account right now: ${e.message}. Please check your connection and try again."
                            )
                        }
                }
                .addOnFailureListener { e ->
                    AuditLogger.logEvent("Client", "Failed Login", e.message ?: "")
                    showLoginError(etEmail, etPassword, tvStatus, "Login failed: ${e.message}")
                }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun showLoginError(etEmail: EditText, etPassword: EditText, tvStatus: TextView, message: String) {
        etEmail.setBackgroundResource(R.drawable.bg_input_field_error)
        etPassword.setBackgroundResource(R.drawable.bg_input_field_error)
        tvStatus.setTextColor(0xFFD32F2F.toInt())
        tvStatus.text = message
    }

    private fun showForgotPasswordDialog() {
        val etResetEmail = EditText(this)
        etResetEmail.hint = "Enter your account email"
        etResetEmail.setPadding(48, 32, 48, 0)

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("We'll send a password reset link to your email.")
            .setView(etResetEmail)
            .setPositiveButton("Send Reset Link") { _, _ ->
                val email = etResetEmail.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        AuditLogger.logEvent("Client", "Password Reset Requested", email)
                        Toast.makeText(this, "Reset link sent! Check your email.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}