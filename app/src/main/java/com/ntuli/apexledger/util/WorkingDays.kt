package com.ntuli.apexledger.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Weekend and public-holiday logic. It uses no Android classes, so it can be unit tested
 * on a normal computer. Dates are text in the form "yyyy-MM-dd".
 */
object WorkingDays {

    data class Check(
        val date: String,
        val isWeekend: Boolean,
        val dayName: String,          // e.g. "Sunday"
        val holidayName: String?,     // e.g. "Day of Reconciliation", or null
        val suggestedDate: String     // the next working day on or after [date]
    ) {
        val isProblem: Boolean get() = isWeekend || holidayName != null

        val reason: String
            get() = when {
                holidayName != null -> "$holidayName (public holiday)"
                isWeekend -> "a $dayName"
                else -> ""
            }
    }

    private fun parse(date: String): Calendar? {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        return try {
            Calendar.getInstance(Locale.US).apply { time = format.parse(date)!! }
        } catch (e: Exception) {
            null
        }
    }

    private fun format(cal: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    private fun isWeekend(cal: Calendar): Boolean {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }

    /** [holidays] maps a date ("yyyy-MM-dd") to the holiday's name. Returns null if [date] is not a valid date. */
    fun check(date: String, holidays: Map<String, String>): Check? {
        val cal = parse(date) ?: return null
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
        return Check(
            date = date,
            isWeekend = isWeekend(cal),
            dayName = dayName,
            holidayName = holidays[date],
            suggestedDate = nextWorkingDay(date, holidays) ?: date
        )
    }

    /** The first day on or after [date] that is not a weekend or a public holiday. */
    fun nextWorkingDay(date: String, holidays: Map<String, String>): String? {
        val cal = parse(date) ?: return null
        var guard = 0
        while ((isWeekend(cal) || holidays.containsKey(format(cal))) && guard < 15) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            guard++
        }
        return format(cal)
    }
}
