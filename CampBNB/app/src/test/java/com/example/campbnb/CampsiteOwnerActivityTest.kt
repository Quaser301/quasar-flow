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

    // TEST 9: A whitespace owner cannot register
    @Test
    fun registerCampsite_whitespaceOwner_fails() {
        var result: Boolean? = null

        controller.registerCampsite(campsite(), "   ") {
            result = it
        }

        assertEquals(false, result)
        assertTrue(repo.saved.isEmpty())
        assertEquals("Please log in to register a campsite", model.statusMessage)
    }

    // TEST 10: The register button mashed twice saves both registrations
    @Test
    fun registerCampsite_pressedTwice_savesBothRegistrations() {
        var first: Boolean? = null
        var second: Boolean? = null

        controller.registerCampsite(campsite(), "owner1") {
            first = it
        }
        controller.registerCampsite(campsite(), "owner1") {
            second = it
        }

        assertEquals(true, first)
        assertEquals(true, second)
        assertEquals(2, repo.saved.size)
    }

    // TEST 11: Updating an id that was never there fails like the wrong owner does
    @Test
    fun updateCampsite_unknownId_fails() {
        val stranger = campsite(name = "Not Ours")

        var result: Boolean? = null

        controller.updateCampsite(stranger, "owner1") {
            result = it
        }

        assertEquals(false, result)
        assertTrue(repo.saved.isEmpty())
        assertEquals("You cannot update this campsite", model.statusMessage)
    }

    // TEST 12: A large owned list still loads every row
    @Test
    fun loadOwnerCampsites_fiftyOwned_allShowUp() {
        for (index in 1..50) {
            repo.existing.add(campsite(owner = "owner1", name = "Camp $index"))
        }

        controller.loadOwnerCampsites("owner1")

        assertEquals(50, model.ownedCampsites.size)
    }

    // Fake repository - no Firebase connection
    private class FakeRepository : CampsiteRepository() {

        val existing = mutableListOf<Campsite>()
        val saved = mutableListOf<Campsite>()

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
            onSuccess(existing.filter { it.ownerId == ownerId })
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
