package com.example.campbnb.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.EyeGlyph
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.components.StarGlyph
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.Screens.RoleBottomBar
import com.example.campbnb.ui.Screens.tabsFor
import com.example.campbnb.ui.campsite.lotteryButtonText
import com.example.campbnb.ui.theme.*
import kotlin.math.roundToInt

/** Everything the details screen shows about one campsite. */
data class CampsiteDetails(
    val id: String,
    val name: String,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val tags: List<String> = emptyList(),
    val capacityGuests: Int = 0,
    val sizeSqFt: Int = 0,
    val location: String = "",
    val description: String = ""
)

/** Stand-in data until a controller supplies real campsite details. */
fun placeholderDetails(id: String) = CampsiteDetails(
    id = id,
    name = "Campsite 1",
    rating = 5.0,
    reviewCount = 0,
    tags = listOf("Fire pit", "Lakeside", "Parking", "Pet friendly", "Washrooms"),
    capacityGuests = 6,
    sizeSqFt = 400,
    location = "123 Nowhereville",
    description = "Lorem ipsum dansum wopan gangnam style hello my name is its actually " +
            "couture doe at dat mean merchant. But if he said would yes would go. I would actually like to."
)

/**
 * Details page for one campsite, opened from an image in Explore or Search.
 *
 * The layout is the same for every role. Only the floating button differs:
 *  - Camper: "Enter Lottery".
 *  - Owner: no button.
 *  - Admin: "Delete Site", behind a confirmation dialog.
 *
 * The role's bottom bar stays visible, with Explore highlighted.
 *
 * @param role Decides the bottom bar and the floating button.
 * @param campsite What to show.
 * @param onBack Called when the back circle is tapped.
 * @param onTabSelected Called with the tab index when a bottom bar circle is tapped.
 * @param onEnterLottery Called when a camper taps "Enter Lottery".
 * @param onDeleteSite Called after an admin confirms deleting the site.
 * @param onSeeReviews Called when "See all reviews" is tapped.
 */
@Composable
fun CampsiteDetailsScreen(
    role: UserRole,
    campsite: CampsiteDetails,
    onBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onEnterLottery: () -> Unit = {},
    lotteryButtonText: String = "Enter Lottery",
    lotteryButtonEnabled: Boolean = true,
    lotteryButtonIsLeave: Boolean = false,
    onDeleteSite: () -> Unit = {},
    onSeeReviews: () -> Unit = {},
    entrantContent: @Composable () -> Unit = {}
) {
    val tabs = remember(role) { tabsFor(role) }
    var showAllTags by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val hasFloatingButton = role != UserRole.OWNER

    Scaffold(
        containerColor = Color.White,
        bottomBar = { RoleBottomBar(tabs, selectedIndex = 0, onSelect = onTabSelected) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (hasFloatingButton) 92.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button and name
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(ButtonGradient).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) { Text("<", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    Text(campsite.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }

                // Picture
                Box(
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(4.dp)).background(FieldGrey)
                )

                // Rating
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(5) { i ->
                            val filled = i < campsite.rating.roundToInt()
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(if (filled) StarTeal else FieldGrey),
                                contentAlignment = Alignment.Center
                            ) { StarGlyph(Modifier.size(16.dp)) }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "See all ${campsite.reviewCount} reviews",
                        fontSize = 12.sp,
                        color = LinkBlue,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = onSeeReviews)
                    )
                }

                // Tags
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tags:", fontSize = 12.sp)
                    campsite.tags.take(3).forEach { TagChip(it) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(EyePink).clickable { showAllTags = true },
                            contentAlignment = Alignment.Center
                        ) { EyeGlyph(Modifier.size(18.dp), color = Color(0xFF7A2E2E)) }
                        Text("(View all tags)", fontSize = 8.sp)
                    }
                }

                // About
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("About this site...", fontSize = 14.sp)
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Capacity: ${campsite.capacityGuests} guests", fontSize = 13.sp)
                        Text("Size: ${campsite.sizeSqFt} sq feet", fontSize = 13.sp)
                        Text("Location: ${campsite.location}", fontSize = 13.sp)
                    }
                }

                // Description
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Description", fontSize = 14.sp)
                    Text(campsite.description, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }

                if (role == UserRole.CAMPER) {
                    entrantContent()
                }
            }

            // Floating button, above the bottom bar
            when (role) {
                UserRole.CAMPER -> {
                var buttonBrush: Brush = ButtonGradient
                var buttonTag = "join_lottery"
                if (lotteryButtonIsLeave) {
                    buttonBrush = DeleteGradient
                    buttonTag = "leave_lottery"
                }
                GradientButton(
                    text = lotteryButtonText,
                    onClick = onEnterLottery,
                    enabled = lotteryButtonEnabled,
                    brush = buttonBrush,
                    height = 52.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .testTag(buttonTag)
                )
            }
                UserRole.ADMIN -> GradientButton(
                    text = "Delete Site",
                    onClick = { confirmDelete = true },
                    brush = DeleteGradient,
                    height = 52.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp)
                )
                UserRole.OWNER -> Unit
            }
        }
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
            confirmButton = { TextButton(onClick = { showAllTags = false }) { Text("Close") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this site?") },
            text = { Text("${campsite.name} will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteSite() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TagChip(label: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(FieldGrey).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(label, fontSize = 11.sp, color = Color.Black) }
}