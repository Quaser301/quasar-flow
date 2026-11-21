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
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Could not load campsites: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
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
                            NotificationLogScreen(
                                title = "Notification Debugging",
                                notificationSource = deps.notificationRepository,
                                returnFunc = { nav.goHome(role) }
                            )
                        }

                        else -> {
                            PlaceholderTab(destination)
                        }
                    }
                },
                exploreContent = {
                    ExploreScreen(
                        recommended = campsiteCards,
                        newSites = campsiteCards,
                        onSettingsClick = {
                            nav.navigate(Screen.Settings(role))
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
                        }
                    )
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
                        onDeleteSite = {
                            // TODO: connect to the campsite deletion workflow
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

        // Reservations
        composable<Screen.Reservations> {
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

            // Load only this owner's campsites.
            LaunchedEffect(deps.deviceID) {
                deps.campsiteRepository.getAll(
                    onSuccess = { allCampsites ->
                        val ownedCampsites = allCampsites.filter {
                            it.ownerId == deps.deviceID
                        }

                        campsites = ownedCampsites

                        // Automatically select the first campsite.
                        if (selectedCampsiteId == null ||
                            ownedCampsites.none { it.id == selectedCampsiteId }
                        ) {
                            selectedCampsiteId = ownedCampsites.firstOrNull()?.id
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Could not load campsites: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }

            // Load reservations whenever the campsite or filter changes.
            LaunchedEffect(selectedCampsiteId, selectedFilter) {
                val campsiteId = selectedCampsiteId

                if (campsiteId == null) {
                    reservations = emptyList()
                } else {
                    deps.reservationRepository.run {
                        when (selectedFilter) {
                            ReservationFilter.ACTIVE -> getActive(
                                campsiteId,
                                onSuccess = { reservations = it },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Could not load reservations: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )

                            ReservationFilter.PAST -> getPast(
                                campsiteId,
                                onSuccess = { reservations = it },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Could not load reservations: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )

                            ReservationFilter.CANCELLED -> getCancelled(
                                campsiteId,
                                onSuccess = { reservations = it },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Could not load reservations: ${error.message}",
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
                onBack = {
                    nav.navigateUp()
                },
                csvPreferences = deps.notificationPreferencesService,
                currentUserId = deps.currentUserId,
                fileDownloadWriter = deps.fileDownloadWriter
            )
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
                notificationPreferences = deps.notificationPreferencesService,
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
        // Tapping a MESSAGE notification opens that reservation's chat.
        composable(NotificationRoutes.RESERVATIONS) {
            LaunchedEffect(Unit) {
                nav.navigate(Screen.Reservations) {
                    popUpTo(NotificationRoutes.RESERVATIONS) { inclusive = true }
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