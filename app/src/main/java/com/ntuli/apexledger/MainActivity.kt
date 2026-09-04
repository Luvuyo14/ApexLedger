package com.ntuli.apexledger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                tvStatus.text = "Please enter email and password"
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid == null) {
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                        return@addOnSuccessListener
                    }

                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            val role = doc.getString("role") ?: "client"
                            val destination = if (role == "client") {
                                DashboardActivity::class.java
                            } else {
                                StaffDashboardActivity::class.java
                            }
                            val role = doc.getString("role") ?: "client"
                            com.ntuli.apexledger.util.AuditLogger.logEvent(
                                if (role == "client") "Client" else "User", "Login"
                            )
                            startActivity(Intent(this, destination))
                            finish()
                        }
                        .addOnFailureListener {
                            startActivity(Intent(this, DashboardActivity::class.java))
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    tvStatus.setTextColor(0xFFD32F2F.toInt())
                    tvStatus.text = "Login failed: ${e.message}"
                }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}