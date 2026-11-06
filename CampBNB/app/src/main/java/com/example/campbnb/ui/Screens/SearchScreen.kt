package com.example.campbnb.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.lottery.fromPickerMillis
import com.example.campbnb.ui.lottery.todayAsPickerMillis
import com.example.campbnb.models.SearchFilters
import com.example.campbnb.ui.components.ArrowDownGlyph
import com.example.campbnb.ui.components.CircleLabelButton
import com.example.campbnb.ui.Screens.CampSearchBar
import com.example.campbnb.ui.Screens.CampsiteCardData
import com.example.campbnb.ui.campsite.RatingDisplay
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.ui.theme.SearchBarBg

private val placeholderResults = List(8) { CampsiteCardData("search$it", "Campsite ${it + 1}") }

/**
 * Search page. Reached by submitting a search on Explore, or by choosing Filter from the
 * Explore hamburger menu. It is the same for every role.
 *
 * The hamburger icon opens a menu that switches two optional sections under the search bar:
 *  - **Filter**: narrow the results by location, dates and number of guests (US 01.01.02).
 *    The list updates as each filter changes (US 01.01.03).
 *  - **Search History** (shown as "Most Recent"): pick a past search, newest first, to run it again.
 * Either, both or neither can be showing, which covers all five frames in the design.
 *
 * @param initialQuery Text to start the search bar with.
 * @param initialShowFilter True to start with the filter panel showing.
 * @param results Campsites to list.
 * @param searchHistory Previous searches, newest first.
 * @param onSearch Called with the query and the filters when either changes or the user submits.
 * @param onCampsiteClick Called when a campsite image is tapped.
 * @param onQrClick Called when the QR Code circle is tapped.
 */
@Composable
fun SearchScreen(
    initialQuery: String = "",
    initialShowFilter: Boolean = false,
    results: List<CampsiteCardData> = placeholderResults,
    searchHistory: List<String> = emptyList(),
    onSearch: (query: String, filters: SearchFilters) -> Unit = { _, _ -> },
    onCampsiteClick: (CampsiteCardData) -> Unit = {},
    onQrClick: () -> Unit = {},
    onClose: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchMode by rememberSaveable {
        mutableStateOf(initialQuery.isNotBlank())
    }
    var showFilter by rememberSaveable { mutableStateOf(initialShowFilter) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(SearchFilters()) }

    val searchEnabled =
        query.isNotBlank() ||
                showFilter ||
                showHistory ||
                searchMode
    LaunchedEffect(searchEnabled) {

        if (!searchEnabled) {
            // leave SearchScreen and return to Explore
            // this should call a callback supplied by NavHost
            onClose()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CampSearchBar(
                query = query,
                onQueryChange = {
                    query = it

                    if (it.isBlank()) {
                        searchMode = false
                    }
                },

                onSubmitSearch = { text ->

                    if (text.isNotBlank()) {
                        searchMode = true
                        onSearch(text, filters)
                    }
                },

                onMagnifyClick = { text ->

                    if (
                        text.isBlank() &&
                        !showFilter &&
                        !showHistory &&
                        searchMode
                    ) {
                        searchMode = false
                    } else {
                        searchMode = true
                        onSearch(text, filters)
                    }
                },

                modifier = Modifier.weight(1f),

                onMenuClick = {
                    menuOpen = true
                },

                menuContent = {
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (showFilter) "Filter ✓" else "Filter")
                            },
                            onClick = {
                                showFilter = !showFilter
                                menuOpen = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (showHistory)
                                        "Search History ✓"
                                    else
                                        "Search History"
                                )
                            },
                            onClick = {
                                showHistory = !showHistory
                                menuOpen = false
                            }
                        )
                    }
                }
            )

            CircleLabelButton("QR Code", onQrClick)
        }

        if (showFilter) {
            FilterPanel(
                filters = filters,
                onChange = { newFilters ->
                    filters = newFilters
                    onSearch(query, newFilters)
                }
            )
        }

        if (showHistory) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                DropdownPill("Most Recent") { close ->
                    if (searchHistory.isEmpty()) {
                        DropdownMenuItem(text = { Text("No recent searches") }, onClick = close, enabled = false)
                    } else {
                        searchHistory.forEach { past ->
                            DropdownMenuItem(
                                text = { Text(past) },
                                onClick = { query = past; onSearch(past, filters); close() }
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (results.isEmpty()) {
                item {
                    Text("No campsites match your search", fontSize = 14.sp, color = Color.DarkGray)
                }
            }
            items(results, key = { it.id }) { campsite ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FieldGrey)
                        .clickable { onCampsiteClick(campsite) },
                    contentAlignment = Alignment.Center
                ) {     // show campsite summary
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(campsite.name, fontSize = 18.sp, color = Color.Black)
                        Text(campsite.location, fontSize = 13.sp, color = Color.Black)
                        Text("Up to " + campsite.maxOccupancy + " guests", fontSize = 13.sp, color = Color.Black)
                        RatingDisplay(campsite.rating, campsite.ratingCount)
                    }
                }
            }
        }
    }
}

/**
 * The filter section: a location box, a dates button and a guests box (US 01.01.02).
 * Every change is reported straight away through [onChange] so the list can update.
 *
 * @param filters The filters as they are now.
 * @param onChange Called with the new filters whenever one changes.
 */
@Composable
private fun FilterPanel(
    filters: SearchFilters,
    onChange: (SearchFilters) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = filters.location,
            onValueChange = { text -> onChange(filters.copy(location = text)) },
            placeholder = { Text("Location", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = filterFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(SearchBarBg)
                .clickable { showDatePicker = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dateLabelFor(filters),
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            ArrowDownGlyph()
        }

        TextField(
            value = filters.guests?.toString() ?: "",
            onValueChange = { text -> onChange(filters.copy(guests = text.toIntOrNull())) },
            placeholder = { Text("Number of guests", fontSize = 14.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(50),
            colors = filterFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        if (filters.activeCount() > 0) {
            TextButton(onClick = { onChange(SearchFilters()) }) {
                Text("Clear filters")
            }
        }
    }

    if (showDatePicker) {
        DateRangeDialog(
            onConfirm = { start, end ->
                showDatePicker = false
                onChange(filters.copy(startDate = start, endDate = end))
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

/** The text on the dates button: "Any dates", one date, or "start to end". */
private fun dateLabelFor(filters: SearchFilters): String {
    if (filters.startDate.isEmpty()) {
        return "Any dates"
    }
    if (filters.endDate.isEmpty() || filters.endDate == filters.startDate) {
        return filters.startDate
    }
    return filters.startDate + " to " + filters.endDate
}

/** Colours for the pill-shaped filter boxes, matching the search bar. */
@Composable
private fun filterFieldColors(): TextFieldColors {
    return TextFieldDefaults.colors(
        focusedContainerColor = SearchBarBg,
        unfocusedContainerColor = SearchBarBg,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black
    )
}

/** Lets the date picker select today and later, but not past days. */
@OptIn(ExperimentalMaterial3Api::class)
private object FutureDatesOnly : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        return utcTimeMillis >= todayAsPickerMillis()
    }
}

/**
 * A dialog for picking a range of dates. Picking only one day means just that day.
 *
 * @param onConfirm Called with the first and last date as yyyy-MM-dd.
 * @param onDismiss Called when the dialog is cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    onConfirm: (startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDateRangePickerState(selectableDates = FutureDatesOnly)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val startMillis = pickerState.selectedStartDateMillis
                    val endMillis = pickerState.selectedEndDateMillis
                    if (startMillis != null) {
                        val start = fromPickerMillis(startMillis)
                        var end = start
                        if (endMillis != null) {
                            end = fromPickerMillis(endMillis)
                        }
                        onConfirm(start, end)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.height(500.dp)
        )
    }
}

/** Lavender pill with a down arrow that opens a dropdown menu holding [content]. */
@Composable
private fun DropdownPill(
    label: String,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val close = { expanded = false }

    Box {
        Row(
            Modifier
                .widthIn(min = 150.dp)
                .clip(RoundedCornerShape(50))
                .background(SearchBarBg)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            ArrowDownGlyph()
        }
        DropdownMenu(expanded = expanded, onDismissRequest = close) {
            this.content(close)
        }
    }
}