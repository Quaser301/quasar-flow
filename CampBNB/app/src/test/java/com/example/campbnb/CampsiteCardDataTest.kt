package com.example.campbnb

import com.example.campbnb.models.Campsite
import com.example.campbnb.ui.screens.CampsiteCardData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The cases added for the spec-audit request recorded there exercise the new
 * `Campsite.toCampsiteCardData()` projection: the four mapped fields, a whole-card
 * equality against a minimally-constructed card proving nothing else is carried,
 * and the blank-listing extreme.
 */

// US 01.05.02: the card data is a PURE projection — copies id, name = campName,
// location = campLoc, maxOccupancy — and NOTHING else is carried (ui.md: ui.screens
// declares the display data class, its rating fields are filled only by the other
// decoration path, never by this projection).

class CampsiteCardDataTest {

    @Test
    fun test_to_campsite_card_data_maps_the_four_projection_fields() {
        val campsite = Campsite(
            ownerId = "owner1",
            campName = "Pine Hollow",
            campLoc = "Lake Placid",
            maxOccupancy = 4,
            tags = listOf("quiet", "fishing"),
            campPrice = 42.0
        ).also { it.id = "card-camp" }

        val card = campsite.toCampsiteCardData()

        assertEquals("card-camp", card.id)
        assertEquals("Pine Hollow", card.name)
        assertEquals("Lake Placid", card.location)
        assertEquals(4, card.maxOccupancy)
    }

    @Test
    fun test_projection_carries_nothing_else_than_the_four_fields() {
        val campsite = Campsite(
            ownerId = "owner1",
            campName = "Pine Hollow",
            campLoc = "Lake Placid",
            maxOccupancy = 4
        ).also { it.id = "card-camp" }

        assertEquals(
            CampsiteCardData(
                id = "card-camp",
                name = "Pine Hollow",
                location = "Lake Placid",
                maxOccupancy = 4
            ),
            campsite.toCampsiteCardData()
        )
    }

    @Test
    fun test_blank_listing_projects_to_a_blank_card() {
        val bare = Campsite().also { it.id = "blank-camp" }

        val card = bare.toCampsiteCardData()

        assertEquals("blank-camp", card.id)
        assertEquals("", card.name)
        assertEquals("", card.location)
        assertEquals(0, card.maxOccupancy)
    }
}
