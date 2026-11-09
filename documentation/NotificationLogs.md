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
        -stopListening (() -> Unit)?
        +state StateFlow~LogViewState~
        +openLogs StateFlow~Set~String~~
        +start()
        +stop()
        +toggleLog(logName String)
        +pressBack()
        +onCleared()
    }

    sealed LogViewState {
        Loading
        Ready(logs List<NotificationLogGroup>)
        Error(message String)
    }

    class NotificationLogGroup {
        +logId String
        +senderId String
        +recipientIds List<String>
        +sentAt Timestamp?
        +title String
        +message String
        +routeText String
    }

    NotificationLogModel {
        +notificationLogGroupsFor(notifications List<Notification>) List<NotificationLogGroup>
        +notificationLogKeyOf(notification Notification) String
    }

    class NotificationController {
        +listenAll(onUpdate, onError) (() -> Unit)
        +sortNewestFirst(list) List<Notification>
    }

    NotificationRepository {
        +listenForAll(onUpdate, onError) (() -> Unit)
    }

    NotificationRoutes {
        +routeFor(notification) String
    }

    ProfileLabel {
        +profileLabelOf(userId, users) String
    }

    ViewModel <|-- NotificationLogViewModel
    NotificationLogViewModel o-- "1" LogViewState : readonly flow
    NotificationLogViewModel ..> NotificationController : one all-user listener
    NotificationLogScreen ..> NotificationLogViewModel : draws the current state
    NotificationLogScreen ..> ProfileLabel : default profile label
    NotificationLogViewModel ..> NotificationLogModel : groups after sorting
    NotificationLogModel ..> NotificationRoutes : where opening leads
    NotificationController ..> NotificationRepository : listenAll pass-through
```

Reading the diagram: the screen never reaches data. It asks its view state for the current
`LogViewState`; the view starts the one listener that is ever live through the controller,
sorts every record newest first with the controller's own ordering, and asks the grouping
model for one entry per broadcast. A dropdown bar names its entry and toggles it in the view,
which holds open dropdowns as a `Set<String>` flow, so a tap re-renders only what changed.

Choices made, in answer to what changed on main since the story was first written:

- **The global feed.** The repository contract is per-user (`listenForUser` filters by
  `recipientId`), so nothing could yield all notifications. The contract grows `listenForAll`
  with the same snapshot handling and no filter, and `NotificationController.listenAll` passes
  it through. A one-shot `getAll` would work but would leave the debug log blind to every
  later broadcast; the listener matches how the rest of this feature already works, and the
  view stops it through its own `stop()`, which `onCleared` runs when the screen goes away.
  Stopping twice changes nothing, so a screen that tears down more than once is safe.
- **The grouping key is now exact, not approximate.** `NotificationController.buildLotteryNotification`
  writes every receiver of one draw with the same fields and an id derived from
  `lotteryId_userId_TYPE`, and its `send` path writes one record per reservation or message
  event. So records sharing the key fields are the same event, and two different broadcasts
  cannot share it without colliding on the document id. The earlier "records that merely
  happen to share these fields would merge" placeholder is replaced by that statement.
- **One entry per message, not one per thread.** `MessageNotificationAdapter` emits one
  `MESSAGE` record per message per recipient, and their texts differ, so a thread of three
  messages is three log entries. That is the debug shape wanted: each broadcast as it was
  written, in order.
- **Timestamp of a joined broadcast.** Members of one broadcast share one server timestamp,
  so the first saved one wins and the per-member scan is gone.
- **Where opening leads.** The entry carries `routeText` from `NotificationRoutes.routeFor`
  rather than inventing a second notion of what a notification opens.
- **The state holder is named for what it is.** `NotificationLogDisplayController` became
  `NotificationLogViewModel`: it extends `ViewModel`, and the rest of this package names such
  classes by their role (`NotificationViewModel`, `MessageViewModel`).
- **Open dropdowns are typed, not delimited.** A `Set<String>` flow replaces the newline-joined
  string of open names; the delimiter-collision caveat disappears with it, and a name that
  contains another name stays separate (tested).
- **Ordering is the controller's.** The local `sortedByDescending` is dropped; the view calls
  `NotificationController.sortNewestFirst`, and the grouping model keeps the order its input
  gives so the order is decided in exactly one place.
- **Profile labels.** The screen keeps `profileText` injected. The default only knows the
  reference, so it labels it unknown; the shared `profileLabelOf` in `ui/components` resolves
  a reference against the saved users the way admin listings already do, and a caller that
  wants real names passes that lookup. Upstream's inbox screen and admin screen are not edited
  here - wiring this helper into them stays with whoever registers those screens.

Not wired: the screen body is not registered into navigation, and the admin/inbox integration
is left to the owner of those files.

Test placement: `notificationTests/NotificationLogTest.kt`, beside the other notification
suites. It drives the state holder and the grouping model through the shared
`FakeNotificationRepository` (the same contract the real repository implements), covering:
the ideal flow (records read, grouped newest first, every receiver counted, the route each
entry carries), no records at all, a read failure, an unserved record inside a served
broadcast, a hundred records over three broadcasts, two records of one message thread
staying separate, two draws never sharing one entry, an empty dropdown name, one log name
that carries another, mashing dropdowns for twenty rounds (an even number of taps ends
closed, an odd one ends open), stopping the listener and stopping it again, the back press
running the lambda it was handed, and the profile-label cases.
