package com.example.campbnb

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.ui.screens.ReservationScreen
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.ui.screens.BookingsScreen
import com.example.campbnb.ui.screens.QrScannerScreen
import com.example.campbnb.ui.screens.QrOldCodeNoticeScreen
import com.example.campbnb.ui.screens.BookingTab
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.SentNotificationsRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.qrLinkRoute
import com.example.campbnb.models.qrOldCodeRoute
import com.example.campbnb.ui.screens.DeviceSignInScreen
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.ui.screens.CampsiteCardData
import com.example.campbnb.ui.screens.ExploreScreen
import com.example.campbnb.ui.screens.HomeDestination
import com.example.campbnb.ui.screens.HomeScreen
import com.example.campbnb.ui.screens.PlaceholderTab
import com.example.campbnb.ui.screens.RoleSelectScreen
import com.example.campbnb.ui.screens.SignUpScreen
import com.example.campbnb.ui.screens.StartScreen
import com.example.campbnb.ui.admin.AdminProfilesScreen
import com.example.campbnb.ui.campsite.CampsiteMonitorActivity
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import com.example.campbnb.ui.campsite.LotteryJoinSection
import com.example.campbnb.ui.campsite.lotteryButtonText
import com.example.campbnb.ui.campsite.toCardList
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.components.profileLabelOf
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import com.example.campbnb.ui.inbox.CorrespondenceScreen
import com.example.campbnb.ui.inbox.CorrespondenceTabs
import com.example.campbnb.ui.inbox.MessagesScreen
import com.example.campbnb.ui.inbox.NotificationInboxScreen
import com.example.campbnb.ui.inbox.NotificationViewModel
import com.example.campbnb.ui.lottery.LotteryDetailsActivity
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.ui.lottery.LotteryWaitlistActivity
import com.example.campbnb.ui.notification.NotificationLogScreen
import com.example.campbnb.ui.search.SearchScreen
import com.example.campbnb.ui.settings.SettingsScreen
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.ui.shared.roleFor
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.ui.shared.viewFor
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.MessageViewModel
import com.example.campbnb.viewModels.simpleViewModelFactory
import kotlin.collections.emptyList

@Composable
fun CampBnbApp(
    deps: Dependencies
) {

    // needed for nav obviously
    val nav = rememberNavController()
    // needed for Toasts
    val context = LocalContext.current


    // The lottery the owner picked on the lottery list, shared by the lottery edit,
    // details and waitlist screens.
    var selectedLottery by remember { mutableStateOf<Lottery?>(null) }

    NavHost(navController = nav, startDestination = Screen.Start) {
        // Start
        composable<Screen.Start> {
            StartScreen(onGetStarted = { nav.navigate(Screen.Login) })
        }

        // Login
        composable<Screen.Login> {
            DeviceSignInScreen(
                deps.deviceID,
                userController = deps.userController,
                onEntered = {
                    val privileges = deps.userModel.currentUser?.roles ?: emptyList()
                    val role = when {
                        Role.ADMIN in privileges -> Role.ADMIN
                        Role.OWNER in privileges -> Role.OWNER
                        else -> Role.ENTRANT
                    }
                    deps.appViewSettings.changeViewTo(viewFor(role))
                    nav.navigate(Screen.Home(role = toUserRole(role))) {
                        popUpTo(Screen.Start) { inclusive = true }
                    }
                },
                onNotFound = {
                    nav.navigate(Screen.SignUp)
                }
            )
        }

        // SignUp
        composable<Screen.SignUp> {
            SignUpScreen(
                onSignUp = { email, username, phoneNumber ->
                    val newUser = User(
                        deviceId = deps.deviceID,
                        name = username,
                        email = email,
                        phone = phoneNumber,
                        roles = listOf(Role.ADMIN)
                    )
                    Database.Users.writeEngine().save(newUser, { /* TODO: handle the complete */})
                    nav.navigate(Screen.Role) }
            )
        }

        // Role Selection
        composable<Screen.Role> { // TODO: have admin accounts only be made by other admins
            RoleSelectScreen(onRoleSelected = { role ->
                val targetView = when (role) {
                    UserRole.ADMIN -> AppView.AdminView
                    UserRole.OWNER -> AppView.OwnerView
                    UserRole.CAMPER -> AppView.EntrantView
                }
                deps.appViewSettings.changeViewTo(targetView)
                nav.navigate(Screen.Home(role = role)) {
                    popUpTo(Screen.Start) {
                        inclusive = true
                    }
                }
            })
        }

        // Home (Explore plus the role's bottom bar)
        composable<Screen.Home> { entry ->
            val route = entry.toRoute<Screen.Home>()
            val role = route.role

            val context = LocalContext.current

            var campsiteCards by remember {
                mutableStateOf<List<CampsiteCardData>>(emptyList())
            }

            // The cards are blank until the Firestore read answers, including after
            // going back into Explore: until then the page shows a placeholder.
            var campsiteCardsLoaded by remember {
                mutableStateOf(false)
            }
            var pendingBookings by remember {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var confirmedBookings by remember(deps.deviceID) {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var selectedBookingTab by remember {
                mutableStateOf(BookingTab.CONFIRMED)
            }

            LaunchedEffect(deps.deviceID) {
                deps.reservationRepository.getForEntrant(
                    entrantId = deps.deviceID,
                    onSuccess = { reservations ->

                        val activeReservations = reservations.filter {
                            it.status == ReservationStatus.ACTIVE
                        }

                        deps.campsiteRepository.getAll(
                            onSuccess = { allCampsites ->

                                confirmedBookings = activeReservations.map { reservation ->
                                    val campsiteName = allCampsites
                                        .firstOrNull { it.id == reservation.campsiteId }
                                        ?.campName ?: "Campsite"

                                    BookingCardData(
                                        id = reservation.lotteryEntryId,
                                        lotteryId = "",
                                        campsiteNames = listOf(campsiteName),
                                        status = "Confirmed",
                                        startDate = reservation.startDate,
                                        endDate = reservation.endDate,
                                        guests = reservation.guests
                                    )
                                }
                            },
                            onFailure = { error ->
                                Toast.makeText(
                                    context,
                                    "Could not load campsite details: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Could not load bookings: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }

            LaunchedEffect(Unit) {
                deps.campsiteRepository.getAll(
                    onSuccess = { campsites ->
                        campsiteCards = campsites.map { campsite ->
                            CampsiteCardData(
                                id = campsite.id,
                                name = campsite.campName,
                                location = campsite.campLoc,
                                maxOccupancy = campsite.maxOccupancy
                            )
                        }
                        campsiteCardsLoaded = true
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Could not load campsites: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        campsiteCardsLoaded = true
                    }
                )
            }

            HomeScreen(
                role = role,
                initialTab = route.tab,
                destinationContent = { destination ->
                    when (destination) {
                        // NOTE: do NOT use a direct nav call to switch to different screens.
                        // That WILL repeatedly invoke that nav call and redraw your screen
                        // INFINITELY.
                        HomeDestination.MY_CAMPSITES -> {
                            MyCampsitesTab(
                                onOpenMyCampsites = {
                                    nav.navigate(Screen.CampsiteMonitorActivity(role))
                                },
                                onOpenLotteries = {
                                    nav.navigate(Screen.LotteryMonitorActivity)
                                },
                                onOpenReservations = {
                                    nav.navigate(Screen.Reservations)
                                }
                            )
                        }

                        HomeDestination.BOOKINGS -> {
                            BookingsScreen(
                                confirmedBookings = confirmedBookings,
                                pendingBookings = pendingBookings,
                                selectedTab = selectedBookingTab,
                                onTabSelected = { selectedBookingTab = it },
                                onBookingClick = { _ ->
                                    // Booking details navigation can be added here later.
                                }
                            )
                        }

                        HomeDestination.MESSAGES -> {
                            CorrespondenceDestination(
                                nav = nav,
                                openTab = CorrespondenceTabs.MESSAGES_TAB,
                                deps = deps
                            )
                        }

                        HomeDestination.INBOX -> {
                            CorrespondenceDestination(
                                nav = nav,
                                openTab = CorrespondenceTabs.NOTIFICATIONS_TAB,
                                deps = deps
                            )
                        }

                        HomeDestination.RESERVATIONS -> {
                            ReservationsTab(
                                onOpenReservations = {
                                    nav.navigate(Screen.Reservations)
                                }
                            )
                        }

                        HomeDestination.ADMIN_PROFILE_VIEW -> {
                            LaunchedEffect(Unit) {
                                deps.userController.loadAllUsers()
                            }
                            AdminProfilesScreen(
                                { nav.goHome(role) },
                                deps.userModel
                            )
                        }

                        HomeDestination.ADMIN_NOTIFICATION_VIEW -> {
                            LaunchedEffect(Unit) {
                                deps.userController.loadAllUsers()
                            }
                            NotificationLogScreen(
                                title = "Notification Debugging",
                                notificationSource = deps.notificationRepository,
                                returnFunc = { nav.goHome(role) },
                                // The debug log holds profile references, so it names the
                                // users they stand for: the saved users are looked up once
                                // above and every reference resolves against them.
                                profileText = { userId ->
                                    profileLabelOf(userId, deps.userModel.allUsers)
                                }
                            )
                        }

                        else -> {
                            PlaceholderTab(destination)
                        }
                    }
                },
                exploreContent = {
                    if (!campsiteCardsLoaded) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                    ExploreScreen(
                        recommended = campsiteCards,
                        newSites = campsiteCards,
                        onSettingsClick = {
                            // Settings is pushed OVER the live Home/Explore frame rather than
                            // re-navigated to: popping with exclusive Home keeps the explored
                            // subtree on the stack, so the matching navigateUp back affordance
                            // pops Settings and returns to the SAME live view, selection and
                            // cards intact (plain navigate would unmount Home and redraw
                            // Explore through the loading placeholder).
                            nav.navigate(Screen.Settings(role)) {
                                popUpTo<Screen.Home> { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onCampsiteClick = { campsite ->
                            nav.navigate(
                                Screen.CampsiteDetailsScreen(role, campsite.id)
                            )
                        },
                        onSearch = { text ->
                            nav.navigate(Screen.Search(role, text))
                        },
                        onFilterClick = {
                            nav.navigate(Screen.Search(role, "", true))
                        },
                        onOpenQr = {
                            nav.navigate(Screen.QrScanner(role))
                        }
                    )
                    }
                },
                appViewSettings = deps.appViewSettings.toAppViewSetting()
            )
        }

        // Campsite registration (owner)
        composable<Screen.CampsiteEditActivity> {
            val controller = remember {
                CampsiteController(
                    CampsiteModel(),
                    deps.campsiteRepository
                )
            }
            CampsiteRegistrationForm(
                onBack = { nav.navigateUp() },
                onUpload = { campsite, _ ->
                    // TODO: upload the chosen image too
                    controller.registerCampsite(campsite, deps.deviceID) { success ->
                        if (success) {
                            nav.navigateUp()
                        }
                    }
                }
            )
        }

        // Campsite editing (owner)
        composable<Screen.EditCampsite> { backStackEntry ->
            val campsiteId = backStackEntry.toRoute<Screen.EditCampsite>().campsiteId

            var campsite by remember(campsiteId) {
                mutableStateOf<Campsite?>(null)
            }

            var isLoading by remember(campsiteId) {
                mutableStateOf(true)
            }

            val controller = remember {
                CampsiteController(CampsiteModel(), deps.campsiteRepository)
            }

            LaunchedEffect(campsiteId) {
                deps.campsiteRepository.getAll(
                    onSuccess = { campsites ->
                        campsite = campsites.find {
                            it.id == campsiteId && it.ownerId == deps.deviceID
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
                    onBack = { nav.navigateUp() },
                    onUpload = { updatedCampsite, _ ->
                        controller.updateCampsite(
                            updatedCampsite,
                            deps.deviceID
                        ) { success ->
                            if (success) {
                                Toast.makeText(
                                    context,
                                    "Campsite updated successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                nav.navigateUp()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to update campsite",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }

        // Search
        composable<Screen.Search> { entry ->
            val route = entry.toRoute<Screen.Search>()
            val searchModel = remember { CampsiteModel() }
            val searchController = remember { CampsiteController(
                searchModel,
                deps.campsiteRepository
                )
            }
            observeModel(searchModel)

            LaunchedEffect(route.query) {
                searchController.loadAndSearch(route.query)
            }

            SearchScreen(
                initialQuery = route.query,
                initialShowFilter = route.openFilter,
                results = toCardList(searchModel.visibleCampsites, searchModel),
                onSearch = { query, filters ->
                    searchController.search(query, filters)
                },
                onClose = {
                    nav.navigateUp()
                },
                // The QR circle opens the camera scanner.
                onQrClick = {
                    nav.navigate(Screen.QrScanner(route.role))
                },
                onCampsiteClick = {
                    nav.navigate(Screen.CampsiteDetailsScreen(route.role, it.id))
                }
            )
        }

        // Camera scanner: opened by the QR circle on Search. A working code opens the
        // campsite it names; going back from that campsite returns here because the
        // navigation stack keeps this screen under the details page (US 01.12.01).
        composable<Screen.QrScanner> { entry ->
            val route = entry.toRoute<Screen.QrScanner>()
            val scannerModel = remember { CampsiteModel() }
            val scannerController = remember {
                CampsiteController(
                    scannerModel,
                    deps.campsiteRepository
                )
            }
            observeModel(scannerModel)
            QrScannerScreen(
                controller = scannerController,
                onOpenCampsite = { campsite ->
                    nav.navigate(Screen.CampsiteDetailsScreen(route.role, campsite.id))
                }
            )
        }

        // Campsite details
        composable<Screen.CampsiteDetailsScreen> { entry ->
            val route = entry.toRoute<Screen.CampsiteDetailsScreen>()


            // Follow the app view the user is in right now (like HomeScreen does), not the role
            // captured when they navigated here.
            val currentView = deps.appViewSettings.viewSetting.collectAsStateWithLifecycle()
            val shownRole = roleFor(currentView.value)

            val campsiteModel = remember { CampsiteModel() }
            val campsiteController = remember { CampsiteController(
                campsiteModel,
                deps.campsiteRepository
                )
            }
            val detailsLotteryModel = remember { LotteryModel() }
            val detailsLotteryController = remember {
                LotteryController(detailsLotteryModel, LotteryRepository(), Database.Lotteries.readEngine(), deps.notificationController) {
                    System.currentTimeMillis()
                }
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
                deps.campsiteRepository.getAll(
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
                            deps.campsiteRepository.getAllRatings(
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
                    // update right after the user rates. Before that, use the ones loaded above.
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
                        onBack = {
                            nav.navigateUp()
                        },
                        onTabSelected = { index ->
                            nav.goHome(route.role, index)
                        },
                        onEnterLottery = {
                            detailsLotteryController.toggleEntrantMembership(deps.deviceID)
                        },
                        lotteryButtonText = lotteryButtonText(detailsLotteryModel),
                        lotteryButtonEnabled = detailsLotteryModel.entrantLottery != null,
                        lotteryButtonIsLeave = detailsLotteryModel.entrantIsOnWaitlist,
                        onDeleteSite = { reason ->
                            val target = policyCampsite
                            if (target != null) {
                                deps.adminController.removeCampsite(
                                    target,
                                    reason,
                                    deps.currentUserId
                                ) { ok ->
                                    Toast.makeText(
                                        context,
                                        if (ok) "Removed the site and told its owner."
                                        else "Could not remove the site.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        onRemoveTag = { tag, reason ->
                            val target = policyCampsite
                            if (target != null) {
                                deps.adminController.removeTag(
                                    target,
                                    tag,
                                    reason,
                                    deps.currentUserId
                                ) { ok ->
                                    Toast.makeText(
                                        context,
                                        if (ok) "Removed the tag and told the site's owner."
                                        else "Could not remove the tag.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        entrantContent = {
                            CampsiteRatingSection(
                                campsiteModel,
                                campsiteController,
                                route.campsiteId,
                                deps.deviceID
                            )
                            LotteryJoinSection(
                                detailsLotteryModel,
                                detailsLotteryController,
                                route.campsiteId,
                                deps.deviceID
                            )
                        }
                    )
                }
            }
        }

        // Lottery edit
        composable<Screen.LotteryEditActivity> {
            val activity = remember {
                LotteryEditActivity(
                    { nav.navigateUp() },
                    // using NavigateUp prevents the user from clicking on the back screen
                    // quickly and navigating too far up
                    { nav.navigateUp() },
                    deps.appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            // The user repository gives the edit screen the owner candidates that
            // invitations may be checked for (US 02.04.08).
            val userRepo = remember { UserRepository() }
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine(),
                    deps.notificationController
                ) { System.currentTimeMillis() }
            }
            activity.LotteryEdit(
                model = model,
                controller = controller,
                userRepository = userRepo,
                initialLottery = selectedLottery,
                deviceID = deps.deviceID
            )
        }

        // Lottery monitor (owner's list of lotteries)
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { nav.navigate(Screen.Start /* TODO: CHANGE TO HOME */) },
                    { nav.navigateUp() },
                    {
                        selectedLottery = null
                        nav.navigate(Screen.LotteryEditActivity)
                    },
                    { lottery ->
                        selectedLottery = lottery
                        nav.navigate(Screen.LotteryDetailsActivity)
                    },
                    deps.appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine(),
                    deps.notificationController
                ) {
                    System.currentTimeMillis()
                }
            }
            activity.LotteryMonitor(
                model = model,
                controller = controller,
                deviceID = deps.deviceID
            )
        }

        // Campsite monitor (owner's list of campsites)
        composable<Screen.CampsiteMonitorActivity> { entry ->
            val route = entry.toRoute<Screen.CampsiteMonitorActivity>()
            val activity = remember {
                CampsiteMonitorActivity(
                    // The previous screen is Home, which picks its tabs from the current view
                    goHome = { nav.navigateUp() },
                    goBack = { nav.navigateUp() },
                    goToRegisterCampsite = { nav.navigate(Screen.CampsiteEditActivity) },
                    goToCampsiteDetails = { campsite ->
                        nav.navigate(
                            Screen.CampsiteDetailsScreen(
                                route.role,
                                campsite.id
                            )
                        )
                    },
                    goToEditCampsite = { campsite ->
                        nav.navigate(Screen.EditCampsite(campsite.id))
                    },
                    fileDownloadWriter = deps.fileDownloadWriter,
                    appViewSettings = deps.appViewSettings.toAppViewSetting(),
                    deviceID = deps.deviceID
                )
            }
            val model = remember { CampsiteModel() }
            val controller = remember { CampsiteController(
                model,
                deps.campsiteRepository
                )
            }
            activity.CampsiteMonitor(model = model, controller = controller)
        }

        // Lottery waitlist
        composable<Screen.LotteryWaitlistActivity> {
            val activity = remember {
                LotteryWaitlistActivity(
                    { nav.navigate(Screen.Start) },
                    { nav.navigateUp() },
                    appViewSettings = deps.appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine(),
                    deps.notificationController
                ) { System.currentTimeMillis() }
            }
            activity.LotteryWaitlist(
                model = model,
                controller = controller,
                selectedLottery = selectedLottery
            )
        }

        // Reservations: the view opened by a notification tap, the owner's My Campsites
        // link and the camper's bottom tab. The private panel below owns the loading and
        // drawing, so all three entries share one wiring.
        composable<Screen.Reservations> { entry ->
            ReservationsPanel(deps) { nav.navigateUp() }
        }

        composable<Screen.Settings> { entry ->
            // need to know our role so we can show
            // the relevant settings
            val role = entry.toRoute<Screen.Settings>().role
            SettingsScreen(
                back = { nav.navigateUp() },
                role = role,
                viewSettings = deps.appViewSettings,
                entrantSettings = deps.entrantSettings,
                ownerSettings = deps.ownerSettings,
                notificationPreferences = deps.notificationPreferencesService,
                appSettings = deps.appSettingsService,
                userIdForSettings = deps.currentUserId
            )
        }

        composable<Screen.LotteryDetailsActivity> {
            val activity = remember {
                LotteryDetailsActivity(
                    returnHome = { nav.navigate(Screen.Start) },
                    navigateBackwards = { nav.navigateUp() },
                    onEditLottery = { _ -> nav.navigate(Screen.LotteryEditActivity) },
                    onViewWaitlist = { _ -> nav.navigate(Screen.LotteryWaitlistActivity) },
                    appViewSettings = deps.appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            activity.LotteryDetails(model = model, selectedLottery = selectedLottery)
        }

        // Notification and message destinations. Tapping a notification opens the notices a
        // viewer received: an owner's own list is the sent one, so their received notices
        // live under the Received tab, while every other viewer has a single notices tab.
        composable(NotificationRoutes.NOTIFICATIONS) {
            CorrespondenceDestination(
                nav = nav,
                openTab = if (deps.currentUserIsOwner) CorrespondenceTabs.RECEIVED_TAB else CorrespondenceTabs.NOTIFICATIONS_TAB,
                deps = deps
            )
        }
        composable(NotificationRoutes.MESSAGES) {
            CorrespondenceDestination(
                nav = nav,
                openTab = CorrespondenceTabs.MESSAGES_TAB,
                deps = deps
            )
        }
        // Tapping a RESERVATION-type notification opens the reservations view. The pop-up
        // names the Home frame, not this route's own: a string route left on the stack
        // would otherwise keep this invisible frame under the reservations screen, and the
        // first back press would land on it (the reported white screen) instead of Home.
        composable(NotificationRoutes.RESERVATIONS) {
            LaunchedEffect(Unit) {
                nav.navigate(Screen.Reservations) {
                    popUpTo<Screen.Home> { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
        // Taps from WIN and INVITE notifications share the booking route. The
        // destination below opens the invitation panel only while the invitation is
        // still pending for the current viewer, and otherwise keeps navigating to the
        // reservations screen (there is still no separate booking screen).
        composable("${NotificationRoutes.BOOKING}/{lotteryId}") { entry ->
            InvitationDestination(
                nav = nav,
                lotteryId = entry.arguments?.getString("lotteryId"),
                deps = deps
            )
        }
        // Tapping a MESSAGE notification opens that reservation's chat.
        composable("${NotificationRoutes.CHAT}/{reservationId}") { entry ->
            CorrespondenceDestination(
                nav = nav,
                openTab = CorrespondenceTabs.MESSAGES_TAB,
                openReservationId = entry.arguments?.getString("reservationId"),
                deps
            )
        }
        // The phone's own camera opens a CampBNB code's link here: MainActivity has
        // already resolved the code's text into one of these routes and queued it on
        // pendingRoute, the same bridge a notification tap uses, and the sign-in ran
        // before this first render, so the user's data populates. A live code names
        // its campsite: this invisible string frame opens the details page and pops
        // itself out of the stack like the reservations bridge does, so the first
        // back press goes to Home, not to this blank frame (the reported white
        // screen). The role carried into the key is the signed-in user's own
        // privileges, the same derivation the login flow uses.
        composable("$qrLinkRoute/{campsiteId}") { entry ->
            val campsiteId = entry.arguments?.getString("campsiteId") ?: ""
            LaunchedEffect(campsiteId) {
                if (campsiteId.isNotBlank()) {
                    val privileges = deps.userModel.currentUser?.roles ?: emptyList()
                    val role = when {
                        Role.ADMIN in privileges -> Role.ADMIN
                        Role.OWNER in privileges -> Role.OWNER
                        else -> Role.ENTRANT
                    }
                    nav.navigate(Screen.CampsiteDetailsScreen(toUserRole(role), campsiteId)) {
                        popUpTo<Screen.Home> { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
        // A dead code's link — its stop already past, or no expiry at all — answers
        // the old-code notice in place of any destination; back goes where the link
        // would otherwise have been.
        composable(qrOldCodeRoute) {
            QrOldCodeNoticeScreen(back = { nav.navigateUp() })
        }

    }

    LaunchedEffect(deps.pendingRoute) {
        if (!deps.pendingRoute.isNullOrBlank()) {
            nav.navigate(deps.pendingRoute!!)
            deps.onPendingRouteHandled()
        }
    }
}

/**
 * Goes to the home page on the given tab, clearing any screens opened on top of it.
 *
 * @param role The signed-in role, which decides the bottom bar.
 * @param tab Index of the bottom bar tab to open.
 */
private fun NavController.goHome(role: UserRole, tab: Int = 0) {
    navigate(Screen.Home(role = role, tab = tab)) {
        popUpTo<Screen.Home> { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * The Correspondence screen, with the tabs this viewer has.
 *
 * The tabs and their contents are chosen here, where the viewer's role is known: an owner
 * is shown the notices they caused under "Notifications" and the notices they received
 * under "Received", while every other viewer has the one notices tab they can see. The
 * screen itself only draws what it is handed.
 *
 * @param nav The app's NavController, used when a notification is tapped.
 * @param openTab Which tab opens, named in CorrespondenceTabs.
 * @param openReservationId If set, the Messages tab opens straight into this reservation's chat.
 */
@Composable
private fun CorrespondenceDestination(
    nav: NavController,
    openTab: String,
    openReservationId: String? = null,
    deps: Dependencies
) {
    if (deps.currentUserIsOwner) {
        CorrespondenceScreen(
            navController = nav,
            tabLabels = listOf(
                CorrespondenceTabs.NOTIFICATIONS_TAB,
                CorrespondenceTabs.RECEIVED_TAB,
                CorrespondenceTabs.MESSAGES_TAB
            ),
            openTab = openTab,
            notificationsTabContent = { sentNoticesTab(deps) },
            receivedTabContent = { receivedNoticesTab(nav, deps) },
            messagesTabContent = { MessagesTab(openReservationId, deps) }
        )
    } else {
        CorrespondenceScreen(
            navController = nav,
            tabLabels = listOf(
                CorrespondenceTabs.NOTIFICATIONS_TAB,
                CorrespondenceTabs.MESSAGES_TAB
            ),
            openTab = openTab,
            notificationsTabContent = { receivedNoticesTab(nav, deps) },
            messagesTabContent = { MessagesTab(openReservationId, deps) }
        )
    }
}

/**
 * The "Sent Notifications" of the current viewer: the notices they caused, read through a
 * repository scoped to their own id, drawn by the notification log.
 */
@Composable
private fun sentNoticesTab(deps: Dependencies) {
    NotificationLogScreen(
        title = "Sent Notifications",
        notificationSource = SentNotificationsRepository(deps.notificationRepository, deps.deviceID)
    )
}

/**
 * The notices the current viewer received. viewModel() keeps the same inbox (and its
 * Firestore listener) across recompositions.
 */
@Composable
private fun receivedNoticesTab(nav: NavController, deps: Dependencies) {
    val userId = deps.deviceID
    val inboxViewModel: NotificationViewModel = viewModel(
        key = "notification-inbox-$userId",
        factory = simpleViewModelFactory {
            NotificationViewModel(deps.notificationController, userId)
        }
    )
    NotificationInboxScreen(viewModel = inboxViewModel, navController = nav)
}

/**
 * The invitation panel opened by a lottery INVITE notification: the invited owner reads
 * what they were invited to and accepts with one tap, which stores the lottery as joined
 * and then opens the owner lottery list, where it now shows. The panel only appears while
 * the invitation is pending for the current viewer, so taps from WIN notifications keep
 * navigating to the reservations screen as before.
 *
 * @param nav The app's NavController.
 * @param lotteryId The route argument naming the invited lottery.
 * @param deps The app-wide dependencies, which name the viewer by device id.
 */
@Composable
private fun InvitationDestination(
    nav: NavController,
    lotteryId: String? = null,
    deps: Dependencies
) {
    val viewerId = deps.deviceID
    var invitedLottery by remember { mutableStateOf<Lottery?>(null) }
    var invitationResolved by remember { mutableStateOf(false) }

    LaunchedEffect(lotteryId) {
        if (!lotteryId.isNullOrEmpty()) {
            Database.Lotteries.readEngine().getById(
                id = lotteryId,
                onSuccess = { found ->
                    invitedLottery = found
                    invitationResolved = true
                },
                onFailure = {
                    invitationResolved = true
                }
            )
        } else {
            invitationResolved = true
        }
    }

    val invited = invitedLottery
    if (invited != null && viewerId in invited.invitedOwnerIds && viewerId !in invited.joinedOwnerIds) {
        val invitationModel = remember { LotteryModel() }
        val invitationRepository = remember { LotteryRepository() }
        val invitationController = remember {
            LotteryController(
                invitationModel,
                invitationRepository,
                Database.Lotteries.readEngine(),
                deps.notificationController
            ) { System.currentTimeMillis() }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lottery Invitation",
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "You were invited to join this lottery. Accept to see it in your list and add your own campsites."
            )
            Button(
                onClick = {
                    invitationController.acceptInvitation(
                        lottery = invited,
                        userId = viewerId,
                        onSuccess = { nav.navigate(Screen.LotteryMonitorActivity) }
                    )
                }
            ) {
                Text("Accept invitation")
            }
            TextButton(onClick = { nav.navigate(Screen.Reservations) }) {
                Text("Not now")
            }
        }
    } else if (invitationResolved) {
        LaunchedEffect(invitationResolved) {
            nav.navigate(Screen.Reservations) {
                popUpTo(Screen.Reservations) { inclusive = true }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

/** The chat list and chat threads. [openReservationId] jumps straight into one chat. */
@Composable
private fun MessagesTab(openReservationId: String? = null, deps: Dependencies) {
    val userId = deps.deviceID
    val messageViewModel: MessageViewModel = viewModel(
        key = "message-inbox-$userId",
        factory = simpleViewModelFactory {
            MessageViewModel(deps.messageController, userId)
        }
    )
    LaunchedEffect(openReservationId) {
        if (!openReservationId.isNullOrBlank()) {
            messageViewModel.open(openReservationId)
        }
    }
    MessagesScreen(viewModel = messageViewModel, currentUserId = userId)
}

/** Owner's My Campsites tab: links to the campsite list, the lottery list and reservations. */
@Composable
private fun MyCampsitesTab(
    onOpenMyCampsites: () -> Unit,
    onOpenLotteries: () -> Unit,
    onOpenReservations: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My Campsites", fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        GradientButton("My Campsites", onOpenMyCampsites)
        Spacer(Modifier.height(12.dp))
        GradientButton("My Lotteries", onOpenLotteries)
        Spacer(Modifier.height(12.dp))
        GradientButton("Reservations", onOpenReservations)
    }
}


/**
 * The reservations view, opened by a notification tap, the owner's My Campsites link and
 * the camper's bottom tab. Which reservations a viewer may list is decided by the read
 * handed here: an owner browses their own campsites and sees every reservation at each,
 * while every other viewer reads their own reservations and is offered only the sites
 * that carry them - an entrant reaching this view gets their own bookings, not an empty
 * campsite selector. The screen only draws what it is handed.
 *
 * @param deps The app-wide dependencies, naming the viewer and the storage boundaries.
 * @param goBack The return function handed to the screen, owned by the caller that opened
 *     this view.
 */
@Composable
private fun ReservationsPanel(deps: Dependencies, goBack: () -> Unit) {
    val context = LocalContext.current

    var campsites by remember {
        mutableStateOf<List<Campsite>>(emptyList())
    }

    var selectedCampsiteId by remember {
        mutableStateOf<String?>(null)
    }

    var selectedFilter by remember {
        mutableStateOf(ReservationFilter.ACTIVE)
    }

    var reservations by remember {
        mutableStateOf<List<com.example.campbnb.models.ReservationModel>>(emptyList())
    }

    // Load the campsites this viewer browses: the owner's own sites, or for anyone else
    // the sites that carry their own reservations.
    LaunchedEffect(deps.deviceID) {
        deps.campsiteRepository.getAll(
            onSuccess = { allCampsites ->
                if (deps.currentUserIsOwner) {
                    val browsedCampsites = allCampsites.filter {
                        it.ownerId == deps.deviceID
                    }
                    campsites = browsedCampsites
                    if (selectedCampsiteId == null ||
                        browsedCampsites.none { it.id == selectedCampsiteId }
                    ) {
                        selectedCampsiteId = browsedCampsites.firstOrNull()?.id
                    }
                } else {
                    deps.reservationRepository.getForEntrant(
                        entrantId = deps.deviceID,
                        onSuccess = { ownReservations ->
                            val browsedCampsites = allCampsites.filter { campsite ->
                                ownReservations.any { it.campsiteId == campsite.id }
                            }
                            campsites = browsedCampsites
                            if (selectedCampsiteId == null ||
                                browsedCampsites.none { it.id == selectedCampsiteId }
                            ) {
                                selectedCampsiteId = browsedCampsites.firstOrNull()?.id
                            }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load your reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            },
            onFailure = { error ->
                Toast.makeText(
                    context,
                    "Could not load campsites: ${'$'}{error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Load reservations whenever the campsite or filter changes. An owner browsing their
    // own sites sees every reservation at a site; anyone else only their own.
    LaunchedEffect(selectedCampsiteId, selectedFilter) {
        val campsiteId = selectedCampsiteId
        val viewerIsOwner = deps.currentUserIsOwner

        if (campsiteId == null) {
            reservations = emptyList()
        } else {
            deps.reservationRepository.run {
                when (selectedFilter) {
                    ReservationFilter.ACTIVE -> getActive(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter { it.entrantId == deps.deviceID }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    ReservationFilter.PAST -> getPast(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter { it.entrantId == deps.deviceID }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    ReservationFilter.CANCELLED -> getCancelled(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter { it.entrantId == deps.deviceID }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }

    ReservationScreen(
        campsites = campsites,
        selectedCampsiteId = selectedCampsiteId,
        selectedFilter = selectedFilter,
        reservations = reservations,
        onCampsiteSelected = { campsiteId ->
            selectedCampsiteId = campsiteId
        },
        onFilterSelected = { filter ->
            selectedFilter = filter
        },
        onBack = goBack,
        csvPreferences = deps.notificationPreferencesService,
        currentUserId = deps.currentUserId,
        fileDownloadWriter = deps.fileDownloadWriter
    )
}

/** Camper's Reservations tab: one link that opens the reservations view. */
@Composable
private fun ReservationsTab(onOpenReservations: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Reservations", fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        GradientButton("Open Reservations", onOpenReservations)
    }
}
