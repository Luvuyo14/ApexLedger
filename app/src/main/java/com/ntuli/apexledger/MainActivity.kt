package com.ntuli.apexledger

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
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
        val progressLogin = findViewById<ProgressBar>(R.id.progressLogin)

        btnLogin.setOnClickListener {
            etEmail.setBackgroundResource(R.drawable.bg_input_field)
            etPassword.setBackgroundResource(R.drawable.bg_input_field)
            tvErrorEmail.visibility = View.GONE
            tvErrorPassword.visibility = View.GONE
            tvStatus.text = ""

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            var hasError = false
            if (email.isEmpty()) {
                etEmail.setBackgroundResource(R.drawable.bg_input_field_error)
                tvErrorEmail.text = "Email is required"
                tvErrorEmail.visibility = View.VISIBLE
                hasError = true
            }
            if (password.isEmpty()) {
                etPassword.setBackgroundResource(R.drawable.bg_input_field_error)
                tvErrorPassword.text = "Password is required"
                tvErrorPassword.visibility = View.VISIBLE
                hasError = true
            }
            if (hasError) return@setOnClickListener

            btnLogin.isEnabled = false
            progressLogin.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid == null) {
                        auth.signOut()
                        showLoginError(etEmail, etPassword, tvStatus, "Something went wrong signing you in. Please try again.")
                        return@addOnSuccessListener
                    }

                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                auth.signOut()
                                showLoginError(etEmail, etPassword, tvStatus, "Your account setup is incomplete. Please contact support.")
                                return@addOnSuccessListener
                            }

                            val role = doc.getString("role")
                            if (role.isNullOrEmpty()) {
                                auth.signOut()
                                showLoginError(etEmail, etPassword, tvStatus, "Your account is missing role information. Please contact support.")
                                return@addOnSuccessListener
                            }

                            val destination = when (role) {
                                "client" -> DashboardActivity::class.java
                                "accountant", "manager", "admin" -> StaffDashboardActivity::class.java
                                else -> null
                            }

                            if (destination == null) {
                                auth.signOut()
                                showLoginError(etEmail, etPassword, tvStatus, "Your account role is not recognized. Please contact support.")
                                return@addOnSuccessListener
                            }

                            AuditLogger.logEvent(if (role == "client") "Client" else "User", "Login")

                            startActivity(Intent(this, destination))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            auth.signOut()
                            showLoginError(etEmail, etPassword, tvStatus, "Could not verify your account right now: ${e.message}. Please check your connection and try again.")
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
        resetLoginButton()
    }

    private fun resetLoginButton() {
        findViewById<Button>(R.id.btnLogin).isEnabled = true
        findViewById<ProgressBar>(R.id.progressLogin).visibility = View.GONE
    }

    private fun showForgotPasswordDialog() {
        val etResetEmail = EditText(this)
        etResetEmail.hint = "Enter your account email"
        etResetEmail.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
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
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // No Firestore lookup here: nobody is signed in on this screen,
                // so the rules (correctly) deny reading the users collection.
                // Firebase only emails addresses that exist in Authentication,
                // and deliberately reports success either way so the screen
                // can't be used to discover which emails have accounts.
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "If an account exists for $email, a reset link has been sent. Check your inbox and spam folder.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Could not send reset email: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}