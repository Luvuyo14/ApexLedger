package com.ntuli.apexledger

import com.ntuli.apexledger.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateUtilsTest {

    @Before
    fun useEnglish() {
        Locale.setDefault(Locale.ENGLISH)
    }

    /** A date n days from today in the app's storage format (negative = in the past). */
    private fun daysFromToday(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, n)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    @Test
    fun pastDueDate_onAnOpenTicket_isOverdue() {
        assertTrue(DateUtils.isOverdue(daysFromToday(-2), "open"))
        assertTrue(DateUtils.isOverdue(daysFromToday(-1), "in_progress"))
    }

    @Test
    fun futureOrTodayDueDate_isNotOverdue() {
        assertFalse(DateUtils.isOverdue(daysFromToday(0), "open"))
        assertFalse(DateUtils.isOverdue(daysFromToday(5), "open"))
    }

    @Test
    fun finishedTickets_areNeverOverdue() {
        assertFalse(DateUtils.isOverdue(daysFromToday(-30), "completed"))
        assertFalse(DateUtils.isOverdue(daysFromToday(-30), "cancelled"))
    }

    @Test
    fun noDueDate_isNotOverdue() {
        assertFalse(DateUtils.isOverdue("", "open"))
    }

    @Test
    fun display_formatsLikeTheWebsite() {
        assertEquals("15 Jun 2026", DateUtils.display("2026-06-15"))
        assertEquals("", DateUtils.display(""))
        assertEquals("garbage", DateUtils.display("garbage"))
    }

    @Test
    fun daysUntil_countsWholeDays() {
        assertEquals(0L, DateUtils.daysUntil(daysFromToday(0)))
        assertEquals(1L, DateUtils.daysUntil(daysFromToday(1)))
        assertEquals(10L, DateUtils.daysUntil(daysFromToday(10)))
        assertEquals(-1L, DateUtils.daysUntil(daysFromToday(-1)))
        assertNull(DateUtils.daysUntil(""))
    }

    @Test
    fun daysOverdue_countsDaysLate() {
        assertEquals(3L, DateUtils.daysOverdue(daysFromToday(-3)))
        assertEquals(0L, DateUtils.daysOverdue(daysFromToday(2)))
    }
}