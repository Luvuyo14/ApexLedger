package com.ntuli.apexledger

import android.app.AlertDialog
import android.content.Intent
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
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
        com.ntuli.apexledger.util.PasswordToggle.attach(findViewById<EditText>(R.id.etPassword))

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<TextView>(R.id.btnRegister)
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

            val lockedMinutes = minutesLocked(email)
            if (lockedMinutes > 0) {
                showLoginError(
                    etEmail, etPassword, tvStatus,
                    "Too many failed attempts. This account is locked for another $lockedMinutes minute(s)."
                )
                return@setOnClickListener
            }

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
                                showLoginError(etEmail, etPassword, tvStatus, "This account was removed or is not set up. Please contact an administrator.")
                                return@addOnSuccessListener
                            }

                            if (doc.getBoolean("active") == false) {
                                auth.signOut()
                                showLoginError(etEmail, etPassword, tvStatus, "This account has been deactivated. Please contact an administrator.")
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

                            clearFailedAttempts(email)
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
                    registerFailedAttempt(email)
                    AuditLogger.logEvent("Client", "Failed Login", e.message ?: "")
                    showLoginError(etEmail, etPassword, tvStatus, "Login failed: ${e.message}")
                }
        }

        findViewById<View>(R.id.btnGoogle).setOnClickListener {
            startGoogleSignIn(etEmail, etPassword, tvStatus, btnLogin, progressLogin)
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    // ---------------------------------------------------------------
    // Single Sign-On with Google
    // ---------------------------------------------------------------
    private fun startGoogleSignIn(
        etEmail: EditText, etPassword: EditText, tvStatus: TextView, btnLogin: Button, progress: ProgressBar
    ) {
        // default_web_client_id is generated from google-services.json once Google sign-in is enabled in Firebase
        val idRes = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (idRes == 0) {
            showLoginError(
                etEmail, etPassword, tvStatus,
                "Google sign-in is not set up yet. In the Firebase Console enable Google sign-in, add your SHA-1 key and download a new google-services.json."
            )
            return
        }

        btnLogin.isEnabled = false
        progress.visibility = View.VISIBLE

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(getString(idRes))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        CredentialManager.create(this).getCredentialAsync(
            this, request, CancellationSignal(), ContextCompat.getMainExecutor(this),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        try {
                            val google = GoogleIdTokenCredential.createFrom(credential.data)
                            signInToFirebase(google, etEmail, etPassword, tvStatus, btnLogin, progress)
                        } catch (e: Exception) {
                            googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, "Google sign-in failed: ${e.message}")
                        }
                    } else {
                        googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, "Google sign-in returned an unexpected result.")
                    }
                }

                override fun onError(e: GetCredentialException) {
                    val message = when (e) {
                        is GetCredentialCancellationException -> "Google sign-in was cancelled."
                        is NoCredentialException -> "No Google account found on this phone. Add one in your phone settings and try again."
                        else -> "Google sign-in failed: ${e.message}"
                    }
                    googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, message)
                }
            }
        )
    }

    private fun googleFailed(
        etEmail: EditText, etPassword: EditText, tvStatus: TextView, btnLogin: Button, progress: ProgressBar, message: String
    ) {
        btnLogin.isEnabled = true
        progress.visibility = View.GONE
        showLoginError(etEmail, etPassword, tvStatus, message)
    }

    private fun signInToFirebase(
        google: GoogleIdTokenCredential, etEmail: EditText, etPassword: EditText,
        tvStatus: TextView, btnLogin: Button, progress: ProgressBar
    ) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    auth.signOut()
                    googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, "Something went wrong signing you in. Please try again.")
                    return@addOnSuccessListener
                }
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            // First time with Google: finish the client profile (phone, tax number, service...)
                            startActivity(
                                Intent(this, RegisterActivity::class.java)
                                    .putExtra("sso", true)
                                    .putExtra("name", google.displayName ?: "")
                                    .putExtra("email", google.id)
                            )
                            btnLogin.isEnabled = true
                            progress.visibility = View.GONE
                            return@addOnSuccessListener
                        }
                        if (doc.getBoolean("active") == false) {
                            auth.signOut()
                            googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, "This account has been deactivated. Please contact an administrator.")
                            return@addOnSuccessListener
                        }
                        val role = doc.getString("role")
                        val destination = when (role) {
                            "client" -> DashboardActivity::class.java
                            "accountant", "manager", "admin" -> StaffDashboardActivity::class.java
                            else -> null
                        }
                        if (destination == null) {
                            auth.signOut()
                            googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, "Your account role is not recognized. Please contact support.")
                            return@addOnSuccessListener
                        }
                        AuditLogger.logEvent(if (role == "client") "Client" else "User", "Login", "Google single sign-on")
                        startActivity(Intent(this, destination))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        auth.signOut()
                        googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, "Could not verify your account right now: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                googleFailed(etEmail, etPassword, tvStatus, btnLogin, progress, "Google sign-in failed: ${e.message}")
            }
    }

    // --- Local lock-out: 5 failed attempts lock the account on this device for 15 minutes ---
    private val prefs get() = getSharedPreferences("login_guard", MODE_PRIVATE)
    private fun key(email: String) = email.lowercase()

    private fun minutesLocked(email: String): Long {
        val until = prefs.getLong("lock_${key(email)}", 0L)
        val remaining = until - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 60000L) + 1 else 0
    }

    private fun registerFailedAttempt(email: String) {
        val k = key(email)
        val fails = prefs.getInt("fails_$k", 0) + 1
        val editor = prefs.edit()
        if (fails >= 5) {
            editor.putLong("lock_$k", System.currentTimeMillis() + 15 * 60 * 1000L)
            editor.putInt("fails_$k", 0)
        } else {
            editor.putInt("fails_$k", fails)
        }
        editor.apply()
    }

    private fun clearFailedAttempts(email: String) {
        prefs.edit().remove("fails_${key(email)}").remove("lock_${key(email)}").apply()
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
                    com.ntuli.apexledger.util.Feedback.show(this, "Please enter your email")
                    return@setPositiveButton
                }
                if (!com.ntuli.apexledger.util.Validators.isValidEmail(email)) {
                    com.ntuli.apexledger.util.Feedback.show(this, "Please enter a valid email address")
                    return@setPositiveButton
                }

                // No Firestore lookup here: nobody is signed in on this screen,
                // so the rules (correctly) deny reading the users collection.
                // Firebase only emails addresses that exist in Authentication,
                // and deliberately reports success either way so the screen
                // can't be used to discover which emails have accounts.
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        com.ntuli.apexledger.util.Feedback.show(this, "If an account exists for $email, a reset link has been sent. Check your inbox and spam folder.")
                    }
                    .addOnFailureListener { e ->
                        com.ntuli.apexledger.util.Feedback.show(this, "Could not send reset email: ${e.message}")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}