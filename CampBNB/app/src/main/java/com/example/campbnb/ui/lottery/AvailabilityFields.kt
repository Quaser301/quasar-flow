package com.example.campbnb.ui.lottery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.campbnb.models.DateUtils

/**
 * The "Available Dates" box on the create lottery screen: the range of dates winners can
 * choose their stay from. Days that are in the past, or before or inside the registration
 * and booking periods, are dimmed in the picker and can't be tapped.
 *
 * @param start First available date (yyyy-MM-dd), or blank if not set.
 * @param end Last available date (yyyy-MM-dd), or blank if not set.
 * @param earliestSelectable First day the picker allows (yyyy-MM-dd).
 * @param enabled False until the booking period is set, since the valid days depend on it.
 * @param errorText A problem with the current range, shown under the box, or null.
 * @param onChange Called with the new start and end when the owner confirms a range.
 */
@Composable
fun AvailableDatesField(
    start: String,
    end: String,
    earliestSelectable: String,
    enabled: Boolean,
    errorText: String?,
    onChange: (start: String, end: String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = if (start.isBlank() || end.isBlank()) "" else "${DateUtils.pretty(start)} - ${DateUtils.pretty(end)}",
        onValueChange = {},
        readOnly = true,
        label = { Text("Available Dates") },
        isError = errorText != null,
        supportingText = {
            Text(
                errorText ?: if (enabled) {
                    "The dates winners can choose their stay from"
                } else {
                    "Set the booking period first"
                }
            )
        },
        trailingIcon = {
            TextButton(
                enabled = enabled,
                modifier = Modifier.testTag("available_dates_open"),
                onClick = { showPicker = true }
            ) {
                Text("Select")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("available_dates_field")
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showPicker = true }
    )

    if (showPicker) {
        DateRangePickerModal(
            title = "Select available dates",
            selectableDates = DateWindow(earliest = earliestSelectable),
            initialDisplayedMonthMillis = toPickerMillis(earliestSelectable),
            onDateRangeSelected = { (pickedStart, pickedEnd) ->
                if (pickedStart != null) {
                    onChange(fromPickerMillis(pickedStart), fromPickerMillis(pickedEnd ?: pickedStart))
                }
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * The "Blocked Dates" box on the edit lottery screen: lets the owner close individual dates
 * inside the available range to winners. Only days inside the range that haven't passed and
 * aren't already blocked can be tapped in the picker.
 *
 * @param availableStart First available date (yyyy-MM-dd).
 * @param availableEnd Last available date (yyyy-MM-dd).
 * @param blocked The dates currently blocked.
 * @param onChange Called with the new set of blocked dates.
 */
@Composable
fun BlockedDatesField(
    availableStart: String,
    availableEnd: String,
    blocked: Set<String>,
    onChange: (Set<String>) -> Unit,
    campsiteName: String = ""
) {
    var showPicker by remember { mutableStateOf(false) }

    val earliest = maxOf(DateUtils.today(), availableStart)
    val hasRoomToBlock = earliest <= availableEnd

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (campsiteName.isBlank()) {
                "Blocked Dates"
            } else {
                "Blocked Dates — $campsiteName"
            },
            style = MaterialTheme.typography.titleSmall
        )

        val ranges = DateUtils.groupIntoRanges(blocked)
        if (ranges.isEmpty()) {
            Text(text = "No dates blocked", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        ranges.forEach { (from, to) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (from == to) DateUtils.pretty(from) else "${DateUtils.pretty(from)} - ${DateUtils.pretty(to)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    modifier = Modifier.testTag("unblock_dates"),
                    onClick = { onChange(blocked - DateUtils.datesBetween(from, to).toSet()) }
                ) {
                    Text("Unblock")
                }
            }
        }

        OutlinedButton(
            enabled = hasRoomToBlock,
            modifier = Modifier.testTag("block_dates_open"),
            onClick = { showPicker = true }
        ) {
            Text(if (hasRoomToBlock) "Block Dates" else "No dates left to block")
        }
    }

    if (showPicker) {
        DateRangePickerModal(
            title = "Select dates to block",
            selectableDates = DateWindow(
                earliest = earliest,
                latest = availableEnd,
                excluded = blocked
            ),
            initialDisplayedMonthMillis = toPickerMillis(earliest),
            onDateRangeSelected = { (pickedStart, pickedEnd) ->
                if (pickedStart != null) {
                    val picked = DateUtils
                        .datesBetween(
                            fromPickerMillis(pickedStart),
                            fromPickerMillis(pickedEnd ?: pickedStart)
                        )
                        .filter {
                            it >= earliest && it <= availableEnd && it !in blocked
                        }
                        .toSet()

                    onChange(blocked + picked)
                }
            },
            onDismiss = { showPicker = false }
        )
    }
}