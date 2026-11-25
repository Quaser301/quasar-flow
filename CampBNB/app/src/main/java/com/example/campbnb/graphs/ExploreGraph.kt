package com.example.campbnb.graphs

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.campbnb.Screen
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.ui.campsite.toCardList
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.CampsiteCardData
import com.example.campbnb.ui.screens.ExploreScreen
import com.example.campbnb.ui.screens.QrScannerScreen
import com.example.campbnb.ui.search.SearchScreen
import com.example.campbnb.ui.shared.observeModel
import kotlinx.serialization.Serializable




/**
 * The explore screen and the related search screen.
 *
 * @param navToSettings Opens the settings screen from Explore.
 * @param navToCampsite Opens one campsite's details page from an explore card.
 * @param navToSearch Opens the search screen for a typed query.
 * @param navToCampDetails Opens one campsite's details page from a search result.
 * @param onNavBack Returns to whatever opened the current screen.
 * @param onSearchFilterClicked Opens the search screen in filter mode.
 * @param onOpenHistory Opens the search screen showing the search history.
 * @param onOpenQr Opens the camera scanner.
 * @param campsiteRepo The shared campsite storage boundary.
 * @param campsiteModel The shared model the search results draw through.
 * @param campsiteControl The shared controller that loads and searches.
 * @param toastGenerator Reports a load failure to the user; the caller owns the channel.
 * @param cameraAllowed Whether this device has let the app use its camera; the camera
 *   scanner opens a camera only when this says yes.
 */
fun NavGraphBuilder.exploreGraph(

    navToSettings: () -> Unit,
    navToCampsite: (data: CampsiteCardData) -> Unit,
    navToSearch: (String) -> Unit,
    navToCampDetails: (role: UserRole, campsiteId: String) -> Unit,

    onNavBack: () -> Unit,
    onSearchFilterClicked: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenQr: () -> Unit,

    campsiteRepo: CampsiteRepository,

    campsiteModel: CampsiteModel,

    campsiteControl: CampsiteController,

    toastGenerator: (reason: String, duration:Int) -> Unit,

    cameraAllowed: Boolean = false
) {
    navigation<NavGraphNodes.ExploreGraph>(startDestination = Screen.Explore) {
        composable<Screen.Explore> {
            var newCampsiteCards by remember {
                mutableStateOf<List<CampsiteCardData>>(emptyList())
            }
            var recommendedCampsiteCards by remember {
                mutableStateOf<List<CampsiteCardData>>(emptyList())
            }

            LaunchedEffect(Unit) {
                campsiteRepo.getAll(
                    onSuccess = { campsites ->
                        newCampsiteCards = campsites.map { campsite ->
                            campsite.toCampsiteCardData()
                        }
                    },
                    onFailure = { error ->
                        toastGenerator(
                            "Could not load campsites: ${error.message}",
                            Toast.LENGTH_LONG)
                    }
                )
            }

            ExploreScreen(
                recommended = newCampsiteCards,
                newSites = newCampsiteCards,
                onSettingsClick = navToSettings,
                onCampsiteClick = navToCampsite,
                onSearch = navToSearch,
                onFilterClick = onSearchFilterClicked,
                onOpenHistory = onOpenHistory,
                onOpenQr = onOpenQr,
            )
        }

        composable<Screen.Search> { entry ->
            val route = entry.toRoute<Screen.Search>()
            val searchModel = remember { campsiteModel }
            val searchController = remember { campsiteControl }

            observeModel(searchModel)

            LaunchedEffect(route.query) {
                searchController.loadAndSearch(route.query)
            }

            SearchScreen(
                initialQuery = route.query,
                initialShowFilter = route.openFilter,
                initialShowHistory = route.openHistory,
                results = toCardList(searchModel.visibleCampsites, searchModel),
                onSearch = { query, filters ->
                    searchController.search(query, filters)
                },
                onClose = onNavBack,
                onQrClick = onOpenQr,
                onCampsiteClick = {
                    navToCampDetails(route.role, it.id)
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
                    campsiteRepo
                )
            }
            QrScannerScreen(
                controller = scannerController,
                onOpenCampsite = { campsite ->
                    navToCampDetails(route.role, campsite.id)
                },
                cameraAllowed = cameraAllowed
            )
        }
    }
}
