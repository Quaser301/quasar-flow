package com.example.campbnb.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.Screen
import com.example.campbnb.ui.components.EyeGlyph
import com.example.campbnb.ui.components.GradientBarButton
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.components.StarGlyph
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.RoleBottomBar
import com.example.campbnb.ui.screens.tabsFor
import com.example.campbnb.ui.shared.StoredImage
import com.example.campbnb.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.ui.admin.ConfirmRemovalDialog
import com.example.campbnb.ui.admin.AdminImageRemoval

// Prompted Claude Sonnet 5.5 for some implementations, especially related to image deletion
// functionality for admins

/** Everything the details screen shows about one campsite. */
data class CampsiteDetails(
    val id: String,
    val name: String,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val tags: List<String> = emptyList(),
    val capacityGuests: Int = 0,
    val location: String = "",
    val description: String = "",
    val imageUrl: String = ""
)

/** Stand-in data until a controller supplies real campsite details. */
fun placeholderDetails(id: String) = CampsiteDetails(
    id = id,
    name = "Campsite 1",
    rating = 5.0,
    reviewCount = 0,
    tags = listOf("Fire pit", "Lakeside", "Parking", "Pet friendly", "Washrooms"),
    capacityGuests = 6,
    location = "123 Nowhereville",
    description = "Lorem ipsum dansum wopan gangnam style hello my name is its actually " +
            "couture doe at dat mean merchant. But if he said would yes would go. I would actually like to."
)

// Soft green used behind the info cards, the tag chips and the rating box.
private val CardTint = Color(0xFFF2F7EE)
private val BodyGrey = Color(0xFF4A4A4A)

/** How many tags the page shows before "View all". */
private const val TAGS_SHOWN = 3

/**
 * Details page for one campsite, opened from an image in Explore or Search.
 *
 * The layout is the same for every role: a green header with the campsite's name, its rating,
 * capacity and location cards, the tags and the description. Campers also get a card with the
 * rating and review boxes and the lottery status (the content passed in as [entrantContent]).
 *
 * The role's action sits in its own strip under the scrolling content, so nothing can slide
 * underneath it:
 *  - Camper: a "Lottery" bar that opens a menu with the join or leave action. It appears only
 *    when the campsite has an active lottery ([lotteryButtonEnabled]).
 *  - Owner: no action strip.
 *  - Admin: a red "Delete Site" button, which asks for confirmation and then for the app-policy
 *    reason (US 03.01.01).
 *
 * The role's bottom bar stays visible, with Explore highlighted.
 *
 * @param role Decides the bottom bar and which action strip is shown.
 * @param campsite What to show.
 * @param onBack Called when the back circle is tapped.
 * @param onTabSelected Called with the Screen a bottom bar circle's tab shows when that
 *     circle is tapped.
 * @param onEnterLottery Called when a camper chooses the lottery action from their menu.
 * @param onDeleteSite Called with the app-policy reason the admin picked for the deletion,
 *     after they confirm deleting the site (US 03.01.01).
 * @param onRemoveImage Called with the app-policy reason the admin picked for removing the
 *     campsite's picture (US 03.03.01). The admin opens it by tapping the picture, which shows
 *     the image settings with a red "Remove image" button and a confirmation pop-up.
 * @param onSeeReviews Called when "See all reviews" is tapped.
 * @param reviewsContent The reviews screen. "See all reviews" swaps it in for the details (the
 *     role's bottom bar stays); it gets a function that closes it and shows the details again.
 *     The system back button closes it too.
 * @param entrantContent Shown to campers in a card under the description.
 */
@Composable
fun CampsiteDetailsScreen(
    role: UserRole,
    campsite: CampsiteDetails,
    onBack: () -> Unit,
    onTabSelected: (Screen) -> Unit,
    onEnterLottery: () -> Unit = {},
    lotteryButtonText: String = "Enter Lottery",
    lotteryButtonEnabled: Boolean = true,
    lotteryButtonIsLeave: Boolean = false,
    onDeleteSite: (RemovalReason?) -> Unit = {},
    onRemoveImage: (RemovalReason?) -> Unit = {},
    onSeeReviews: () -> Unit = {},
    reviewsContent: @Composable (onClose: () -> Unit) -> Unit = {},
    entrantContent: @Composable () -> Unit = {}
) {
    val tabs = remember(role) { tabsFor(role) }
    var showAllTags by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var lotteryMenuOpen by remember { mutableStateOf(false) }
    var imageSettingsOpen by remember { mutableStateOf(false) }
    // Only an admin can click the picture, and only if the campsite has one to remove.
    val imageClickable = role == UserRole.ADMIN && campsite.imageUrl.isNotBlank()

    // The admin's delete asks which app policy the site broke before it is deleted
    // (US 03.01.01): confirming the delete opens the reason picker, and the picker is
    // what calls [onDeleteSite] with the reason.
    var pickReason by remember { mutableStateOf(false) }

    // "See all reviews" swaps the reviews screen in for the details (the reviews screen
    // is handed through reviewsContent, where the app knows it); the system back button
    // closes it too.
    var showReviews by remember { mutableStateOf(false) }

    // A camper with no active lottery has nothing to put in the Lottery menu, so no strip.
    val showActionStrip = (role == UserRole.CAMPER && lotteryButtonEnabled) || role == UserRole.ADMIN

    BackHandler(enabled = showReviews) { showReviews = false }

    Scaffold(
        containerColor = Color.White,
        bottomBar = { RoleBottomBar(tabs, selectedIndex = 0, onSelect = onTabSelected) }
    ) { padding ->
        if (showReviews) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                reviewsContent { showReviews = false }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Everything that scrolls
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    DetailsHeader(
                        campsite.name,
                        campsite.imageUrl,
                        onBack,
                        onImageClick = if (imageClickable) { { imageSettingsOpen = true } } else null
                    )

                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        RatingRow(
                            rating = campsite.rating,
                            reviewCount = campsite.reviewCount,
                            onSeeReviews = {
                                showReviews = true
                                onSeeReviews()
                            }
                        )

                        Row(
                            Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InfoCard("Capacity", "${campsite.capacityGuests} guests", Modifier.weight(1f))
                            InfoCard("Location", campsite.location, Modifier.weight(1f))
                        }

                        TagsSection(campsite.tags, onViewAll = { showAllTags = true })

                        AboutSection(campsite.description)

                        if (role == UserRole.CAMPER) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CardTint)
                                    .padding(16.dp)
                            ) {
                                entrantContent()
                            }
                        }
                    }
                }

                // The role's action, in its own strip below the scrolling content
                if (showActionStrip) {
                    Surface(color = Color.White, shadowElevation = 8.dp) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                            when (role) {
                                UserRole.CAMPER -> Column {
                                    GradientBarButton(
                                        text = "Lottery",
                                        modifier = Modifier.fillMaxWidth().testTag("lottery_menu_bar"),
                                        onClick = { lotteryMenuOpen = true }
                                    )
                                    DropdownMenu(
                                        expanded = lotteryMenuOpen,
                                        onDismissRequest = { lotteryMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(lotteryButtonText) },
                                            modifier = Modifier.testTag("lottery_enter"),
                                            onClick = {
                                                lotteryMenuOpen = false
                                                onEnterLottery()
                                            }
                                        )
                                    }
                                }
                                UserRole.ADMIN -> GradientButton(
                                    text = "Delete Site",
                                    onClick = { confirmDelete = true },
                                    brush = DeleteGradient,
                                    height = 52.dp,
                                    modifier = Modifier.testTag("delete_site")
                                )
                                UserRole.OWNER -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    if (imageSettingsOpen && imageClickable) {
        AdminImageRemoval(
            title = campsite.name,
            stored = campsite.imageUrl,
            onRemove = onRemoveImage,
            onDismiss = { imageSettingsOpen = false }
        )
    }

    if (showAllTags) {
        AlertDialog(
            onDismissRequest = { showAllTags = false },
            title = { Text("Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    campsite.tags.forEach { TagChip(it) }
                }
            },
            confirmButton = { TextButton(modifier = Modifier.testTag("tags_close"), onClick = { showAllTags = false }) { Text("Close") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this site?") },
            text = { Text("${campsite.name} will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; pickReason = true },
                    modifier = Modifier.testTag("confirm_delete_site")
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(modifier = Modifier.testTag("admin_delete_cancel"), onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    // Second step: the admin says which app policy the site broke before it is deleted.
    if (pickReason) {
        ConfirmRemovalDialog(
            title = "Which policy was broken?",
            message = "${campsite.name} will be permanently deleted and its owner will be told why. " +
                    "This cannot be undone.",
            confirmLabel = "Delete",
            reasonRequired = true,
            onConfirm = { reason ->
                pickReason = false
                onDeleteSite(reason)
            },
            onDismiss = { pickReason = false }
        )
    }
}

/**
 * The top of the page: the campsite's picture (US 02.01.02) behind the back circle and the
 * campsite's name. The green gradient underneath is what shows for a campsite with no
 * picture, or while the picture is still decoding or if it can't be decoded.
 *
 * @param imageData The campsite's picture as stored text (CampsiteDetails.imageUrl); empty if none.
 * @param imageData The campsite's picture as stored text (CampsiteDetails.imageUrl); empty if none.
 * @param onImageClick Called when the picture is tapped; null when it is not clickable (only
 * an admin can, to remove it, US 03.03.01).
 */
@Composable
private fun DetailsHeader(name: String, imageData: String, onBack: () -> Unit, onImageClick: (() -> Unit)? = null) {
    var headerModifier = Modifier
        .fillMaxWidth()
        .height(230.dp)
        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
        .background(ScreenGradient)
    headerModifier = headerModifier.testTag("details_header")
    if (onImageClick != null) {
        headerModifier = headerModifier.clickable(onClick = onImageClick)
    }
    Box(headerModifier) {
        // Draws nothing when there is no picture, so the gradient above stays visible.
        StoredImage(
            stored = imageData,
            contentDescription = name,
            modifier = Modifier.fillMaxSize().testTag("details_image"),
            contentScale = ContentScale.Crop
        )
        // Darkens the bottom so the white name stays readable on any picture.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .testTag("details_back")
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("<", color = ButtonEnd, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Text(
            text = name,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        )
    }
}

/** The average as a number and five stars, with the link that opens the reviews screen. */
@Composable
private fun RatingRow(rating: Double, reviewCount: Int, onSeeReviews: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (reviewCount > 0) {
            Text(String.format(Locale.US, "%.1f", rating), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { i ->
                val filled = i < rating.roundToInt()
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (filled) StarTeal else FieldGrey),
                    contentAlignment = Alignment.Center
                ) { StarGlyph(Modifier.size(13.dp)) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "See all $reviewCount reviews",
            fontSize = 12.sp,
            color = LinkBlue,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .testTag("see_reviews")
                .clickable(onClick = onSeeReviews)
                .padding(vertical = 8.dp)
        )
    }
}

/** A small rounded card with a grey label above a bold value. */
@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(CardTint)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

/** A bold heading for one block of the page. */
@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

/** The first few tags as chips, with the eye button that opens the full list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(tags: List<String>, onViewAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Tags")
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(EyePink)
                    .testTag("see_tags")
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EyeGlyph(Modifier.size(16.dp), color = Color(0xFF7A2E2E))
                Text("View all tags", fontSize = 11.sp, color = Color(0xFF7A2E2E))
            }
        }
        if (tags.isEmpty()) {
            Text("No tags yet", fontSize = 13.sp, color = Color.Gray)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (tag in tags.take(TAGS_SHOWN)) {
                    TagChip(tag)
                }
            }
        }
    }
}

/** The description text under its heading. */
@Composable
private fun AboutSection(description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("About this campsite")
        if (description.isBlank()) {
            Text("No description provided.", fontSize = 14.sp, color = Color.Gray)
        } else {
            Text(description, fontSize = 14.sp, lineHeight = 21.sp, color = BodyGrey)
        }
    }
}

@Composable
private fun TagChip(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(CardTint)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, fontSize = 12.sp, color = ButtonEnd) }
}