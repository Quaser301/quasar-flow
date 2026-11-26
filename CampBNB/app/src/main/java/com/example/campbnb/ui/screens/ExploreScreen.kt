package com.example.campbnb.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.MenuGlyph
import com.example.campbnb.ui.components.SearchGlyph
import com.example.campbnb.ui.theme.ButtonEnd
import com.example.campbnb.ui.theme.ButtonStart
import com.example.campbnb.ui.theme.SearchBarBg
import com.example.campbnb.ui.shared.SearchMenuItemOption
import com.example.campbnb.ui.shared.searchBurgerMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What a campsite card in the Explore grid needs to show.
 *
 * @property imageData The campsite's picture as Base64 text of the image bytes (PNG, JPEG or
 * WebP). Empty means no picture, and the card shows a green placeholder instead.
 */
data class CampsiteCardData(
    val id: String,
    val name: String,
    val location: String = "",
    val maxOccupancy: Int = 0,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val imageData: String = ""
)

private val placeholderCards = List(6) {
    CampsiteCardData("placeholder$it", "(image)")
}

// Sizes for the campsite grid. The grid has two rows, so its height is worked out from these.
private val CardWidth = 168.dp
private val CardHeight = 184.dp
private val PhotoHeight = 96.dp
private val GridSpacing = 12.dp

// Soft greens used by the map placeholder and the Settings circle.
private val SoftGreen = Color(0xFFE3EFD9)
private val MapTop = Color(0xFFEAF3E4)
private val MapBottom = Color(0xFFD5E8C9)

// Rating star colours.
private val StarGold = Color(0xFFFFB400)
private val StarEmpty = Color(0xFFD9D9D9)

/**
 * The Explore page, which is also the home page. It is identical for campers, owners and
 * admins: a search bar and Settings button, a map, and one horizontally scrolling grid of
 * campsite cards (two cards tall) that holds the recommended and the new campsites together.
 *
 * Only the bottom bar around it depends on the user's role (see [HomeScreen]).
 *
 * @param recommended Cards for the recommended campsites.
 * @param newSites Cards for the new campsites. A campsite that is in both lists shows once.
 * @param onCampsiteClick Called when a campsite card is tapped.
 * @param onSettingsClick Called when the Settings circle is tapped.
 * @param onMenuClick Called when the hamburger icon is tapped.
 * @param onFilterClick Called when "Filter" is chosen from the hamburger menu. Opens the
 * Search page with its filter panel showing (US 01.01.02).
 * @param onOpenHistory Called when "Search History" is chosen from the hamburger menu.
 *     Opens the search view with its recent searches showing; the item itself comes
 *     from the shared menu source so both burgers list the same items (fix 11).
 * @param onSearch Called with the query when the user submits a search.
 * @param onOpenQr Called when "QR Scanner" is chosen from the search bar's menu. Opens the
 *     camera scanner, so a search-bar interaction reaches it (US 01.12.01).
 */
@Composable
fun ExploreScreen(

    recommended: List<CampsiteCardData> = placeholderCards,
    newSites: List<CampsiteCardData> = placeholderCards,
    onCampsiteClick: (CampsiteCardData) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenQr: () -> Unit = {},
    onSearch: (String) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    val cards = combineCards(recommended, newSites)

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
                        if (text.isNotBlank()) {
                            onSearch(text)
                        }
                    },

                    modifier = Modifier.weight(1f),

                    onMenuClick = {
                        menuOpen = true
                        onMenuClick()
                    },

                    menuContent = {
                        searchBurgerMenu(
                            expanded = menuOpen,
                            onSelect = { option ->
                                when (option) {
                                    SearchMenuItemOption.FILTER -> onFilterClick()
                                    SearchMenuItemOption.SEARCH_HISTORY -> onOpenHistory()
                                    SearchMenuItemOption.QR_SCANNER -> onOpenQr()
                                }
                            },
                            onDismiss = { menuOpen = false }
                        )
                    }
                )

                SettingsButton(onSettingsClick)
            }
        }
        item { MapPlaceholder() }
        item { CampsiteGrid("Discover campsites", cards, onCampsiteClick) }
    }
}

/**
 * Joins two card lists into one, keeping the first card for any id that appears twice.
 * The grid uses the id as its key, and Compose crashes if two items share a key.
 */
private fun combineCards(
    first: List<CampsiteCardData>,
    second: List<CampsiteCardData>
): List<CampsiteCardData> {
    val combined = mutableListOf<CampsiteCardData>()
    val seenIds = mutableSetOf<String>()
    for (card in first + second) {
        if (seenIds.add(card.id)) {
            combined.add(card)
        }
    }
    return combined
}

/**
 * Pill-shaped search field with a menu icon on the left and a search icon on the right.
 *
 * @param query The current text.
 * @param onQueryChange Called as the user types.
 * @param onSubmitSearch Called with the query when the user submits it.
 * @param onMagnifyClick Called with the query when the search icon is tapped.
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
                Box(Modifier.testTag("search_menu_open").clickable(onClick = onMenuClick).padding(8.dp)) { MenuGlyph() }
                menuContent()
            }
        },
        trailingIcon = {
            Box(
                Modifier
                    .testTag("search_submit")
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
        // one shared search-bar affordance: Explore and the search view both drive
        // this renderer, one name finds either field.
        modifier = modifier.testTag("search_field")
    )
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SoftGreen)
            .testTag("settings_open")
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("Settings", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = ButtonEnd)
    }
}

/**
 * Stand-in for the map: a soft green panel with faint grid lines and a pin.
 * Swap for Google Maps Compose when you're ready.
 */
@Composable
private fun MapPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(MapTop, MapBottom))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 36.dp.toPx()
            val lineColor = Color.White.copy(alpha = 0.55f)
            val lineWidth = 2.dp.toPx()

            var x = step
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = lineWidth)
                x = x + step
            }

            var y = step
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = lineWidth)
                y = y + step
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ButtonEnd),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Map", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ButtonEnd)
        }
    }
}

/**
 * A titled grid of campsite cards that scrolls sideways. The grid is two cards tall, so the
 * cards fill the screen width in columns of two instead of one long row. With no campsites
 * it shows a short message instead of an empty grid.
 */
@Composable
private fun CampsiteGrid(
    title: String,
    campsites: List<CampsiteCardData>,
    onClick: (CampsiteCardData) -> Unit
) {
    Column {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
        )

        if (campsites.isEmpty()) {
            Text(
                "No campsites to show yet",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            return
        }

        // A grid inside a LazyColumn needs a fixed height: two cards, the gap between them,
        // and 4.dp of padding above and below so the card shadows are not cut off.
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(CardHeight * 2 + GridSpacing + 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing)
        ) {
            items(campsites, key = { it.id }) { campsite ->
                CampsiteCard(campsite, onClick)
            }
        }
    }
}

/** One campsite: its picture on top, then the name, the star rating and the location. */
@Composable
private fun CampsiteCard(campsite: CampsiteCardData, onClick: (CampsiteCardData) -> Unit) {
    Card(
        modifier = Modifier
            .width(CardWidth)
            .height(CardHeight),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // the shared card affordance: Explore and the search view's
                // results render the same campsite-pick, one name finds either.
                .testTag("campsite_card")
                .clickable { onClick(campsite) }
        ) {
            CampsitePhoto(campsite, Modifier.fillMaxWidth().height(PhotoHeight))

            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = campsite.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RatingLine(campsite.rating, campsite.ratingCount)
                Text(
                    text = campsite.location,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The campsite's picture, with how many guests it holds in a small pill over the bottom corner.
 * A green gradient with the campsite's first letter sits underneath, so campsites without a
 * picture (or with one that is still decoding or can't be decoded) still look intentional.
 */
@Composable
private fun CampsitePhoto(campsite: CampsiteCardData, modifier: Modifier = Modifier) {
    val picture = rememberCampsiteImage(campsite.imageData)

    Box(
        modifier.background(Brush.linearGradient(listOf(ButtonStart, ButtonEnd))),
        contentAlignment = Alignment.Center
    ) {
        if (picture == null) {
            Text(
                text = campsite.name.take(1).uppercase(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f)
            )
        } else {
            Image(
                bitmap = picture,
                contentDescription = campsite.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (campsite.maxOccupancy > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Up to ${campsite.maxOccupancy} guests",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * The decoded picture, or null while it decodes (or if it can't be decoded).
 * Decoding happens off the main thread so scrolling stays smooth.
 */
@Composable
private fun rememberCampsiteImage(stored: String): ImageBitmap? {
    var image by remember(stored) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(stored) {
        if (stored.isBlank()) {
            image = null
        } else {
            image = withContext(Dispatchers.Default) {
                decodeCampsiteImage(stored)
            }
        }
    }

    return image
}

/**
 * Turns stored Base64 text into a bitmap, shrunk so a small card doesn't hold a huge image.
 * Returns null if the text isn't a valid image.
 */
private fun decodeCampsiteImage(stored: String): ImageBitmap? {
    try {
        // Accept both plain Base64 and "data:image/png;base64,..." text.
        var text = stored
        val comma = text.indexOf(",")
        if (text.startsWith("data:") && comma >= 0) {
            text = text.substring(comma + 1)
        }

        val bytes = Base64.decode(text, Base64.DEFAULT)

        // First pass reads only the picture's size, so we can pick how much to shrink it.
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= 600) {
            sampleSize = sampleSize * 2
        }

        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (bitmap == null) {
            return null
        }
        return bitmap.asImageBitmap()
    } catch (e: IllegalArgumentException) {
        return null   // the text was not valid Base64
    }
}

/** Five stars (gold up to the rounded average), the average and the count. Nothing if unrated. */
@Composable
private fun RatingLine(rating: Double, ratingCount: Int) {
    if (ratingCount == 0) {
        return
    }

    val filledStars = rating.roundToInt()

    Row(verticalAlignment = Alignment.CenterVertically) {
        for (position in 1..5) {
            var starColor = StarEmpty
            if (position <= filledStars) {
                starColor = StarGold
            }
            Text("★", fontSize = 14.sp, color = starColor)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            String.format(Locale.US, "%.1f", rating),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(" ($ratingCount)", fontSize = 12.sp, color = Color.Gray)
    }
}