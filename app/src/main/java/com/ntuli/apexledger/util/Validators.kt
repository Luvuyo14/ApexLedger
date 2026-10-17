package com.ntuli.apexledger.util

/** Input rules used by the registration and admin forms. Plain Kotlin, so they can be unit tested. */
object Validators {

    /** At least 8 characters, 1 uppercase letter and 1 number. */
    val PASSWORD = Regex("^(?=.*[A-Z])(?=.*\\d).{8,}$")

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
    private val TAX_NUMBER = Regex("^\\d{10}$")

    fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

    fun isStrongPassword(password: String): Boolean = PASSWORD.matches(password)

    /** A South African tax number is exactly 10 digits. */
    fun isValidTaxNumber(taxNumber: String): Boolean = TAX_NUMBER.matches(taxNumber)
}