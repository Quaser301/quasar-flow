package com.example.campbnb.ui.campsite

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campbnb.Campsite

/**
 * Displays the Form for Campsite Registration
 */
@Composable
fun CampsiteRegistrationForm(
    onBack: () -> Unit,
    onUpload: (Campsite, Uri?) -> Unit
) {
    var CampName by remember { mutableStateOf("") }
    var CampLoc by remember { mutableStateOf("") }
    var CampDesc by remember { mutableStateOf("") }
    var CampAmenities by remember { mutableStateOf("") }
    var maxOccupancy by remember { mutableStateOf("") }
    var geoLocation by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var CampImg by remember { mutableStateOf<Uri?>(null) }
    var BackConfirmation by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                CampImg = uri
            }
        }
    )

    val Validation =
        CampName.trim().isNotEmpty() &&
                CampLoc.trim().isNotEmpty() &&
                CampDesc.trim().isNotEmpty() &&
                CampAmenities.trim().isNotEmpty() &&
                (maxOccupancy.toIntOrNull() ?: 0) > 0 &&
                geoLocation.trim().isNotEmpty()

    val Owner_PutInput =
        CampName.isNotBlank() ||
                CampLoc.isNotBlank() ||
                CampDesc.isNotBlank() ||
                CampAmenities.isNotBlank() ||
                maxOccupancy.isNotBlank() ||
                geoLocation.isNotBlank() ||
                tagInput.isNotBlank() ||
                tags.isNotEmpty() ||
                CampImg != null

    BackHandler {
        if (Owner_PutInput) {
            BackConfirmation = true
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Register a Campsite",
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = CampName,
            onValueChange = { CampName = it },
            label = { Text("Campsite Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = CampLoc,
            onValueChange = { CampLoc = it },
            label = { Text("Campsite Location") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = CampDesc,
            onValueChange = { CampDesc = it },
            label = { Text("Campsite Description") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = CampAmenities,
            onValueChange = { CampAmenities = it },
            label = { Text("Campsite Amenities (comma-separated)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = maxOccupancy,
            onValueChange = { maxOccupancy = it },
            label = { Text("Maximum Occupancy") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = geoLocation,
            onValueChange = { geoLocation = it },
            label = { Text("Geographical location") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Tags",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = tagInput,
            onValueChange = { tagInput = it },
            label = { Text("Enter a tag") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (tagInput.isNotBlank()) {
                    tags = tags + tagInput.trim()
                    tagInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Tag")
        }
        for (tag in tags) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tag)
                TextButton(
                    onClick = {
                        tags = tags - tag
                    }
                ) {
                    Text("Remove")
                }
            }
        }
        Text(
            text = "Campsite Image (Optional)",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = {
                imagePicker.launch("image/*")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Upload Image")
        }
        if (CampImg != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Image Selected")
                TextButton(
                    onClick = {
                        CampImg = null
                    }
                ) {
                    Text("Remove Image")
                }
            }
        }
        Button(
            onClick = {
                val campsite = Campsite(
                    CampName = CampName.trim(),
                    CampLoc = CampLoc.trim(),
                    CampDesc = CampDesc.trim(),
                    CampAmenities = CampAmenities.trim().split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    maxOccupancy = maxOccupancy.trim().toIntOrNull() ?: 0,
                    geoLocation = geoLocation.trim(),
                    tags = tags
                )
                onUpload(campsite, CampImg)
            },
            enabled = Validation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Upload my listing")
        }
    }

    if (BackConfirmation) {
        AlertDialog(
            onDismissRequest = {
                BackConfirmation = false
            },
            title = {
                Text("Discard Changes?")
            },
            text = {
                Text("Your input will be discarded if you go back. Are you sure?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        BackConfirmation = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        BackConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
