package com.example.campbnb

import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CampsiteOwnerTest {

    private lateinit var controller: CampsiteController
    private lateinit var model: CampsiteModel
    private lateinit var repo: FakeRepository

    @Before
    fun setUp() {
        model = CampsiteModel()
        repo = FakeRepository()
        controller = CampsiteController(model, repo)
    }

    private fun campsite(
        owner: String = "owner1",
        name: String = "Edmonton Camp"
    ): Campsite {
        return Campsite(
            ownerId = owner,
            campName = name,
            campLoc = "Edmonton",
            campPrice = 75.0,
            maxOccupancy = 4
        )
    }

    // TEST 1: Register campsite successfully
    @Test
    fun registerCampsite_validOwner_succeeds() {
        var result: Boolean? = null

        controller.registerCampsite(campsite(), "owner1") {
            result = it
        }

        assertEquals(true, result)
        assertEquals(1, repo.saved.size)
        assertEquals("owner1", repo.saved[0].ownerId)
    }

    // TEST 2: Cannot register without owner ID
    @Test
    fun registerCampsite_emptyOwner_fails() {
        var result: Boolean? = null

        controller.registerCampsite(campsite(), "") {
            result = it
        }

        assertEquals(false, result)
        assertTrue(repo.saved.isEmpty())
    }

    // TEST 3: Owner can edit their own campsite
    @Test
    fun updateCampsite_correctOwner_succeeds() {
        val original = campsite()
        repo.existing.add(original)

        val updated = campsite(name = "Updated Camp").apply {
            id = original.id
        }

        var result: Boolean? = null

        controller.updateCampsite(updated, "owner1") {
            result = it
        }

        assertEquals(true, result)
        assertEquals(1, repo.saved.size)
        assertEquals("Updated Camp", repo.saved[0].campName)
        assertEquals(original.id, repo.saved[0].id)
    }

    // TEST 4: Another owner cannot edit campsite
    @Test
    fun updateCampsite_wrongOwner_fails() {
        val original = campsite("owner1")
        repo.existing.add(original)

        val updated = campsite("owner1", "Changed Camp").apply {
            id = original.id
        }

        var result: Boolean? = null

        controller.updateCampsite(updated, "owner2") {
            result = it
        }

        assertEquals(false, result)
        assertTrue(repo.saved.isEmpty())
    }

    // TEST 5: Owner sees their own campsites
    @Test
    fun loadOwnerCampsites_showsOwnCampsites() {
        repo.existing.add(campsite("owner1", "Camp A"))
        repo.existing.add(campsite("owner1", "Camp B"))

        controller.loadOwnerCampsites("owner1")

        assertEquals(2, model.ownedCampsites.size)
    }

    // TEST 6: Other owners' campsites are hidden
    @Test
    fun loadOwnerCampsites_excludesOtherOwners() {
        repo.existing.add(campsite("owner1", "My Camp"))
        repo.existing.add(campsite("owner2", "Other Camp"))

        controller.loadOwnerCampsites("owner1")

        assertEquals(1, model.ownedCampsites.size)
        assertEquals("My Camp", model.ownedCampsites[0].campName)
        assertEquals("owner1", model.ownedCampsites[0].ownerId)
    }

    // TEST 7: Delete an existing campsite
    @Test
    fun deleteCampsite_existingId_succeeds() {
        val original = campsite()
        repo.existing.add(original)

        var result: Result<Exception?>? = null

        repo.deleteCampsite(original.id) {
            result = it
        }

        assertTrue(result?.isSuccess == true)
        assertTrue(repo.existing.isEmpty())
    }

    // TEST 8: Delete an unknown campsite fails
    @Test
    fun deleteCampsite_unknownId_fails() {
        var result: Result<Exception?>? = null

        repo.deleteCampsite("unknown-id") {
            result = it
        }

        assertTrue(result?.isFailure == true)
    }

    // TEST 9: Registering copies the listing fields but takes the owner from the
    // argument and drops availability, as the registration contract states.
    @Test
    fun registerCampsite_takes_the_argument_owner_and_drops_availability() {
        val argument = Campsite(
            ownerId = "stored-owner",
            campName = "Availability Camp",
            campLoc = "Edmonton",
            availableStartDate = "2026-01-01",
            availableEndDate = "2026-01-05",
            blockedDates = listOf("2026-01-02")
        )

        var result: Boolean? = null

        controller.registerCampsite(argument, "owner-arg") {
            result = it
        }

        assertEquals(true, result)
        assertEquals(1, repo.saved.size)
        assertEquals("owner-arg", repo.saved[0].ownerId)
        assertEquals("Availability Camp", repo.saved[0].campName)
        assertTrue("registration keeps no availability of its own", repo.saved[0].availableStartDate.isEmpty())
        assertTrue("registration keeps no availability of its own", repo.saved[0].availableEndDate.isEmpty())
        assertTrue("registration keeps no blocked dates", repo.saved[0].blockedDates.isEmpty())
    }

    // TEST 10: A refused registration names itself.
    @Test
    fun registerCampsite_emptyOwner_names_the_refusal() {
        var result: Boolean? = null

        controller.registerCampsite(campsite(), "") {
            result = it
        }

        assertEquals(false, result)
        assertEquals("Please log in to register a campsite", model.statusMessage)
        assertTrue(repo.saved.isEmpty())
    }

    // TEST 11: An update with nothing to point at is refused before any read.
    @Test
    fun updateCampsite_missing_id_refused() {
        var result: Boolean? = null

        // A fresh listing would carry a random id of its own, so the id is emptied
        // by hand: only a truly blank one takes the "Invalid owner" refusal.
        controller.updateCampsite(
            campsite().apply { id = "" },
            "owner1"
        ) {
            result = it
        }

        assertEquals(false, result)
        assertEquals("Invalid owner or campsite", model.statusMessage)
        assertTrue(repo.saved.isEmpty())
    }

    // TEST 12: An update cannot move a listing to another owner.
    @Test
    fun updateCampsite_changedOwner_refused() {
        val original = campsite("owner1")
        repo.existing.add(original)

        val updated = campsite(owner = "someone-else").apply {
            id = original.id
        }

        var result: Boolean? = null

        controller.updateCampsite(updated, "owner1") {
            result = it
        }

        assertEquals(false, result)
        assertEquals("Campsite owner cannot be changed", model.statusMessage)
        assertTrue(repo.saved.isEmpty())
    }

    // TEST 13: An accepted update keeps the argument's availability but takes the
    // identity (id and owner) from the stored record, as the update contract states.
    @Test
    fun updateCampsite_keeps_availability_and_the_stored_identity() {
        val original = campsite("owner1")
        repo.existing.add(original)

        // the argument may not name another owner either - a blank one would be
        // "a different campsite.ownerId" and refused, so it carries the same owner.
        val updated = Campsite(
            ownerId = "owner1",
            campName = "Availability Updated",
            campLoc = "Edmonton",
            availableStartDate = "2026-02-01"
        ).apply {
            id = original.id
        }

        var result: Boolean? = null

        controller.updateCampsite(updated, "owner1") {
            result = it
        }

        assertEquals(true, result)
        assertEquals(1, repo.saved.size)
        assertEquals("Availability Updated", repo.saved[0].campName)
        assertEquals("2026-02-01", repo.saved[0].availableStartDate)
        assertEquals("owner1", repo.saved[0].ownerId)
        assertEquals(original.id, repo.saved[0].id)
        assertEquals("Campsite updated successfully", model.statusMessage)
    }

    // TEST 14: A failed owner listing read names the prefix and the cause.
    @Test
    fun loadOwnerCampsites_failure_names_the_prefix_and_cause() {
        repo.failNextByOwner = true

        controller.loadOwnerCampsites("owner1")

        assertEquals("Could not load your campsites: offline", model.statusMessage)
        assertTrue(model.ownedCampsites.isEmpty())
    }

    // Fake repository - no Firebase connection
    private class FakeRepository : CampsiteRepository() {

        val existing = mutableListOf<Campsite>()
        val saved = mutableListOf<Campsite>()
        var failNextByOwner = false

        override fun getAll(
            onSuccess: (List<Campsite>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(existing)
        }

        override fun getByOwner(
            ownerId: String,
            onSuccess: (List<Campsite>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            if (failNextByOwner) {
                failNextByOwner = false
                onFailure(Exception("offline"))
            } else {
                onSuccess(existing.filter { it.ownerId == ownerId })
            }
        }

        override fun SaveCampsite(
            campsite: Campsite,
            onComplete: (Result<Exception?>) -> Unit
        ) {
            saved.add(campsite)
            onComplete(Result.success(null))
        }

        override fun deleteCampsite(
            campsiteId: String,
            onComplete: (Result<Exception?>) -> Unit
        ) {
            val removed = existing.removeAll {
                it.id == campsiteId
            }

            if (removed) {
                onComplete(Result.success(null))
            } else {
                onComplete(
                    Result.failure(
                        IllegalArgumentException("Campsite not found")
                    )
                )
            }
        }
    }
}
