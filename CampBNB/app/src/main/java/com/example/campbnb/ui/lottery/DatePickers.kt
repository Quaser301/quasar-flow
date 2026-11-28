package com.example.campbnb.ui.lottery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.campbnb.models.DateUtils
import com.example.campbnb.models.DateUtils.today
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * UI file andrioid's date range picker modal to be used for the lottery and booking
 * periods. Publically available: https://developer.android.com/develop/ui/compose/components/datepickers
 */

/**
 * A date range picker in a dialog.
 *
 * @param onDateRangeSelected Receives the chosen start and end as UTC-midnight millis. Either
 * can be null if the user confirmed without choosing.
 * @param onDismiss Called when the dialog closes, whether confirmed or cancelled.
 * @param selectableDates Which days can be tapped. Days that fail it are dimmed and can't be
 * selected. Everything is selectable by default; use [DateWindow] to limit it.
 * @param initialDisplayedMonthMillis Month to open on (UTC-midnight millis). Defaults to the
 * current month, so pass the first selectable day when that is far away.
 * @param title Heading shown in the picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    onDateRangeSelected: (Pair<Long?, Long?>) -> Unit,
    onDismiss: () -> Unit,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates,
    initialDisplayedMonthMillis: Long? = null,
    title: String = "Select date range"
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialDisplayedMonthMillis = initialDisplayedMonthMillis,
        selectableDates = selectableDates
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("lottery_date_confirm"),
                onClick = {
                    onDateRangeSelected(
                        Pair(
                            dateRangePickerState.selectedStartDateMillis,
                            dateRangePickerState.selectedEndDateMillis
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(modifier = Modifier.testTag("lottery_date_cancel"), onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = title
                )
            },
            showModeToggle = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(16.dp)
        )
    }
}

/**
 * Limits a date picker to a window of days. Days outside it, or in [excluded], are dimmed
 * and can't be tapped.
 *
 * @param earliest First selectable day (yyyy-MM-dd), or null for no lower limit.
 * @param latest Last selectable day (yyyy-MM-dd), or null for no upper limit.
 * @param excluded Individual days inside the window that are also unselectable.
 */
@OptIn(ExperimentalMaterial3Api::class)
class DateWindow(
    private val earliest: String? = null,
    private val latest: String? = null,
    private val excluded: Set<String> = emptySet()
) : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = fromPickerMillis(utcTimeMillis)
        return (earliest == null || date >= earliest) &&
                (latest == null || date <= latest) &&
                date !in excluded
    }

    override fun isSelectableYear(year: Int): Boolean =
        (earliest == null || year >= earliest.take(4).toInt()) &&
                (latest == null || year <= latest.take(4).toInt())
}

/** Converts a date picker's UTC-midnight millis into yyyy-MM-dd. */
fun fromPickerMillis(utcMillis: Long): String = DateUtils.utcDate(utcMillis)

/** Converts yyyy-MM-dd into the UTC-midnight millis a date picker uses. */
fun toPickerMillis(date: String): Long {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.parse(date)!!.time
}

/** Today's date (device timezone) as the UTC-midnight millis a date picker uses. */
fun todayAsPickerMillis(): Long = toPickerMillis(today())