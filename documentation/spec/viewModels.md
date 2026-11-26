# viewModels — the live state screens observe

Spec file for the `com.example.campbnb.viewModels` package
(`CampBNB/app/src/main/java/com/example/campbnb/viewModels/`, 6 files).

A ViewModel owns ONE live value and exposes it as a READONLY `StateFlow`, so the
matching screen re-renders when the value changes (`MutableStateFlow` kept private,
`asStateFlow()` exposed publicly). This package is where the UI's observable state
machines live; the screens themselves (`ui.md`) only read these flows.

Written against branch `wyatt-us-01.05.02` (tip `5275878` plus working tree), 2026-11-23.

## House shape (AppViewSettings.kt)

```kotlin
enum class AppView { AdminView, EntrantView, OwnerView }   // declared HERE, this package

class AppViewSettingController : ViewModel()      // plain class: construct, never subclass
    private val _viewSetting = MutableStateFlow(AppView.EntrantView)   // DEFAULT: EntrantView
    private var viewChoiceWasMade = false                              // explicit-choice tracker
    val viewSetting: StateFlow<AppView>                                // readonly wrapper
    fun changeViewTo(newView: AppView)                                // sets the value; counts as a choice made
    fun applyViewDefaultIfNoneChosen(newView: AppView)               // role default: no-op once ANY view has been chosen
    fun applyLoadedSettings(record: AppSettingsModel)                // names a view → changeViewTo (a saved choice counts as made); empty/unknown = no-op
    fun toAppViewSetting(): AppViewSetting                            // wraps the LIVE FLOW

class AppViewSetting(val setting: StateFlow<AppView>) : ViewModel() {}
```

- `AppViewSetting` holds the `StateFlow` itself, never a snapshot: anything latched on
  one re-renders as the controller changes the setting. The instance never changes.
- Gate: a view from a real choice — a saved record that named one, or a switch — keeps
  that choice across lifetimes: `applyViewDefaultIfNoneChosen` changes nothing once any
  choice exists, so sign-in role defaults cannot clobber a saved or switched view
  (the reported failure of the admin's settings view). Users who never chose a view
  still get the role default at sign-in.
- `AppView` is the app-wide view vocabulary (`app.md` routes branch on it;
  `ui.md` view-switching reads `viewSetting`).

`DeviceSignInController : ViewModel()` (DeviceSignInViewModels.kt, US 01.09.01):

- Exposes `deviceId: StateFlow<String>` (starts EMPTY) and `notice: StateFlow<String>`
  (starts EMPTY).
- `rememberDevice(readId)`: writes ONLY when nothing is remembered yet AND `readId` is
  not blank — first readable id wins; a blank read leaves nothing; later reads never
  replace an id.
- `noticeFrom(message)` sets the notice (empty string = none); `clearNotice()` drops it.

These two ViewModels touch nothing external — JVM-reachable: construct, call members,
assert `flow.value`. (Constructing any `: ViewModel()` class needs no Android `Context`.)

## SimpleFactory.kt

`fun <T : ViewModel> simpleViewModelFactory(create: () -> T): ViewModelProvider.Factory` —
the generic factory for ViewModels that need constructor parameters (an anonymous
`ViewModelProvider.Factory` object; `create()` is called per requested class, cast through
`Class<VM>`). Wiring utility; nothing to test beyond calling `create`.

## MessageViewModel.kt — state of the Messages tab

```kotlin
sealed interface MessagesState {
    data object Loading
    data class Ready(conversations: List<Conversation>,
                     openConversation: Conversation? = null,   // null = chat LIST showing
                     thread: List<Message> = emptyList())
    data class Error(message: String)
}
class MessageViewModel(controller: MessageController, userId: String) : ViewModel()
    val state: StateFlow<MessagesState>       // starts Loading
```

Behavior (all of it observable through `state` plus the controller's calls):

- `start()` runs at construction (`init`): stops any previous listener, resets to
  Loading. Blank `userId` → `Error("No signed-in user was found.")` and NO listener.
  Otherwise `controller.listenForUser(userId, onUpdate, onError)`; the returned stop
  function is kept (`onCleared()` invokes it on teardown).
  `onUpdate(messages)` stores them and sets loaded, then `publish()`es;
  `onError(error)` → `Error(error.message ?: "Could not load messages.")`.
- `open(reservationId)` / `close()` set the open chat (or null) and `publish()`.
- `publish()` does NOTHING until `loaded` (first snapshot arrived); then it builds
  the Ready value: `Conversations.group(userId, allMessages)` (pure member,
  `controller.md`); the open chat is the conversation whose `reservationId` matches,
  or — when none matches — a BARE `Conversation(reservationId = id)`: a chat with no
  messages opens EMPTY; `thread = Conversations.thread(...)` when open, else empty.
- `send(text, onResult: (Result<Message>) -> Unit = {})`: with NO chat open →
  `Result.failure(IllegalStateException("No chat is open"))`, nothing sent; with both
  parties known (`Conversation.hasBothParticipants`) → `controller.reply(open, userId,
  text, onResult)`, otherwise `controller.sendMessage(open.reservationId, userId, text,
  onResult)`. (`controller.md` carries both message rules.)
- OPENING A CHAT READS IT: after publishing with a chat open, messages in the thread
  addressed to `userId` (`recipientId == userId` AND `status == UNREAD`) get
  `controller.markMessagesRead(ids)` — each id is only ever sent ONCE (guarded by an
  in-memory set, so the snapshot following the write cannot re-trigger); a failed mark
  REMOVES those ids from the set, so a later open retries them.

Unit reach: constructing this ViewModel requires a real `MessageController`, whose
`ReservationRepository` needs real engines — OUTSIDE the JVM unit surface. Assert the
chat-list/thread semantics through `Conversations.group/thread` and `Conversation`
directly (`controller.md`); this spec section is the integration contract.

## NotificationLogViewModel.kt — the notification log screen's state

```kotlin
sealed interface LogViewState {
    data object Loading
    data class Ready(logs: List<NotificationLogGroup>, lines: List<NotificationLogLine>)
    data class Error(message: String)
}
class NotificationLogViewModel(
    source: NotificationRepository,               // SEAM — fake this (interface)
    showDbIds: Boolean = true,                    // may this viewer read a raw db key?
    profileLabel: (String) -> String,             // resolver: pass a plain lambda
) : ViewModel()
    val state: StateFlow<LogViewState>            // starts Loading
    val openLogs: StateFlow<Set<String>>          // which dropdowns are open, starts EMPTY
    fun entryIdFor(logId: String): String         // the db key an entry is looked up by
```

Runs `readLog()` at construction (`init`).

- `readLog()`: sets Loading, then `source.run(NotificationCommand.Read(...))`
  (data.md's one-read-never-followed rule; the log never subscribes — a whole-collection
  listener would re-deliver every record). SUCCESS path: records are ordered with
  `NotificationController.sortNewestFirst` (the companion — unstamped FIRST,
  `controller.md`) and grouped with `notificationLogGroupsFor` (`models.md`); then
  `showNotificationLog(groups)` stores them and sets Ready with
  `notificationLogLinesFor(groups, <current openLogs>, profileLabel)` (`models.md`).
  FAILURE path: `Error(error.message ?: "Could not read the notification log.")`.
- `toggleLog(logName)`: empty name changes NOTHING (early return — note `isEmpty`,
  so a whitespace name IS a valid dropdown key). Otherwise the name is added to /
  removed from the open set. The open state is kept EVEN BEFORE any successful read —
  the next read draws it; until a read has succeeded no Ready state is invented.
  After a successful read, toggling REBUILDS the lines for the same entries with the
  new open set (only differing lines redraw — the Ready value changes).
- `reflowLines()`: RE-DERIVES the lines for the last-read entries WITHOUT re-reading
  (NOOP before a successful read; the open set is kept as-is). The screen calls it whenever a
  passed `UserModel` reports users changing (the debug view watches the real model, app.md) —
  profiles loaded after the log opened re-resolve profile references — bar senders and
  receiver lines alike — to names.
- The log is read again ONLY on demand (`readLog()` re-run by the screen's refresh tap).
- `showDbIds` is handed to `notificationLogLinesFor` (`models.md`) on every line-building
  step, so a viewer who may not read raw database keys never sees one.
- `entryIdFor(logId)` answers the `notificationId` of the group a log line belongs to, or
  `""` when no read has succeeded or the name matches no entry. The screens use it as the
  id to hand a removal: the log's own name IS the entry's db key, so a delete control needs
  no extra vocabulary of its own.

This ViewModel IS JVM-reachable: fake the `NotificationRepository` (in-file fake of the
interface handling `NotificationCommand.Read`), pass any `profileLabel` lambda
(`{ id -> "unknown" }` works), then call members and assert `state.value` — a
`Ready(logs, lines)` whose groups/lines follow `models.md`'s
`notificationLogGroupsFor`/`notificationLogLinesFor` contracts.

## SettingsViewModels.kt — the Settings modal's live values

PLACEHOLDER NOTE in-source: the entry names/defaults below are placeholders for not-yet-
decided settings; the STRUCTURE holds replacements. The strings below are the current
truth (code is truth) — tests that assert them pin placeholders, so prefer asserting the
MECHANICS (what toggling does) over the placeholder text, and never assume these names
are final product vocabulary.

- `EntrantSettingsController : ViewModel()` — one readonly `StateFlow` each:
  `bookingSortSetting` (String, default `"Newest first"`), `inboxAlertsSetting`
  (String, default `"All activity"`), `messageFilterSetting` (String, default
  `"Unread first"`), `notificationSettings` (List<NotificationSettings>, default
  `[PUSH]`). Setters `changeBookingSortTo/changeInboxAlertsTo/changeMessageFilterTo`
  set values directly. `toggleNotificationSetting(setting, enabled)`: enabling ADDS the
  setting only if absent (never a duplicate); disabling REMOVES it. Multiple settings
  may coexist (e.g. PUSH and DOWNLOAD). `applyLoadedSettings(record: AppSettingsModel)`
  applies ONLY the record's NON-BLANK entrant choices (through those setters); blank
  ones leave defaults; the record's notification settings are NOT part of this group
  (they live in `NotificationPreferencesService`, `data.md`). `applyLoadedNotificationSettings(enabled)`
  sets `notificationSettings` to the loaded list AS LOADED — an empty list is a real
  choice (all notifications off), not an absent record; `AppContainer.init` (`app.md`)
  feeds it from `NotificationPreferencesService.loadNotificationSettings` so the
  Profile view shows a user's saved toggles on next launch.
- `OwnerSettingsController : ViewModel()` — `listingVisibilitySetting` (String, default
  `"Visible to entrants"`), `lotteryOpenSetting` (Boolean, default TRUE — whether the
  owner's lotteries accept new entrants; note this is view-local UI state, distinct from
  `Lottery.statusAt` in `models.md`), `availabilityNoticeSetting` (String, default
  `"Open calendar"`); setters `changeListingVisibilityTo / changeLotteryOpenTo(open) /
  changeAvailabilityNoticeTo`. `applyLoadedSettings(record)` applies non-blank strings
  through the setters and takes `record.ownerLotteryAccepting` directly — the record's
  boolean default EQUALS this controller's default, so an absent record changes nothing.
- `SettingsDisplayController : ViewModel()` — dropdown expansion. `expandedStateOf(groupId)`
  returns the group's readonly `StateFlow<Boolean>`; known groups
  `"APP_VIEW_SWITCH"`, `"ADMIN_SETTINGS"`, `"ENTRANT_SETTINGS"`, `"OWNER_SETTINGS"` each
  own a flow (all start CLOSED / false); ANY OTHER name shares ONE fallback flow — toggling
  `"BOGUS"` twice flips that shared flow, and two unknown names alias each other. That is
  the current behavior; a test may assert it (a test-writer who considers it a defect
  should say so, not guess a different behavior). `toggleGroup(groupId)` flips the
  matching flow.

All three are plain classes (no Context, nothing external): JVM-reachable — construct,
toggle, assert flow values.

## LotterySelectedViewModel.kt — the lottery selected across the lottery screens

```kotlin
class LotterySelectedViewModel {
    val selectedLottery: StateFlow<Lottery?>   // starts null
    fun updateLottery(new: Lottery?): Unit      // plain write
}
```

No seams, no init side effects — a single state cell. The APP ROOT builds it once
(`remember { LotterySelectedViewModel() }`) and hands it to `lotteryGraph` (app.md): the
monitor clears it before opening edit and records the selection before opening details, and
every lottery window reads through `selectedLottery`. IS JVM-reachable, trivially: assert
the cell starts null, `updateLottery(x)` ⇒ `.value === x`, `updateLottery(null)` clears it.

## Standing viewModels up in tests

- ViewModels are plain classes: construct the REAL one, never subclass. `init` runs at
  construction — a fake seam must already behave when the object comes into being
  (e.g. `NotificationLogViewModel`'s fake answers `NotificationCommand.Read` on
  construction).
- Seam-typed constructor parameters (`NotificationRepository`) get in-file fakes per
  `data.md`; function-typed parameters (`profileLabel`) are plain lambdas. Parameters
  typed as non-fakeable classes (`MessageController`) force the real collaborator and
  with it its engine requirement — such ViewModels stay outside the JVM unit surface;
  test their state machines through the pure members they call instead.
- The observation channel is `StateFlow.value` — never Jetpack rendering. Call a setter,
  read `.value`.
- Naming: `AppView`, route names, and group ids (`"APP_VIEW_SWITCH"` etc.) are the
  vocabulary here; settings placeholder strings are explicitly NOT settled vocabulary.
