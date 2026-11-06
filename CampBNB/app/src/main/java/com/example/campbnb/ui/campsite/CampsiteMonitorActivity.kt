package com.example.campbnb.ui.campsite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.ViewObserver
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.ui.shared.DeviceId
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.campbnb.data.CampsiteRepository
// Prompted Claude Sonnet 5.5 to build this screen the same way as LotteryMonitorActivity,
// but listing the owner's campsites (US 02.03.01).

/**
 * View: the owner's "My Campsites" list. Same layout as the owner's lottery list: a Back
 * button and title at the top, one card per campsite the owner registered, and a wide
 * "Register Campsite" button at the bottom.
 *
 * Functions:
 * - update: required by ViewObserver, nothing to do
 * - CampsiteMonitor: the screen
 *
 * @property goHome Called if the app view changes to one that cannot show this screen.
 * @property goBack Called when the Back button is tapped.
 * @property goToRegisterCampsite Called when the Register Campsite button is tapped.
 * @property goToCampsiteDetails Called with the campsite when its Details button is tapped.
 * @property goToEditCampsite Called with the campsite when its Edit button is tapped.
 * @property appViewSettings The live app view setting.
 */
class CampsiteMonitorActivity(
    val goHome: () -> Unit,
    val goBack: () -> Unit,
    val goToRegisterCampsite: () -> Unit,
    val goToCampsiteDetails: (Campsite) -> Unit,
    val goToEditCampsite: (Campsite) -> Unit,
    val appViewSettings: AppViewSetting
) : ViewObserver<CampsiteModel> {

    // Required by ViewObserver. The screen redraws through observeModel(), so there is
    // nothing to do here.
    override fun update(model: CampsiteModel) {}

    // List of the owner's campsites
    @Composable
    fun CampsiteMonitor(
        model: CampsiteModel,
        controller: CampsiteController,
        ownerId: String = ""
    ) {
        observeModel(model)
        var campsiteToDelete by remember {
            mutableStateOf<Campsite?>(null)
        }

        var deleteError by remember {
            mutableStateOf("")
        }

        val repository = remember { CampsiteRepository() }
        AppViewModificationIsValidForCurrentWindowChecker(
            goHome,
            appViewSettings,
            listOf(
                AppView.EntrantView,
                AppView.AdminView
            )
        )

        // Must be the same id registerCampsite() saves as the owner, which is the device id.
        val context = LocalContext.current
        val resolvedOwnerId = ownerId.ifBlank { DeviceId.get(context) }

        LaunchedEffect(resolvedOwnerId) {
            if (resolvedOwnerId.isNotBlank()) {
                controller.loadOwnerCampsites(resolvedOwnerId)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { goBack() },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = "My Campsites",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Read the list here, outside the LazyColumn. CampsiteModel is not Compose state,
            // so LazyColumn only sees a new list if its content lambda captures the new list.
            val campsites = model.ownedCampsites
            if (deleteError.isNotEmpty()) {
                Text(
                text = deleteError,
                color = MaterialTheme.colorScheme.error)
            }

            if (campsites.isEmpty()) {
                Text(
                    text = "You have not registered any campsites yet.",
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(campsites) { campsite ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = campsite.campName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = campsite.campLoc,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Price: $${campsite.campPrice}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(
                                modifier = Modifier.padding(start = 8.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(onClick = { goToCampsiteDetails(campsite) }) {
                                    Text("Details")
                                }
                                Button(onClick = { goToEditCampsite(campsite) }) {
                                    Text("Edit")
                                }
                                Button(onClick = {
                                    campsiteToDelete = campsite
                                }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Wide, short rectangular button at the bottom
            Button(
                onClick = { goToRegisterCampsite() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Register Campsite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
        if (campsiteToDelete != null) {
            val site = campsiteToDelete!!
            AlertDialog(
                onDismissRequest = {
                    campsiteToDelete = null
                },
                title = { Text("Delete Campsite") },
                text = { Text("Are you sure you want to delete ${site.campName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            repository.deleteCampsite(site.id) { result ->
                                if (result.isSuccess) {
                                    controller.loadOwnerCampsites(resolvedOwnerId)
                                    campsiteToDelete = null
                                } else {
                                    deleteError = "Could not delete campsite"
                                }
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            campsiteToDelete = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}