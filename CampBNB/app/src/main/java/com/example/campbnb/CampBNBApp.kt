package com.example.campbnb
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.CampsiteModel
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.Screens.DeviceSignInScreen
import com.example.campbnb.ui.Screens.HomeDestination
import com.example.campbnb.ui.Screens.HomeScreen
import com.example.campbnb.ui.Screens.LoginScreen
import com.example.campbnb.ui.Screens.PlaceholderTab
import com.example.campbnb.ui.Screens.RoleSelectScreen
import com.example.campbnb.ui.Screens.SignUpScreen
import com.example.campbnb.ui.Screens.StartScreen
import com.example.campbnb.ui.lottery.LotteryDetailsActivity
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.ui.shared.DeviceId
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.viewModels.AppViewSettingController

@Composable
fun CampBnbApp(appViewSettings: AppViewSettingController) {

    val nav = rememberNavController()
    val context = LocalContext.current
    val userModel = remember { UserModel() }
    val userRepo = remember { UserRepository() }
    val userController = remember { UserController(userModel, userRepo) }

    NavHost(navController = nav, startDestination = Screen.Start) {
        composable<Screen.Start> {
                StartScreen(onGetStarted = { nav.navigate(Screen.Login) })
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
            DeviceSignInScreen(
                readDevice = { DeviceId.get(context, {""}) },
                userController = userController,
                onEntered = {
                    val role = userModel.currentUser?.roles?.firstOrNull()?: Role.ENTRANT
                    nav.navigate(Screen.Home(roleName = toUserRole(role))) {
                        popUpTo(Screen.Start) { inclusive = true }
                    }
                },
                onNotFound = {
                    nav.navigate(Screen.SignUp)
                }
            )
        }
        composable<Screen.SignUp> {
            SignUpScreen(
                onSignUp = { email, username, phoneNumber ->
                    val newUser = User(
                        deviceId = DeviceId.get(context),
                        name = username,
                        email = email,
                        phone = phoneNumber,
                        roles = listOf(Role.ADMIN)
                    )
                    Database.Users.writeEngine().save(newUser, { /* TODO: handle the complete */})
                    nav.navigate(Screen.Role) },
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
            HomeScreen(role = role,
                destinationContent = { destination ->
                    when (destination) {
                        HomeDestination.MY_CAMPSITES -> {
                            Button(
                                onClick = {
                                    nav.navigate(Screen.CampsiteEditActivity)
                                }
                            ) {
                                Text("Register Campsite")
                            }
                        }
                        else -> PlaceholderTab(destination)
                    }
                },
                appViewSettings = appViewSettings.toAppViewSetting()
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
                Database.Lotteries.readEngine()
            ) { System.currentTimeMillis() } }
            activity.LotteryEdit(model = model, controller = controller)
        }
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { nav.navigate(Screen.Start /* TODO: CHANGE TO HOME */) },
                    { nav.navigateUp() },
                    { nav.navigate(Screen.LotteryEditActivity) },
                    { lottery -> nav.navigate(Screen.LotteryDetailsActivity) },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine()
                ) {
                    System.currentTimeMillis()
                }
            }
            activity.LotteryMonitor(model = model, controller = controller)
        }
        composable<Screen.LotteryDetailsActivity> {
            val activity = remember {
                LotteryDetailsActivity(
                    returnHome = { nav.navigate(Screen.Start) },
                    navigateBackwards = { nav.navigateUp() },
                    onEditLottery = { lottery -> nav.navigate(Screen.LotteryEditActivity) },
                    onViewWaitlist = { lottery -> /* TODO */ },
                    appViewSettings = appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            activity.LotteryDetails(model = model)
        }
    }
}
