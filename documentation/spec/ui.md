# ui — screens, shared window parts, and the pure helpers inside them

Spec file for the `com.example.campbnb.ui.*` packages (`CampBNB/app/src/main/java/com/example/campbnb/ui/`,
12 directories).

Most of `ui/` is Composables — they DRAW and call callbacks; a Composable is never a JVM
unit target. But several files also carry PURE file-level functions and public enums/data
classes, and those ARE unit-reachable. This file splits accordingly: first the testable
pure surface with exact rules, then the screens at wiring-contract depth.

IMPORTANT LAYOUT FACT: Kotlin package names do not always match directories here.
`ui/screens/messagesScreen.kt` declares package `ui.inbox`, `ui/screens/SearchScreen.kt`
declares `ui.search`, `ui/screens/CampsiteDetailsScreen.kt` declares `ui.details`. Resolve
symbols by the package line, never by the directory.

Written against branch `wyatt-us-01.05.02` (tip `c985351` plus working tree), 2026-11-22.

## Testable pure surface (package — exact contracts)

A file-level function is reachable from a test package UNLESS marked `private` (Kotlin
file-only visibility). Where a private helper's rule is given, say so — assert its output
through the public entry point, never the helper itself.

### ui.components — the UI role vocabulary

```kotlin
enum class UserRole(val label: String) { CAMPER("Camper"), OWNER("Owner"), ADMIN("Admin") }
```
Enhanced enum with a `label` property (`"Camper"`/`"Owner"`/`"Admin"` are the label values).
The UI-role type used by HomeScreen/SettingsScreen/`Screen.Home` payloads. Components.kt
also holds the shared widgets (`GradientButton`, `BackArrow`, `TextField`…, drawing only).

`ui.components/RoleMapping.kt` — receiver-style maps, TOTAL, pure:
- `fun Role.toUserRole(): UserRole` — ENTRANT→CAMPER, OWNER→OWNER, ADMIN→ADMIN.
- `fun UserRole.toRole(): Role` — CAMPER→ENTRANT, the other two by name.
- `fun UserRole.toAppView(): AppView` — CAMPER→EntrantView, OWNER→OwnerView, ADMIN→AdminView.
- `fun AppView.toUserRole(): UserRole` — exact inverse.
(`Role` is the stored role enum, models.md.)

`ui.components/ProfileLabel.kt`:
- `fun profileLabelOf(userId: String, users: List<User>): String` — empty `userId` →
  `"(no profile)"`; no saved user with `id == userId` → `"unknown ($userId)"`; the user's
  name TRIMMED and empty → `"(no name)"`; otherwise the TRIMMED name.
- `val unresolvableProfileLabel: (String) -> String = { userId -> profileLabelOf(userId, emptyList()) }` —
  the stable resolver when there are no saved users (empty users list ⇒ every non-empty
  id draws `"unknown (<id>)"`).
Both PURE, directly unit-reachable (build `User`s per models.md).

### ui.shared — role maps (second spelling) and small providers

`ui.shared/RoleMapping.kt` carries the SAME pairs in standalone-function style, and these
differ slightly in shape from the components/ ones:
- `toUserRole(Role)`: OWNER→OWNER, ADMIN→ADMIN, EVERYTHING ELSE→CAMPER.
- `toRole(UserRole)`: OWNER, ADMIN by name; everything else→ENTRANT.
- `viewFor(Role): AppView`: OWNER→OwnerView, ADMIN→AdminView, everything else→EntrantView.
- `roleFor(AppView): UserRole`: OwnerView→OWNER, AdminView→ADMIN, everything else→CAMPER.
These four are exhaustive-total by fallback (the `when`-style maps in ui.components are
exhaustive by case) — treat both spellings as ONE contract; a test may target either, and
they agree on every enum value.

`ui.shared/CameraFrame.kt` (US 01.12.01 provider seam):
- `interface CameraFrameSource { fun requestFrame(onFrame: (ByteArray?) -> Unit) }`
- `class NoCameraFrame : CameraFrameSource` — every request answers `onFrame(null)`.
JVM-reachable: construct `NoCameraFrame()` and assert the callback receives null. The
in-source note records why a real provider cannot exist on this platform (camera2
`CameraManager` is a final class with a package-private constructor; frame planes keep
bytes in a `java.io.ByteBuffer` type this runtime no longer exposes) — matches the
night-run finding; do not try to build a camera provider in a test. The androidx.camera
path (US 01.05.02, live scanner) CAN now start the device camera and the scanner screen
shows its live preview, but that path hands back no picture bytes either, so this seam
stays frameless on the device.

`ui.shared/DeviceId.kt`: `object DeviceId { fun get(context, src: () -> String = { …ANDROID_ID… }): String = src() }` —
returns whatever `src()` returns (the real source is `Settings.Secure.getString(context.contentResolver, ANDROID_ID)`).
Needs a Context to call ⇒ outside JVM units; its `src` default-parameter IS the injection
point ("Can be overriden for testing" per its doc). Screens take `deviceID` as a plain
value (app.md), so nothing below needs `DeviceId` itself.

`ui.shared/ObserveModel.kt`: `@Composable observeModel(model)` — redraws the calling screen
whenever an `ObservableModel` calls `notifyViews()` (models.md owns both types). Wiring only;
the TODO block in this file is a developer note (future rating-section placement), not a contract.

`ui.shared/Shared.kt` — all Composables:
- `AppViewModificationIsValidForCurrentWindowChecker(returnTo, viewSetting: AppViewSetting, returnIfAppViewIs: List<AppView>)`:
  observes the live flow; `LaunchedEffect` keyed on the view value → exactly ONE `returnTo()`
  call per change to a flagged view (not once per frame). The shared "leave this window when
  the view changed to a bad one" mechanism.
- `BarWithBackChevronAndHeaderCentered(returnFunc, headerTitle)`: back chevron (clickable→returnFunc)
  + centered header text + invisible recentering chevron.
- `fun<T> AppDropDownMenu(entries, onSelect, onDismissed, textRenderer, expanded = false,
  centered = false)`: each entry's click = `onSelect(entry)` THEN `onDismissed()`; `centered =
  true` renders the opened items as centered full-width rows (its only caller today is the
  app-view switch, which passes true — feature 3).
- `fun<T> AppSwitchMenu(entries, selected, onSelect: (T, Boolean) -> Unit, textRenderer)`: one
  Switch row per entry; `onSelect(entry, switchedValue)` on toggle; row enabled iff
  `selected.contains(entry)`.
- `enum class SearchMenuItemOption { FILTER, SEARCH_HISTORY, QR_SCANNER }` +
  `searchBurgerMenu(expanded, onSelect, onDismiss = {}, active = { _ -> false })`: THE one
  source of the search-bar hamburger items — labels exact (`"Filter"`, `"Search History"`,
  `"QR Scanner"`), each item's click = `onSelect(option)` THEN `onDismiss()`. Explore's bar
  and the search view's bar both call this, so the two burgers can never list different
  items (fix 11); a caller supplies only what choosing means in its view, and
  `active(option)` appends `" ✓"` to options whose section is already showing.

### ui.screens — home-bar vocabulary (HomeScreen.kt)

```kotlin
enum class HomeDestination { EXPLORE, MESSAGES, BOOKINGS, RESERVATIONS, INBOX, PROFILE,
    MY_CAMPSITES, REPORTS, FLAGGED, ADMIN_PROFILE_VIEW, ADMIN_NOTIFICATION_VIEW }
data class HomeTab(val label: String, val destination: HomeDestination)
fun tabsFor(role: UserRole): List<HomeTab>
```
`tabsFor` is PURE and unit-reachable — exact display order and labels:
- CAMPER: Explore(EXPLORE), Messages(MESSAGES), Bookings(BOOKINGS), Reservations(RESERVATIONS),
  Inbox(INBOX), Profile(PROFILE).
- OWNER: Explore, Messages, My Campsites(MY_CAMPSITES), Inbox, Profile.
- ADMIN: Explore, Messages, Reports(REPORTS), Flagged(FLAGGED),
  Profiles(ADMIN_PROFILE_VIEW), Notifications(ADMIN_NOTIFICATION_VIEW).
Explore is always FIRST. `HomeScreen` itself: tabs are RE-DERIVED from the live
`AppView` (`AdminView→tabsFor(ADMIN)`, `OwnerView→OWNER`, `EntrantView→CAMPER`) via a
`LaunchedEffect` on the flow — the route's `role` argument only seeds the initial list;
Back (BackHandler) resets to tab 0 (Explore); the selected tab SEEDS from `initialTab`
(clamped to the role's tabs): a plain `remember` resets exactly on a re-mount, so `goHome`
naming a tab lands on that tab, while in-place tab switches keep the value between
re-renders; read sites clamp the index to the live (possibly shorter) tab list.
Composable half: not a unit target. Note `REPORTS`/`FLAGGED`/`PROFILE` destinations reach
the graph's `else →` branch (`app.md`) — placeholder content today; the enum values exist
because the bar vocabulary is settled ahead of those screens.

`ui.screens/BookingsScreen.kt`: `enum class BookingTab { CONFIRMED, PENDING }` (vocabulary;
public). `ui.screens/ExploreScreen.kt` declares the public `CampsiteCardData` data class —
fields used at every call site: `id, name, location, maxOccupancy` (+ optional
`rating, ratingCount` supplied only by `toCardData`); constructible in tests, defaults
unasserted (not read off source here). `ui.screens/ReservationScreen.kt` is the
reservations renderer (params per app.md). `ui.screens/DeviceSignInScreen.kt`, `QrScannerScreen.kt`,
`QrOldCodeNoticeScreen.kt` (`back: () -> Unit = {}` fires on its Back button), `SetUpScreens.kt`
(StartScreen/SignUpScreen/RoleSelectScreen) — Composables, contract = their call sites in app.md.
`QrScannerScreen` also opens the device camera via androidx.camera when the screen opens and
draws its live preview once the preview surface arrives (US 01.05.02, live scanner); screen-internal
wiring only — no parameter or call-site change, and the frame-reading seam is untouched. `ui.screens/messagesScreen.kt` (package **ui.inbox**): private
drawing helpers `ConversationList/ConversationRow/ChatThread/MessageBubble` +
`private relativeTime(seconds: Long?): String = seconds?.let { DateUtils.getRelativeTimeSpanString(seconds*1000L, System.currentTimeMillis(), MINUTE_IN_MILLIS).toString() } ?: "Just now"`
— null seconds ⇒ `"Just now"`; file-PRIVATE (and DUPLICATED identically in
`ui.inbox/MessageActivity.kt`) ⇒ NOT directly unit-reachable; no test should name it.

### ui.search (file `ui/screens/SearchScreen.kt`)

`private dateLabelFor(filters: SearchFilters): String` — file-PRIVATE, spec for reading:
`filters.startDate` empty → `"Any dates"`; endDate empty OR equal to startDate → startDate
alone; else `"<start> to <end>"`. `SearchFilters`/`SearchScreen` are public
(params per app.md call site; the filter fields `startDate/endDate` are STRINGS in
`yyyy-MM-dd` — the same wire format as `fromPickerMillis` and models' date fields).
`dateLabelFor` itself cannot be called from a test package.
The Search view's burger is the shared `searchBurgerMenu` (above, ui.shared): choosing an
item toggles that section (with its `✓` decoration for what is showing) or fires
`onQrClick`; `initialShowHistory` — one more parameter per app.md — opens the "Most Recent"
section straight away, which is how the Explore burger's Search History item lands this view.

### ui.settings (file `ui/settings/SettingsScreen.kt`)

```kotlin
enum class SettingsGroupId { APP_VIEW_SWITCH, ADMIN_SETTINGS, ENTRANT_SETTINGS, OWNER_SETTINGS }
fun groupLabel(groupId: SettingsGroupId): String          // PURE — reachable
fun settingsGroupsFor(role: UserRole, currentView: AppView): List<SettingsGroupId>  // PURE — reachable
```
`groupLabel` exact (lower-case in-source): APP_VIEW_SWITCH→`"app view switch"`,
ADMIN_SETTINGS→`"admin settings"`, ENTRANT_SETTINGS→`"entrant settings"`, OWNER_SETTINGS→`"owner settings"`.
`settingsGroupsFor` exact: ADMIN with AdminView → `[APP_VIEW_SWITCH, ADMIN_SETTINGS]`;
ADMIN while viewing OwnerView → `[APP_VIEW_SWITCH, ADMIN_SETTINGS, OWNER_SETTINGS]`;
ADMIN while viewing EntrantView → `[APP_VIEW_SWITCH, ADMIN_SETTINGS, ENTRANT_SETTINGS]`
(the role's own settings mount BELOW admin settings while the admin views as that role);
OWNER → `[OWNER_SETTINGS]`; CAMPER → `[ENTRANT_SETTINGS]`.
`SettingsScreen(back, role, viewSettings: AppViewSettingController, entrantSettings = EntrantSettingsController(), ownerSettings = OwnerSettingsController(), adminSettingsContent = { AdminSettingsPlaceholder() }, notificationPreferences: NotificationPreferencesService = NoNotificationPreferencesService(), appSettings: AppSettingsService = NoAppSettingsService(), userIdForSettings: String = "")`
mounts one dropdown per group; expansion state lives in a `remember`ed
`SettingsDisplayController` (viewModels.md). The app-view switch group's dropdown renders
its items as centered rows — the shared `AppDropDownMenu` with `centered = true` (feature 3). Its doc warns: controllers should be kept by
the CALLER (defaults are standalone-use only) — app.md shows the shared instances. The
app view switch group's selection does TWO things per chosen view: `viewSettings.changeViewTo(view)`
AND `appSettings.setAppViewChoice(userIdForSettings, view)` (data.md owns the record and
its merge rules: saving one group never erases the others; `applyLoadedSettings` is the
restart half, app.md's `init` runs it). The placeholder owner/entrant entries have NO
editing affordance yet, so the service's other groups save only from controller-level calls.

### ui.inbox — correspondence tabs + the live inbox ViewModel

`ui.inbox/CorrespondenceActivity.kt`:
```kotlin
object CorrespondenceTabs { const val NOTIFICATIONS_TAB = "Notifications"; RECEIVED_TAB = "Received"; MESSAGES_TAB = "Messages" }
```
`CorrespondenceScreen(navController, tabLabels, openTab, notificationsTabContent, receivedTabContent = {}, messagesTabContent)`
— layout-only (the CALLER decides which tabs a viewer has; app.md owns that rule). Its
exact internal rules, for reading: the opened tab is `tabLabels.indexOf(openTab).coerceAtLeast(0)`
— an unknown/unavailable tab name opens the FIRST tab rather than failing; content dispatch
is by the SELECTED label, not position: MESSAGES→messages content, RECEIVED→received content,
anything else→notifications content. Header text `Correspondence`; `Back` button calls
`navController.popBackStack()`. Not a unit target (Composable); `CorrespondenceTabs` values
ARE vocabulary an integration reads.

`ui.inbox/NotificationViewModel.kt` — a real JVM-reachable ViewModel (no Context needed):
```kotlin
sealed interface InboxState { data object Loading; data class Ready(items: List<Notification>); data class Error(message: String) }
class NotificationViewModel(controller: NotificationController, userId: String) : ViewModel()
    val state: StateFlow<InboxState>     // starts Loading; init RUNS start() at construction
```
Behavior (all observable through `state` + controller calls — same discipline as
viewModels.md):
- `start()`: stops any previous listener, resets to Loading; blank `userId` →
  `Error("No signed-in user was found.")`, NO listener; else `controller.listen(userId, onUpdate, onError)`
  and the returned stop function is kept (`onCleared()` invokes it).
- `onUpdate(update)` → `Ready(NotificationController.sortNewestFirst(update.all))` — the
  inbox orders EVERYTHING newest-first with the controller's companion rule (controller.md:
  unstamped records sort first).
- `onError(error)` → `Error(error.message ?: "Could not load notifications.")`.
- `open(notification)`: marks read (`controller.markRead(notification.id)`) ONLY when
  `notification.status == UNREAD` — opening a read record does nothing.
- `markAllRead()`: if the state is NOT Ready → returns (nothing happens); else marks
  every UNREAD record's id.
Construction seam: the controller argument is a REAL `NotificationController` stood up over
a fake `NotificationRepository` exactly as controller.md's "Standing controllers up" shows —
then this ViewModel is constructed (init listens), the fake streams records, and the test
asserts `state.value` (`Ready` order, blank-id `Error` copy, `open` on a READ record
makes no `markRead` call). This is the same fake idiom as data.md/controller.md — it
compiles against the real `InboxState`.

`ui.inbox/MessageActivity.kt`: `MessagesScreen(viewModel: MessageViewModel, currentUserId: String)`
draws the chat list/threads straight off `viewModel.state` (`collectAsState`) — Loading ⇒
spinner; its own PRIVATE `relativeTime` (identical rule to messagesScreen's, "Just now"
for null) renders message sent-times; bubbles show `message.content`. `MessageViewModel`
semantics live in viewModels.md. `ui.inbox/NotificationActivity.kt`: `NotificationInboxScreen(viewModel, navController)` —
draws the inbox from `NotificationViewModel.state` above. `ui.inbox/NotificationViewModel.kt`
is the ViewModel class itself (note: it sits in `ui/inbox/` — directory and package AGREE
here; it is only the OTHER files that mismatch).

### ui.admin — the browsed profiles list (pure, exact)

`ui.admin/AdminProfilesScreen.kt` (the "Profiles" admin tab; `observeModel` redraw on user change):
```kotlin
class ProfileRow(val nameLabel: String, val emailLabel: String, val rolesLabel: String) {}  // plain class — construct, don't subclass
private profileSortKeyOf(user: User): String   // name TRIMMED empty → "zz~" + user.email ; else name.lowercase()
private nameLabelOf(user: User): String        // trimmed empty → "(no name)" ; else the TRIMMED name
private emailLabelOf(user: User): String       // email empty (untrimmed check!) → "(no email)" ; else email TRIMMED
private roleLabelOf(user: User): String        // roles empty → "(no role)" ; else each role's enum NAME joined by "/" (e.g. OWNER → "OWNER")
fun profileRowsFor(users: List<User>): List<ProfileRow>      // PUBLIC
fun profileRowText(row: ProfileRow): String = "${row.nameLabel}  -  ${row.emailLabel}  -  ${row.rolesLabel}"  // PUBLIC — two spaces around the dash
```
The four label helpers are file-PRIVATE — assert their rules THROUGH `profileRowsFor` +
`profileRowText`. `profileRowsFor` = `users.sortedBy { profileSortKeyOf } .map { ProfileRow(nameLabelOf, emailLabelOf, roleLabelOf) }`:
one row per user, name-first lowercase order, nameless users LAST (the `"zz~"` marker sorts
after real names; in-source PLACEHOLDER NOTE says the ordering is a chosen behavior, so a
test pinning exact order should phrase the rule, not pretend the request fixed it).
Sort ties between users with equal keys are NOT spec'd (stable-sort detail — do not test it).
`roleLabelOf` uses the enum's generated name (models.md's `Role` has no custom name) ⇒
uppercase tokens `ENTRANT`/`OWNER`/`ADMIN` separated by `/` in the user's stored roles ORDER
(no dedup, no sort). Empty users list ⇒ empty list ("no users means no rows").
`ui.admin/AdminActivity.kt` + `PolicyActivity.kt` are near-empty placeholder classes
(admin actions go through `AdminController`, controller.md).

### ui.campsite — rating and lottery text (pure) + owner screens

`ui.campsite/RatingStars.kt`:
- `fun starsFor(average: Double): Int` — `Math.round(average)` (half-away-from-zero) clamped
  to 0..5. PURE, reachable. Note it is `java.math.Math.round` (Java semantics): −0.5 rounds to 0.
- `fun ratingSummary(average: Double, count: Int): String` — count 0 ⇒ `"No ratings yet"`;
  else `String.format(Locale.US, "%.1f", average)` + `" (1 rating)"` for count 1 or
  `" (N ratings)"` otherwise (exact copy, US locale ⇒ dot decimal separator).
  (`RatingDisplay`/`RatingStars` Composables draw gold/grey `★` chars, testTag `star_<1..5>`;
  stars are TEXT characters — in-source TODO, no screen-reader labels.)
`ui.campsite/LotteryJoinSection.kt`:
- `fun lotteryStatusText(lottery: Lottery?, joined: Boolean, position: Int): String` —
  null lottery ⇒ `"No active lottery"`; joined ⇒ `"You are on the waiting list (Position: $position)"`;
  else ⇒ `"Lottery open for entries"`. PURE.
- `fun lotteryButtonText(model: LotteryModel): String` — reads the model's live fields:
  `entrantLottery == null` ⇒ `"No Active Lottery"`; `entrantIsOnWaitlist` ⇒ `"Leave Lottery"`;
  else `"Enter Lottery"`. Reachable over a FRESH `LotteryModel()` (models.md: defaults ⇒
  `"No Active Lottery"`); drive the fields through models' own setters per models.md.
`ui.campsite/CampsiteScreenData.kt`: `toCardData(campsite, model)` / `toCardList(campsites, model)`
→ `ui.screens.CampsiteCardData` (copy id/name/location/maxOccupancy + the MODEL's
`averageRating`/`ratingCount` for that id) and `toDetails(campsite, model)` →
`ui.details.CampsiteDetails` (same + `tags = campsite.tags` as stored — no amenities merge;
the app.md Details-screen loader is the one that merges). PURE over plain `Campsite` values
+ a `CampsiteModel` (fresh model ⇒ rating 0 / count 0 — models.md). Unit-reachable.
`ui.campsite/CampsiteEditActivity.kt` declares `CampsiteRegistrationForm(onBack, onUpload(campsite, …), existingCampsite = default)` —
the registration/edit form (register vs edit selected by the `existingCampsite` parameter).
`ui.campsite/CampsiteMonitorActivity.kt` — an ACTIVITY class (constructor = navigation
callbacks + `fileDownloadWriter` + `appViewSettings` + `deviceID`; method
`CampsiteMonitor(model, controller)` renders). Params authoritative from app.md's call site.
`CampsiteRatingSection(model, controller, campsiteId, userId)`, `CampsiteOwnerActivity`,
`LotteryJoinSection` (above) — Composables. `CampsiteSearchActivity.kt`/`CampsiteDetailsActivity.kt`
are 3-line placeholder classes.

### ui.lottery — date-picker conversion (pure) + the lottery activities

`ui.lottery/DatePickers.kt`:
- `fun fromPickerMillis(utcMillis: Long): String` — `yyyy-MM-dd` formatted in EXPLICIT UTC
  (`Locale.US`, `TimeZone.getTimeZone("UTC")`). PURE — assert on fixed longs (e.g. a known
  UTC-midnight value maps to its date; DST/timezone of the device must NOT leak in).
- `fun todayAsPickerMillis(): Long` — `models.DateUtils.today()` (models.md: today's `yyyy-MM-dd`)
  PARSED back as UTC-midnight millis. Reachable; pair it with `fromPickerMillis` only
  inside one calendar day (`fromPickerMillis(todayAsPickerMillis())` equals models.md's
  `today()` — but a test straddling midnight may see the next day; prefer the direct
  fixed-long assertions).
- `DateRangePickerModal(onDateRangeSelected: (Pair<Long?, Long?>) -> Unit, onDismiss)` — Composable
  wrapper of Material3's DatePickerDialog/DateRangePicker; confirm = ("OK") hands
  `Pair(startMillis?, endMillis?)` then calls `onDismiss`; cancel = ("Cancel") → `onDismiss` only.
  Lottery/booking date windows are picked through this (LotteryEdit screens).
The four lottery ACTIVITY classes (`LotteryEditActivity`, `LotteryMonitorActivity`,
`LotteryWaitlistActivity`, `LotteryDetailsActivity`): navigation-callback constructors +
render methods called from app.md (recorded there) — `selectedLottery` is carried across
screens by the graph's shared state, not by the activities (app.md). `LotteryEditActivity`
additionally receives a `UserRepository()` for invitation-candidate checks (US 02.04.08).
Their drawing bodies are not unit targets. Truth kept, though: date-range rows display the
picker's UTC-midnight millis on an explicit UTC basis — a device-local formatter showed the
day before on both endpoints west of UTC (fix 13) — and edit renders a creator-only
"Force Close" control delegating to controller.md's `forceCloseLottery` (feature 1).

### ui.notification — the log screen

`ui/notification/NotificationLogScreen.kt`: Composable taking `title`,
`notificationSource: NotificationRepository`, `returnFunc` (optional), `profileText`
(optional resolver — app.md passes `profileLabelOf(...)`), `savedUsers` (optional `UserModel`
— app.md passes the real one for the debug view only, and the view model's watch per viewModels.md
makes late-loaded profiles re-resolve receiver lines; the default is a private model that is never
notified). Reads the log through the
data.md `NotificationCommand.Read` contract and draws the group/line output per models.md's
`notificationLogGroupsFor`/`notificationLogLinesFor`. Nothing testable beyond the lower
packages' contracts; the debug-log title strings (`"Notification Debugging"`, `"Sent
Notifications"`) are app.md integration copy.

### ui.theme, ui.profile, ui.booking — no test surface

`ui.theme/` (Color/Type tokens: `FieldGrey`, `ButtonGradient`, `DataColor`, `DataColorImportant`,
`CampBnbTheme`) — visual tokens, no behavior. `ui.profile/ProfileActivity.kt` and every
`ui/booking/*Activity.kt` (3–4 lines) are empty placeholder classes — future screen
namespaces; today the bookings/reservations renderers are `ui.screens` (BookingsScreen) and
the app-level `ReservationsPanel` (app.md).

## Standing ui up in tests

- Unit-test the PURE members ONLY: role maps (both spellings), `profileLabelOf`/
  `unresolvableProfileLabel`, `tabsFor`, `groupLabel`, `settingsGroupsFor`, `profileRowsFor`
  + `profileRowText`, `starsFor`, `ratingSummary`, `lotteryStatusText`, `lotteryButtonText`,
  `fromPickerMillis`, `toCardData`/`toCardList`/`toDetails`, and the two ViewModel state
  machines (`NotificationViewModel` here, its pattern documented in viewModels.md + controller.md).
- Everything marked private/file-private (`dateLabelFor`, `relativeTime`, the four admin
  label helpers) is NOT nameable from a test — assert through the public entry point that
  draws it, or skip.
- Nothing Composable/Renderer is a unit target; their contracts exist so the test agent can
  PREDICT names and wiring (`HomeDestination`, `BookingTab`, `SettingsGroupId`,
  `CorrespondenceTabs`, `CampsiteCardData`, `ProfileRow`, `InboxState` are all public
  vocabulary in the test's package graph) without instantiating the Composables.
- Fresh model instances (`CampsiteModel()`, `LotteryModel()`) are cheap and legal (models.md)
  — they drive the `…Text`/`…Summary`/`toCardData` helpers' defaults honestly.
- Vocabulary to mirror in names: tab labels (`"Explore"`, `"My Campsites"`, …), settings
  group ids (`"app view switch"`, …), correspondence tabs (`"Notifications"`, `"Received"`,
  `"Messages"`), the profile placeholders (`"(no name)"`, `"(no email)"`, `"(no role)"`,
  `"unknown (…)"`, `"(no profile)"`), rating copy (`"No ratings yet"`, `" (2 ratings)"`),
  lottery button/status copy. `HomeDestination`/`SettingsGroupId` enum members are code
  vocabulary; the label strings are display vocabulary — keep both straight.
- Design observations a test author may legitimately flag (do not test against, note in the test if asserted): the double
  spelling of role maps (shared vs components) is duplication-of-contract; `relativeTime` is
  duplicated across two files; the "unknown tab opens first" fallback and the placeholder
  ordering marker `"zz~"` are documented chosen behaviors.
