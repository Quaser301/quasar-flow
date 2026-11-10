# Notification Logs

UML documentation for the notification debug log, in
`CampBNB/app/src/main/java/com/example/campbnb/ui/notification/NotificationLogScreen.kt`
with its view state in `viewModels/NotificationLogViewModels.kt` and its grouping model in
`models/NotificationLogModel.kt` (US 03.08.01). This is the re-pull of that story onto the
current tree: the notification feature has since grown a per-user inbox, a per-user live
listener and delivery routes, so the log is rebuilt against those rather than carried over
from the older branch.

```mermaid
classDiagram
    class ViewModel <<abstract>>

    class NotificationLogViewModel {
        -_state MutableStateFlow~LogViewState~
        -_openLogs MutableStateFlow~Set~String~~
        -readLogs List~NotificationLogGroup~?
        +state StateFlow~LogViewState~
        +openLogs StateFlow~Set~String~~
        +readLog()
        +toggleLog(logName String)
    }

    sealed LogViewState {
        Loading
        Ready(logs List~NotificationLogGroup~, lines List~NotificationLogLine~)
        Error(message String)
    }

    class NotificationLogGroup {
        +logId String
        +senderId String
        +recipientIds List~String~
        +sentAtLabel String
        +title String
        +message String
        +routeText String
    }

    class NotificationLogLine {
        +lineKey String
        +logId String
        +text String
        +style LogLineStyle
    }

    enum LogLineStyle {
        DROPDOWN
        RECIPIENT
        NOTICE_TITLE
        NOTICE_BODY
    }

    NotificationLogModel {
        +notificationLogKeyOf(notification Notification) String
        +notificationLogGroupsFor(notifications List~Notification~) List~NotificationLogGroup~
        +notificationLogLinesFor(logs List~NotificationLogGroup~, openLogIds Set~String~, profileLabel) List~NotificationLogLine~
    }

    class NotificationController {
        +readAll(onSuccess onFailure, onError)
        +sortNewestFirst(list) List~Notification~
    }

    NotificationRepository {
        +saveAll(notifications, onDone)
        +readAll(onSuccess, onFailure)
    }

    FirestoreNotificationRepository {
        +readAll(onSuccess, onFailure) via readEngine().query().execute
    }

    NotificationRoutes {
        +routeFor(notification) String
    }

    ProfileLabel {
        +profileLabelOf(userId, users) String
        +unresolvableProfileLabel Function~String, String~
    }

    class NotificationLogScreen {
        +NotificationLogScreen(controller, navController, profileText)
        +NotificationLogTopBar(display, navController)
        +NotificationLogLineDisplay(display, line)
    }

    ViewModel <|-- NotificationLogViewModel
    NotificationLogViewModel o-- "1" LogViewState : readonly flow
    NotificationLogViewModel o-- "1" openLogs : readonly flow
    NotificationLogScreen ..> NotificationLogViewModel : draws one keyed line list
    NotificationLogScreen ..> ProfileLabel : default unresolved reference
    NotificationLogViewModel ..> NotificationController : one-shot read and ordering
    NotificationController ..> NotificationRepository : readAll pass-through
    NotificationRepository <|.. FirestoreNotificationRepository : Firestore read engine
    NotificationLogViewModel ..> NotificationLogModel : groups and builds lines
    NotificationLogModel ..> NotificationRoutes : where opening leads
```

Reading the diagram: the screen never reaches data. It asks its view state for the current
`LogViewState`; the view reads every saved notification once through the controller it was
handed, orders every record newest first with the controller's own ordering, asks the grouping
model for one entry per broadcast, and turns those entries into a flat list of keyed lines.
A dropdown bar names its entry and toggles it in the view; opening it replaces the lines with
that entry's receivers and notice, and the keyed list redraws only the lines that differ. A
refresh button re-runs the same read. The ready state carries both the entries and their lines,
so a tap or a refresh has one observable result, not two. The back button navigates through the
`androidx.navigation.NavController` instance the screen is handed; no back callback is passed into
the view state. The default profile resolver is the stable `unresolvableProfileLabel` value in
`ProfileLabel`, so a caller that leaves `profileText` out keeps the same resolver across redraws.

Choices made, in answer to what changed on main since the story was first written:

- **One-shot read, refresh on tap.** The repository contract remains per-user for delivery, but
  the log needs every saved record. `NotificationRepository` grows `readAll(onSuccess, onFailure)`,
  `FirestoreNotificationRepository` implements it through the existing read engine, and
  `NotificationController.readAll` passes it through. The debug log is a record of broadcasts that
  have already been sent, so it does not subscribe. A whole-collection listener re-delivers every
  record on every write anywhere in the collection; reading once on open and again on a tap removes
  that cost and the listener accumulation. Nothing owns a subscription, so `stop()` and `onCleared()`
  are gone.
- **The grouping key is exact, not approximate.** `NotificationController.buildLotteryNotification`
  writes every receiver of one draw with the same broadcast fields and a receiver-derived document
  id, and its send path writes one record per reservation or message event. Records sharing the
  broadcast fields are the same event; two different broadcasts cannot share this key without also
  sharing the generated document id, which would overwrite the first one.
- **One entry per message, not one per thread.** `MessageNotificationAdapter` emits one
  `MESSAGE` record per message per recipient, and their texts differ, so a thread of three messages
  is three log entries. That is the debug shape wanted: each broadcast as it was written.
- **Timestamp is a label, built once.** The entry carries `sentAtLabel` as text. `describeSavedAt`
  formats a saved Firebase timestamp once while the log is built and returns "no saved timestamp"
  for an unserved record. The screen does no date formatting.
- **The flat list is model data, not screen control flow.** `LogLineStyle` names the four looks the
  debug screen gives a line; `NotificationLogLine` carries the already-written text, a stable
  `lineKey`, the owning `logId`, and that style. `notificationLogLinesFor` produces the only list
  the screen can draw: a closed entry contributes its dropdown bar; an open entry contributes its
  bar, one line per receiver, then the notice title and body. Keys use a separate namespace inside
  each entry (`logId#receiver#id`, `logId#notice#title`, `logId#notice#body`), so a receiver id
  cannot collide with another line key. The screen calls `LazyColumn { items(items = shown.lines,
  key = { it.lineKey }) }`; opening or closing one dropdown changes only that entry's lines.
- **Labels are resolved at line-build time.** The screen injects `profileText`, and the view passes
  it to `notificationLogLinesFor` once per successful read or dropdown tap. The default is the stable
  `unresolvableProfileLabel` value for listings that have no saved users to look up; a caller that can
  resolve references passes its own stable resolver, for example `profileLabelOf` fed with saved users.
  Per-receiver lookup is not run while drawing.
- **Ordering is the controller's.** The local sort is dropped; the view calls
  `NotificationController.sortNewestFirst`, and the grouping model keeps the order its input gives,
  so order is decided in exactly one place.
- **The state holder is named for what it is.** It extends `ViewModel` and is named
  `NotificationLogViewModel`, matching the package's convention for state holders.
- **Open dropdowns are typed, not delimited.** A `StateFlow<Set<String>>` holds open entry names,
  and a name that matches no entry changes nothing. A name that carries another entry's name stays
  separate because the set holds whole keys, not substrings.

This document covers the screen body and the arguments it is registered with: the app hands it its
shared notification controller and one nav controller, and leaves `profileText` at the stable
unresolved resolver unless it has a stable resolver of its own. The inbox and admin screens are not
edited here; `profileLabelOf` is the shared lookup they can adopt.

Test placement: `notificationTests/NotificationLogTest.kt`, beside the other notification suites.
It drives the state holder and the model through the shared `FakeNotificationRepository` (the same
contract the real repository implements), covering: the ideal flow (records saved, read once when the
log opens, grouped newest first, one entry per broadcast, a dropdown opened drawing its receivers and
its notice), the extremes (nothing saved at all, an unserved record with no saved timestamp, a
hundred records over three broadcasts), wrongly entered data (an empty dropdown name, a log name
that matches no entry, a receiver whose id spells another line's key, a profile reference that cannot
be resolved, and the stable default resolver labelling an unresolved reference), and stress (mashing
dropdowns, tapping refresh several times, a failed read after a successful one, and an open state kept
across a failed read).