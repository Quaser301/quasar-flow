package com.example.campbnb.data

// AI-assisted (US 02.10.02, permission-scoped repository): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.models.Notification
import com.example.campbnb.models.sentNotificationsFor

/**
 * A view over another repository that serves only the notices one user caused.
 *
 * This is the repository the app hands a view, chosen where the app knows what the
 * viewer is allowed to see: an owner listing their own correspondence gets this one, a
 * view that may see everything gets the source itself. A read is narrowed by
 * [sentNotificationsFor] over the records the source serves, so no second read is paid.
 *
 * Only a read is narrowed, because scoping answers what a viewer may list: storing,
 * following and marking belong to whoever can reach the source, so every other request is
 * run against the source unchanged. That is the point of asking a store with a
 * [NotificationCommand]: a view over a store states its one change on the request it
 * rewrites and hands the rest on.
 *
 * @param source Where notices are stored.
 * @param senderId The user whose notices a read serves.
 */
class SentNotificationsRepository(
    private val source: NotificationRepository,
    private val senderId: String,
): NotificationRepository {
    override fun run(command: NotificationCommand) {
        when (command) {
            // A read is asked for again with the view's own continuations, so only the
            // records this viewer may list reach them.
            is NotificationCommand.Read -> source.run(
                NotificationCommand.Read(
                    onSuccess = { records -> command.onSuccess.invoke(sentNotificationsFor(records, senderId)) },
                    onFailure = command.onFailure
                )
            )

            else -> source.run(command)
        }
    }
}
