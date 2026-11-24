# notifications — reporting records to the operating system

Spec file for the `com.example.campbnb.notifications` package
(`CampBNB/app/src/main/java/com/example/campbnb/notifications/`, 5 files).

This package NEVER builds records — `NotificationController` and the adapters in
`controller.md` do. Everything here takes an already-built `Notification`
(`models.md`) and either posts it as an OS banner or maps it to an in-app route.

Written against branch `wyatt-us-01.05.02` (tip `03519ac` plus working tree), 2026-11-24.

## NotificationChannels (object) — record type → OS channel

```kotlin
object NotificationChannels {
    const val LOTTERY_RESULTS = "lottery_results"   // RESERVATIONS = "reservations"
    const val MESSAGES = "messages"                 // GENERAL = "general"
    const val LOTTERY_INVITES = "lottery_invites"
    fun channelFor(type: NotificationType): String
    fun createAll(context: Context)
}
```

`channelFor` is a TOTAL map — every `NotificationType` (`models.md`) has a channel:

| type | channel |
| --- | --- |
| WIN, LOSE | `LOTTERY_RESULTS` |
| INVITE | `LOTTERY_INVITES` |
| CONFIRMATION, CANCELLATION, REJECTION | `RESERVATIONS` |
| MESSAGE | `MESSAGES` |
| OWNER_NOTICE | `GENERAL` |

`channelFor` is PURE and unit-reachable: call it on a bare `NotificationType.WIN`
and assert the channel string (channel id strings above are the exact values).

`createAll(context)`: a NO-OP when the device SDK is below Android O; otherwise it
registers all five channels with these exact display titles/importances
(internal ids above are never shown):

- `Lottery results` / HIGH / description `Tells you whether you won or lost a campsite lottery`
- `Reservations` / DEFAULT / description `Confirmations, cancellations and rejections`
- `Messages` / HIGH / description `New messages from campsite owners and entrants`
- `General` / DEFAULT / description `Other notices from campsite owners`
- `Lottery invitations` / DEFAULT / description `Invitations to join another owner's lottery`

## NotificationRoutes (object) — record → in-app route, both crossings

```kotlin
object NotificationRoutes {
    const val EXTRA_ROUTE = "campbnb.route"                       // Intent extra key
    const val EXTRA_NOTIFICATION_ID = "campbnb.notificationId"    // Intent extra key
    const val NOTIFICATIONS = "notifications"  // MESSAGES = "messages"
    const val BOOKING = "booking"             // RESERVATIONS = "reservations"
    const val CHAT = "chat"
    fun routeFor(n: Notification): String
    fun screenFor(n: Notification): Screen
    fun screenFromIntentText(intentRoute: String): Screen?
}
```

`routeFor` is a `when` on `n.type` — PURE, fully unit-reachable (build a `Notification`
with `models.md` rules; the route depends ONLY on `type` plus one id field):

- WIN or INVITE → `"booking/<n.lotteryId>"` when `lotteryId` is NOT blank;
  otherwise `"notifications"`.
- LOSE, OWNER_NOTICE, CONFIRMATION, CANCELLATION, REJECTION → `"reservations"`
  (no id needed).
- MESSAGE → `"chat/<n.reservationId>"` when `reservationId` is NOT blank;
  otherwise `"messages"`.

`screenFor` is the SAME decision against the SAME fields, as the typed `Screen`
route instead of the text (a `when` with the identical arms): the in-app
destination a tapped notice opens — `Screen.BookingEntry(<lotteryId>)`,
`Screen.NotifReservations`, `Screen.ChatReservationEntry(<reservationId>)`,
falling back to `Screen.Notifications` / `Screen.Messages`. Every in-app notice
tap navigates by this (the notices tab hands it to its caller's typed callback,
`app.md`), so the compiler — not the runtime — checks every notice's destination
against the sealed screen vocabulary.

`screenFromIntentText` is the inverse of `routeFor`: it reads one of those texts
back as the screen to open (`"reservations"` → `Screen.NotifReservations`,
`"booking/x"` → `Screen.BookingEntry("x")`, `"chat/x"` → `Screen.ChatReservationEntry("x")`,
and the two name-only texts → their screens), or null when the text names no known
route — nothing is opened for such text. The texts are ONLY the format of the
Android Intent extra that crosses the OS boundary: `SystemNotifier` writes
`routeFor(n)` there and `MainActivity` reads it back through this decode onto the
app's pending-SCREEN bridge (`app.md`). The two forms never disagree: `screenFor`
and `routeFor` switch on the same fields with the same arms, and
`screenFromIntentText(routeFor(n))` is `screenFor(n)` for every record.

## NotificationPermission (object) + the card that asks

- `isGranted(context)`: `NotificationManagerCompat.from(context).areNotificationsEnabled()`
  — that single call is the whole rule.
- `openSettings(context)`: SDK ≥ O → `Settings.ACTION_APP_NOTIFICATION_SETTINGS` with
  extra `Settings.EXTRA_APP_PACKAGE = context.packageName`; below O →
  `ACTION_APPLICATION_DETAILS_SETTINGS` with data URI `package:<packageName>`.
  Always `FLAG_ACTIVITY_NEW_TASK`, always via `context.startActivity`.

`@Composable NotificationPermissionCard(modifier = Modifier)` — screen-level wiring,
NOT a JVM unit target; its contract:

- Draws NOTHING once `isGranted` is true (it re-checks on every lifecycle RESUME).
- Otherwise a full-width card, exact copy: title `Turn on notifications`; body
  `We will tell you the moment you win or lose a lottery, even when the app is in
  the background. Without this you will only see results inside the app.`
- Button label `Turn on` when SDK ≥ TIRAMISU AND the prompt has not been denied this
  session (`deniedOnce` flips true on a denial); otherwise the button routes to
  `openSettings` with label `Open notification settings`. Once denied, the in-app
  prompt is never offered again — only the settings door.

## NotificationWatcher (class) — banners for a live user

```kotlin
class NotificationWatcher(private val controller: NotificationController,
                          private val notifier: SystemNotifier)
fun start(userId: String)   // no callbacks; side effects only
fun stop()                  // safe when nothing is active
```

Rules, in order inside `start`:
1. `stop()` runs FIRST — a previous listener is always stopped before starting (or refusing).
2. Blank `userId` → return; no listener exists.
3. `controller.listen(userId, onUpdate, onError)` — the returned stop function is kept.
4. FIRST `onUpdate` snapshot is a BASELINE: it sets nothing visible (old records do not
   get banners). Second and later snapshots: for each record in `update.added` with
   `status == UNREAD` AND `pushAllowed == true`, `notifier.show(record)`.
5. `onError` only writes a warning log (`Log.w` at tag `NotificationWatcher`, message
   `Notification listener failed`) — banners never fail.

This is a Level-1 design: banners only while the app process lives.

Unit reach: `SystemNotifier` is a plain class (not fakeable) and constructing it needs
an Android `Context`, which JVM tests do not have — `NotificationWatcher` behavior is
NOT JVM-unit-testable; its baseline rule is an integration contract. Drive the record
half of this chain at `NotificationController` over a `NotificationRepository` fake
(`controller.md` "Standing controllers up") instead.

## SystemNotifier (class) — one record becomes one banner

```kotlin
class SystemNotifier(private val context: Context)
fun show(n: Notification)
```

1. Permission FIRST: `NotificationPermission.isGranted` false → NOTHING happens
   (a banner is never attempted without permission).
2. Tap intent: `MainActivity::class.java` with `FLAG_ACTIVITY_NEW_TASK |
   FLAG_ACTIVITY_CLEAR_TOP`, extras `EXTRA_ROUTE = routeFor(n)` and
   `EXTRA_NOTIFICATION_ID = n.id` (the two `NotificationRoutes` keys).
3. `uniqueNumber = n.id.hashCode()` — same number keys the PendingIntent and the
   `notify()` call.
4. Banner on `channelFor(n.type)` (the mapping above): icon
   `R.drawable.ic_stat_campsite` (the app's icon resource, `app.md`), content
   title/message copied VERBATIM from the record (`n.title`, `n.message`) plus the
   `BigTextStyle` twin, `autoCancel = true`, content intent = the tap intent,
   priority HIGH; then `NotificationManagerCompat.notify(uniqueNumber, banner)`.

NOTE `show` itself does NOT check `pushAllowed` — that filter lives in
`NotificationWatcher`. `SystemNotifier` filters on permission only.

## Push — what delivering from the server would need (contract open — no backend access here)

PUSH is NOT a separate delivery system in this app. An OS banner is already push-shaped:
`SystemNotifier` turns one built record into a banner whose tap-in carries exactly
`EXTRA_ROUTE = routeFor(n)` and `EXTRA_NOTIFICATION_ID = n.id` (MainActivity reads
those extras on tap and reads the route text back through `screenFromIntentText` onto
the app's pending-SCREEN bridge — app.md). Tapping a banner that ARRIVED by itself lands
on the right in-app screen; tapping one the server DELIVERED must behave identically.
That is the whole client half, and it exists and is verified. What push still needs is all server-side or OS-transport
and NONE of it is observable from here:

1. A subscription per user. The server owns subscriptions (a topic per user, or the user
   per topic — the choice is the backend's); the client would REGISTER one. The gate is
   exactly the existing preference: `allowPushNotifications` (models.md, default true) gates
   banner delivery in `NotificationWatcher` and must gate subscription registration with the
   same reading — a user who turns push off gets no registrations, not just no banners.
2. Payload schema, kept to the client's OWN vocabulary so any push client is interchangeable:
   a delivered push carries the pair (`routeFor(n)` as the route key, `n.id` as the
   notification id) — the same two `NotificationRoutes` keys MainActivity already reads from
   tap extras and decodes to a screen through `screenFromIntentText`. Records are complete when built (this package NEVER builds them), so NOTHING
   else is recomputed client-side: title, message and channel all come from the delivered
   record's own fields server-side or must not be re-invented here. A payload that carries
   only (route, id) is the minimum; anything more (raw record fields) is transport detail,
   not client contract.
3. A transport. Native OS-level push subscriptions on Android 15+ would be the OS
   subscription manager — NOT in this app's dependency set, and no new androidx deps are
   added (house rule). A web-push transport (server-issued tokens, FCM/APNS-style) needs a
   backend to issue and refresh them.

NEEDS INFO (backend access, owner to settle): which provider, where subscriptions are stored,
and the server's token issuance. Deliberately NOT implemented tonight: inventing a client
subscription wrapper against no server is guessing the API. The pure client half (delivery
arrives as the (route, id) pair → existing tap-in behaviour) needs no new code — MainActivity
already maps exactly that pair, and both are already covered by the `"reservations"` and
`"qr-link"` string-route bridges (app.md).

## Standing this package up in tests

- The two PURE members carry the whole JVM-testable surface and take no fakes at all:
  `NotificationChannels.channelFor(type)` on bare enum values, and
  `NotificationRoutes.routeFor(n)` on `Notification`s built via `models.md` rules —
  assert exact strings (`"booking/l-7"`, `"chat/r-3"`, and both blank-id arms
  `"notifications"` / `"messages"`).
- `NotificationPermission.isGranted` / `openSettings`, `createAll`, `SystemNotifier`,
  `NotificationWatcher`, and the Composable all need an Android `Context` — outside the
  JVM unit surface. Their contracts above are for reading and for integration tests.
- A test writing about this package should test the MAPS (channelFor, routeFor), not the
  OS calls; the maps are where the regressions hide (a new `NotificationType` breaks
  both exhaustive `when`s at COMPILE time — that is the guard, no test needed).
