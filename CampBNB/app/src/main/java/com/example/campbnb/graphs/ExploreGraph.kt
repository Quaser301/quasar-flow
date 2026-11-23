package com.example.campbnb.graphs

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
import com.example.campbnb.ui.search.SearchScreen
import com.example.campbnb.ui.shared.observeModel
import kotlinx.serialization.Serializable

@Serializable
data object ExploreGraph


/**
 * The explore screen and the related search screen.
 *
 */
fun NavGraphBuilder.ExploreGraph(

    navToSettings: () -> Unit,
    navToCampsite: (data: CampsiteCardData) -> Unit,
    navToSearch: (String) -> Unit,
    navToCampDetails: (role: UserRole, deviceID:String) -> Unit,

    onNavBack: () -> Unit,
    onSearchFilterClicked: () -> Unit,

    campsiteRepo: CampsiteRepository,

    campsiteModel: CampsiteModel,

    campsiteControl: CampsiteController,

    toastGenerator: (reason: String) -> Unit
) {
    navigation<ExploreGraph>(startDestination = Screen.Explore) {
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
                        toastGenerator("Could not load campsites: ${error.message}")
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
            )
        }

        composable<Screen.Search> {entry ->
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
                results = toCardList(searchModel.visibleCampsites, searchModel),
                onSearch = { query, filters ->
                    searchController.search(query, filters)
                },
                onClose = onNavBack,
                onCampsiteClick = {
                    navToCampDetails(route.role, it.id)
                }
            )
        }
    }
}