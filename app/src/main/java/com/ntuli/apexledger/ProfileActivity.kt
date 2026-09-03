package com.ntuli.apexledger

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val etFullName = findViewById<EditText>(R.id.etFullName)
        val tvEmail = findViewById<TextView>(R.id.tvEmailReadonly)
        val switchEmail = findViewById<SwitchMaterial>(R.id.switchEmail)
        val switchSms = findViewById<SwitchMaterial>(R.id.switchSms)
        val switchPush = findViewById<SwitchMaterial>(R.id.switchPush)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveProfile)
        val btnChangePassword = findViewById<MaterialButton>(R.id.btnChangePassword)

        tvEmail.text = auth.currentUser?.email ?: ""

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    etFullName.setText(doc.getString("full_name") ?: "")
                    switchEmail.isChecked = doc.getBoolean("notify_email") ?: true
                    switchSms.isChecked = doc.getBoolean("notify_sms") ?: false
                    switchPush.isChecked = doc.getBoolean("notify_push") ?: true
                }
        }

        btnSave.setOnClickListener {
            if (uid == null) return@setOnClickListener
            val updates = mapOf(
                "full_name" to etFullName.text.toString().trim(),
                "notify_email" to switchEmail.isChecked,
                "notify_sms" to switchSms.isChecked,
                "notify_push" to switchPush.isChecked
            )
            db.collection("users").document(uid).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(48, 24, 48, 0)

        val etCurrentPassword = EditText(this)
        etCurrentPassword.hint = "Current password"
        etCurrentPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container.addView(etCurrentPassword)

        val etNewPassword = EditText(this)
        etNewPassword.hint = "New password (6+ characters)"
        etNewPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container.addView(etNewPassword)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val currentPassword = etCurrentPassword.text.toString()
                val newPassword = etNewPassword.text.toString()

                if (newPassword.length < 6) {
                    Toast.makeText(this, "New password must be 6+ characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val user = auth.currentUser
                val email = user?.email
                if (user != null && email != null) {
                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}