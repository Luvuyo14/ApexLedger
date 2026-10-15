package com.ntuli.apexledger.util

/** Turns raw database values (tax_return, in_progress ...) into the labels the website shows. */
object Labels {
    fun service(raw: String): String = when (raw) {
        "tax_return" -> "Tax Return"
        "payroll" -> "Payroll"
        "bookkeeping" -> "Bookkeeping"
        "advisory" -> "Advisory"
        "audit" -> "Audit"
        "other" -> "Other"
        else -> title(raw)
    }

    fun status(raw: String): String = title(raw)

    fun priority(raw: String): String = title(raw)

    /** "in_progress" -> "In Progress" */
    fun title(raw: String): String =
        raw.split("_", " ").filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /** Ticket-number prefix used by the website: TAX-2026-0001 */
    fun ticketPrefix(serviceType: String): String = when (serviceType) {
        "tax_return" -> "TAX"
        "payroll" -> "PAY"
        "advisory" -> "ADV"
        "bookkeeping" -> "BKP"
        "audit" -> "AUD"
        else -> "GEN"
    }
}
