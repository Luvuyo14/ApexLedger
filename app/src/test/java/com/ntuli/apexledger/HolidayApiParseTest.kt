package com.ntuli.apexledger

import com.ntuli.apexledger.util.HolidayApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the app reads the JSON the Nager.Date API sends (no internet needed). */
class HolidayApiParseTest {

    private val sample = """
        [
          {"date":"2026-12-16","localName":"Day of Reconciliation","name":"Day of Reconciliation",
           "countryCode":"ZA","fixed":true,"global":true,"counties":null,"launchYear":null,"types":["Public"]},
          {"date":"2026-12-25","localName":"Christmas Day","name":"Christmas Day",
           "countryCode":"ZA","fixed":true,"global":true,"counties":null,"launchYear":null,"types":["Public"]}
        ]
    """.trimIndent()

    @Test
    fun parse_readsEveryHoliday() {
        val holidays = HolidayApi.parse(sample)
        assertEquals(2, holidays.size)
        assertEquals("2026-12-16", holidays[0].date)
        assertEquals("Day of Reconciliation", holidays[0].name)
        assertEquals("Christmas Day", holidays[1].localName)
    }

    @Test
    fun parse_emptyList_givesNoHolidays() {
        assertTrue(HolidayApi.parse("[]").isEmpty())
    }

    @Test(expected = org.json.JSONException::class)
    fun parse_brokenJson_throws() {
        HolidayApi.parse("this is not json")
    }

    @Test(expected = org.json.JSONException::class)
    fun parse_itemWithoutDate_throws() {
        HolidayApi.parse("""[{"name":"Mystery day"}]""")
    }
}