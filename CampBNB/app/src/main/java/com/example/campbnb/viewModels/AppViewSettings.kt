package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.campbnb.models.AppSettingsModel



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

    // Whether the current setting came from a real choice (a saved record that names a
    // view, or a switch the user made) rather than from a role-derived default. A user
    // who chose a view keeps that choice across lifetimes: the sign-in role default is
    // only applied while nothing has been chosen.
    private var viewChoiceWasMade = false

    /**
     * Switches the current view setting to the provided [AppView].
     *
     * The switch counts as an explicit choice, so later role defaults no longer apply.
     *
     * @param newView The [AppView] to switch to.
     */
    fun changeViewTo(newView: AppView) {
        viewChoiceWasMade = true
        _viewSetting.value = newView
    }

    /**
     * Applies the view that fits a role when the user has never chosen one, as the sign-in
     * default. Once a saved record named a view or the user switched views, this changes
     * nothing: the user's own choice wins across lifetimes.
     *
     * @param newView The [AppView] derived from the signed-in role.
     */
    fun applyViewDefaultIfNoneChosen(newView: AppView) {
        if (!viewChoiceWasMade) {
            changeViewTo(newView)
        }
    }

    /**
     * Applies a loaded [AppSettingsModel]: the recorded app view choice, when it names
     * one of the views, becomes the current setting and marks the choice as made. An
     * empty or unknown choice leaves the current setting untouched, so a user with
     * nothing saved keeps the default.
     *
     * @param record The loaded settings record, from [AppSettingsService.loadAppSettings].
     */
    fun applyLoadedSettings(record: AppSettingsModel) {
        for (view in AppView.values()) {
            if (view.name == record.appViewChoice) {
                changeViewTo(view)
            }
        }
    }

    // allows any superview to ask for a readonly wrapper of the live StateFlow rather than
    // latching on to the controller itself.
    /**
     * Converts the current state of the controller into an [AppViewSetting].
     *
     * @return A readonly [AppViewSetting] instance holding the [AppView] [StateFlow].
     */
    fun toAppViewSetting(): AppViewSetting {
        return AppViewSetting(viewSetting)
    }
}

/**
 * A readonly [ViewModel] wrapping the live [AppView] [StateFlow] of an [AppViewSettingController].
 *
 * It holds the [StateFlow] itself rather than its value, so any superview latching on to one of
 * these re-renders automatically as [AppViewSettingController] changes the current setting.
 * The class instance itself never changes: the [StateFlow] it holds is immutable.
 *
 * Instances are created by [AppViewSettingController.toAppViewSetting].
 *
 * @property setting The live [StateFlow] of [AppView] this class instance presents.
 */
class AppViewSetting(val setting: StateFlow<AppView>): ViewModel() {}

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
