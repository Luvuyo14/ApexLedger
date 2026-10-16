package com.ntuli.apexledger.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Talks to the free Nager.Date REST API (no key needed):
 *     GET https://date.nager.at/api/v3/PublicHolidays/{year}/{country}
 * Holidays for a year never change, so each year is saved on the phone after the first download.
 * Every result is delivered on the main (screen) thread.
 */
object HolidayApi {
    private const val BASE_URL = "https://date.nager.at/api/v3/PublicHolidays"
    private const val COUNTRY = "ZA"                 // South Africa
    private const val PREFS = "holiday_cache"

    /** Turns the API's JSON text into a list of holidays. Throws if the text is not valid. */
    fun parse(json: String): List<Holiday> {
        val array = JSONArray(json)
        val list = ArrayList<Holiday>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            list.add(Holiday(item.getString("date"), item.optString("name"), item.optString("localName")))
        }
        return list
    }

    /** Public holidays for one year: the saved copy if there is one, otherwise from the internet. */
    fun forYear(context: Context, year: Int, onDone: (List<Holiday>?, String?) -> Unit) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${COUNTRY}_$year"

        prefs.getString(key, null)?.let { saved ->
            try {
                onDone(parse(saved), null)
                return
            } catch (e: Exception) {
                prefs.edit().remove(key).apply()      // damaged copy: download again
            }
        }

        val main = Handler(Looper.getMainLooper())
        Thread {
            var holidays: List<Holiday>? = null
            var error: String? = null
            try {
                val connection = URL("$BASE_URL/$year/$COUNTRY").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/json")
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        error = "the holiday service answered with code ${connection.responseCode}"
                    } else {
                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                        holidays = parse(text)
                        prefs.edit().putString(key, text).apply()
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: UnknownHostException) {
                error = "no internet connection"
            } catch (e: SocketTimeoutException) {
                error = "the holiday service took too long to answer"
            } catch (e: Exception) {
                error = e.message ?: "unknown problem"
            }
            main.post { onDone(holidays, error) }
        }.start()
    }

    /** Holidays for several years at once, as a map of date -> holiday name. */
    fun forYears(context: Context, years: List<Int>, onDone: (Map<String, String>?, String?) -> Unit) {
        val merged = HashMap<String, String>()
        var remaining = years.size
        var firstError: String? = null
        for (year in years) {
            forYear(context, year) { list, error ->
                list?.forEach { merged[it.date] = it.name }
                if (list == null && firstError == null) firstError = error
                remaining--
                if (remaining == 0) onDone(if (firstError == null) merged else null, firstError)
            }
        }
    }
}