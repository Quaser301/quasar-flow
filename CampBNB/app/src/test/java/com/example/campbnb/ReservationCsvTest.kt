package com.example.campbnb

import com.example.campbnb.models.Campsite
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.data.InMemoryFileDownloadWriter
import com.example.campbnb.models.reservationCsvBytesOf
import com.example.campbnb.models.reservationCsvFileName
import com.example.campbnb.models.reservationCsvText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The cases follow the reservation CSV download request recorded there: the rows, the
 * file name, and the bytes written through the shared download sink.
 */

class ReservationCsvTest {

    @Test
    fun test_reservation_csv_uses_the_requested_rows() {
        val reservation = ReservationModel(
            startDate = "2026-01-01",
            endDate = "2026-01-03",
            campsiteId = "camp-one",
            ownerId = "owner-one",
            dates = listOf("2026-01-01", "2026-01-03")
        )
        val campsite = Campsite(
            campName = "Test Camp",
            campLoc = "Test Location",
            ownerId = "owner-one"
        )

        val csv = reservationCsvText(reservation, campsite)

        assertTrue(
            "the csv contains the requested reservation fields in order",
            csv == """
                Start,2026-01-01
                End,2026-01-03
                Campsite Name/Location,Test Camp / Test Location
                Owner,owner-one
            """.trimIndent()
        )
    }

    @Test
    fun test_reservation_csv_uses_available_defaults_for_missing_data() {
        val reservation = ReservationModel(
            startDate = "2026-01-01",
            endDate = "2026-01-02"
        )

        val csv = reservationCsvText(reservation, null)

        assertTrue(
            "missing campsite and owner data does not crash csv creation",
            csv == """
                Start,2026-01-01
                End,2026-01-02
                Campsite Name/Location,
                Owner,
            """.trimIndent()
        )
    }

    @Test
    fun test_reservation_csv_quotes_a_field_containing_a_comma() {
        val reservation = ReservationModel(
            startDate = "2026-01-01",
            endDate = "2026-01-03",
            campsiteId = "camp-one",
            ownerId = "owner-one",
            dates = listOf("2026-01-01", "2026-01-03")
        )
        val campsite = Campsite(
            campName = "Camp, Ridge",
            campLoc = "Valley",
            ownerId = "owner-one"
        )

        val csv = reservationCsvText(reservation, campsite)

        assertTrue(
            "the quoted csv field keeps comma data inside one csv field",
            csv.contains("""Campsite Name/Location,"Camp, Ridge / Valley"""")
        )
    }

    @Test
    fun test_reservation_csv_file_name_follows_the_lottery_entry() {
        val reservation = ReservationModel(
            startDate = "2026-01-01",
            endDate = "2026-01-02",
            lotteryEntryId = "entry-7"
        )
        assertEquals("entry-7.csv", reservationCsvFileName(reservation))
    }

    @Test
    fun test_reservation_csv_file_name_is_cleaned_and_reserved_when_missing() {
        val messy = ReservationModel(
            startDate = "2026-01-01",
            endDate = "2026-01-02",
            lotteryEntryId = "entry 7/x"
        )
        assertEquals("entry-7-x.csv", reservationCsvFileName(messy))
        val withoutEntry = ReservationModel(
            startDate = "2026-01-01",
            endDate = "2026-01-02"
        )
        assertEquals("unspecified.csv", reservationCsvFileName(withoutEntry))
    }

    @Test
    fun test_reservation_csv_download_writes_under_its_name_through_the_shared_writer() {
        val reservation = ReservationModel(
            startDate = "2026-01-01",
            endDate = "2026-01-03",
            campsiteId = "camp-one",
            ownerId = "owner-one",
            lotteryEntryId = "camp-one",
            dates = listOf("2026-01-01", "2026-01-03")
        )
        val campsite = Campsite(
            campName = "Test Camp",
            campLoc = "Test Location",
            ownerId = "owner-one"
        )
        val writer = InMemoryFileDownloadWriter()
        writer.downloadFile(
            reservationCsvFileName(reservation),
            reservationCsvBytesOf(reservation, campsite)
        ) { result -> assertTrue("the shared writer reports done", result.isSuccess) }
        val writtenCsv = writer.writtenFiles["camp-one.csv"]
        if (writtenCsv == null) {
            assertTrue(false)
        } else {
            assertEquals(
                """
                    Start,2026-01-01
                    End,2026-01-03
                    Campsite Name/Location,Test Camp / Test Location
                    Owner,owner-one
                """.trimIndent(),
                writtenCsv.decodeToString()
            )
        }
    }
}