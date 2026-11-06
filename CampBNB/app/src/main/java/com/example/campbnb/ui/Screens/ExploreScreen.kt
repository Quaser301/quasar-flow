package com.example.campbnb.ui.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.CircleLabelButton
import com.example.campbnb.ui.components.MenuGlyph
import com.example.campbnb.ui.components.SearchGlyph
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.ui.theme.SearchBarBg

/** What a campsite card in a horizontal row needs to show. Add an image URL later. */
data class CampsiteCardData(
    val id: String,
    val name: String,
    val location: String = "",
    val maxOccupancy: Int = 0,
    val rating: Double = 0.0,
    val ratingCount: Int = 0
)

private val placeholderCards = List(6) {
    CampsiteCardData("placeholder$it", "(image)")
}
/**
 * The Explore page, which is also the home page. It is identical for campers, owners and
 * admins: a search bar and Settings button, a map, and "Recommended" and "New Sites" rows.
 *
 * Only the bottom bar around it depends on the user's role (see [HomeScreen]).
 *
 * @param recommended Cards for the "Recommended" row.
 * @param newSites Cards for the "New Sites" row.
 * @param onCampsiteClick Called when a campsite card is tapped.
 * @param onSettingsClick Called when the Settings circle is tapped.
 * @param onMenuClick Called when the hamburger icon is tapped (open a drawer here later).
 * @param onSearch Called with the query when the user submits a search.
 */
@Composable
fun ExploreScreen(

    recommended: List<CampsiteCardData> = placeholderCards,
    newSites: List<CampsiteCardData> = placeholderCards,
    onCampsiteClick: (CampsiteCardData) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onSearch: (String) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }   // import androidx.compose.runtime.saveable.rememberSaveable

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CampSearchBar(
                    query = query,
                    onQueryChange = { query = it },

                    onSubmitSearch = { text ->
                        if (text.isNotBlank()) {
                            onSearch(text)
                        }
                    },

                    onMagnifyClick = { text ->
                        onSearch(text)
                    },

                    modifier = Modifier.weight(1f),
                    onMenuClick = onMenuClick
                )

                SettingsButton(onSettingsClick)
            }
        }
        item { MapPlaceholder() }
        item { CampsiteRow("Recommended", recommended, onCampsiteClick) }
        item { CampsiteRow("New Sites", newSites, onCampsiteClick) }
    }
}

/**
 * Pill-shaped search field with a menu icon on the left and a search icon on the right.
 *
 * @param query The current text.
 * @param onQueryChange Called as the user types.
 * @param onSearch Called with the query when the user submits it.
 * @param onMenuClick Called when the hamburger icon is tapped.
 * @param menuContent A dropdown menu anchored to the hamburger icon. Empty by default.
 */
@Composable
fun CampSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onMagnifyClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    menuContent: @Composable () -> Unit = {}
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search for campsites...", fontSize = 14.sp) },
        leadingIcon = {
            Box {
                Box(Modifier.clickable(onClick = onMenuClick).padding(8.dp)) { MenuGlyph() }
                menuContent()
            }
        },
        trailingIcon = {
            Box(
                Modifier
                    .clickable {
                        onMagnifyClick(query)
                    }
                    .padding(8.dp)
            ) {
                SearchGlyph()
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSubmitSearch(query)
            }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SearchBarBg,
            unfocusedContainerColor = SearchBarBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black
        ),
        modifier = modifier
    )
}

/** Round settings button.
 * @param onClick called when the button is tapped */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(FieldGrey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("Settings", fontSize = 10.sp, color = Color.Black)
    }
}

/** Stand-in for the map. Swap for Google Maps Compose when you're ready. */
@Composable
private fun MapPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(230.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(FieldGrey),
        contentAlignment = Alignment.Center
    ) {
        Text("Map", fontSize = 28.sp, color = Color.Black)
    }
}

/** A titled, horizontally scrolling row of campsite cards. */
@Composable
private fun CampsiteRow(
    title: String,
    items: List<CampsiteCardData>,
    onClick: (CampsiteCardData) -> Unit
) {
    Column {
        Text(
            title,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { campsite ->
                Box(
                    Modifier
                        .size(width = 180.dp, height = 110.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FieldGrey)
                        .clickable { onClick(campsite) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = campsite.name,
                            fontSize = 16.sp,
                            color = Color.Black
                        )

                        Text(
                            text = campsite.location,
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Text(
                            text = "Up to ${campsite.maxOccupancy} guests",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}