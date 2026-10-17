package com.ntuli.apexledger

import com.ntuli.apexledger.util.Labels
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {

    @Test
    fun service_usesTheWebsiteWording() {
        assertEquals("Tax Return", Labels.service("tax_return"))
        assertEquals("Payroll", Labels.service("payroll"))
        assertEquals("Bookkeeping", Labels.service("bookkeeping"))
        assertEquals("Advisory", Labels.service("advisory"))
        assertEquals("Audit", Labels.service("audit"))
    }

    @Test
    fun unknownService_isTidiedUp() {
        assertEquals("Foo Bar", Labels.service("foo_bar"))
    }

    @Test
    fun status_turnsUnderscoresIntoWords() {
        assertEquals("In Progress", Labels.status("in_progress"))
        assertEquals("Awaiting Documents", Labels.status("awaiting_documents"))
        assertEquals("Under Review", Labels.status("under_review"))
        assertEquals("Open", Labels.status("open"))
    }

    @Test
    fun priority_isCapitalised() {
        assertEquals("Urgent", Labels.priority("urgent"))
    }

    @Test
    fun ticketPrefix_matchesTheService() {
        assertEquals("TAX", Labels.ticketPrefix("tax_return"))
        assertEquals("PAY", Labels.ticketPrefix("payroll"))
        assertEquals("ADV", Labels.ticketPrefix("advisory"))
        assertEquals("BKP", Labels.ticketPrefix("bookkeeping"))
        assertEquals("AUD", Labels.ticketPrefix("audit"))
        assertEquals("GEN", Labels.ticketPrefix("something_else"))
    }
}