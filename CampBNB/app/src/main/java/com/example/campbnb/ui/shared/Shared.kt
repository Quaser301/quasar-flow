package com.example.campbnb.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting


@Composable
fun AppViewModificationIsValidForCurrentWindowChecker(returnTo: () -> Unit, viewSetting: AppViewSetting, returnIfAppViewIs:List<AppView>) {
    val currentViewType = viewSetting.setting.collectAsStateWithLifecycle()
    LaunchedEffect(currentViewType) {
        if (returnIfAppViewIs.contains(currentViewType.value)) {
            returnTo()
        }
    }
}