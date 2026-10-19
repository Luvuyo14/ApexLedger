package com.ntuli.apexledger.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * One-time clean-up of the database: the five older snake_case fields in `users` are renamed to
 * camelCase so every field follows the same naming convention. It runs when an administrator opens
 * the dashboard and does nothing once it has finished. Each profile is changed in one atomic update
 * (new field written and old field deleted together), so a profile is never left half-renamed.
 */
object DataMigration {
    private const val PREFS = "migrations"
    private const val KEY = "user_fields_camel_v1"

    /** old field name -> new field name */
    val USER_FIELD_RENAMES: Map<String, String> = linkedMapOf(
        "full_name" to "fullName",
        "created_at" to "createdAt",
        "notify_email" to "notifyEmail",
        "notify_sms" to "notifySms",
        "notify_push" to "notifyPush"
    )

    /** The renames a profile still needs, judged from the field names it currently has. */
    fun fieldsToRename(existingFields: Set<String>): List<Pair<String, String>> =
        USER_FIELD_RENAMES.entries.filter { it.key in existingFields }.map { it.key to it.value }

    /** [onDone] receives how many profiles were changed. */
    fun renameUserFields(activity: Activity, onDone: (Int) -> Unit = {}) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return

        val db = FirebaseFirestore.getInstance()
        db.collection("users").get().addOnSuccessListener { snapshot ->
            val writes = ArrayList<Pair<DocumentReference, HashMap<String, Any?>>>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val updates = HashMap<String, Any?>()
                for ((oldName, newName) in fieldsToRename(data.keys)) {
                    if (newName !in data.keys) updates[newName] = data[oldName]   // never overwrite a new value
                    updates[oldName] = FieldValue.delete()
                }
                if (updates.isNotEmpty()) writes.add(doc.reference to updates)
            }
            commit(db, writes, 0, prefs, writes.size, onDone)
        }
        // if the read fails nothing is saved, so the clean-up simply tries again at the next admin login
    }

    private fun commit(
        db: FirebaseFirestore,
        writes: List<Pair<DocumentReference, HashMap<String, Any?>>>,
        from: Int,
        prefs: SharedPreferences,
        total: Int,
        onDone: (Int) -> Unit
    ) {
        if (from >= writes.size) {
            prefs.edit().putBoolean(KEY, true).apply()
            onDone(total)
            return
        }
        val batch = db.batch()
        for (i in from until minOf(from + 400, writes.size)) {
            batch.update(writes[i].first, writes[i].second)
        }
        batch.commit().addOnSuccessListener { commit(db, writes, from + 400, prefs, total, onDone) }
        // on failure the flag stays unset and the clean-up retries next time
    }
}