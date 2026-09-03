package com.ntuli.apexledger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val servicePreferences = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etTaxNumber = findViewById<EditText>(R.id.etTaxNumber)
        val spinnerServicePreference = findViewById<Spinner>(R.id.spinnerServicePreference)
        val etAddress = findViewById<EditText>(R.id.etAddress)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnRegisterClient = findViewById<MaterialButton>(R.id.btnRegisterClient)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        spinnerServicePreference.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, servicePreferences
        )

        btnRegisterClient.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val taxNumber = etTaxNumber.text.toString().trim()
            val servicePreference = servicePreferences[spinnerServicePreference.selectedItemPosition]
            val address = etAddress.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty() || taxNumber.isEmpty() || address.isEmpty()) {
                tvStatus.text = "Please fill in all required fields"
                return@setOnClickListener
            }
            if (taxNumber.length != 10) {
                tvStatus.text = "Tax number must be exactly 10 digits"
                return@setOnClickListener
            }
            if (password.length < 8) {
                tvStatus.text = "Password must be at least 8 characters"
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                tvStatus.text = "Passwords do not match"
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener

                    val profile = hashMapOf(
                        "full_name" to fullName,
                        "email" to email,
                        "phone" to phone,
                        "taxNumber" to taxNumber,
                        "servicePreference" to servicePreference,
                        "address" to address,
                        "role" to "client",
                        "assignedAccountantId" to "",
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