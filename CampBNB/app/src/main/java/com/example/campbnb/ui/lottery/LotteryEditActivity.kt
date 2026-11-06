package com.example.campbnb.ui.lottery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.ViewObserver
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.ui.shared.DeviceId
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for editing/adding a lottery.
 * 03-11-26: Prompted Gemini 3.6 Flash for how to create and format
 * an interactable dropdown menu to be used for selecting available campsites.
 * 03-11-26: Prompted Gemini 3.6 Flash for how to integrate date range picker file
 * into this activity for the booking and registration fields.
 * 04-11-26: Used built in gemini flash 3.6 to add a few personal comments and kdoc comments
 * 05-11-26: Prompted gemini 3.6 flash to structure my save button functionality which creates a lottery
 * object based on the input fields and then saves it to firebase.
 * 06-11-26: Prompted gemini 3.6 to modify the campsite dropdown into a checkbox(toggle) dropdown
 * to allow for multiple campsite selection.
 * 06-11-26: Prompted gemini 3.6 to fill out kdocs
 *
 * @property returnHome Callback function to navigate back to the home screen.
 * @property navigateBackwards Callback function to navigate backwards in the navigation stack.
 * @property appViewSettings Current view settings used for window verification and access checks.
 */
// Note: injecting temporary comments cause I need to know what I'm doing.
// remove once actual docs are available.
// also: why is this a class???? - W
class LotteryEditActivity(
    val returnHome: () -> Unit,
    val navigateBackwards: () -> Unit,
    val appViewSettings: AppViewSetting

) : ViewObserver<LotteryModel> {

    /**
     * Updates the UI observer when [model] changes. Handled via Compose state recomposition.
     *
     * @param model The updated [LotteryModel] instance.
     */
    override fun update(model: LotteryModel) {
    }

    /**
     * Renders the form for creating or updating a campsite lottery.
     *
     * Allows owners to select multiple campsites, specify winner numbers and waitlist capacity,
     * and pick date ranges for registration and booking windows.
     *
     * @param model The lottery state model shown by views.
     * @param controller The controller managing lottery database actions.
     * @param initialLottery Optional existing [Lottery] instance to edit; null if creating a new lottery.
     * @param ownerId Unique identifier of the campsite owner creating or editing the lottery.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    // Editing Lotteries
    @Composable
    fun LotteryEdit(
        model: LotteryModel,
        controller: LotteryController,
        initialLottery: Lottery? = null,
        ownerId: String = ""
    ) {
        AppViewModificationIsValidForCurrentWindowChecker(
            returnHome,
            appViewSettings,
            listOf(
                AppView.EntrantView,
                // could remove, but afaik admins shouldn't see the lottery unless posing
                // as an Owner.
                AppView.AdminView
            )
        )

        var selectedCampsites by remember { mutableStateOf<Set<Campsite>>(emptySet()) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        var numberOfWinners by remember { mutableStateOf("") }
        var waitlistCapacity by remember { mutableStateOf("") }

        // Date selection state
        var regStartMillis by remember { mutableStateOf<Long?>(null) }
        var regEndMillis by remember { mutableStateOf<Long?>(null) }
        var showRegDatePicker by remember { mutableStateOf(false) }

        var bookingStartMillis by remember { mutableStateOf<Long?>(null) }
        var bookingEndMillis by remember { mutableStateOf<Long?>(null) }
        var showBookingDatePicker by remember { mutableStateOf(false) }

        var ownerCampsitesList by remember { mutableStateOf<List<Campsite>>(emptyList()) }

        LaunchedEffect(initialLottery) {
            if (initialLottery != null) {
                numberOfWinners = initialLottery.numberOfWinners.toString()
                waitlistCapacity = initialLottery.waitlistCapacity?.toString() ?: ""
                regStartMillis = initialLottery.registrationStart
                regEndMillis = initialLottery.registrationEnd
                bookingStartMillis = initialLottery.bookingStart
                bookingEndMillis = initialLottery.bookingEnd
            }
        }

        val context = LocalContext.current
        val resolvedOwnerId = ownerId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: DeviceId.get(context) }

        val lotteryToSave = Lottery(
            ownerId = resolvedOwnerId,
            campsiteIds = selectedCampsites.map { it.id },
            numberOfWinners = numberOfWinners.toIntOrNull() ?: 0,
            waitlistCapacity = waitlistCapacity.toIntOrNull(), // null if blank
            registrationStart = regStartMillis ?: 0L,
            registrationEnd = regEndMillis ?: 0L,
            bookingStart = bookingStartMillis ?: 0L,
            bookingEnd = bookingEndMillis ?: 0L
        ).apply {
            if (initialLottery != null && initialLottery.id.isNotBlank()) {
                id = initialLottery.id
            }
        }

        LaunchedEffect(resolvedOwnerId) {
            if (resolvedOwnerId.isNotBlank()) {
                controller.loadOwnerCampsites(resolvedOwnerId) { fetchedCampsites ->
                    ownerCampsitesList = fetchedCampsites
                    if (initialLottery != null && selectedCampsites.isEmpty()) {
                        selectedCampsites = fetchedCampsites.filter { it.id in initialLottery.campsiteIds }.toSet()
                    }
                }
            }
        }

        fun formatDateRange(start: Long?, end: Long?): String {
            if (start == null || end == null) return ""
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return "${formatter.format(Date(start))} - ${formatter.format(Date(end))}"
        }

        val isEditMode = initialLottery != null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { navigateBackwards() },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(text = "Back")
                }
                Text(
                    text = if (isEditMode) "Edit Lottery" else "Create Lottery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Multiple Campsites Dropdown Selection (Checkbox Dropdown)
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (selectedCampsites.isEmpty()) "" else selectedCampsites.joinToString(", ") { it.campName },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pick Campsites") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    if (ownerCampsitesList.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No campsites available") },
                            onClick = { dropdownExpanded = false }
                        )
                    } else {
                        ownerCampsitesList.forEach { campsite ->
                            val isSelected = campsite in selectedCampsites
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null
                                        )
                                        Column {
                                            Text(
                                                text = campsite.campName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (campsite.campLoc.isNotBlank()) {
                                                Text(
                                                    text = campsite.campLoc,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    selectedCampsites = if (isSelected) {
                                        selectedCampsites - campsite
                                    } else {
                                        selectedCampsites + campsite
                                    }
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = numberOfWinners,
                onValueChange = { numberOfWinners = it },
                label = { Text("Number of Winners") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = waitlistCapacity,
                onValueChange = { waitlistCapacity = it },
                label = { Text("Waitlist Capacity (leave blank for unlimited)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Registration Date Range Picker
            OutlinedTextField(
                value = formatDateRange(regStartMillis, regEndMillis),
                onValueChange = {},
                readOnly = true,
                label = { Text("Registration Period") },
                trailingIcon = {
                    TextButton(onClick = { showRegDatePicker = true }) {
                        Text("Select")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showRegDatePicker = true }
            )

            // Booking Date Range Picker
            OutlinedTextField(
                value = formatDateRange(bookingStartMillis, bookingEndMillis),
                onValueChange = {},
                readOnly = true,
                label = { Text("Booking Period") },
                trailingIcon = {
                    TextButton(onClick = { showBookingDatePicker = true }) {
                        Text("Select")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showBookingDatePicker = true }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { navigateBackwards() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    enabled = lotteryToSave.isValid(System.currentTimeMillis()),
                    onClick = {

                        //Call controller to save to Firestore
                        controller.saveLottery(
                            lottery = lotteryToSave,
                            onSuccess = {
                                navigateBackwards()
                            },
                            onFailure = { exception ->
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isEditMode) "Update" else "Save")
                }
            }
        }

        // Modals for Date Range Pickers
        if (showRegDatePicker) {
            DateRangePickerModal(
                onDateRangeSelected = { (start, end) ->
                    regStartMillis = start
                    regEndMillis = end
                },
                onDismiss = { showRegDatePicker = false }
            )
        }

        if (showBookingDatePicker) {
            DateRangePickerModal(
                onDateRangeSelected = { (start, end) ->
                    bookingStartMillis = start
                    bookingEndMillis = end
                },
                onDismiss = { showBookingDatePicker = false }
            )
        }
    }
}
