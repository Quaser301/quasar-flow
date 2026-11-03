package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow



// Developer Note: we inherit from ViewModel to make sure our state is saved
// if any sort of window resizing or other such event happens, since Jetpack
// Compose will otherwise dispose of values and re-render.

// likewise we use Stateflow to provide a readonly, coroutine ready entry for
// superviews to latch on to

/**
 * Provides a concrete [ViewModel] holding the current view setting for the app.
 *
 * The current setting is provided via [StateFlow], so that any superview can observe changes and
 * re-render itself in the new view.
 *
 * @property viewSetting The current [AppView], kept as an immutable `StateFlow` for superviews to
 * observe.
 */
class AppViewSettingController: ViewModel() {
    // default to EntrantView because it has the least potential for Bad Stuff
    private val _viewSetting = MutableStateFlow(AppView.EntrantView)
    val viewSetting = _viewSetting.asStateFlow()

    /**
     * Switches the current view setting to the provided [AppView].
     *
     * @param newView The [AppView] to switch to.
     */
    fun changeViewTo(newView: AppView) {
        _viewSetting.value = newView
    }

    // allows any superview to ask for a readonly snapshot of the current setting rather than
    // latching on to the controller itself.
    /**
     * Converts the current state of the controller into an [AppViewSetting].
     *
     * @return A readonly [AppViewSetting] instance holding the currently set [AppView].
     */
    fun toAppViewSetting(): AppViewSetting {
        return AppViewSetting(viewSetting.value)
    }
}

/**
 * A readonly, immutable [ViewModel] for a single [AppView].
 *
 * Unlike [AppViewSettingController], it cannot change once created and updates nothing on its own:
 * it is a snapshot of one [AppView] for other views to present. Instances are created by
 * [AppViewSettingController.toAppViewSetting].
 *
 * @property setting The [AppView] this class instance presents.
 */
sealed class AppViewSetting(val setting: AppView): ViewModel()

/**
 * The views a user can be in, switched via [AppViewSettingController].
 *
 * Switching to a view updates any view that differs between [EntrantView] and [OwnerView] to their
 * respective view.
 */
enum class AppView {
    // the default full admin view.
    AdminView,
    // the Entrant perspective.
    EntrantView,
    // the Campsite Owner perspective.
    OwnerView
}
