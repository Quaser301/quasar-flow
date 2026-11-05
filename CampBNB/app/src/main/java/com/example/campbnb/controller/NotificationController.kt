package com.example.campbnb.controller

class NotificationController {
    /**
     * Sends notifications to users about reservation events.
     *
     * [ReservationController] calls this class when a reservation is confirmed, cancelled or
     * rejected, so the other party finds out without the controller knowing how the message is
     * delivered. Callers don't wait for a result: a notification that fails to send never
     * fails the reservation itself.
     *
     * Commands:
     *  - [send]: Delivers one notification to one user.
     *
     * @author Zino
     * @see ReservationController
     */

    fun send(recipientId: String, type: String, details: kotlin.collections.Map<String, String>) {}
    // B, A
}