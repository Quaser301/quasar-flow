package com.example.campbnb.models //maybe move to database or data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Date helpers that work on every Android version. All dates are ISO strings (yyyy-MM-dd). */
object DateUtils {

    private fun formatter() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    /** @return today's date as yyyy-MM-dd, in the device's timezone. */
    fun today(): String = formatter().format(Date())

    /**
     * Lists every date from [start] to [end], inclusive.
     *
     * @param start First date, yyyy-MM-dd.
     * @param end Last date, yyyy-MM-dd.
     * @return The dates in order, or an empty list if [end] is before [start].
     * @throws java.text.ParseException if either string isn't a valid yyyy-MM-dd date.
     */
    fun datesBetween(start: String, end: String): List<String> {
        val fmt = formatter()
        val last = fmt.parse(end)!!
        val cal = Calendar.getInstance().apply { time = fmt.parse(start)!! }
        val dates = mutableListOf<String>()
        while (!cal.time.after(last)) {
            dates.add(fmt.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }
}