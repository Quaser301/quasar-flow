package com.example.campbnb.ui.campsite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.Campsite
import com.google.firebase.auth.FirebaseAuth

@Composable
fun CampsiteOwnerActivity(
    onEdit: (String) -> Unit,
    onRegister: () -> Unit
) {
    var campsites by remember {
        mutableStateOf(emptyList<Campsite>())
    }

    var error by remember {
        mutableStateOf("")
    }

    val repository = remember { CampsiteRepository() }
    val ownerId = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(ownerId) {
        if (ownerId == null) {
            error = "Please log in first"
        } else {
            repository.getAll(
                onSuccess = { allCampsites ->
                    campsites = allCampsites.filter {
                        it.ownerId == ownerId
                    }
                },
                onFailure = {
                    error = "Could not load campsites"
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("My Campsites")

        Button(onClick = onRegister) {
            Text("Register Campsite")
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
                }
            }
        }
    }
}