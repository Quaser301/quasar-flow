package com.example.campbnb.ui.admin

/*
 * AdminImagesScreen.kt
 *
 * Role: view. The admin's browser of every uploaded picture, campsite and profile (US 03.06.01).
 * Tapping a picture opens the same image settings panel as everywhere else (US 03.03.01), so
 * an admin can browse and remove from one place.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.controller.AdminController
import com.example.campbnb.models.UploadedImage
import com.example.campbnb.ui.shared.BarWithBackChevronAndHeaderCentered
import com.example.campbnb.ui.shared.StoredImage
import com.example.campbnb.ui.theme.FieldGrey

/**
 * The Images subview: a back button, an Images header and every uploaded picture as a tile
 * with a caption saying whose it is. The list is read when the screen opens and again after
 * each removal, so a removed picture's tile disappears.
 *
 * @param back Called when the back button is pressed.
 * @param adminController Reads the pictures and carries out the removal the admin confirms.
 */
@Composable
fun AdminImagesScreen(back: () -> Unit, adminController: AdminController) {
    var images by remember { mutableStateOf<List<UploadedImage>>(emptyList()) }
    var notice by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<UploadedImage?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        adminController.loadUploadedImages { found, problem ->
            images = found
            if (problem != null) {
                notice = problem
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarWithBackChevronAndHeaderCentered(back, "Images")
        if (notice.isNotBlank()) {
            Text(
                notice,
                fontSize = 14.sp,
                modifier = Modifier.testTag("images_message").padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        if (images.isEmpty()) {
            Text(
                "No uploaded images",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.testTag("images_empty").padding(16.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(images, key = { it.key }) { image ->
                ImageTile(image, onClick = { selected = image })
            }
        }
    }

    val target = selected
    if (target != null) {
        AdminImageRemoval(
            title = target.title,
            stored = target.image,
            onRemove = { reason ->
                adminController.removeImage(target.kind, target.sourceId, reason) { _, message ->
                    notice = message
                    reload += 1
                }
            },
            onDismiss = { selected = null }
        )
    }
}

/** One picture with a caption such as "Campsite - Lakeside". */
@Composable
private fun ImageTile(image: UploadedImage, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(FieldGrey)
            .testTag("image_tile_" + image.key)
            .clickable(onClick = onClick)
    ) {
        StoredImage(
            stored = image.image,
            contentDescription = image.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            image.kind.label + " - " + image.title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}