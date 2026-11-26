package com.example.campbnb.graphs

/**
 * The campsite half of the navigation graph: the owner's My Campsites entry, the
 * registration and editing of a campsite, and one campsite's details page.
 *
 * The graph is registered under the app root by name, so the screens inside it are
 * found by the compiler-checked Screen types rather than by string routes.
 */
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.campbnb.Screen
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.AdminController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FileDownloadWriter
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.tabs.MyCampsitesTab
import com.example.campbnb.ui.campsite.CampsiteMonitorActivity
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import com.example.campbnb.ui.campsite.CampsiteReviewsScreen
import com.example.campbnb.ui.campsite.LotteryJoinSection
import com.example.campbnb.ui.campsite.lotteryButtonText
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.ui.shared.roleFor
import com.example.campbnb.viewModels.AppViewSetting
import kotlinx.serialization.Serializable





/**
 * Registers every campsite screen as one sub-graph, entered from My Campsites.
 *
 * @param deviceID The current device's stable id.
 * @param appViewSettings The shared live view setting, followed while drawing.
 * @param notification The shared notification behavior service.
 * @param admin The shared admin policy actions over other users' listings.
 * @param currentUserId The signed-in user's id, for the policy actions' messages.
 * @param currentUserName The signed-in user's name, shown beside their own rating on the
 *     reviews screen before the saved rating is reloaded.
 * @param onNavBack Returns to whatever opened the current screen.
 * @param onOpenMyCampsites Opens the owner's campsite list.
 * @param onOpenLotteries Opens the owner's lottery list.
 * @param onOpenReservations Opens the reservations view.
 * @param onCampsiteDetailsClicked A details screen's tab was selected: go home naming
 *     the tab by the Screen its content shows.
 * @param campsiteRepo The shared campsite storage boundary.
 * @param campsiteModel The shared campsite model.
 * @param toastGenerator Reports a result to the user; the caller owns the channel.
 */
fun NavGraphBuilder.campsiteGraph(
    deviceID: String,
    appViewSettings: AppViewSetting,
    notification: NotificationController,
    admin: AdminController,
    currentUserId: String,
    currentUserName: String,
    fileDownloadWriter: FileDownloadWriter,

    onNavBack: () -> Unit,
    onNavHome: () -> Unit,
    onOpenMyCampsites: () -> Unit,
    onOpenLotteries: () -> Unit,
    onOpenReservations: () -> Unit,
    onOpenCampsiteReg: () -> Unit,
    onOpenCampsiteDetails: (role: UserRole, id: String) -> Unit,
    onOpenCampsiteEdit: (id: String) -> Unit,
    onCampsiteDetailsClicked: (role: UserRole, tab: Screen) -> Unit,

    campsiteRepo: CampsiteRepository,
    campsiteModel: CampsiteModel,

    toastGenerator: (String, Int) -> Unit,
) {
    navigation<NavGraphNodes.CampsiteGraph>(startDestination = Screen.MyCampsites) {
        composable<Screen.MyCampsites> {
            MyCampsitesTab(onOpenMyCampsites, onOpenLotteries, onOpenReservations)
        }

        composable<Screen.CampsiteRegistration> {
            val controller = remember {
                CampsiteController(campsiteModel, campsiteRepo)
            }

            CampsiteRegistrationForm(
                onBack = onNavBack,
                onUpload = { campsite, uri ->
                    controller.registerCampsite(campsite, deviceID) { success ->
                        if (success) {
                            onNavBack()
                        }
                    }
                }
            )
        }

        composable<Screen.EditCampsite> { backStackEntry ->
            val campsiteID = backStackEntry.toRoute<Screen.EditCampsite>().campsiteId

            var campsite by remember(campsiteID) {
                mutableStateOf<Campsite?>(null)
            }

            var isLoading by remember(campsiteID) {
                mutableStateOf(true)
            }

            val controller = remember {
                CampsiteController(campsiteModel, campsiteRepo)
            }

            LaunchedEffect(campsiteID) {
                campsiteRepo.getAll(
                    onSuccess = { campsites ->
                        campsite = campsites.find {
                            it.id == campsiteID && it.ownerId == deviceID
                        }
                        isLoading = false
                    },
                    onFailure = {
                        isLoading = false
                    }
                )
            }

            if (isLoading) {
                Text("Loading campsite...")
            } else if (campsite == null) {
                Text("Campsite not found")
            } else {
                CampsiteRegistrationForm(
                    existingCampsite = campsite,
                    onBack = onNavBack,
                    onUpload = { updated, uri ->
                        controller.updateCampsite(
                            updated,
                            deviceID
                        ) { success ->
                            if (success) {
                                toastGenerator("Campsite Updated Successfully!", Toast.LENGTH_SHORT)
                                onNavBack()
                            } else {
                                toastGenerator("Failed to update campsite.", Toast.LENGTH_LONG)
                            }
                        }
                    }
                )
            }
        }

        composable<Screen.CampsiteDetailsScreen> { entry ->
            val route = entry.toRoute<Screen.CampsiteDetailsScreen>()

            // Follow the app view the user is in right now (like HomeScreen does), not the
            // role captured when they navigated here.
            val currentView = appViewSettings.setting.collectAsStateWithLifecycle()
            val shownRole = roleFor(currentView.value)

            val campsiteController = remember {
                CampsiteController(campsiteModel, campsiteRepo)
            }
            val detailsLotteryModel = remember { LotteryModel() }
            val detailsLotteryController = remember {
                LotteryController(
                    detailsLotteryModel,
                    LotteryRepository(),
                    Database.Lotteries.readEngine(),
                    notification
                ) { System.currentTimeMillis() }
            }
            observeModel(campsiteModel)

            var campsiteDetails by remember(route.campsiteId) {
                mutableStateOf<CampsiteDetails?>(null)
            }

            // A policy action over the listing (removing one of its tags) edits the SAVED
            // record, never the drawn view: the view folds tags and amenities together and
            // carries no fields to write back.
            var policyCampsite by remember(route.campsiteId) {
                mutableStateOf<Campsite?>(null)
            }

            var isLoading by remember(route.campsiteId) {
                mutableStateOf(true)
            }

            var errorMessage by remember(route.campsiteId) {
                mutableStateOf<String?>(null)
            }

            LaunchedEffect(route.campsiteId) {
                isLoading = true
                errorMessage = null
                campsiteDetails = null
                policyCampsite = null

                // First, retrieve the campsite.
                campsiteRepo.getAll(
                    onSuccess = { allCampsites ->

                        val campsite = allCampsites.firstOrNull {
                            it.id == route.campsiteId
                        }

                        if (campsite == null) {
                            errorMessage = "Campsite could not be found."
                            isLoading = false
                        } else {
                            policyCampsite = campsite
                            // Then retrieve ratings for this campsite.
                            campsiteRepo.getAllRatings(
                                onSuccess = { allRatings ->

                                    val campsiteRatings = allRatings.filter {
                                        it.campsiteId == campsite.id
                                    }

                                    val averageRating =
                                        if (campsiteRatings.isNotEmpty()) {
                                            campsiteRatings
                                                .map { it.stars }
                                                .average()
                                        } else {
                                            0.0
                                        }

                                    campsiteDetails = CampsiteDetails(
                                        id = campsite.id,
                                        name = campsite.campName,
                                        rating = averageRating,
                                        reviewCount = campsiteRatings.size,
                                        tags = (campsite.tags + campsite.campAmenities)
                                            .distinct(),
                                        capacityGuests = campsite.maxOccupancy,
                                        location = campsite.campLoc,
                                        description = campsite.campDesc,
                                        imageUrl = campsite.campImg
                                    )

                                    isLoading = false
                                },
                                onFailure = { error ->
                                    errorMessage =
                                        "Could not load campsite ratings: ${error.message}"
                                    isLoading = false
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        errorMessage =
                            "Could not load campsite: ${error.message}"
                        isLoading = false
                    }
                )
            }

            when {
                isLoading -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(errorMessage!!)
                    }
                }

                campsiteDetails != null -> {
                    // Once the model has ratings, use its numbers so the stars and average
                    // update right after the user rates. Before that, use the ones loaded
                    // above.
                    var shownDetails = campsiteDetails!!
                    if (campsiteModel.ratingCount(route.campsiteId) > 0) {
                        shownDetails = shownDetails.copy(
                            rating = campsiteModel.averageRating(route.campsiteId),
                            reviewCount = campsiteModel.ratingCount(route.campsiteId)
                        )
                    }

                    CampsiteDetailsScreen(
                        role = shownRole,
                        campsite = shownDetails,
                        onBack = onNavBack,
                        onTabSelected = { tab ->
                            onCampsiteDetailsClicked(route.role, tab)
                        },
                        onEnterLottery = {
                            detailsLotteryController.toggleEntrantMembership(deviceID)
                        },
                        lotteryButtonText = lotteryButtonText(detailsLotteryModel),
                        lotteryButtonEnabled = detailsLotteryModel.entrantLottery != null,
                        lotteryButtonIsLeave = detailsLotteryModel.entrantIsOnWaitlist,
                        onDeleteSite = { reason ->
                            admin.deleteCampsite(
                                route.campsiteId,
                                reason
                            ) { ok, message ->
                                if (ok) {
                                    toastGenerator(
                                        "Site Removed",
                                        Toast.LENGTH_SHORT
                                    )
                                } else {
                                    toastGenerator(
                                        message,
                                        Toast.LENGTH_LONG
                                    )
                                }
                            }
                        },
                        // One tag out of the SAVED record, not out of the drawn view: the
                        // view folds tags and amenities into one list and carries no fields
                        // to write back (controller.md owns the record-building rules).
                        onRemoveTag = { tag, reason ->
                            val listing = policyCampsite
                            if (listing == null) {
                                toastGenerator(
                                    "Could not remove the tag.",
                                    Toast.LENGTH_SHORT
                                )
                            } else {
                                admin.removeTag(
                                    listing,
                                    tag,
                                    reason,
                                    deviceID
                                ) { ok ->
                                    if (ok) {
                                        toastGenerator(
                                            "Removed the tag and told the site's owner.",
                                            Toast.LENGTH_SHORT
                                        )
                                    } else {
                                        toastGenerator(
                                            "Could not remove the tag.",
                                            Toast.LENGTH_LONG
                                        )
                                    }
                                }
                            }
                        },
                        // "See all reviews" on the details screen swaps this reviews
                        // screen in for the details. The ratings come from the same
                        // model the rating section writes through, so the list shows
                        // a rating the user has just saved; the admin's delete of one
                        // card goes through the shared admin policy actions.
                        reviewsContent = { closeReviews ->
                            LaunchedEffect(route.campsiteId) {
                                campsiteController.loadRatings()
                            }
                            CampsiteReviewsScreen(
                                campsiteName = shownDetails.name,
                                reviews = campsiteModel.ratingsFor(route.campsiteId),
                                average = campsiteModel.averageRating(route.campsiteId),
                                isAdmin = shownRole == UserRole.ADMIN,
                                onBack = closeReviews,
                                onDeleteReview = { review, reason ->
                                    admin.deleteRating(review.id, reason) { ok, message ->
                                        toastGenerator(message, Toast.LENGTH_SHORT)
                                        if (ok) {
                                            campsiteController.loadRatings()
                                        }
                                    }
                                }
                            )
                        },
                        entrantContent = {
                            CampsiteRatingSection(
                                campsiteModel,
                                campsiteController,
                                route.campsiteId,
                                deviceID,
                                currentUserName
                            )
                            LotteryJoinSection(
                                detailsLotteryModel,
                                detailsLotteryController,
                                route.campsiteId,
                                deviceID
                            )
                        }
                    )
                }
            }
        }

        // Campsite monitor (owner's list of campsites)
        composable<Screen.CampsiteMonitor> { entry ->
            val route = entry.toRoute<Screen.CampsiteMonitor>()
            val activity = remember {
                CampsiteMonitorActivity(
                    // Back goes to Home naming the My Campsites tab: a bare Home
                    // pop re-enters Home through its start, which is the first
                    // tab, and landed the owner on Explore (reported regression).
                    // Naming the tab sends Home's nested graph the right one.
                    goHome = onNavHome,
                    goBack = onNavBack,
                    goToRegisterCampsite = onOpenCampsiteReg,
                    goToCampsiteDetails = { campsite ->
                        onOpenCampsiteDetails(route.role, campsite.id)
                    },
                    goToEditCampsite = { campsite ->
                        onOpenCampsiteEdit(campsite.id)
                    },
                    // The csv export button writes through this boundary; without it
                    // the screen offers no download at all (the reported regression).
                    fileDownloadWriter = fileDownloadWriter,
                    appViewSettings = appViewSettings,
                    deviceID = deviceID
                )
            }
            val model = remember { CampsiteModel() }
            val controller = remember { CampsiteController(
                model,
                campsiteRepo
            )
            }
            activity.CampsiteMonitor(model = model, controller = controller)
        }

    }
}
