package com.example.campbnb.models //maybe move to database or data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    /** The calendar day a UTC-midnight timestamp (what the date pickers give) stands for. */
    fun utcDate(utcMillis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(utcMillis))
    }

    /** The calendar day [millis] falls on, in the device's timezone. */
    fun dateOf(millis: Long): String = formatter().format(Date(millis))

    /** The day after [date] (yyyy-MM-dd). */
    fun dayAfter(date: String): String {
        val fmt = formatter()
        val cal = Calendar.getInstance().apply {
            time = fmt.parse(date)!!
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return fmt.format(cal.time)
    }

    /** Groups dates into runs of consecutive days, as (first, last) pairs in order. */
    fun groupIntoRanges(dates: Collection<String>): List<Pair<String, String>> {
        val sorted = dates.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val ranges = mutableListOf<Pair<String, String>>()
        var start = sorted.first()
        var previous = start
        for (date in sorted.drop(1)) {
            if (date == dayAfter(previous)) {
                previous = date
            } else {
                ranges.add(start to previous)
                start = date
                previous = date
            }
        }
        ranges.add(start to previous)
        return ranges
    }

    /** A date for display, like "Jul 14, 2026". Falls back to the raw text if it can't be read. */
    fun pretty(date: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(parsed)
    } catch (e: Exception) {
        date
    }
}