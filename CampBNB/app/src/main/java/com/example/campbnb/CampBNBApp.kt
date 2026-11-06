package com.example.campbnb
import androidx.navigation.toRoute
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.CampsiteModel
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import com.example.campbnb.ui.campsite.CampsiteOwnerActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.campbnb.models.Campsite
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.Screens.HomeDestination
import com.example.campbnb.ui.Screens.HomeScreen
import com.example.campbnb.ui.Screens.LoginScreen
import com.example.campbnb.ui.Screens.PlaceholderTab
import com.example.campbnb.ui.Screens.RoleSelectScreen
import com.example.campbnb.ui.Screens.SignUpScreen
import com.example.campbnb.ui.Screens.StartScreen
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.viewModels.AppViewSettingController

@Composable
fun CampBnbApp(appViewSettings: AppViewSettingController) {

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Screen.Start) {
        composable<Screen.Start> {
            StartScreen(onGetStarted = { nav.navigate(Screen.Login) })

        }

        composable<Screen.EditCampsite> { backStackEntry ->

            val campsiteId =
                backStackEntry.toRoute<Screen.EditCampsite>().campsiteId

            var campsite by remember(campsiteId) {
                mutableStateOf<Campsite?>(null)
            }

            var isLoading by remember(campsiteId) {
                mutableStateOf(true)
            }

            val repository = remember { CampsiteRepository() }

            val controller = remember {
                CampsiteController(CampsiteModel(), repository)
            }

            LaunchedEffect(campsiteId) {
                repository.getAll(
                    onSuccess = { campsites ->
                        campsite = campsites.find { it.id == campsiteId }
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

                        val ownerId =
                            FirebaseAuth.getInstance().currentUser?.uid ?: ""

                        controller.updateCampsite(
                            updatedCampsite,
                            ownerId
                        ) { success ->

                            if (success) {
                                nav.navigateUp()
                            }
                        }
                    }
                )
            }
        }

        composable<Screen.CampsiteEditActivity> {
            val context = LocalContext.current

            val model = remember { CampsiteModel() }
            val repo = remember { CampsiteRepository() }
            val controller = remember {
                CampsiteController(model, repo)
            }

            CampsiteRegistrationForm(
                onBack = {
                    nav.navigateUp()
                },
                onUpload = { campsite, imageUri ->

                    val ownerId = FirebaseAuth.getInstance().currentUser?.uid

                    if (ownerId == null) {
                        Toast.makeText(
                            context,
                            "You must be logged in",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        controller.registerCampsite(
                            campsite = campsite,
                            ownerId = ownerId,
                            onComplete = { success ->
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Campsite registered successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    nav.navigateUp()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Could not register campsite",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            )
        }
        composable<Screen.Login> {
            LoginScreen(
                onLogin = { _, role, _ ->   // TODO: authenticate first
                    nav.navigate(Screen.Home(roleName = role)) {
                        popUpTo(Screen.Start) {
                            inclusive = true
                        }
                    }
                },
                onSignUpClick = { nav.navigate(Screen.SignUp) }
            )
        }
        composable<Screen.SignUp> {
            SignUpScreen(
                onSignUp = { email, username -> /* TODO: create the account */ nav.navigate(Screen.Role) },
                onLoginClick = { nav.navigateUp() }
            )
        }
        composable<Screen.Role> {
            RoleSelectScreen(onRoleSelected = { role ->
                nav.navigate(Screen.Home(roleName = role)) {
                    popUpTo(Screen.Start) {
                        inclusive = true
                    }
                }
            })
        }
        composable<Screen.Home> { entry ->
            val role = entry.toRoute<Screen.Home>().roleName
            HomeScreen(
                role = role,
                appViewSettings = appViewSettings.toAppViewSetting(),
                destinationContent = { destination ->
                    when (destination) {
                        HomeDestination.MY_CAMPSITES -> CampsiteOwnerActivity(
                            onEdit = { campsiteId ->
                                nav.navigate(Screen.EditCampsite(campsiteId))
                            },
                            onRegister = {
                                nav.navigate(Screen.CampsiteEditActivity)
                            }
                        )
                        else -> PlaceholderTab(destination)
                    }
                }
            )
        }
        composable<Screen.LotteryEditActivity> {
            val activity = remember {
                LotteryEditActivity(
                    { nav.navigateUp() },
                    // using NavigateUp prevents the user from clicking on the back screen
                    // quickly and navigating too far up
                    { nav.navigateUp() },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember { LotteryController(
                model,
                repo,
                TODO(),
                TODO()
            ) }
            activity.LotteryEdit(model = model, controller = controller)
        }
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { nav.navigate(Screen.Start /* TODO: CHANGE TO HOME */) },
                    { nav.navigateUp() },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember { LotteryController(model, repo, TODO(), TODO()) }
            activity.LotteryMonitor(model = model, controller = controller)
        }
    }
}
