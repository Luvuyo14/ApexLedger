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

        val etFullName = findViewById<EditText>(R.id.etFullName)
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
                .addOnSuccessListener {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    tvStatus.setTextColor(0xFFD32F2F.toInt())
                    tvStatus.text = "Login failed: ${e.message}"
                }
        }

        btnRegister.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.length < 6) {
                tvStatus.text = "Fill in name, email, and a 6+ character password"
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener

                    val profile = hashMapOf(
                        "full_name" to fullName,
                        "email" to email,
                        "role" to "client",
                        "notify_email" to true,
                        "notify_sms" to false,
                        "notify_push" to true,
                        "created_at" to com.google.firebase.Timestamp.now()
                    )

                    db.collection("users").document(uid).set(profile)
                        .addOnSuccessListener {
                            tvStatus.setTextColor(0xFF2E7D32.toInt())
                            tvStatus.text = "Account created! You can now sign in."
                        }
                        .addOnFailureListener { e ->
                            tvStatus.setTextColor(0xFFD32F2F.toInt())
                            tvStatus.text = "Profile save failed: ${e.message}"
                        }
                }
                .addOnFailureListener { e ->
                    tvStatus.setTextColor(0xFFD32F2F.toInt())
                    tvStatus.text = "Registration failed: ${e.message}"
                }
        }
    }
}