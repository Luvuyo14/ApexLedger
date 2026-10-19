
package com.ntuli.apexledger.util

import android.app.Activity
import android.view.View
import com.ntuli.apexledger.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Fills a dashboard card with the next South African public holidays, using the Nager.Date API.
 * The card stays hidden when there is nothing coming up or the holidays cannot be loaded.
 */
object HolidayCard {

    fun show(activity: Activity, card: View, withinDays: Int = 45, max: Int = 3) {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        // in November and December the next holiday may fall in January
        val years = if (now.get(Calendar.MONTH) >= Calendar.NOVEMBER) listOf(year, year + 1) else listOf(year)

        HolidayApi.forYears(activity, years) { holidays, _ ->
            if (activity.isFinishing || activity.isDestroyed) return@forYears
            if (holidays == null) { card.visibility = View.GONE; return@forYears }

            val upcoming = holidays.entries
                .mapNotNull { (date, name) ->
                    val days = DateUtils.daysUntil(date) ?: return@mapNotNull null
                    if (days in 0..withinDays.toLong()) Triple(date, name, days) else null
                }
                .sortedBy { it.third }
                .take(max)

            if (upcoming.isEmpty()) { card.visibility = View.GONE; return@forYears }

            val container = DashRows.section(card, "Upcoming Public Holidays")
            for ((date, name, days) in upcoming) {
                val chip = when (days) {
                    0L -> "Today"
                    1L -> "Tomorrow"
                    else -> "in $days days"
                }
                DashRows.addRow(
                    container, name, longDate(date), chip,
                    R.drawable.badge_status_open, R.color.navy
                )
            }
            card.visibility = View.VISIBLE
        }
    }

    /** "2026-12-16" -> "Wednesday, 16 Dec 2026" */
    private fun longDate(date: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!
        SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(parsed)
    } catch (e: Exception) {
        date
    }
}