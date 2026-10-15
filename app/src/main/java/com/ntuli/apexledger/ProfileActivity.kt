package com.ntuli.apexledger

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
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
import com.ntuli.apexledger.util.Labels

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
        val tvAvatarInitials = findViewById<TextView>(R.id.tvAvatarInitials)
        val switchEmail = findViewById<SwitchMaterial>(R.id.switchEmail)
        val switchSms = findViewById<SwitchMaterial>(R.id.switchSms)
        val switchPush = findViewById<SwitchMaterial>(R.id.switchPush)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveProfile)
        val switchDark = findViewById<SwitchMaterial>(R.id.switchDarkMode)
        switchDark.isChecked = com.ntuli.apexledger.util.ThemeMode.isDark(this)
        switchDark.setOnCheckedChangeListener { _, on ->
            if (on != com.ntuli.apexledger.util.ThemeMode.isDark(this)) {
                com.ntuli.apexledger.util.ThemeMode.setDark(this, on)
            }
        }
        val btnSavePersonal = findViewById<MaterialButton>(R.id.btnSavePersonal)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etAddress = findViewById<EditText>(R.id.etAddress)
        val tvTaxNumber = findViewById<TextView>(R.id.tvTaxNumber)
        val tvServicePref = findViewById<TextView>(R.id.tvServicePref)
        val tvRoleReadonly = findViewById<TextView>(R.id.tvRoleReadonly)
        val tvDepartmentReadonly = findViewById<TextView>(R.id.tvDepartmentReadonly)
        val groupClient = findViewById<android.view.View>(R.id.groupClientFields)
        val groupStaff = findViewById<android.view.View>(R.id.groupStaffFields)
        var isClient = true
        val btnChangePassword = findViewById<MaterialButton>(R.id.btnChangePassword)

        tvEmail.text = auth.currentUser?.email ?: ""

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val fullName = doc.getString("full_name") ?: ""
                    etFullName.setText(fullName)
                    tvAvatarInitials.text = initialsFrom(fullName)
                    switchEmail.isChecked = doc.getBoolean("notify_email") ?: true
                    switchSms.isChecked = doc.getBoolean("notify_sms") ?: false
                    switchPush.isChecked = doc.getBoolean("notify_push") ?: true

                    val role = doc.getString("role") ?: "client"
                    findViewById<TextView>(R.id.tvBannerTitle).text = doc.getString("full_name") ?: "My Profile"
                    findViewById<TextView>(R.id.tvBannerSubtitle).text =
                        listOf(doc.getString("email") ?: "", Labels.title(role)).filter { it.isNotEmpty() }.joinToString("  •  ")
                    isClient = role == "client"
                    if (isClient) {
                        groupClient.visibility = android.view.View.VISIBLE
                        etPhone.setText(doc.getString("phone") ?: "")
                        etAddress.setText(doc.getString("address") ?: "")
                        tvTaxNumber.text = doc.getString("taxNumber") ?: "—"
                        tvServicePref.text = Labels.service(doc.getString("servicePreference") ?: "")
                    } else {
                        groupStaff.visibility = android.view.View.VISIBLE
                        tvRoleReadonly.text = Labels.title(role)
                        tvDepartmentReadonly.text = (doc.getString("department") ?: "").ifEmpty { "—" }
                    }
                }
        }

        btnSavePersonal.setOnClickListener {
            if (uid == null) return@setOnClickListener
            val name = etFullName.text.toString().trim()
            if (name.isEmpty()) {
                com.ntuli.apexledger.util.Feedback.show(this, "Full name is required")
                return@setOnClickListener
            }
            val personal = hashMapOf<String, Any>("full_name" to name)
            if (isClient) {
                personal["phone"] = etPhone.text.toString().trim()
                personal["address"] = etAddress.text.toString().trim()
            }
            db.collection("users").document(uid).update(personal)
                .addOnSuccessListener {
                    com.ntuli.apexledger.util.Feedback.show(this, "Profile updated")
                    tvAvatarInitials.text = initialsFrom(name)
                }
                .addOnFailureListener { e ->
                    com.ntuli.apexledger.util.Feedback.show(this, "Update failed: ${e.message}")
                }
        }

        btnSave.setOnClickListener {
            if (uid == null) return@setOnClickListener
            val updates = mapOf(
                "notify_email" to switchEmail.isChecked,
                "notify_sms" to switchSms.isChecked,
                "notify_push" to switchPush.isChecked
            )
            db.collection("users").document(uid).update(updates)
                .addOnSuccessListener {
                    com.ntuli.apexledger.util.Feedback.show(this, "Preferences saved")
                }
                .addOnFailureListener { e ->
                    com.ntuli.apexledger.util.Feedback.show(this, "Update failed: ${e.message}")
                }
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
    }

    private fun initialsFrom(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0][0]}${parts[1][0]}".uppercase()
            parts.size == 1 && parts[0].isNotEmpty() -> parts[0][0].uppercase()
            else -> "?"
        }
    }

    private fun showChangePasswordDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = view.findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword = view.findViewById<EditText>(R.id.etNewPassword)
        com.ntuli.apexledger.util.PasswordToggle.attach(etCurrentPassword)
        com.ntuli.apexledger.util.PasswordToggle.attach(etNewPassword)
        val tvErrorCurrentPassword = view.findViewById<TextView>(R.id.tvErrorCurrentPassword)
        val tvErrorNewPassword = view.findViewById<TextView>(R.id.tvErrorNewPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(view)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btnUpdate = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnUpdate.setOnClickListener {
                etCurrentPassword.setBackgroundResource(R.drawable.bg_input_field)
                etNewPassword.setBackgroundResource(R.drawable.bg_input_field)
                tvErrorCurrentPassword.visibility = android.view.View.GONE
                tvErrorNewPassword.visibility = android.view.View.GONE

                val currentPassword = etCurrentPassword.text.toString()
                val newPassword = etNewPassword.text.toString()

                var hasError = false
                if (currentPassword.isEmpty()) {
                    etCurrentPassword.setBackgroundResource(R.drawable.bg_input_field_error)
                    tvErrorCurrentPassword.text = "Current password is required"
                    tvErrorCurrentPassword.visibility = android.view.View.VISIBLE
                    hasError = true
                }
                if (newPassword.length < 8) {
                    etNewPassword.setBackgroundResource(R.drawable.bg_input_field_error)
                    tvErrorNewPassword.text = "New password must be at least 8 characters"
                    tvErrorNewPassword.visibility = android.view.View.VISIBLE
                    hasError = true
                }
                if (hasError) return@setOnClickListener

                val user = auth.currentUser
                val email = user?.email
                if (user != null && email != null) {
                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    com.ntuli.apexledger.util.Feedback.show(this, "Password updated")
                                    dialog.dismiss()
                                }
                                .addOnFailureListener { e ->
                                    com.ntuli.apexledger.util.Feedback.show(this, "Failed: ${e.message}")
                                }
                        }
                        .addOnFailureListener {
                            etCurrentPassword.setBackgroundResource(R.drawable.bg_input_field_error)
                            tvErrorCurrentPassword.text = "Current password is incorrect"
                            tvErrorCurrentPassword.visibility = android.view.View.VISIBLE
                        }
                }
            }
        }

        dialog.show()
    }
}