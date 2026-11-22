package com.example.campbnb.ui.screens

// AI-assisted (US 01.05.02, notice a dead code's opened link shows): written by Qwen 3.8 Flash
// Next Coder IQ1_M.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Old-code notice (US 01.05.02): the phone's own camera opened a CampBNB code's link,
 * but the code behind it is dead — its stop-working moment has already passed, or it
 * carries no expiry at all — so there is no destination the link can open, and this
 * notice stands in its place. The user leaves through the back affordance and gets a
 * current CampBNB code instead; opening a live code's link never reaches this screen.
 *
 * @param back Called when the user taps the back affordance: the app goes back the
 *   way it came.
 */
@Composable
fun QrOldCodeNoticeScreen(back: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "That QR code is old.",
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "The code behind that link has stopped working, or carries no expiry at all. Get a current CampBNB code and open its link instead.",
            modifier = Modifier.padding(top = 8.dp)
        )

        Button(
            onClick = { back() }
        ) {
            Text("Back")
        }
    }
}
