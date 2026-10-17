package com.ntuli.apexledger

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ntuli.apexledger.util.Labels
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val servicePreferences = listOf("tax_return", "payroll", "bookkeeping", "advisory", "audit")

    // Min 8 chars, at least 1 uppercase letter, at least 1 digit
    private val passwordRegex = com.ntuli.apexledger.util.Validators.PASSWORD

    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etTaxNumber: EditText
    private lateinit var tvErrorTaxNumber: TextView
    private lateinit var tvErrorService: TextView
    private lateinit var etAddress: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText

    private lateinit var tvErrorFullName: TextView
    private lateinit var tvErrorEmail: TextView
    private lateinit var tvErrorPhone: TextView
    private lateinit var tvErrorAddress: TextView
    private lateinit var tvErrorPassword: TextView
    private lateinit var tvErrorConfirmPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        com.ntuli.apexledger.util.PasswordToggle.attach(findViewById<EditText>(R.id.etPassword))
        com.ntuli.apexledger.util.PasswordToggle.attach(findViewById<EditText>(R.id.etConfirmPassword))

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etTaxNumber = findViewById(R.id.etTaxNumber)
        tvErrorTaxNumber = findViewById(R.id.tvErrorTaxNumber)
        tvErrorService = findViewById(R.id.tvErrorService)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelRegister).setOnClickListener {
            if (intent.getBooleanExtra("sso", false)) auth.signOut()
            finish()
        }
        val spinnerServicePreference = findViewById<Spinner>(R.id.spinnerServicePreference)
        etAddress = findViewById(R.id.etAddress)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        val btnRegisterClient = findViewById<MaterialButton>(R.id.btnRegisterClient)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // Opened after "Continue with Google" for someone who has no profile yet:
        // no password needed, name and email come from their Google account.
        val ssoMode = intent.getBooleanExtra("sso", false)
        if (ssoMode) {
            etFullName.setText(intent.getStringExtra("name") ?: "")
            etEmail.setText(intent.getStringExtra("email") ?: "")
            etEmail.isEnabled = false
            listOf(R.id.lblPassword, R.id.etPassword, R.id.tvErrorPassword, R.id.lblConfirmPassword,
                R.id.etConfirmPassword, R.id.tvErrorConfirmPassword).forEach { findViewById<View>(it).visibility = View.GONE }
            findViewById<TextView>(R.id.tvRegisterTitle).text = "Complete your profile"
            btnRegisterClient.text = "Finish"
        }
        val tvBackToLogin = findViewById<TextView>(R.id.tvBackToLogin)

        tvErrorFullName = findViewById(R.id.tvErrorFullName)
        tvErrorEmail = findViewById(R.id.tvErrorEmail)
        tvErrorPhone = findViewById(R.id.tvErrorPhone)
        tvErrorAddress = findViewById(R.id.tvErrorAddress)
        tvErrorPassword = findViewById(R.id.tvErrorPassword)
        tvErrorConfirmPassword = findViewById(R.id.tvErrorConfirmPassword)

        spinnerServicePreference.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("No preference (optional)") + servicePreferences.map { Labels.service(it) }
        )

        tvBackToLogin.setOnClickListener { finish() }

        btnRegisterClient.setOnClickListener {
            clearAllFieldErrors()
            tvStatus.text = ""

            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val servicePosition = spinnerServicePreference.selectedItemPosition
            val servicePreference = if (servicePosition > 0) servicePreferences[servicePosition - 1] else ""
            val taxNumber = etTaxNumber.text.toString().trim()
            val address = etAddress.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            var hasError = false

            if (fullName.isEmpty()) {
                showFieldError(etFullName, tvErrorFullName, "Full name is required")
                hasError = true
            }
            if (email.isEmpty()) {
                showFieldError(etEmail, tvErrorEmail, "Email is required")
                hasError = true
            } else if (!com.ntuli.apexledger.util.Validators.isValidEmail(email)) {
                showFieldError(etEmail, tvErrorEmail, "Enter a valid email address")
                hasError = true
            }
            if (phone.isEmpty()) {
                showFieldError(etPhone, tvErrorPhone, "Phone number is required")
                hasError = true
            }
            if (!com.ntuli.apexledger.util.Validators.isValidTaxNumber(taxNumber)) {
                showFieldError(etTaxNumber, tvErrorTaxNumber, "Tax number must be exactly 10 digits")
                hasError = true
            }
            if (address.isEmpty()) {
                showFieldError(etAddress, tvErrorAddress, "Address is required")
                hasError = true
            }
            if (!ssoMode) {
                if (password.isEmpty()) {
                    showFieldError(etPassword, tvErrorPassword, "Password is required")
                    hasError = true
                } else if (!passwordRegex.matches(password)) {
                    showFieldError(
                        etPassword, tvErrorPassword,
                        "Password needs 8+ chars, 1 uppercase letter and 1 number"
                    )
                    hasError = true
                }
                if (confirmPassword != password) {
                    showFieldError(etConfirmPassword, tvErrorConfirmPassword, "Passwords do not match")
                    hasError = true
                }
            }

            if (hasError) return@setOnClickListener

            btnRegisterClient.isEnabled = false

            val onAccountReady: (FirebaseUser?) -> Unit = ready@{ newUser ->
                if (newUser == null) {
                    btnRegisterClient.isEnabled = true
                    tvStatus.setTextColor(0xFFD32F2F.toInt())
                    tvStatus.text = "Registration failed. Please try again."
                    return@ready
                }

                val dutyKeyword = when (servicePreference) {
                    "tax_return" -> "Taxation"
                    "payroll" -> "Payroll"
                    "bookkeeping" -> "Bookkeeping"
                    "advisory" -> "Advisory"
                    "audit" -> "Audit"
                    else -> ""
                }

                db.collection("users")
                    .whereEqualTo("role", "accountant")
                    .get()
                    .addOnSuccessListener { accountantsResult ->
                        val matchedAccountant = accountantsResult.documents.firstOrNull { doc ->
                            val department = doc.getString("department") ?: ""
                            dutyKeyword.isNotEmpty() && department.startsWith(dutyKeyword) &&
                                    doc.getBoolean("active") != false
                        }

                        val profile = hashMapOf(
                            "full_name" to fullName,
                            "email" to email,
                            "phone" to phone,
                            "taxNumber" to taxNumber,
                            "servicePreference" to servicePreference,
                            "address" to address,
                            "role" to "client",
                            "assignedAccountantId" to (matchedAccountant?.id ?: ""),
                            "notify_email" to true,
                            "notify_sms" to false,
                            "notify_push" to true,
                            "created_at" to com.google.firebase.Timestamp.now()
                        )

                        db.collection("users").document(newUser.uid).set(profile)
                            .addOnSuccessListener {
                                btnRegisterClient.isEnabled = true
                                if (ssoMode) {
                                    com.ntuli.apexledger.util.AuditLogger.logEvent("Client", "Login", "Google single sign-on (new profile)")
                                    startActivity(android.content.Intent(this, DashboardActivity::class.java))
                                    finishAffinity()
                                    return@addOnSuccessListener
                                }
                                // Sign out so the person logs in explicitly,
                                // matching the "You can now sign in" message.
                                auth.signOut()
                                tvStatus.setTextColor(0xFF2E7D32.toInt())
                                tvStatus.text = if (matchedAccountant != null) {
                                    "Account created! You've been assigned to ${matchedAccountant.getString("full_name")}. You can now sign in."
                                } else {
                                    if (servicePreference.isEmpty()) {
                                        "Account created! You can now sign in. An accountant is assigned to you when you request a service."
                                    } else {
                                        "Account created! No accountant currently handles this service. An admin will assign one shortly. You can now sign in."
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                rollbackAccount(newUser)
                                btnRegisterClient.isEnabled = true
                                tvStatus.setTextColor(0xFFD32F2F.toInt())
                                tvStatus.text = "Could not save your profile, so the account was not created: ${e.message}. Please try again."
                            }
                    }
                    .addOnFailureListener { e ->
                        rollbackAccount(newUser)
                        btnRegisterClient.isEnabled = true
                        tvStatus.setTextColor(0xFFD32F2F.toInt())
                        tvStatus.text = "Could not complete registration, so the account was not created: ${e.message}. Please try again."
                    }
            }

            if (ssoMode) {
                onAccountReady(auth.currentUser)
            } else auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result -> onAccountReady(result.user) }
                .addOnFailureListener { e ->
                    btnRegisterClient.isEnabled = true
                    if (e is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        showFieldError(etEmail, tvErrorEmail, "An account with this email already exists")
                    } else {
                        tvStatus.setTextColor(0xFFD32F2F.toInt())
                        tvStatus.text = "Registration failed: ${e.message}"
                    }
                }
        }
    }

    /**
     * Removes a just-created Auth account when the rest of registration
     * failed, so the person isn't left with a login that has no profile
     * (which would block them from simply trying again).
     */
    private fun rollbackAccount(user: FirebaseUser) {
        user.delete().addOnCompleteListener { auth.signOut() }
    }

    private fun showFieldError(field: EditText, errorLabel: TextView, message: String) {
        field.setBackgroundResource(R.drawable.bg_input_field_error)
        errorLabel.text = message
        errorLabel.visibility = View.VISIBLE
    }

    private fun clearAllFieldErrors() {
        val fields = listOf(etFullName, etEmail, etPhone, etTaxNumber, etAddress, etPassword, etConfirmPassword)
        val errorLabels = listOf(
            tvErrorFullName, tvErrorEmail, tvErrorPhone, tvErrorTaxNumber, tvErrorService,
            tvErrorAddress, tvErrorPassword, tvErrorConfirmPassword
        )

        fields.forEach { it.setBackgroundResource(R.drawable.bg_input_field) }
        errorLabels.forEach { it.visibility = View.GONE }
    }
}