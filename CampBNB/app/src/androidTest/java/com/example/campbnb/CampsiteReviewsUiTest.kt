package com.example.campbnb

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) tests for the campsite reviews screen:
 *  - US 01.13.02: an entrant posts a review with their rating, and it appears, with their name,
 *    on the reviews screen that "See all reviews" opens.
 *  - US 03.09.01 and US 03.10.01: an admin selects a card on that screen, deletes it after
 *    picking a reason, and the reviewer is told why. Everyone else cannot delete.
 *
 * Each test drives the real details screen the way a user would. Repositories are in-memory
 * fakes, so nothing touches Firebase.
 */

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Notification
import com.example.campbnb.models.Rating
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import com.example.campbnb.ui.campsite.CampsiteReviewsScreen
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.CampsiteDetails
import com.example.campbnb.ui.screens.CampsiteDetailsScreen
import com.example.campbnb.ui.shared.observeModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory ratings and one campsite called "Lakeside"; never touches Firebase. */
private class ReviewsUiFakeCampsiteRepository : CampsiteRepository() {
    val campsites = mutableListOf(
        Campsite(ownerId = "owner-1", campName = "Lakeside").apply { id = "camp-1" }
    )
    val ratings = mutableListOf<Rating>()

    override fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(campsites.toList())
    }

    override fun getById(
        campsiteId: String,
        onSuccess: (Campsite?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(campsites.firstOrNull { it.id == campsiteId })
    }

    override fun getAllRatings(onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(ratings.toList())
    }

    override fun getRatingById(
        ratingId: String,
        onSuccess: (Rating?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(ratings.firstOrNull { it.id == ratingId })
    }

    override fun saveRating(rating: Rating, onComplete: (Result<Exception?>) -> Unit) {
        ratings.removeAll { it.id == rating.id }
        ratings.add(rating)
        onComplete(Result.success(null))
    }

    override fun deleteRating(ratingId: String, onComplete: (Result<Exception?>) -> Unit) {
        ratings.removeAll { it.id == ratingId }
        onComplete(Result.success(null))
    }
}

/** Remembers the notifications saved, so a test can read what the reviewer was told. */
private class ReviewsUiFakeNotificationRepository : NotificationRepository {
    val saved = mutableListOf<Notification>()

    override fun saveAll(notifications: List<Notification>, onDone: (Result<Unit>) -> Unit) {
        saved.addAll(notifications)
        onDone(Result.success(Unit))
    }

    override fun readAll(onSuccess: (List<Notification>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(saved.toList())
    }

    override fun listenForUser(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
        return {}
    }

    override fun markRead(notificationId: String, onDone: (Result<Unit>) -> Unit) {
        onDone(Result.success(Unit))
    }
}

@RunWith(AndroidJUnit4::class)
class CampsiteReviewsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val camper = User(deviceId = "user-1", name = "Casey Camper", email = "casey@example.com")
    private val admin = User(
        deviceId = "admin-1", name = "Ada Admin", email = "ada@example.com",
        roles = listOf(Role.ADMIN)
    )

    private val campsiteRepo = ReviewsUiFakeCampsiteRepository()
    private val notifications = ReviewsUiFakeNotificationRepository()
    private var lastMessage = ""

    /** Two reviews from other people, already on the campsite. */
    private fun addExistingReviews() {
        campsiteRepo.ratings.add(Rating("camp-1", "user-8", 5, "Robin Ranger", "Quiet and clean."))
        campsiteRepo.ratings.add(Rating("camp-1", "user-9", 1, "Sam Spammer", "Buy my product at spam.example"))
    }

    /** Shows the details screen of "Lakeside" as the app does, for [signedInUser] viewing as [role]. */
    private fun showDetailsScreen(role: UserRole, signedInUser: User) {
        val campsiteModel = CampsiteModel()
        val campsiteController = CampsiteController(campsiteModel, campsiteRepo)
        val userModel = UserModel()
        userModel.setCurrentUser(signedInUser)
        val adminController = AdminController(
            UserController(userModel, UserRepository()),
            UserRepository(),
            campsiteRepo,
            EmptyLotteryRepository(),
            NotificationController(notifications),
            EmptyRemovalRepository()
        )

        composeRule.setContent {
            observeModel(campsiteModel)
            CampsiteDetailsScreen(
                role = role,
                campsite = CampsiteDetails(
                    id = "camp-1",
                    name = "Lakeside",
                    rating = campsiteModel.averageRating("camp-1"),
                    reviewCount = campsiteModel.ratingCount("camp-1")
                ),
                onBack = {},
                onTabSelected = {},
                reviewsContent = { closeReviews ->
                    LaunchedEffect(Unit) {
                        campsiteController.loadRatings()
                    }
                    CampsiteReviewsScreen(
                        campsiteName = "Lakeside",
                        reviews = campsiteModel.ratingsFor("camp-1"),
                        average = campsiteModel.averageRating("camp-1"),
                        isAdmin = role == UserRole.ADMIN,
                        onBack = closeReviews,
                        onDeleteReview = { review, reason ->
                            adminController.deleteRating(review.id, reason) { _, message ->
                                lastMessage = message
                                campsiteController.loadRatings()
                            }
                        }
                    )
                },
                entrantContent = {
                    CampsiteRatingSection(
                        campsiteModel,
                        campsiteController,
                        "camp-1",
                        signedInUser.deviceId,
                        signedInUser.name
                    )
                }
            )
        }
    }

    /** Selects the card of [userId], taps Delete review, picks [reason] and confirms. */
    private fun deleteCardOf(userId: String, reason: RemovalReason) {
        composeRule.onNodeWithTag("review_card_" + userId).performClick()
        composeRule.onNodeWithTag("delete_review").performClick()
        composeRule.onNodeWithTag("reason_" + reason.name).performClick()
        composeRule.onNodeWithTag("confirm_removal").performClick()
    }

    @Test
    fun postingAReview_showsItOnTheReviewsScreenWithTheReviewersName() {
        showDetailsScreen(UserRole.CAMPER, camper)

        composeRule.onNodeWithTag("star_4").performScrollTo().performClick()
        composeRule.onNodeWithTag("review_input").performScrollTo().performTextInput("Loved the lake view")
        composeRule.onNodeWithTag("post_review").performScrollTo().performClick()

        assertEquals("one rating, not two", 1, campsiteRepo.ratings.size)
        assertEquals("the stars were kept", 4, campsiteRepo.ratings[0].stars)
        assertEquals("the review is attached to the rating", "Loved the lake view", campsiteRepo.ratings[0].review)
        assertEquals("with the reviewer's name", "Casey Camper", campsiteRepo.ratings[0].userName)

        composeRule.onNodeWithTag("see_reviews").performClick()
        composeRule.onNodeWithText("Casey Camper").assertIsDisplayed()
        composeRule.onNodeWithText("Loved the lake view").assertIsDisplayed()

        composeRule.onNodeWithTag("reviews_back").performClick()
        composeRule.onNodeWithTag("see_reviews").assertIsDisplayed()
        composeRule.onNodeWithTag("reviews_back").assertDoesNotExist()
    }

    @Test
    fun postingAReview_beforeRating_isRefused() {
        showDetailsScreen(UserRole.CAMPER, camper)

        composeRule.onNodeWithTag("review_input").performScrollTo().performTextInput("No stars yet")
        composeRule.onNodeWithTag("post_review").performScrollTo().performClick()

        assertTrue("nothing was saved", campsiteRepo.ratings.isEmpty())
        composeRule.onNodeWithText("Tap a star rating first, then post your review").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEntrant_cannotSelectAReviewToDeleteIt() {
        addExistingReviews()
        showDetailsScreen(UserRole.CAMPER, camper)

        composeRule.onNodeWithTag("see_reviews").performClick()
        composeRule.onNodeWithText("Robin Ranger").assertIsDisplayed()
        composeRule.onNodeWithTag("review_card_user-9").performClick()

        composeRule.onNodeWithTag("delete_review").assertDoesNotExist()
        assertEquals("both reviews are still there", 2, campsiteRepo.ratings.size)
    }

    @Test
    fun anAdmin_selectingAReview_getsADeleteButton_thatRemovesItAndTellsTheReviewerWhy() {
        addExistingReviews()
        showDetailsScreen(UserRole.ADMIN, admin)

        composeRule.onNodeWithTag("see_reviews").performClick()
        composeRule.onNodeWithTag("delete_review").assertDoesNotExist()

        deleteCardOf("user-9", RemovalReason.SPAM_OR_FAKE)

        assertEquals("the admin is told it worked", "Rating and review deleted", lastMessage)
        assertEquals("only the selected review went", 1, campsiteRepo.ratings.size)
        assertEquals("the other one stayed", "user-8", campsiteRepo.ratings[0].userId)
        composeRule.onNodeWithTag("review_card_user-9").assertDoesNotExist()
        composeRule.onNodeWithTag("review_card_user-8").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_review").assertDoesNotExist()

        assertEquals("the reviewer was sent one notice", 1, notifications.saved.size)
        assertEquals("addressed to them", "user-9", notifications.saved[0].recipientId)
        assertTrue("it names the campsite", notifications.saved[0].message.contains("Lakeside"))
        assertTrue(
            "and it says why",
            notifications.saved[0].message.contains(RemovalReason.SPAM_OR_FAKE.label)
        )
    }

    @Test
    fun anAdmin_cancellingTheDeletePopUp_keepsTheReview() {
        addExistingReviews()
        showDetailsScreen(UserRole.ADMIN, admin)

        composeRule.onNodeWithTag("see_reviews").performClick()
        composeRule.onNodeWithTag("review_card_user-9").performClick()
        composeRule.onNodeWithTag("delete_review").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this rating and review?").assertDoesNotExist()
        assertEquals("both reviews are still there", 2, campsiteRepo.ratings.size)
        assertTrue("nobody was told anything", notifications.saved.isEmpty())
    }
}