package com.ntuli.apexledger

import com.ntuli.apexledger.util.WorkingDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingDaysTest {

    private val holidays = mapOf(
        "2026-12-16" to "Day of Reconciliation",   // Wednesday
        "2026-12-25" to "Christmas Day"            // Friday
    )

    @Test
    fun normalWeekday_isNotAProblem() {
        val check = WorkingDays.check("2026-12-17", holidays)!!   // Thursday
        assertFalse(check.isProblem)
        assertEquals("2026-12-17", check.suggestedDate)
    }

    @Test
    fun sunday_isWeekend_andMovesToMonday() {
        val check = WorkingDays.check("2026-12-20", holidays)!!
        assertTrue(check.isWeekend)
        assertEquals("Sunday", check.dayName)
        assertEquals("a Sunday", check.reason)
        assertEquals("2026-12-21", check.suggestedDate)
    }

    @Test
    fun saturday_movesToMonday() {
        assertEquals("2026-12-28", WorkingDays.nextWorkingDay("2026-12-26", holidays))
    }

    @Test
    fun publicHoliday_isReportedByName() {
        val check = WorkingDays.check("2026-12-16", holidays)!!
        assertEquals("Day of Reconciliation", check.holidayName)
        assertEquals("Day of Reconciliation (public holiday)", check.reason)
        assertTrue(check.isProblem)
        assertEquals("2026-12-17", check.suggestedDate)
    }

    @Test
    fun holidayFollowedByWeekend_skipsBoth() {
        // Friday 25 Dec is a holiday, then Saturday and Sunday: the next working day is Monday
        assertEquals("2026-12-28", WorkingDays.nextWorkingDay("2026-12-25", holidays))
    }

    @Test
    fun holidayOnAWeekend_showsTheHolidayName() {
        val check = WorkingDays.check("2026-12-26", mapOf("2026-12-26" to "Day of Goodwill"))!!
        assertTrue(check.isWeekend)
        assertEquals("Day of Goodwill (public holiday)", check.reason)
    }

    @Test
    fun suggestionCanCrossIntoNextYear() {
        val yearEnd = mapOf("2026-12-31" to "Company shutdown", "2027-01-01" to "New Year's Day")
        assertEquals("2027-01-04", WorkingDays.nextWorkingDay("2026-12-31", yearEnd))
    }

    @Test
    fun invalidDates_returnNull() {
        assertNull(WorkingDays.check("2026-13-45", holidays))
        assertNull(WorkingDays.check("not a date", holidays))
        assertNull(WorkingDays.nextWorkingDay("", holidays))
    }

    @Test
    fun noHolidaysLoaded_stillDetectsWeekends() {
        val check = WorkingDays.check("2026-12-20", emptyMap())
        assertNotNull(check)
        assertTrue(check!!.isWeekend)
    }
}