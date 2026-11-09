package com.example.campbnb

import com.example.campbnb.models.DateUtils.datesBetween
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryStatus
import com.example.campbnb.models.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Adversarial generation, the malicious-QA persona: every case below is designed
 * from the documented contracts alone (docstrings and signatures), asking what
 * bizarre or extreme input could make these functions crash or return something
 * other than what their documentation promises.
 *
 * @see models/DateUtils.kt datesBetween: inclusive list, empty when end precedes start, ParseException on invalid dates.
 * @see models/Lottery.kt statusAt derives a status from boundaries; isValid refuses dates in the past.
 * @see models/SearchFilters.kt activeCount counts how many filters are set; null guests means not set.
 */
class AdversarialDomainTest {

    @Test
    fun blankStartIsRejected() {
        val reached = try {
            datesBetween("", "2022-01-01")
            false
        } catch (error: Exception) {
            true
        }
        assertEquals(true, reached)
    }

    @Test
    fun unpaddedDateIsRejected() {
        val reached = try {
            datesBetween("2022-1-1", "2022-01-05")
            false
        } catch (error: Exception) {
            true
        }
        assertEquals(true, reached)
    }

    @Test
    fun impossibleCalendarDateIsRejected() {
        val reached = try {
            datesBetween("2022-02-30", "2022-03-05")
            false
        } catch (error: Exception) {
            true
        }
        assertEquals(true, reached)
    }

    @Test
    fun nonASCIIDigitsAreRejected() {
        val reached = try {
            datesBetween("٢٠٢٢-٠١-٠١", "2022-01-05")
            false
        } catch (error: Exception) {
            true
        }
        assertEquals(true, reached)
    }

    @Test
    fun invertedRangeYieldsTheDocumentedEmptyList() {
        val dates = datesBetween("2022-06-10", "2022-06-01")
        assertEquals(0, dates.size)
    }

    @Test
    fun oneDayRangeYieldsThatOneDay() {
        val dates = datesBetween("2022-06-10", "2022-06-10")
        assertEquals(1, dates.size)
        assertEquals("2022-06-10", dates[0])
    }

    @Test
    fun leapDayIsReallyThere() {
        val dates = datesBetween("2024-02-28", "2024-03-02")
        assertEquals(4, dates.size)
        assertEquals("2024-02-29", dates[1])
    }

    @Test
    fun fourYearWindowHasExactlyTheCalendarCount() {
        val dates = datesBetween("2020-01-01", "2024-12-31")
        assertEquals(1827, dates.size)
        assertEquals("2020-01-01", dates[0])
        assertEquals("2024-12-31", dates[1826])
    }

    @Test
    fun anAllZeroLotteryBeforeItsStartIsUpcoming() {
        val status = Lottery().statusAt(Long.MIN_VALUE)
        assertEquals(LotteryStatus.UPCOMING, status)
    }

    @Test
    fun everyExtremeTimeStillNamesSomeStatus() {
        val broken = Lottery(
            registrationStart = Long.MAX_VALUE,
            registrationEnd = Long.MIN_VALUE,
            bookingStart = Long.MIN_VALUE,
            bookingEnd = Long.MAX_VALUE
        )
        val extremes = listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE, 3000L)
        var named = true
        for (now in extremes) {
            val status = broken.statusAt(now)
            named = named && status in LotteryStatus.values()
        }
        assertEquals(true, named)
    }

    @Test
    fun datesInThePastAreNotSaveable() {
        val past = Lottery(
            registrationStart = 1000L,
            registrationEnd = 2000L,
            bookingStart = 3000L,
            bookingEnd = 4000L
        )
        assertEquals(false, past.isValid(5000L))
    }

    @Test
    fun noFilterMeansNoActiveFilters() {
        assertEquals(0, SearchFilters().activeCount())
    }

    @Test
    fun everyFilterSetIsCounted() {
        val all = SearchFilters(
            location = "Lake",
            startDate = "2022-01-01",
            endDate = "2022-01-08",
            guests = 3
        )
        assertEquals(4, all.activeCount())
    }

    @Test
    fun zeroGuestsIsASetFilterNotAnUnsetOne() {
        val oneGuestParty = SearchFilters(guests = 0)
        assertEquals(1, oneGuestParty.activeCount())
    }

    @Test
    fun whitespaceLocationIsASetFilter() {
        assertEquals(1, SearchFilters(location = "   ").activeCount())
    }

    @Test
    fun aHalfOpenDateRangeCountsOnce() {
        assertEquals(1, SearchFilters(startDate = "2022-01-01").activeCount())
    }
}
