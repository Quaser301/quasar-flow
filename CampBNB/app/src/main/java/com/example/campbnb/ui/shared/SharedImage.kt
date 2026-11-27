package com.example.campbnb.ui.shared

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.campbnb.data.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Prompted Claude Sonnet 5.5 for implementation and concepts related to this file.

/**
 * Showing and choosing pictures that are stored as text (US 02.01.02, US 01.08.02): the
 * screen side of ImageRepository. A picture is the Base64 text of its image bytes, the same
 * text the Explore cards already decode.
 */

/**
 * Turns stored Base64 text into a bitmap, or null if the text is blank or not a valid image.
 * Accepts both plain Base64 and "data:image/png;base64,..." text.
 */
fun decodeStoredImage(stored: String): ImageBitmap? {
    if (stored.isBlank()) {
        return null
    }
    try {
        var text = stored
        val comma = text.indexOf(",")
        if (text.startsWith("data:") && comma >= 0) {
            text = text.substring(comma + 1)
        }
        val bytes = Base64.decode(text, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            return null
        }
        return bitmap.asImageBitmap()
    } catch (e: IllegalArgumentException) {
        return null   // the text was not valid Base64
    }
}

/**
 * Draws the picture stored in [stored]. Decoding happens off the main thread, and nothing is
 * drawn while it runs or if [stored] is blank or not an image, so a caller can put its own
 * fallback (initials, say) underneath.
 */
@Composable
fun StoredImage(
    stored: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var picture by remember(stored) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(stored) {
        if (stored.isBlank()) {
            picture = null
        } else {
            picture = withContext(Dispatchers.Default) {
                decodeStoredImage(stored)
            }
        }
    }

    val shown = picture
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

/**
 * Prepares the phone's photo picker and returns a function that opens it. When the user
 * chooses a picture it is shrunk and turned into text by [ImageRepository] (off the main
 * thread), then handed to [onPicked]. If the choice cannot be used, [onFailed] runs instead.
 * Backing out of the picker calls neither.
 *
 * @param maxEdge The longest side allowed, see ImageRepository.CAMPSITE_EDGE and PROFILE_EDGE.
 * @param onPicked Called with the picture as stored text.
 * @param onFailed Called when the chosen file is not an image or is too big to store.
 * @return A function that opens the picker; call it from a button's click.
 */
@Composable
fun rememberImagePicker(
    maxEdge: Int,
    onPicked: (String) -> Unit,
    onFailed: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val images = remember { ImageRepository(context.contentResolver) }
    var chosen by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(chosen) {
        val uri = chosen
        if (uri != null) {
            val text = withContext(Dispatchers.Default) {
                images.encode(uri, maxEdge)
            }
            // Cleared so that picking the very same picture again still runs this.
            chosen = null
            if (text == null) {
                onFailed()
            } else {
                onPicked(text)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                chosen = uri
            }
        }
    )

    return {
        launcher.launch("image/*")
    }
}