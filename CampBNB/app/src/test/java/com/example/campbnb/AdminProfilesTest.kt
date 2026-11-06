package com.example.campbnb

/**
 * The browsed-profile listing is tested through its public entry points:
 * profileRowsFor and profileRowText, which is what the Profiles subview draws.
 * The cases are the ones the request leaves open: nothing saved at all, a user
 * with no fields to show, listing order despite capital differences and a nameless
 * user, several roles on one person, and a row line keeping all three fields.
 */

import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.ui.admin.ProfileRow
import com.example.campbnb.ui.admin.profileRowsFor
import com.example.campbnb.ui.admin.profileRowText
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminProfilesTest {

    @Test
    fun test_NoUsersMeansNoRows() {
        val rows = profileRowsFor(emptyList())

        assertTrue("no saved users means nothing to browse", rows.count() == 0)
    }

    @Test
    fun test_AUserWithoutFieldsStillShowsOnePlaceholderRow() {
        val rows = profileRowsFor(listOf(User(deviceId = "d-quiet")))

        assertTrue("the user is browsed even with nothing filled in", rows.count() == 1)
        assertTrue(
            "every field falls back to its placeholder instead of collapsing",
            profileRowText(rows[0]) == "(no name)  -  (no email)  -  (no role)"
        )
    }

    @Test
    fun test_ListingOrderIgnoresCapitalizationAndPutsNamelessLast() {
        val rows = profileRowsFor(listOf(
            User(deviceId = "d-none"),
            User(deviceId = "d-delta", name = "Delta"),
            User(deviceId = "d-alpha", name = "alpha")
        ))

        assertTrue("one row per user, none merged or dropped", rows.count() == 3)
        assertTrue(
            "the listing order follows display names regardless of capitalization",
            rows[0].nameLabel == "alpha" &&
                    rows[1].nameLabel == "Delta" &&
                    rows[2].nameLabel == "(no name)"
        )
    }

    @Test
    fun test_SeveralRolesOnOnePersonAreAllShown() {
        val rows = profileRowsFor(listOf(
            User(
                deviceId = "d-both",
                name = "Kit",
                email = "kit@example",
                roles = listOf(Role.ENTRANT, Role.ADMIN)
            )
        ))

        assertTrue(
            "each held role appears, separated, so the admin sees everything the person can do",
            rows[0].rolesLabel == "ENTRANT/ADMIN"
        )
    }

    @Test
    fun test_ARowLineCarriesEveryBrowsedField() {
        val line = profileRowText(profileRowsFor(listOf(
            User(deviceId = "d-full", name = "June", email = "june@example")
        ))[0])

        assertTrue("the name is visible", "June" in line)
        assertTrue("the email is visible", "june@example" in line)
        assertTrue(
            "the role is visible even when none is held",
            "(no role)" in line
        )
    }
}
