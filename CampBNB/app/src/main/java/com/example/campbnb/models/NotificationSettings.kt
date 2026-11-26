package com.example.campbnb.models

/** Stable identifiers for the notification options exposed by the settings screen. */
enum class NotificationSettings {
    PUSH,
    CSV
}

/** What an entrant receives when a reservation is confirmed (US 01.05.02). */
enum class ConfirmationDelivery { NOTIFICATION, CSV }

/** Reads the saved settings list as the one dropdown choice. No CSV flag means Notification (the default). */
fun confirmationDeliveryOf(enabled: List<NotificationSettings>): ConfirmationDelivery =
    if (NotificationSettings.CSV in enabled) ConfirmationDelivery.CSV else ConfirmationDelivery.NOTIFICATION

/** Writes a dropdown choice back into the saved list. PUSH stays on so other push notices keep working. */
fun settingsWithDelivery(
    enabled: List<NotificationSettings>,
    delivery: ConfirmationDelivery
): List<NotificationSettings> = when (delivery) {
    ConfirmationDelivery.NOTIFICATION ->
        (enabled.filterNot { it == NotificationSettings.CSV } + NotificationSettings.PUSH).distinct()
    ConfirmationDelivery.CSV ->
        (enabled + NotificationSettings.CSV).distinct()
}
