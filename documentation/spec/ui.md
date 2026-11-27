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

Written against branch `wyatt-us-01.05.02` (tip `5275878` plus working tree), 2026-11-23.

## Testable pure surface (package — exact contracts)

A file-level function is reachable from a test package UNLESS marked `private` (Kotlin
file-only visibility). Where a private helper's rule is given, say so — assert its output
through the public entry point, never the helper itself.

### ui.components — the UI role vocabulary

```kotlin
enum class UserRole(val label: String) { CAMPER("Camper"), OWNER("Owner"), ADMIN("Admin") }
```
Enhanced enum with a `label` property (`"Camper"`/`"Owner"`/`"Admin"` are the label values).
The UI-role type used by HomeScreen/ProfileScreen/`Screen.Home` payloads. Components.kt
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
- `val anonymousProfileLabel: (String) -> String = { _ -> "unknown" }` — the label an
  unresolvable profile reference takes for a reader who may NOT read the raw database key
  it stands for: the bare word, with no key they could not look up. Which of the two a
  listing draws is the caller's decision, keyed on the live `AppView` setting (app.md):
  only a viewer whose view is the admin's gets the key.
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
in-source note records why a real provider cannot be written HERE, and that was re-read
off the compiler this round — do not try to build a camera provider in a test or in app
code. What the platform DOES resolve: `Preview.Builder`, `CameraSelector.of`,
`ImageProcessor.Builder` (so the live preview works, US 01.05.02). What it does NOT:
`androidx.camera.core.ImageAnalysis.Builder` (the class resolves, its nested Builder does
not), `androidx.camera.core.ImageSource` (absent), `ImageAnalysis.processWithProcessor`
and `ImageAnalysis.attachImageProcessor` (absent) — so no analyzed frame can be started,
which is the whole reason the seam stays frameless. (The older camera2/`java.io.ByteBuffer`
wording below is the OTHER dead path, kept for history: `CameraManager` is a final class
with a package-private constructor and frame planes keep bytes in a buffer type this
runtime no longer exposes.) A QR DECODER over pixels is separately absent: Barleader's
`BarcodeReader.read` accepts only the unnameable `BufferedImage!` that `javax.imageio.ImageIO`
returns, and that runtime is not on the device, so a frame's bytes could not be read even
if a frame could be had.

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

`ui.shared/SharedImage.kt` (US 02.01.02, US 01.08.02 — the screen side of ImageRepository):
- `fun decodeStoredImage(stored: String): ImageBitmap?` — the Base64 text of image bytes →
  a bitmap; `null` for blank text or text that is not a valid image. Accepts plain Base64
  and `data:image/...;base64,...` spelling. JVM-reachable only through the null arms (the
  decode itself runs on Android picture decoders): blank and not-Base64 both answer null.
- `@Composable fun StoredImage(stored, contentDescription, modifier = Modifier, contentScale =
  ContentScale.Fit)` — draws the picture in [stored], decoding OFF the main thread; draws
  NOTHING while decoding or when the text is blank or not an image, so a caller puts its own
  fallback (initials, say) underneath.
- `@Composable fun rememberImagePicker(maxEdge, onPicked: (String) -> Unit, onFailed: () -> Unit):
  () -> Unit` — prepares the phone's photo picker and returns a function that opens it (call
  it from a button's click). A chosen picture is shrunk and turned into text by
  `ImageRepository` off the main thread, then handed to `onPicked`; a choice that is not an
  image or is too big to store runs `onFailed`; backing out of the picker calls neither.

### ui.screens — home-bar vocabulary (HomeScreen.kt)

```kotlin
data class HomeTab(val label: String, val destination: Screen)
fun tabsFor(role: UserRole): List<HomeTab>
```
`tabsFor` is PURE and unit-reachable — exact display order and labels:
- CAMPER: Explore(`Screen.Explore`), Messages(`Screen.Messages`), Bookings(`Screen.Bookings`),
  Reservations(`Screen.Reservations`), Inbox(`Screen.Inbox`), Profile(`Screen.Profile`).
- OWNER: Explore, Messages, My Campsites(`Screen.MyCampsites`), Inbox, Profile.
- ADMIN: Explore, Messages, Reports(`Screen.Reports`), Flagged(`Screen.Flagged`),
  Profiles(`Screen.AdminProfileView`), Notifications(`Screen.AdminNotificationView`).
Explore is always FIRST. A tab's `destination` is the `Screen` route its content registers
under Home's nested graph (app.md says which nodes live there) — the bar moves on that
nested graph, never the app's, and there is no separate destination enum any more: the
Screen seal itself names every tab. `HomeScreen` itself: tabs are RE-DERIVED from the live
`AppView` (`AdminView→tabsFor(ADMIN)`, `OwnerView→OWNER`, `EntrantView→CAMPER`) through
`remember(viewState, role)` — the live view decides the list and the `role` argument only
keys the remembered value. The nested graph OPENS on the NODE that owns the tab `initialTab`
NAMES — `initialTab` is a SCREEN or null, never an index, and the lookup is graphs.md's
`graphNodeFor` in `GraphUtils.kt` (a `NavHost` may only start on a direct child of its own
graph, so the start names the feature-graph node —
Explore→`NavGraphNodes.ExploreGraph`, MyCampsites→`CampsiteGraph`, Messages/Bookings/Inbox→
`CorrespondenceGraph`, Reservations→`ReservationGraph`, the admin debug tabs→`DebugGraph`;
the Misc tabs Profile/Reports/Flagged start on the screen itself, being bare direct
children — graphs.md). `goHome` naming a tab (the my-campsites back affordances and the
details-page picker name it) lands the bar there; naming no tab, or naming a screen the live
bar does not show, opens the first tab. The bar's
circles
navigate with `popUpTo(findStartDestination) { saveState }` + `restoreState` +
`launchSingleTop`, so Explore (always first) stays under every other tab and switching
back restores the passed-over tabs' saved state rather than rebuilding it.
Two `RoleBottomBar` overloads (Composable halves, not unit targets): the Home one lights
a circle from the live nav position — `currentDestination.hierarchy` matched against the
tab's own class identifier, which the file-PRIVATE `routePrefixOf` reads off the screen
object itself (nothing about a screen is spelled out); the details-page one has no live
graph under its caller, so the caller names the lit `selectedIndex` and gets the tapped
tab's SCREEN back (`onSelect: (Screen) -> Unit`, called with `tab.destination` — the
details-page picker needs the Screen to name that tab to Home; the lit position stays an
index because it is display only, with no graph to name). `NavCircle` is file-PRIVATE. `PlaceholderTab(title)` is the temporary content
for tabs without screens yet (Profile/Reports/Flagged today).

`ui.screens/BookingsScreen.kt`: `enum class BookingTab { CONFIRMED, PENDING }` (vocabulary;
public). `ui.screens/ExploreScreen.kt` declares the public `CampsiteCardData` data class —
fields used at every call site: `id, name, location, maxOccupancy` (+ optional
`rating, ratingCount` supplied only by `toCardData`); constructible in tests, defaults
unasserted (not read off source here). `ui.screens/ReservationScreen.kt` is the
reservations renderer (params per app.md). `ui.screens/DeviceSignInScreen.kt`, `QrScannerScreen.kt`,
`QrOldCodeNoticeScreen.kt` (`back: () -> Unit = {}` fires on its Back button), `SetUpScreens.kt`
(StartScreen/SignUpScreen/RoleSelectScreen) — Composables, contract = their call sites in app.md.
`QrScannerScreen` also opens the device camera via androidx.camera when the screen opens and
draws its live preview once the preview surface arrives (US 01.05.02, live scanner). The ask
is NOT the screen's: only the running activity can ask a device, so MainActivity asks for
`android.permission.CAMERA` once at start through plain `android.app.Activity.requestPermissions`
(no `ActivityResultContracts` ask exists in this build's flow) and carries ONLY a refusal as the
app-level fact `Dependencies.cameraRefused` (default false). The screen opens its camera UNLESS
it reads a refusal — a device that shows no dialog and never calls back is not a refusal, so its
camera is opened and the camera's own failure is what the screen reports, LOUD, in its notice.
A refusal leaves the placeholder and the typed path. The screen binds the FIRST camera the device
itself lists (`cameraProvider.availableCameraInfos` → `CameraSelector.of(that camera's identifier)`),
NOT `DEFAULT_BACK_CAMERA`: a lens-facing selector asks for a back-facing lens, and a device whose
camera reports no lens category at all (a chromebook's does — its own error names
`LensCategory:null` while naming `Cams:1`) matches no lens filter, so the bind throws
"No available camera can be found" on a device that has a camera. A device that lists no camera
names that in its notice instead. The in-app image-file scan (the `image_path_field`
text field and its `image_scan` button) is GONE: the user called it bulky and the phone's own
camera opens a code's link instead (the registered `campbnb` scheme, app.md); the screen keeps
its live preview, the typed-code field, the frame-reading seam and the Open button. Screen-internal
wiring only — no parameter or call-site change, and the frame-reading seam is untouched. A code
that names a registered campsite opens it through `onOpenCampsite`; text that is not code text,
or names a campsite that is not registered, says so in the notice — there is no expired arm and
no "that code stopped working" message any more, since a code carries no expiry (models.md). `ui.screens/messagesScreen.kt` (package **ui.inbox**): private
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
There is NO `SettingsScreen` composable any more: the owner asked for the Settings screen
to be removed once every setting was readable from the Profile view, so `Screen.Settings`
is gone from the sealed set, `MiscGraph` no registers no Settings node, and the Profile
view mounts the pieces below directly. The file keeps its name and its PUBLIC vocabulary
(`SettingsGroupId`, `groupLabel`, `settingsGroupsFor`, `SettingsDisplayController`'s
contract) because that ordering model is what the settings a role's menu names are still
specified by — the Profile view draws the groups it names, in that order. Expansion state
lives in a `remember`ed `SettingsDisplayController` (viewModels.md) held by whoever mounts
the surface. The app-view switch group's dropdown renders its items as centered rows — the
shared `AppDropDownMenu` with `centered = true` (feature 3). Its doc warns: controllers
should be kept by the CALLER (defaults are standalone-use only) — app.md shows the shared
instances. The app view switch group's selection does TWO things per chosen view:
`viewSettings.changeViewTo(view)` AND `appSettings.setAppViewChoice(userIdForSettings, view)`
(data.md owns the record and its merge rules: saving one group never erases the others;
`applyLoadedSettings` is the restart half, app.md's `init` runs it).

The pieces the Profile view is built from are PUBLIC so it mounts these rather than a second
copy that could disagree: `SettingsDropdown(display, groupId, content)` (the labelled bar +
menu), `ViewSwitchMenu(display, viewSettings, appSettings, userId)` (the three-view menu),
and `AppPolicyDialog(onDismiss)` (the policy pop-up: titled `App Policy`, body
`stringResource(R.string.app_policy)` from `res/values/strings.xml`, closed by OK or by
dismissing, doing nothing else). The `App Policy` bar is offered to EVERY role (it is not a
`SettingsGroupId` — it is not role-gated) and the Profile view opens the same
`AppPolicyDialog`.

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
`ui.admin/AdminActivity.kt` and `PolicyActivity.kt` were deleted upstream (admin actions go
through `AdminController`, controller.md).

The screen itself is `AdminProfilesScreen(back, userModel, adminController)` — the third
argument carries out the removals an admin confirms (app.md shows the shared instance). Its
drawn surface: each row (`ProfileEntry`, file-private) offers a settings toggle, a delete
action, a remove-owner action (tapping the row's ROLES label), and — when the user has a
picture — the picture itself, a 44dp round `StoredImage` under testTag `profile_image_<userId>`,
tapping it opening the image removal. Confirming a profile delete opens `ConfirmRemovalDialog`
with `reasonRequired = false` (a profile may be deleted without naming a reason); confirming an
OWNER removal opens the same dialog with `reasonRequired = true` and title `Remove this
campsite owner?` (the cascade is `AdminController.removeOwner`, controller.md); tapping the
picture opens `AdminImageRemoval` (below), whose confirm calls
`adminController.removeImage(ImageKind.PROFILE, <userId>, reason)`. Whatever any of these
answers with is drawn back into the list under testTag `profiles_message` while it is non-blank.

`ui.admin/AdminImageRemoval.kt` — `fun AdminImageRemoval(title, stored,
onRemove: (RemovalReason?) -> Unit, onDismiss: () -> Unit)`: the one "remove this picture?"
panel every image removal opens. It shows the picture (`StoredImage`), lists every
`RemovalReason` as a radio row (same testTag shape as `ConfirmRemovalDialog`), and its
confirm calls `onRemove` with the picked reason (a reason is required); closing it —
cancelled or confirmed — calls `onDismiss`.

`ui.admin/AdminImagesScreen.kt` — `fun AdminImagesScreen(back, adminController)` (US 03.06.01,
the admin's browser of every uploaded picture): it reads the users list and the campsites
through the controller and draws `uploadedImagesFor` (models.md) as one row per picture —
campsites first, then profiles — each row showing the picture, its kind's label and its
owner's title, and offering the same `AdminImageRemoval` panel addressed to that picture's
`sourceId`. It is registered bare in `DebugGraph` (graphs.md), not a bottom-bar tab.

### ui.admin — the removal dialogs (shared view)

`ui.admin/ConfirmRemovalDialog.kt` — the one "are you sure?" pop-up every admin removal uses;
each story supplies only its own text:
```kotlin
fun ConfirmRemovalDialog(title, message, confirmLabel, reasonRequired: Boolean,
    onConfirm: (RemovalReason?) -> Unit, onDismiss: () -> Unit)
```
The dialog lists every `RemovalReason` as a radio row (testTag `reason_<ENUM_NAME>`, e.g.
`reason_SPAM_OR_FAUD`… the name is the enum's) under a heading that says `Reason (optional)`
or `Reason (required)`; the red confirm button (testTag `confirm_removal`) is DISABLED until a
reason is picked when `reasonRequired` is true. `onConfirm` gets the picked reason, or null
when none was required; `onDismiss` is also what tapping outside the pop-up calls.

`ui.admin/ProfileRemovedDialog.kt` — `fun ProfileRemovedDialog(onOk: () -> Unit)`: the notice
for the RUNNING user whose profile an admin deleted (title `Your profile was deleted`, body
says their reservations were cancelled and lottery entries removed, and to tap OK to return to
sign-in). Its only button is OK, testTag `profile_removed_ok`; app.md owns the navigation it
starts.

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
screens by the SHARED `LotterySelectedViewModel` (viewModels.md; app.md builds it once and
passes it into `lotteryGraph`), not by the activities. `LotteryEditActivity`
additionally receives a `UserRepository()` for invitation-candidate checks (US 02.04.08).
Their drawing bodies are not unit targets. Truth kept, though: date-range rows display the
picker's UTC-midnight millis on an explicit UTC basis — a device-local formatter showed the
day before on both endpoints west of UTC (fix 13) — edit renders a creator-only
"Force Close" control delegating to controller.md's `forceCloseLottery` (feature 1), and
edit's invited-owner panel is a FIXED-VISIBLE-HEIGHT scrollable sheet, not a dropdown:
six rows visible at 56.dp each (sheet height = `rowHeight × visibleRows`, and the
multiplication is only ever Dp × Int), rows full-width at one row height with a checkbox
that mirrors the selection set, scrolling via `verticalScroll(rememberScrollState())` —
following the app's own `LotteryWaitlistActivity` idiom. An `ExposedDropdownMenu` before
this hid every invitee past the visible items (reported regression 10). The monitor's card
for a lottery offers Details ALWAYS and a `lottery_delete_closed` "Delete" control ONLY while
`Lottery.statusAt(now)` is `CLOSED` — its booking window has passed, so the lottery can no
longer be joined, edited or drawn and is only clutter in this list; the control calls
controller.md's `deleteLottery` and the card answers by going away (the controller takes the
lottery out of the model's list as well as out of the records). No other card state gets the
control, and no lottery screen renders a status message.

### ui.notification — the log screen

`ui/notification/NotificationLogScreen.kt`: Composable taking `title`,
`notificationSource: NotificationRepository`, `returnFunc` (optional), `profileText`
(optional resolver — app.md passes `profileLabelOf(...)`), `savedUsers` (optional `UserModel`
— app.md passes the real one for the debug view only, and the view model's watch per viewModels.md
makes late-loaded profiles re-resolve profile references — bar senders and receiver lines
alike; the default is a private model that is never
notified), `rawIdsAllowed` (default true — a viewer who may NOT read a raw database key
passes false and every bar ends where the notice opens; only the admin's own debug view
leaves it true), `deleteNotice` (optional `(String, (removed: Boolean) -> Unit) -> Unit` —
when the caller can remove one saved notice, it hands the action and each bar grows a
`log_delete` control that passes that bar's entry id, resolved through the view model's
`entryIdFor`, and a continuation for the caller to run once the store confirms the removal.
A confirmed removal re-reads the log through the view model's `readLog()`, so the entry
stops showing without the viewer tapping Refresh; the re-read happens ONLY in that
continuation, because the store answers asynchronously and a re-read asked for beside the
removal would race it and could still list the entry. With no action there is no control).
Reads the log through the
data.md `NotificationCommand.Read` contract and draws the group/line output per models.md's
`notificationLogGroupsFor`/`notificationLogLinesFor`. Nothing testable beyond the lower
packages' contracts; the debug-log title strings (`"Notification Debugging"`, `"Sent
Notifications"`) are app.md integration copy.

`ui/inbox/NotificationActivity.kt` (`NotificationInboxScreen`, private `NotificationRow`) and
`ui/notification/tabs/NoticeTabs.kt` (`SentNoticesTab`, `ReceivedNoticesTab`) take the same
optional `deleteNotice`, now `(String, (removed: Boolean) -> Unit) -> Unit` (the received
rows pass the continuation as empty — that listing is followed live, so the store answering
that a notice is gone needs nothing here): a row of one notice carries a
`notification_delete` control. `SentNoticesTab` also takes `unresolvedProfileText: (String)
-> String` and passes it to the log as its `profileText`, so which of ui.components' two
labels an unresolvable reference draws is the caller's decision, keyed on the live view
(app.md). The sent tab still passes `rawIdsAllowed = false` — a person reading the notices
they caused is not reading database keys.

### ui.theme, ui.booking — no test surface

`ui.theme/` (Color/Type tokens: `FieldGrey`, `ButtonGradient`, `DataColor`, `DataColorImportant`,
`CampBnbTheme`) — visual tokens, no behavior. `ui/booking/*Activity.kt` (3–4 lines) are empty
placeholder classes — future screen namespaces; today the bookings/reservations renderers are
`ui.screens` (BookingsScreen) and the app-level `ReservationsPanel` (app.md). (`ui.profile/`
and the `ui.admin/` + `ui/campsite/` placeholder Activities were deleted upstream: the profile
renders through `ui/screens/ProfileScreen.kt` and the admin's screens are real now.)

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
  duplicated across two files; the profile-list sort prefix (`"zz~" + email` in
  `AdminProfilesScreen` — a deterministic sort that puts no-email profiles first) is a
  documented chosen behavior.
- Tagging, for future UI testing: interactive elements carry `Modifier.testTag("<element>")`
  (import `androidx.compose.ui.platform.testTag`) — lowercase snake, ELEMENT-level names
  (`chat_back`, `unread_badge`, `send_button`, `star_<n>`, `conversation_row`). A name
  identifies the affordance, not the screen: two renderers of the SAME affordance (inbox
  list rows in `ui/screens` and `ui/inbox`) deliberately SHARE the name, so a device test
  asking for `conversation_row` finds either. Names are otherwise kept unique across ui.
  The full tag pass is DONE (same-day follow-up to the night batch): every interactive element
  under `ui/` now carries a tag. Two rules complete the convention. A primitive that renders exactly ONE affordance
  carries its tag inside the primitive — `header_back`, `menu_choice`, `search_burger_item`,
  `view_switch_row`, `search_field` (the shared search bar), `home_tab`, `text_field`,
  `link_row`, `role_field`, `role_choice`. A primitive that renders MANY affordances
  (`GradientButton`, `GradientBarButton`, `CircleLabelButton`) stays UNTAGGED inside; the
  call site supplies its tag through the `modifier` parameter
  (`modifier = Modifier.testTag("sign_up_button")`). Beyond the inbox twins, shared names
  are reserved for true same-affordance renderers: `try_again` (both inbox error states),
  `campsite_card` (Explore rows and the search view's results), `register_campsite` and
  `campsite_edit_open`/`campsite_delete_mark`/`campsite_delete_confirm`/
  `campsite_delete_cancel` (campsite monitor and owner activity), the lottery date-dialog
  names (shared `DatePickers` modal). Disabled, non-interactive items (the "No recent
  searches" dropdown entry) carry no tag.
