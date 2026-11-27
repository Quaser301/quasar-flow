package com.example.campbnb.ui.campsite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campbnb.data.ImageRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.ui.shared.StoredImage
import com.example.campbnb.ui.shared.rememberImagePicker

/**
 * Displays the Form for Campsite Registration
 * the campsite registration form includes the Camp Name, location, description, amenities,
 * maximum occupancy, geo location, tags, cancel policy, price, and an optional image
 * (US 02.01.02).
 *
 * The picked image is shrunk and turned into text by ImageRepository straight away, so the
 * Campsite handed to [onUpload] already carries it in campImg; an empty campImg means no
 * image. Removing the image of an existing campsite hands over an empty campImg.
 */
@Composable
fun CampsiteRegistrationForm(
    existingCampsite: Campsite? = null,
    onBack: () -> Unit,
    onUpload: (Campsite) -> Unit
) {
    var CampName by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.campName ?: "")
    }

    var CampLoc by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.campLoc ?: "")
    }

    var CampDesc by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.campDesc ?: "")
    }

    var CampAmenities by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.campAmenities?.joinToString(", ") ?: "")
    }

    var maxOccupancy by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.maxOccupancy?.toString() ?: "")
    }

    var geoLocation by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.geoLocation ?: "")
    }

    var tagInput by remember { mutableStateOf("") }

    var tags by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.tags ?: emptyList<String>())
    }

    // The image as stored text (see ImageRepository); empty means no image.
    var CampImg by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.campImg ?: "")
    }
    var ImageError by remember { mutableStateOf("") }
    var BackConfirmation by remember { mutableStateOf(false) }

    var cancelPolicy by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.cancelPolicy ?: "")
    }

    var CampPrice by remember(existingCampsite) {
        mutableStateOf(existingCampsite?.campPrice?.toString() ?: "")
    }

    val pickImage = rememberImagePicker(
        maxEdge = ImageRepository.CAMPSITE_EDGE,
        onPicked = { text ->
            CampImg = text
            ImageError = ""
        },
        onFailed = {
            ImageError = "That image could not be used. Please pick a different one."
        }
    )

    val Validation =
        CampName.trim().isNotEmpty() &&
                (CampPrice.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true) &&
                cancelPolicy.trim().isNotEmpty() &&
                CampLoc.trim().isNotEmpty() &&
                CampDesc.trim().isNotEmpty() &&
                CampAmenities.trim().isNotEmpty() &&
                (maxOccupancy.toIntOrNull() ?: 0) > 0 &&
                geoLocation.trim().isNotEmpty()


    val Owner_PutInput =
        CampName.isNotBlank() ||
                CampPrice.isNotBlank() ||
                cancelPolicy.isNotBlank() ||
                CampLoc.isNotBlank() ||
                CampDesc.isNotBlank() ||
                CampAmenities.isNotBlank() ||
                maxOccupancy.isNotBlank() ||
                geoLocation.isNotBlank() ||
                tagInput.isNotBlank() ||
                tags.isNotEmpty() ||
                CampImg.isNotEmpty()

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
        Button(
            modifier = Modifier.testTag("edit_back"),
            onClick = {
                if (Owner_PutInput) {
                    BackConfirmation = true
                } else {
                    onBack()
                }
            }
        ) {
            Text("Back")
        }
        Text(
            text = if (existingCampsite == null) {
                "Register a Campsite"
            } else {
                "Edit Campsite"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = CampName,
            onValueChange = { CampName = it },
            label = { Text("Campsite Name") },
            modifier = Modifier.testTag("name_field").fillMaxWidth()
        )
        OutlinedTextField(
            value = CampLoc,
            onValueChange = { CampLoc = it },
            label = { Text("Campsite Location") },
            modifier = Modifier.testTag("location_field").fillMaxWidth()
        )
        OutlinedTextField(
            value = CampDesc,
            onValueChange = { CampDesc = it },
            label = { Text("Campsite Description") },
            modifier = Modifier.testTag("description_field").fillMaxWidth()
        )
        OutlinedTextField(
            value = CampAmenities,
            onValueChange = { CampAmenities = it },
            label = { Text("Campsite Amenities (comma-separated)") },
            modifier = Modifier.testTag("amenities_field").fillMaxWidth()
        )
        OutlinedTextField(
            value = maxOccupancy,
            onValueChange = { maxOccupancy = it },
            label = { Text("Maximum Occupancy") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.testTag("occupancy_field").fillMaxWidth()
        )
        OutlinedTextField(
            value = CampPrice,
            onValueChange = { CampPrice = it },
            label = { Text("Campsite Price") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag("price_field").fillMaxWidth()
        )
        OutlinedTextField(
            value = cancelPolicy,
            onValueChange = { cancelPolicy = it },
            label = { Text("Cancel Policy") },
            modifier = Modifier.testTag("cancel_policy_field").fillMaxWidth()
        )
        OutlinedTextField(
            value = geoLocation,
            onValueChange = { geoLocation = it },
            label = { Text("Geographical location") },
            modifier = Modifier.testTag("geo_field").fillMaxWidth()
        )
        Text(
            text = "Tags",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = tagInput,
            onValueChange = { tagInput = it },
            label = { Text("Enter a tag") },
            modifier = Modifier.testTag("tag_field").fillMaxWidth()
        )
        Button(
            modifier = Modifier.testTag("tag_add").fillMaxWidth(),
            onClick = {
                if (tagInput.isNotBlank()) {
                    tags = tags + tagInput.trim()
                    tagInput = ""
                }
            }
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
                    modifier = Modifier.testTag("tag_remove"),
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
            modifier = Modifier.testTag("image_upload").fillMaxWidth(),
            onClick = {
                pickImage()
            }
        ) {
            Text(
                if (CampImg.isEmpty()) {"Upload Image"}
                else {"Change Image"}
            )
        }
        if (ImageError.isNotEmpty()) {
            Text(
                text = ImageError,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (CampImg.isNotEmpty()) {
            StoredImage(
                stored = CampImg,
                contentDescription = "Campsite image preview",
                modifier = Modifier.testTag("image_preview").fillMaxWidth().height(160.dp),
                contentScale = ContentScale.Crop
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Image Selected")
                TextButton(
                    modifier = Modifier.testTag("image_remove"),
                    onClick = {
                        CampImg = ""
                        ImageError = ""
                    }
                ) {
                    Text("Remove Image")
                }
            }
        }
        Button(
            modifier = Modifier.testTag("save_changes").fillMaxWidth(),
            onClick = {
                val campsite = Campsite(
                    campName = CampName.trim(),
                    campLoc = CampLoc.trim(),
                    campDesc = CampDesc.trim(),
                    campAmenities = CampAmenities.trim().split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    maxOccupancy = maxOccupancy.trim().toIntOrNull() ?: 0,
                    geoLocation = geoLocation.trim(),
                    tags = tags,
                    cancelPolicy = cancelPolicy.trim(),
                    campPrice = CampPrice.toDoubleOrNull() ?: 0.0,
                    ownerId = existingCampsite?.ownerId ?: "",
                    campImg = CampImg,
                    availableStartDate = existingCampsite?.availableStartDate ?: "",
                    availableEndDate = existingCampsite?.availableEndDate ?: "",
                    blockedDates = existingCampsite?.blockedDates ?: emptyList()
                )

                if (existingCampsite != null) {
                    campsite.id = existingCampsite.id
                }
                onUpload(campsite)
            },
            enabled = Validation
        ) {
            Text(
                if (existingCampsite == null) {"Upload my listing"}
                else {"Save Changes"}
            )
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
                    modifier = Modifier.testTag("discard_confirm"),
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
                    modifier = Modifier.testTag("discard_cancel"),
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