package com.example.campbnb.ui.campsite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.ui.shared.DeviceId

@Composable
fun CampsiteOwnerActivity(
    onEdit: (String) -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit = {},
    onOpenLotteries: () -> Unit = {}
) {
    var campsites by remember {
        mutableStateOf(emptyList<Campsite>())
    }

    var error by remember {
        mutableStateOf("")
    }
    var campsiteToDelete by remember { mutableStateOf<Campsite?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val repository = remember { CampsiteRepository() }
    val context = LocalContext.current
    val ownerId = remember(context) { DeviceId.get(context) }

    LaunchedEffect(ownerId) {
        repository.getAll(
            onSuccess = { allCampsites ->
                campsites = allCampsites.filter {
                    it.ownerId == ownerId
                }
                error = ""
            },
            onFailure = {
                error = "Could not load campsites"
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("My Campsites")

        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onRegister) {
                Text("Register Campsite")
            }
            Button(onClick = onOpenLotteries) {
                Text("My Lotteries")
            }
        }

        if (error.isNotEmpty()) {
            Text(error)
        }

        LazyColumn {
            items(campsites) { campsite ->
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(campsite.campName)
                    Text(campsite.campLoc)
                    Text("Price: $${campsite.campPrice}")

                    Button(
                        onClick = {
                            onEdit(campsite.id)
                        }
                    ) {
                        Text("Edit Campsite")
                    }
                    Button(
                        onClick = {
                            campsiteToDelete = campsite
                            showDeleteConfirmation = true
                        }
                    ) {
                        Text("Delete Campsite")
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation && campsiteToDelete != null) {
        val site = campsiteToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                campsiteToDelete = null
            },
            title = { Text("Delete Campsite") },
            text = { Text("Are you sure you want to delete ${site.campName}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Database.Campsites.writeEngine().delete(site.id) { result ->
                            if (result.isSuccess) {
                                campsites = campsites.filter { it.id != site.id }
                            } else {
                                error = "Could not delete campsite"
                            }
                        }
                        showDeleteConfirmation = false
                        campsiteToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        campsiteToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
