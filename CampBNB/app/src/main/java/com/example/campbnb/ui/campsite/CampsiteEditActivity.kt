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
import com.example.campbnb.models.Campsite
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting

/**
 * Displays the Form for Campsite Registration
 */
@Composable
fun CampsiteRegistrationForm(
    onBack: () -> Unit,
    onUpload: (Campsite, Uri?) -> Unit,
    appViewSettings: AppViewSetting
) {
    AppViewModificationIsValidForCurrentWindowChecker(
        onBack,
        appViewSettings,
        listOf(AppView.EntrantView, AppView.AdminView)
    )

    var campName by remember { mutableStateOf("") }
    var campLoc by remember { mutableStateOf("") }
    var campDesc by remember { mutableStateOf("") }
    var campAmenities by remember { mutableStateOf("") }
    var maxOccupancy by remember { mutableStateOf("") }
    var geoLocation by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var campImg by remember { mutableStateOf<Uri?>(null) }
    var backConfirmation by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                campImg = uri
            }
        }
    )

    val Validation =
        campName.trim().isNotEmpty() &&
                campLoc.trim().isNotEmpty() &&
                campDesc.trim().isNotEmpty() &&
                campAmenities.trim().isNotEmpty() &&
                (maxOccupancy.toIntOrNull() ?: 0) > 0 &&
                geoLocation.trim().isNotEmpty()

    val Owner_PutInput =
        campName.isNotBlank() ||
                campLoc.isNotBlank() ||
                campDesc.isNotBlank() ||
                campAmenities.isNotBlank() ||
                maxOccupancy.isNotBlank() ||
                geoLocation.isNotBlank() ||
                tagInput.isNotBlank() ||
                tags.isNotEmpty() ||
                campImg != null

    BackHandler {
        if (Owner_PutInput) {
            backConfirmation = true
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
            value = campName,
            onValueChange = { campName = it },
            label = { Text("Campsite Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = campLoc,
            onValueChange = { campLoc = it },
            label = { Text("Campsite Location") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = campDesc,
            onValueChange = { campDesc = it },
            label = { Text("Campsite Description") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = campAmenities,
            onValueChange = { campAmenities = it },
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
        if (campImg != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Image Selected")
                TextButton(
                    onClick = {
                        campImg = null
                    }
                ) {
                    Text("Remove Image")
                }
            }
        }
        Button(
            onClick = {
                val campsite = Campsite(
                    campName = campName.trim(),
                    campLoc = campLoc.trim(),
                    campDesc = campDesc.trim(),
                    campAmenities = campAmenities.trim().split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    maxOccupancy = maxOccupancy.trim().toIntOrNull() ?: 0,
                    geoLocation = geoLocation.trim(),
                    tags = tags
                )
                onUpload(campsite, campImg)
            },
            enabled = Validation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Upload my listing")
        }
    }

    if (backConfirmation) {
        AlertDialog(
            onDismissRequest = {
                backConfirmation = false
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
                        backConfirmation = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        backConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
