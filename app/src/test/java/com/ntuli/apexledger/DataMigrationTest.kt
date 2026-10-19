package com.ntuli.apexledger

import com.ntuli.apexledger.util.DataMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataMigrationTest {

    @Test
    fun oldFields_areRenamedToCamelCase() {
        val fields = setOf("full_name", "email", "role", "created_at", "notify_email", "notify_sms", "notify_push")
        val renames = DataMigration.fieldsToRename(fields)
        assertEquals(
            listOf(
                "full_name" to "fullName",
                "created_at" to "createdAt",
                "notify_email" to "notifyEmail",
                "notify_sms" to "notifySms",
                "notify_push" to "notifyPush"
            ),
            renames
        )
    }

    @Test
    fun alreadyMigratedProfile_needsNothing() {
        val fields = setOf("fullName", "email", "role", "createdAt", "notifyEmail", "notifySms", "notifyPush")
        assertTrue(DataMigration.fieldsToRename(fields).isEmpty())
    }

    @Test
    fun staffProfileWithoutNotificationFields_onlyRenamesWhatExists() {
        val fields = setOf("full_name", "email", "role", "department", "active", "created_at")
        assertEquals(
            listOf("full_name" to "fullName", "created_at" to "createdAt"),
            DataMigration.fieldsToRename(fields)
        )
    }

    @Test
    fun everyNewNameIsCamelCase() {
        DataMigration.USER_FIELD_RENAMES.values.forEach { name ->
            assertTrue("$name should be camelCase", name.matches(Regex("^[a-z]+([A-Z][a-z]+)*$")))
        }
    }
}