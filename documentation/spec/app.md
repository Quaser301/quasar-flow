# app — the whole-app wiring (root package `com.example.campbnb`)

Spec file for the five files at the root of `CampBNB/app/src/main/java/com/example/campbnb/`:
`Dependencies.kt` (contract), `AppContainer.kt` (the only production provider),
`MainActivity.kt` (Android entry), `Screen.kt` (the navigation vocabulary),
`CampBNBApp.kt` (the `NavHost` graph).

This package is GLUE: it owns no behavior of its own — it creates the shared services
once and connects the packages below it. Nothing here is a JVM unit target (see the last
section); every semantic a screen depends on is specified in the lower-area spec files,
which is where tests belong. This file is written so the test agent can see the
SEAMS and the app's routing vocabulary without reading the code.

Written against branch `wyatt-us-01.05.02` (tip `c985351` plus working tree), 2026-11-22.

## Dependencies (interface) — the app-level seam

`interface Dependencies` — one member per shared service or session value, all doc-commented:
`deviceID: String`, `userModel: UserModel`, `pendingRoute: String?` (null = no route
waiting), `onPendingRouteHandled: () -> Unit` (clears the pending route once the graph
accepts it), `var currentUserId: String`, `currentUserIsOwner: Boolean`,
`notificationRepository`, `userRepository`, `reservationRepository`, `messageRepository`,
`campsiteRepository`, `notificationController`, `adminController`, `messageController`,
`userController`, `messageNotificationAdapter`, `appViewSettings: AppViewSettingController`,
`entrantSettings: EntrantSettingsController`, `ownerSettings: OwnerSettingsController`,
`notificationPreferencesService`, `appSettingsService`, `fileDownloadWriter: FileDownloadWriter`.

Contract text (verbatim in spirit): "A provider supplies one stable value for each
member. Screens and activities must receive or read the supplied value instead of
creating their own copy… A test provider may replace any member with a controlled fake."
That last sentence is the DESIGN of this seam: an app-level test would implement this
interface with fakes and hand it to `CampBnbApp(deps = …)`. The house does no such
test today (the Composable graph needs a Context); record the seam for reading, and put
real tests in the lower packages. `var` members (`currentUserId`, `deviceID`,
`pendingRoute`) are mutable by the provider; the rest are stable values.

## AppContainer (`object AppContainer : Dependencies`)

One singleton provider; every member is `by lazy` (created once, on first need) or a
`by mutableStateOf` cell. The construction graph (each argument matches the lower spec
file's ctor exactly — these calls are the authoritative seam record):

- `userModel = UserModel()` (models.md); `userController = UserController(userModel, userRepository)`.
- `notificationRepository = FirestoreNotificationRepository()`; `userRepository`,
  `messageRepository = FirestoreMessageRepository()`, `campsiteRepository = CampsiteRepository()`
  — the Firestore-backed factories from `database.md`/`data.md` (contrast the JVM-test
  fakes: these need live engines, which is why the house's unit tests construct fakes instead).
- `reservationRepository = ReservationRepository(Database.Reservations.readEngine(),
  Database.Reservations.writeEngine(), Database.BookedDates.writeEngine())`.
- `notificationController = NotificationController(notificationRepository, userController)` —
  the UserController IS the opt-out check (US 01.10.01: users who turned notifications
  off still get the in-app record but no banner).
- `adminController = AdminController(campsiteRepository, notificationController)`.
- `messageController = MessageController(repo = messageRepository, reservationRepository =
  reservationRepository, notifier = messageNotificationAdapter, campsiteNameLookup = …)` —
  the lookup lambda: blank campsite id → `onName("")`; else
  `Database.Campsites.readEngine().getById` → `campName.orEmpty()`, miss → `onName("")`.
- `messageNotificationAdapter = MessageNotificationAdapter(notificationController)`.
- `appViewSettings` is ONE `AppViewSettingController()` (viewModels.md) for the whole
  app; `entrantSettings` one `EntrantSettingsController()`; `ownerSettings` one
  `OwnerSettingsController()` (the Settings screen gets the SHARED owner instance now,
  not a per-visit default);
  `notificationPreferencesService = FirestoreNotificationPreferencesService(read, write engines)`;
  `appSettingsService = FirestoreAppSettingsService(read, write engines)` (data.md);
  `fileDownloadWriter = PublicDownloadsFileDownloadWriter()` (data.md).
- Demo constants `const val DEMO_ENTRANT = "demo-entrant"` / `DEMO_OWNER = "demo-owner"`;
  `currentUserId` starts `DEMO_ENTRANT` but `init` overwrites (marked temporary in-source).
- `currentUserIsOwner get() = userModel.currentUser?.roles?.contains(Role.OWNER) ?: false` —
  profile-role based, so an owner reaches it however they signed in, not only as a demo user.
- `pendingRoute: String? by mutableStateOf(null)`;
  `onPendingRouteHandled: () -> Unit = { pendingRoute = null }`.

`fun init(context)`: `deviceID = DeviceId.get(context)` (ui/shared), `currentUserId = deviceID`,
then `loadNotificationSettings(deviceID, { enabled -> entrantSettings.applyLoadedNotificationSettings(enabled) }, no-op failure)` and
`loadAppSettings(deviceID, { record -> appViewSettings.applyLoadedSettings(record);
entrantSettings.applyLoadedSettings(record); ownerSettings.applyLoadedSettings(record) },
no-op failure)`: NEITHER load is warm-up any more — the loaded prefs SEEDED into the
entrant controller (the entrant's saved notification toggles are therefore alive when
the Settings screen first reads them), and the loaded record is SEEDED into
the three settings controllers before any screen reads them (viewModels.md owns what each
`applyLoadedSettings` does; a record with nothing stored answers defaults and changes
nothing). Neither load is fatal (both error callbacks are no-ops): a test of `init` may
assert only that it does not throw — seeding tests for either load belong at the
controller level, not here.

Unit reach: the `object` itself is constructible in Kotlin/JVM, but every member touched
by a getter goes live-Firestore (`Database.*.readEngine()` etc.); house tests never
stand it up. Its ONE JVM-reachable rule is `currentUserIsOwner` (pure getter over a
`UserModel` — models.md defines what a saved `User` is): `false` when there is no
current user or no OWNER role. The getter reads `userModel.currentUser` — whatever a
`UserController` flow has stored; asserting this getter standalone needs an already-set
`userModel`, which lower-area tests own.

## MainActivity (`class MainActivity : ComponentActivity()`)

Order inside `onCreate` (integration contract, exact):
1. `super.onCreate`, `enableEdgeToEdge()`.
2. `NotificationChannels.createAll(applicationContext)` — channels must exist BEFORE any
   notification is posted (notifications.md).
3. `AppContainer.init(applicationContext)` — the device id and the current user exist
   before any screen renders.
4. `if (savedInstanceState == null)` run `handleNotificationIntent(intent)` then
   `handleQrLinkIntent(intent)` — a cold launch with either intent already set (a banner
   tapped, or the phone's own camera having opened a code's link, before this process
   started) still routes.
5. `setContent { CampBnbTheme { LaunchedEffect(AppContainer.deviceID) { watcher.start(deviceID) };
   CampBnbApp(deps = AppContainer) } }` — the banner watcher starts for THIS device id
   (notifications.md's baseline rule then governs what banners appear).

`onNewIntent` = `setIntent` + `handleNotificationIntent(intent)` + `handleQrLinkIntent(intent)`
(same handlers as the cold-launch calls above).
`handleNotificationIntent(intent)`: null → return; missing `NotificationRoutes.EXTRA_ROUTE`
extra → return (a notification without a route opens nothing); `EXTRA_NOTIFICATION_ID`
NOT null-or-blank → `AppContainer.notificationController.markRead(id)` (the in-app record
marks itself read when the tap opens the app); finally `pendingRoute = route` — the
graph below navigates and calls `onPendingRouteHandled`. So: tap → (mark read) + (queue
route) → graph opens the screen.
`handleQrLinkIntent(intent)` (US 01.05.02 — the phone's own camera opened a code's link):
null → return; missing `models.qrPayloadExtraKey` extra → return (nothing the camera opened);
resolve `models.qrRouteOfLinkPayload(payload)` at the opened moment, and ONLY a non-null-or-blank
route is set on `pendingRoute` (a live code queues `qrLinkRoute/{campsiteId}`, a dead one the
`qrOldCodeRoute` notice route — models.md owns the decision) — a link that resolves nothing
never overwrites an already-queued notification route. Sign-in (item 3 above) ran before any
render, so an opened link's user data populates when the graph opens.
`onDestroy`: `watcher.stop()` before `super.onDestroy()`.
`watcher` is the lazy `NotificationWatcher(AppContainer.notificationController,
SystemNotifier(applicationContext))`.

## Screen (`@Serializable sealed interface Screen`) — the navigation vocabulary

One named type per screen the app can display; the seal makes every `is`-check
exhaustible at COMPILE time (developer note: a renamed/removed screen breaks every
window still referring to it — that is what keeps `navigate()` calls safe). Members,
exact: `Home(role: UserRole, tab: Int = 0)`, `CampsiteEditActivity`,
`EditCampsite(campsiteId: String)`, `LotteryEditActivity`, `LotteryMonitorActivity`,
`LotteryDetailsActivity`, `LotteryWaitlistActivity`, `Start`, `Login`, `SignUp`,
`Role`, `Settings(role: UserRole)`, `Search(role, query: String = "", openFilter: Boolean = false)`,
`QrScanner(role: UserRole)`, `Reservations`, `CampsiteDetail(role, campsiteId)` (declared;
the graph below opens `CampsiteDetailsScreen` instead — see graph section),
`CampsiteMonitorActivity(role: UserRole)`, `CampsiteDetailsScreen(role: UserRole, campsiteId: String)`.
`UserRole` is the UI role (ui/components — ui.md); data-class payloads ARE the route
arguments (`toRoute<T>()` reads them back). `Home.tab` is the bottom-bar tab index.

## CampBnbApp (`@Composable fun CampBnbApp(deps: Dependencies)`) — the `NavHost` graph

`startDestination = Screen.Start`; one `composable<Screen.X>` per Screen member (plus the
string routes below). Per-screen instances are `remember`-constructed: fresh `CampsiteModel()`/
`LotteryModel()` + `CampsiteController(model, deps.campsiteRepository)` or
`LotteryController(model, LotteryRepository(), Database.Lotteries.readEngine(), deps.notificationController)
{ System.currentTimeMillis() }` (the `nowProvider` is literally wall-clock — models.md's
`Lottery.statusAt` depends on it; note each lottery screen builds its OWN controller over
its OWN fresh model/repository). Shared `var selectedLottery` (a `Lottery?`) carries the
lottery the owner picked on the list into the edit/details/waitlist screens.

Routing rules that matter to readers and future integration tests (graph edges — one
line each, all `nav.navigate(...)`):
- Start → `Screen.Login` on get-started. Login = `DeviceSignInScreen(deps.deviceID,
  userController = deps.userController, onEntered, onNotFound)`. **onEntered**: privileges
  = `userModel.currentUser?.roles`; role picks by PRECEDENCE `ADMIN` beats `OWNER` beats
  else `ENTRANT`; `deps.appViewSettings.changeViewTo(viewFor(role))` then navigate
  `Home(toUserRole(role))` `popUpTo(Start) inclusive` — the start/login frames are cleared.
  **onNotFound** → `Screen.SignUp`.
- SignUp: builds `User(deviceId = deps.deviceID, name, email, phone, roles = listOf(Role.ADMIN))`
  via `Database.Users.writeEngine().save` (flagged in-source: sign-up currently grants
  ADMIN; TODO marker) then navigates `Screen.Role`.
- Role: `RoleSelectScreen(onRoleSelected)` maps `UserRole→AppView` INLINE (ADMIN→AdminView,
  OWNER→OwnerView, CAMPER→EntrantView — same as ui.md's `toAppView`), sets the view,
  navigates `Home(role)` popping up to Start inclusive.
- Home: `entry.toRoute<Screen.Home>()` payload drives tabs; `destinationContent` switches
  `HomeDestination` (ui/screens — ui.md): MY_CAMPSITES→`MyCampsitesTab` (three gradient
  buttons → `CampsiteMonitorActivity(role)`, `LotteryMonitorActivity`, `Screen.Reservations`);
  BOOKINGS→`BookingsScreen` (tab selection kept in a `var` here); MESSAGES→
  `CorrespondenceDestination(openTab = CorrespondenceTabs.MESSAGES_TAB)`; INBOX→same with
  NOTIFICATIONS_TAB; RESERVATIONS→`ReservationsTab`; ADMIN_PROFILE_VIEW→`loadAllUsers()`
  then `AdminProfilesScreen({nav.goHome(role)}, deps.userModel)`; ADMIN_NOTIFICATION_VIEW→
  `NotificationLogScreen(title = "Notification Debugging", notificationSource = deps.notificationRepository,
  profileText = { userId -> profileLabelOf(userId, deps.userModel.allUsers) })` (the
  debug log resolves profile references via ui.md's `profileLabelOf`); `else`→`PlaceholderTab(destination)`.
  Explore content: spinner until `campsiteCardsLoaded` (the `getAll` read answered —
  including after re-entering Explore); then `ExploreScreen` whose callbacks navigate:
  settings→`Settings(role)` PUSHED over the LIVE Home frame (`popUpTo<Screen.Home> { inclusive = false }`
  + `launchSingleTop = true` — Settings has exactly ONE entry path, so the push keeps the explored
  subtree on the stack and the Settings bridge's `navigateUp` back pops to the SAME live Explore
  view, selection and cards intact; a plain navigate would unmount Home and redraw Explore through
  the spinner — owner-approved design for the Explore-not-restored complaint), campsite click→`CampsiteDetailsScreen(role, id)`, search→`Search(role, text)`,
  filter→`Search(role, "", true)`, QR circle→`QrScanner(role)`. Bookings for the tab:
  `deps.reservationRepository.getForEntrant(entrantId = deps.deviceID, …)` then keep
  `status == ReservationStatus.ACTIVE` only, mapped to `BookingCardData` with
  `status = "Confirmed"`, `campsiteNames = listOf(found name or "Campsite")`,
  `lotteryEntryId` as id. Failure Toasts, exact: `"Could not load bookings: ${error.message}"`,
  `"Could not load campsite details: …"`, `"Could not load campsites: …"` — Toast copy is
  integration text, NOT unit assertions.
- Search: `observeModel(searchModel)` then `loadAndSearch(query)`; results drawn with
  `toCardList(searchModel.visibleCampsites, searchModel)` (ui.md); `onClose`→navigateUp.
- QrScanner screen: `QrScannerScreen(controller = …)` `onOpenCampsite`→that campsite's details;
  the stack keeps this screen under the details page, so back returns here (US 01.12.01).
- CampsiteDetailsScreen: **shownRole follows the CURRENT app view**
  (`deps.appViewSettings.viewSetting.collectAsStateWithLifecycle()` → `roleFor(…)`) — not
  the role captured in the route. Loads `getAll` then (found) `getAllRatings` filtered to
  the id; average `0.0` when none; details built as `CampsiteDetails(...)` with
  `tags = (campsite.tags + campsite.campAmenities).distinct()`; error strings exact:
  `"Campsite could not be found."`, `"Could not load campsite: …"`, `"Could not load
  campsite ratings: …"`. Once `campsiteModel.ratingCount(id) > 0`, re-show with the MODEL's
  average/count (`.copy`), so stars update right after rating. Policy actions act on the
  SAVED record (`policyCampsite`), never the drawn one: `onDeleteSite`→
  `deps.adminController.removeCampsite(target, reason, deps.currentUserId)` Toast
  `"Removed the site and told its owner."`/`"Could not remove the site."`; `onRemoveTag`→
  `removeTag(target, tag, reason, …)` Toast `"Removed the tag and told the site's owner."`/`"Could not remove the tag."`
  (controller.md owns the record-building rules). The floating join/leave button is LABELLED
  by ui.md's `lotteryButtonText(model)`, enabled by `entrantLottery != null`, is-leave by
  `entrantIsOnWaitlist`; `onEnterLottery`→`detailsLotteryController.toggleEntrantMembership(deps.deviceID)`;
  entrant content = `CampsiteRatingSection` + `LotteryJoinSection`.
- Lottery edit/monitor/details/waitlist: each composable `remember`s an ACTIVITY CLASS
  (`LotteryEditActivity(...)` etc. — ui.md: constructor = return callbacks + `appViewSettings =
  deps.appViewSettings.toAppViewSetting()`), then calls the activity's method
  (`activity.LotteryEdit(model, controller, userRepository = UserRepository(), initialLottery = selectedLottery, deviceID)`,
  `LotteryMonitor(model, controller, deviceID)`, `LotteryWaitlist(model, controller, selectedLottery)`,
  `LotteryDetails(model, selectedLottery)`). Monitor sets `selectedLottery = null` before
  opening edit, or `= lottery` before opening details — the cross-screen carry.
- CampsiteMonitorActivity (same-name collision: `Screen.CampsiteMonitorActivity` the route,
  ui's activity class the renderer): ctor gets `goHome`/`goBack`/`goToRegisterCampsite`
  (→`CampsiteEditActivity`)/`goToCampsiteDetails`/`goToEditCampsite`/`fileDownloadWriter`/
  `appViewSettings`/`deviceID`; renders via `activity.CampsiteMonitor(model, controller)`.
- `Screen.Reservations` → `ReservationsPanel(deps)`: an OWNER browses THEIR OWN sites
  (`ownerId == deps.deviceID`) and sees EVERY reservation at a site; every other viewer
  browses only sites carrying their own reservations (`getForEntrant` … `any { it.campsiteId == campsite.id }`)
  and gets only `loaded.filter { it.entrantId == deps.deviceID }`. Filter state is
  `ReservationFilter.ACTIVE/PAST/CANCELLED` (controller.md); changing campsite/filter
  re-runs the matching read; Toasts `"Could not load your reservations: …"` / `"Could not load campsites: …"` /
  `"Could not load reservations: …"`. Hands `ReservationScreen(... csvPreferences = deps.notificationPreferencesService,
  currentUserId, fileDownloadWriter …)` (CSV download ↔ controller.md's `generateReceiptCsv`).
- Settings composable passes `viewSettings = deps.appViewSettings, entrantSettings,
  notificationPreferences = deps.notificationPreferencesService, userIdForSettings = deps.currentUserId`.
  Its `back = { nav.navigateUp() }` pairs with the pushed Explore entry above: it pops Settings
  and reveals the still-mounted Home frame, so Explore returns live, selection and cards intact
  (this was the Explore-not-restored complaint; the design was owner-approved as the push, not
  a state hoist or a named Explore node).
- `private fun NavController.goHome(role, tab = 0)` = navigate `Home(role, tab)`
  `popUpTo<Screen.Home> { inclusive = true }; launchSingleTop = true`.

**String routes** (runtime-checked, NOT the Screen seal) — these are exactly the
`NotificationRoutes` constants from notifications.md plus the `qrLinkRoute`/`qrOldCodeRoute`
link routes from models.md, so `SystemNotifier`'s tap AND the phone's own camera opening a
code's link (resolved by MainActivity) land here, and `pendingRoute` bridges MainActivity →
graph: a `LaunchedEffect(deps.pendingRoute)` navigates `deps.pendingRoute!!` (string) then
calls `onPendingRouteHandled()`:
- `"notifications"` → Correspondence tab `RECEIVED_TAB` if owner else `NOTIFICATIONS_TAB` (the
  owner's OWN sent notices live under Received — see tab wiring below).
- `"messages"` → `CorrespondenceDestination(MESSAGES_TAB)`.
- `"reservations"` → immediately `nav.navigate(Screen.Reservations)` with
  `popUpTo<Screen.Home> { inclusive = false }` + `launchSingleTop` (comment: a string route
  left on the stack would keep an invisible frame under reservations — the reported white
  screen bug — instead of Home).
- `"booking/{lotteryId}"` → `InvitationDestination`: reads the lottery via
  `Database.Lotteries.readEngine().getById`; the invitation panel shows ONLY while
  `viewerId in invited.invitedOwnerIds` and NOT `in joinedOwnerIds`; Accept button →
  `invitationController.acceptInvitation(lottery, userId, onSuccess = navigate LotteryMonitorActivity)`;
  "Not now" → `Screen.Reservations`; otherwise (already joined / resolved / not invited) it
  navigates `Screen.Reservations` (`popUpTo(Screen.Reservations) inclusive`) — taps from WIN
  notifications keep behaving that way (the reservation-then-reservations path). Exact panel
  copy: header `Lottery Invitation`, body `You were invited to join this lottery. Accept to
  see it in your list and add your own campsites.`, buttons `Accept invitation` / `Not now`.
- `"chat/{reservationId}"` → `CorrespondenceDestination(MESSAGES_TAB, openReservationId = …)`.
- `"qr-link/{campsiteId}"` (models.md `qrLinkRoute`, queued by MainActivity after resolving a
  LIVE code's opened-link text) → `campsiteId = entry.arguments?.getString("campsiteId") ?: ""`;
  `LaunchedEffect(campsiteId)` and when it is NOT blank immediately `nav.navigate(Screen.CampsiteDetailsScreen(
  toUserRole(role), campsiteId))` with `popUpTo<Screen.Home> { inclusive = false }` + `launchSingleTop`
  (same white-screen fix as the reservations bridge: the invisible string frame pops itself out, so
  the first back press goes to Home, not to a blank frame). The key's role = the signed-in user's
  OWN privileges with the login precedence (`currentUser?.roles`: ADMIN then OWNER else ENTRANT —
  the same derivation the Login block runs), mapped through `toUserRole` (ui.md RoleMapping); the
  details composable follows the current app view regardless, so the key role is a carrier. A live
  code's link reaches the SAME details screen a scan of that code opens — a code names a campsite.
- `"qr-link-old"` (models.md `qrOldCodeRoute`, queued for a DEAD code's link — stop past, or no
  expiry at all) → `QrOldCodeNoticeScreen(back = { nav.navigateUp() })` in place of any destination.
  ONE path segment wide, so it can never collide with the two-segment campsite link route.

Correspondence/message tab wiring (owns WHERE each list comes from — the screens themselves
only draw what they are handed, ui.md): owner gets THREE tabs
(`CorrespondenceTabs.NOTIFICATIONS_TAB` = `SentNotificationsRepository(deps.notificationRepository,
deps.deviceID)` (data.md — a scoped wrapper) drawn by `NotificationLogScreen(title = "Sent Notifications")`;
RECEIVED = `receivedNoticesTab`; MESSAGES), every other viewer gets TWO (NOTIFICATIONS = the
received notices). `receivedNoticesTab` = `viewModel(key = "notification-inbox-$userId", factory =
simpleViewModelFactory { NotificationViewModel(deps.notificationController, userId) })` →
`NotificationInboxScreen(viewModel, nav)` — `viewModel()` keeps the same inbox listener across
recompositions (viewModels.md's `SimpleFactory`). `MessagesTab` = same pattern with
`MessageViewModel(deps.messageController, userId)` + `simpleViewModelFactory`; a non-blank
`openReservationId` calls `messageViewModel.open(id)`; draws `MessagesScreen(viewModel, currentUserId)`.

## Test reach (this package)

NOTHING in this package is a JVM unit target: MainActivity and every Composable need an
Android Context and the androidx runtime; AppContainer's members demand live Firestore
engines. The exact strings above (Toast copy, invitation panel copy) are integration copy
— do not turn them into unit assertions. What IS testable and where it lives: every
rule a screen exercises (read filters, role precedence, banner baseline, route maps,
list grouping) is fully specified in `data.md`, `database.md`, `models.md`,
`controller.md`, `notifications.md`, `viewModels.md` — tests go there. This file's role
for the test agent: know that `Dependencies` is the app-level seam (a test provider MAY
implement it with fakes — nobody in the house does yet) and that the Screen seal plus the
seven string routes are the app's routing vocabulary.
