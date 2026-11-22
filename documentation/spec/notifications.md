# notifications — reporting records to the operating system

Spec file for the `com.example.campbnb.notifications` package
(`CampBNB/app/src/main/java/com/example/campbnb/notifications/`, 5 files).

This package NEVER builds records — `NotificationController` and the adapters in
`controller.md` do. Everything here takes an already-built `Notification`
(`models.md`) and either posts it as an OS banner or maps it to an in-app route.

Written against branch `wyatt-us-01.05.02` (tip `c985351` plus working tree), 2026-11-22.

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

## NotificationRoutes (object) — record → in-app route

```kotlin
object NotificationRoutes {
    const val EXTRA_ROUTE = "campbnb.route"                       // Intent extra key
    const val EXTRA_NOTIFICATION_ID = "campbnb.notificationId"    // Intent extra key
    const val NOTIFICATIONS = "notifications"  // MESSAGES = "messages"
    const val BOOKING = "booking"             // RESERVATIONS = "reservations"
    const val CHAT = "chat"
    fun routeFor(n: Notification): String
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

The route constants are the vocabulary of the app's route table (`app.md`);
`SystemNotifier` puts them into Intent extras under the two key constants.

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
