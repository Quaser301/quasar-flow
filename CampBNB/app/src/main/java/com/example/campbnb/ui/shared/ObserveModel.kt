package com.example.campbnb.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.campbnb.models.ObservableModel
import com.example.campbnb.models.ViewObserver

// TODO: Implement this code below in the details screen once accessible
//On each campsite card in the list (US 01.01.04):
//RatingDisplay(model.averageRating(campsite.id), model.ratingCount(campsite.id))
//
//// Inside the Details screen, below the campsite info (US 01.13.01, 01.01.06):
//CampsiteRatingSection(campsiteModel, campsiteController, campsite.id, userModel.currentUserId())
//LotteryJoinSection(lotteryModel, lotteryController, campsite.id, userModel.currentUserId())

/**
 * Makes the calling screen redraw whenever [model] calls notifyViews().
 * Call it once at the top of a screen: observeModel(model).
 * The returned number changes on every update; the screen can ignore it.
 */
@Composable
fun <M> observeModel(model: ObservableModel<M>): Int {
    var version by remember { mutableIntStateOf(0) }

    DisposableEffect(model) {
        val observer = object : ViewObserver<M> {
            override fun update(changedModel: M) {
                version = version + 1
            }
        }
        model.addView(observer)
        onDispose {
            model.removeView(observer)
        }
    }
    return version
}