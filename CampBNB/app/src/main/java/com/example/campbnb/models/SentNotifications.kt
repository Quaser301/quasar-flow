package com.example.campbnb.models

// AI-assisted (US 02.10.02, owner-sent filter): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

import com.example.campbnb.models.Notification

/**
 * The records one user caused, out of the records given.
 *
 * Nothing is read here: this only narrows records the caller already has, so the same
 * function serves a view over the whole collection and a view over one sender's. Input
 * order is kept, which is what a newest-first log relies on.
 *
 * A record the app created on its own carries an empty sender, so it belongs to no
 * sender: only records a user deliberately caused count as sent.
 *
 * @param records The saved notification records.
 * @param senderId The user the notices must have been caused by; a blank name matches nothing.
 * @return The records that user caused, in the order given; none means an empty list.
 */
fun sentNotificationsFor(records: List<Notification>, senderId: String): List<Notification> =
    if (senderId.isBlank()) emptyList()
    else records.filter { it.senderId == senderId }
