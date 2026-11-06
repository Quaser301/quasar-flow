package com.example.campbnb.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.ArrowDownGlyph
import com.example.campbnb.ui.components.CircleLabelButton
import com.example.campbnb.ui.Screens.CampSearchBar
import com.example.campbnb.ui.Screens.CampsiteCardData
import com.example.campbnb.ui.campsite.RatingDisplay
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.ui.theme.SearchBarBg

private val placeholderResults = List(8) { CampsiteCardData("search$it", "Campsite ${it + 1}") }
private val defaultFilterOptions = listOf("Fire pit", "Lakeside", "Parking", "Pet friendly", "Washrooms")

/**
 * Search page. Reached by submitting a search on Explore, and the same for every role.
 *
 * The hamburger icon opens a menu that switches two optional dropdowns under the search bar:
 *  - **Filter**: tick tags to narrow the results.
 *  - **Search History** (shown as "Most Recent"): pick a past search, newest first, to run it again.
 * Either, both or neither can be showing, which covers all five frames in the design.
 *
 * @param initialQuery Text to start the search bar with.
 * @param results Campsites to list.
 * @param filterOptions Tags offered in the Filter dropdown.
 * @param searchHistory Previous searches, newest first.
 * @param onSearch Called with the query and the ticked filters when either changes or the user submits.
 * @param onCampsiteClick Called when a campsite image is tapped.
 * @param onQrClick Called when the QR Code circle is tapped.
 */
@Composable
fun SearchScreen(
    initialQuery: String = "",
    results: List<CampsiteCardData> = placeholderResults,
    filterOptions: List<String> = defaultFilterOptions,
    searchHistory: List<String> = emptyList(),
    onSearch: (query: String, filters: Set<String>) -> Unit = { _, _ -> },
    onCampsiteClick: (CampsiteCardData) -> Unit = {},
    onQrClick: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(emptySet<String>()) }

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
                onQueryChange = { query = it },
                onSearch = { onSearch(it, filters) },
                modifier = Modifier.weight(1f),
                onMenuClick = { menuOpen = true },
                menuContent = {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showFilter) "Filter  ✓" else "Filter") },
                            onClick = { showFilter = !showFilter; menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (showHistory) "Search History  ✓" else "Search History") },
                            onClick = { showHistory = !showHistory; menuOpen = false }
                        )
                    }
                }
            )
            CircleLabelButton("QR Code", onQrClick)
        }

        if (showFilter || showHistory) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showFilter) {
                    DropdownPill(if (filters.isEmpty()) "Filter" else "Filter (${filters.size})") {
                        filterOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = option in filters, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(option)
                                    }
                                },
                                onClick = {
                                    filters = if (option in filters) filters - option else filters + option
                                    onSearch(query, filters)
                                }
                            )
                        }
                    }
                }
                if (showHistory) {
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
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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