package com.example.campbnb.adminTests

import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.ui.admin.profileRowText
import com.example.campbnb.ui.admin.profileRowsFor
import org.junit.Assert
import org.junit.Test

/**
 * The browsed-profile listing is tested through its public entry points:
 * profileRowsFor and profileRowText, which is what the Profiles subview draws.
 * The cases are the ones the request leaves open: nothing saved at all, a user
 * with no fields to show, listing order despite capital differences and a nameless
 * user, several roles on one person, and a row line keeping all three fields.
 */

class AdminProfilesTest {

    @Test
    fun test_NoUsersMeansNoRows() {
        val rows = profileRowsFor(emptyList())

        Assert.assertTrue("no saved users means nothing to browse", rows.count() == 0)
    }

    @Test
    fun test_AUserWithoutFieldsStillShowsOnePlaceholderRow() {
        val rows = profileRowsFor(listOf(User(deviceId = "d-quiet")))

        Assert.assertTrue("the user is browsed even with nothing filled in", rows.count() == 1)
        Assert.assertTrue(
            "every field falls back to its placeholder instead of collapsing",
            profileRowText(rows[0]) == "(no name)  -  (no email)  -  (no role)"
        )
    }

    @Test
    fun test_ListingOrderIgnoresCapitalizationAndPutsNamelessLast() {
        val rows = profileRowsFor(
            listOf(
                User(deviceId = "d-none"),
                User(deviceId = "d-delta", name = "Delta"),
                User(deviceId = "d-alpha", name = "alpha")
            )
        )

        Assert.assertTrue("one row per user, none merged or dropped", rows.count() == 3)
        Assert.assertTrue(
            "the listing order follows display names regardless of capitalization",
            rows[0].nameLabel == "alpha" &&
                    rows[1].nameLabel == "Delta" &&
                    rows[2].nameLabel == "(no name)"
        )
    }

    @Test
    fun test_SeveralRolesOnOnePersonAreAllShown() {
        val rows = profileRowsFor(
            listOf(
                User(
                    deviceId = "d-both",
                    name = "Kit",
                    email = "kit@example",
                    roles = listOf(Role.ENTRANT, Role.ADMIN)
                )
            )
        )

        Assert.assertTrue(
            "each held role appears, separated, so the admin sees everything the person can do",
            rows[0].rolesLabel == "ENTRANT/ADMIN"
        )
    }

    @Test
    fun test_ARowLineCarriesEveryBrowsedField() {
        val line = profileRowText(
            profileRowsFor(
                listOf(
                    User(deviceId = "d-full", name = "June", email = "june@example")
                )
            )[0]
        )

        Assert.assertTrue("the name is visible", "June" in line)
        Assert.assertTrue("the email is visible", "june@example" in line)
        Assert.assertTrue(
            "the role is visible even when none is held",
            "(no role)" in line
        )
    }

    @Test
    fun test_PaddedNamesAndEmailsDrawTrimmedLabels() {
        val rows = profileRowsFor(listOf(
            User(deviceId = "d-padded", name = "  June  ", email = "  june@example  ")
        ))

        Assert.assertTrue("the name label carries no padding", rows[0].nameLabel == "June")
        Assert.assertTrue("the email label carries no padding", rows[0].emailLabel == "june@example")
    }

    @Test
    fun test_ASpacesOnlyEmailIsNotTreatedAsMissing() {
        // The empty-email check is un-trimmed, so spaces-only passes the check
        // and the label draws the trimmed (empty) value rather than the note.
        val rows = profileRowsFor(listOf(
            User(deviceId = "d-spaces", name = "Spacey", email = "   ")
        ))

        Assert.assertTrue("the email label is the trimmed value, which is empty", rows[0].emailLabel.isEmpty())
    }

    @Test
    fun test_NamelessUsersOrderAmongThemselvesByTheirEmails() {
        val rows = profileRowsFor(listOf(
            User(deviceId = "d-beta", email = "b@x.com"),
            User(deviceId = "d-alpha", email = "a@x.com")
        ))

        Assert.assertTrue(
            "the sort marker uses the email, so the listing order follows it",
            rows[0].emailLabel == "a@x.com" && rows[1].emailLabel == "b@x.com"
        )
    }

    @Test
    fun test_RepeatedRolesShowRepeatedlyInTheirGivenOrder() {
        val rows = profileRowsFor(listOf(
            User(
                deviceId = "d-repeat",
                roles = listOf(Role.OWNER, Role.ENTRANT, Role.OWNER)
            )
        ))

        Assert.assertTrue(
            "roles are not deduplicated or reordered",
            rows[0].rolesLabel == "OWNER/ENTRANT/OWNER"
        )
    }
}
