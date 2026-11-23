package com.example.campbnb.graphs

/**
 * The campsite half of the navigation graph: the owner's My Campsites entry, the
 * registration and editing of a campsite, and one campsite's details page.
 *
 * The graph is registered under the app root by name, so the screens inside it are
 * found by the compiler-checked Screen types rather than by string routes.
 */
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
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.tabs.MyCampsitesTab
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import com.example.campbnb.ui.campsite.LotteryJoinSection
import com.example.campbnb.ui.campsite.lotteryButtonText
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.ui.shared.roleFor
import com.example.campbnb.viewModels.AppViewSetting
import kotlinx.serialization.Serializable


@Serializable
data object CampsiteGraph


/**
 * Registers every campsite screen as one sub-graph, entered from My Campsites.
 *
 * @param deviceID The current device's stable id.
 * @param appViewSettings The shared live view setting, followed while drawing.
 * @param notification The shared notification behavior service.
 * @param admin The shared admin policy actions over other users' listings.
 * @param currentUserId The signed-in user's id, for the policy actions' messages.
 * @param onNavBack Returns to whatever opened the current screen.
 * @param onOpenMyCampsites Opens the owner's campsite list.
 * @param onOpenLotteries Opens the owner's lottery list.
 * @param onOpenReservations Opens the reservations view.
 * @param onCampsiteDetailsClicked A details screen's tab was selected: go home naming
 *     the tab.
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

    onNavBack: () -> Unit,
    onOpenMyCampsites: () -> Unit,
    onOpenLotteries: () -> Unit,
    onOpenReservations: () -> Unit,
    onCampsiteDetailsClicked: (role: UserRole, tab: Int) -> Unit,

    campsiteRepo: CampsiteRepository,
    campsiteModel: CampsiteModel,

    toastGenerator: (String) -> Unit,
) {
    navigation<CampsiteGraph>(startDestination = Screen.MyCampsites) {
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
                                toastGenerator("Campsite Updated Successfully!")
                                onNavBack()
                            } else {
                                toastGenerator("Failed to update campsite.")
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

            var isLoading by remember(route.campsiteId) {
                mutableStateOf(true)
            }

            var errorMessage by remember(route.campsiteId) {
                mutableStateOf<String?>(null)
            }

            // The saved record behind these details: a policy action acts on the listing
            // itself, not on what is drawn.
            var policyCampsite by remember(route.campsiteId) {
                mutableStateOf<Campsite?>(null)
            }

            LaunchedEffect(route.campsiteId) {
                isLoading = true
                errorMessage = null
                campsiteDetails = null

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
                        onTabSelected = { index ->
                            onCampsiteDetailsClicked(route.role, index)
                        },
                        onEnterLottery = {
                            detailsLotteryController.toggleEntrantMembership(deviceID)
                        },
                        lotteryButtonText = lotteryButtonText(detailsLotteryModel),
                        lotteryButtonEnabled = detailsLotteryModel.entrantLottery != null,
                        lotteryButtonIsLeave = detailsLotteryModel.entrantIsOnWaitlist,
                        onDeleteSite = { reason ->
                            val target = policyCampsite
                            if (target != null) {
                                admin.removeCampsite(
                                    target,
                                    reason,
                                    currentUserId
                                ) { ok ->
                                    if (ok) {
                                        toastGenerator(
                                            "Removed the site and told its owner."
                                        )
                                    } else {
                                        toastGenerator("Could not remove the site.")
                                    }
                                }
                            }
                        },
                        onRemoveTag = { tag, reason ->
                            val target = policyCampsite
                            if (target != null) {
                                admin.removeTag(
                                    target,
                                    tag,
                                    reason,
                                    currentUserId
                                ) { ok ->
                                    if (ok) {
                                        toastGenerator(
                                            "Removed the tag and told the site's owner."
                                        )
                                    } else {
                                        toastGenerator("Could not remove the tag.")
                                    }
                                }
                            }
                        },
                        entrantContent = {
                            CampsiteRatingSection(
                                campsiteModel,
                                campsiteController,
                                route.campsiteId,
                                deviceID
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
    }
}
