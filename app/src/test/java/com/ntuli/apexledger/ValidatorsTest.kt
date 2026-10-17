package com.ntuli.apexledger

import com.ntuli.apexledger.util.Validators
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun validEmails_areAccepted() {
        assertTrue(Validators.isValidEmail("luvuyo@gmail.com"))
        assertTrue(Validators.isValidEmail("priya.tax@ntuli.co.za"))
        assertTrue(Validators.isValidEmail("  spaced@test.com  "))
    }

    @Test
    fun invalidEmails_areRejected() {
        assertFalse(Validators.isValidEmail(""))
        assertFalse(Validators.isValidEmail("no-at-sign.com"))
        assertFalse(Validators.isValidEmail("missing@domain"))
        assertFalse(Validators.isValidEmail("@nouser.com"))
        assertFalse(Validators.isValidEmail("two@@signs.com"))
    }

    @Test
    fun strongPasswords_areAccepted() {
        assertTrue(Validators.isStrongPassword("Passw0rd"))
        assertTrue(Validators.isStrongPassword("LongerPassword123"))
    }

    @Test
    fun weakPasswords_areRejected() {
        assertFalse(Validators.isStrongPassword(""))
        assertFalse(Validators.isStrongPassword("Pass1"))          // too short
        assertFalse(Validators.isStrongPassword("password1"))      // no uppercase
        assertFalse(Validators.isStrongPassword("Password"))       // no number
    }

    @Test
    fun taxNumber_mustBeExactlyTenDigits() {
        assertTrue(Validators.isValidTaxNumber("1234567890"))
        assertFalse(Validators.isValidTaxNumber("123456789"))      // 9 digits
        assertFalse(Validators.isValidTaxNumber("12345678901"))    // 11 digits
        assertFalse(Validators.isValidTaxNumber("12345abcde"))
        assertFalse(Validators.isValidTaxNumber(""))
    }
}