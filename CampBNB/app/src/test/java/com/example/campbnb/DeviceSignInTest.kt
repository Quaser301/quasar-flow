package com.example.campbnb

/**
 * The device sign-on state is driven directly, as the other sign-on and settings
 * suites do to their ViewModel-backed holders. The cases are the ones the request
 * leaves open: a device that cannot be read signs nobody on, the first readable
 * device is written down once and for all, a later different read never replaces
 * it, and notices appear on demand.
 */

import com.example.campbnb.viewModels.DeviceSignInController
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSignInTest {

    @Test
    fun test_AnUnreadableDeviceNeverSignsAnyoneOn() {
        val display = DeviceSignInController()

        display.rememberDevice("")
        display.rememberDevice("   ")
        display.noticeFrom("Could not read this device's id, so sign-on is unavailable.")

        assertTrue("blank reads leave no identity behind", display.deviceId.value.isEmpty())
        assertTrue(
            "the reason is visible so the entrant knows why sign-on is off",
            display.notice.value == "Could not read this device's id, so sign-on is unavailable."
        )
    }

    @Test
    fun test_TheFirstReadableDeviceIsRememberedForTheWholeSession() {
        val display = DeviceSignInController()

        display.rememberDevice("dev-1")

        assertTrue("the readable id is written down", display.deviceId.value == "dev-1")

        display.rememberDevice("dev-2")

        assertTrue(
            "no later read replaces the identity the session signed on with",
            display.deviceId.value == "dev-1"
        )
    }

    @Test
    fun test_ANoticeIsShownThenDropped() {
        val display = DeviceSignInController()

        display.noticeFrom("Try again near the device reader")
        assertTrue("the notice appears", display.notice.value == "Try again near the device reader")

        display.clearNotice()

        assertTrue("clearing leaves nothing shown", display.notice.value.isEmpty())
    }

    @Test
    fun test_ARealIdSurvivesBeingReadAgainUnchanged() {
        val display = DeviceSignInController()

        display.rememberDevice("dev-same")
        display.rememberDevice("dev-same")

        assertTrue("an unchanging identity stays put", display.deviceId.value == "dev-same")
    }
}
