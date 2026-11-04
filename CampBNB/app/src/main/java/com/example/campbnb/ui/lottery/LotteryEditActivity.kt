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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.ViewObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for editing/adding a lottery.
 * 03-11-26: Prompted Gemini 3.6 Flash for how to create and format
 * an interactable dropdown menu to be used for selecting available campsites.
 * 03-11-26: Prompted Gemini 3.6 Flash for how to integrate date range picker file
 * into this activity for the booking and registration fields.
 */

class LotteryEditActivity(private val navController: NavController) : ViewObserver<LotteryModel> {
    override fun update(model: LotteryModel) {
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LotteryEdit(
        model: LotteryModel,
        controller: LotteryController,
        ownerCampsites: List<Campsite> = emptyList()
    ) {
        var selectedCampsite by remember { mutableStateOf<Campsite?>(null) }
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

        fun formatDateRange(start: Long?, end: Long?): String {
            if (start == null || end == null) return ""
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return "${formatter.format(Date(start))} - ${formatter.format(Date(end))}"
        }

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
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(text = "Back")
                }
                Text(
                    text = "Lottery Form",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Campsite Dropdown Selection
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCampsite?.campName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pick Campsite") },
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
                    if (ownerCampsites.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No campsites available") },
                            onClick = { dropdownExpanded = false }
                        )
                    } else {
                        ownerCampsites.forEach { campsite ->
                            DropdownMenuItem(
                                text = {
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
                                },
                                onClick = {
                                    selectedCampsite = campsite
                                    dropdownExpanded = false
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
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        navController.popBackStack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
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
