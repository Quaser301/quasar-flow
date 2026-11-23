# app — the whole-app wiring (root package `com.example.campbnb`)

Spec file for the five files at the root of `CampBNB/app/src/main/java/com/example/campbnb/`:
`Dependencies.kt` (contract), `AppContainer.kt` (the only production provider),
`MainActivity.kt` (Android entry), `Screen.kt` (the navigation vocabulary),
`CampBNBApp.kt` (the `NavHost` graph), PLUS the `graphs/` sub-package
(`com.example.campbnb.graphs`): `StartupGraph.kt`, `ExploreGraph.kt`, `CampsiteGraph.kt`,
`LotteryGraph.kt` — one `NavGraphBuilder` extension function per feature area, CALLED by
name inside `CampBnbApp`'s `NavHost` (this branch's restructure; the per-screen screen
windows below are specified here by which file registers them).

This package is GLUE: it owns no behavior of its own — it creates the shared services
once and connects the packages below it. Nothing here is a JVM unit target (see the last
section); every semantic a screen depends on is specified in the lower-area spec files,
which is where tests belong. This file is written so the test agent can see the
SEAMS and the app's routing vocabulary without reading the code.

Written against branch `wyatt-us-01.05.02` (tip `5275878` plus working tree), 2026-11-23.

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
exact: `Home(role: UserRole, tab: Int = 0)`, `CampsiteRegistration`,
`EditCampsite(campsiteId: String)`, `LotteryEdit`, `LotteryMonitor`,
`LotteryDetails`, `LotteryWaitlist`, `Start`, `Login`, `SignUp`,
`Role`, `Settings(role: UserRole)`, `Search(role, query: String = "", openFilter: Boolean = false,
openHistory: Boolean = false)`,
`QrScanner(role: UserRole)`, `Reservations`, `CampsiteDetail(role, campsiteId)` (declared;
the graph below opens `CampsiteDetailsScreen` instead — see graph section),
`CampsiteMonitor(role: UserRole)`, `CampsiteDetailsScreen(role: UserRole, campsiteId: String)`,
plus the Home bottom-bar tab routes as data objects: `Explore`, `Messages`, `Bookings`,
`Inbox`, `Profile`, `MyCampsites`, `Reports`, `Flagged`, `AdminProfileView`,
`AdminNotificationView` — the nested Home graph (below) registers content for the tabs.
`UserRole` is the UI role (ui/components — ui.md); data-class payloads ARE the route
arguments (`toRoute<T>()` reads them back). `Home.tab` is the index of the bottom-bar
tab Home opens on (`HomeScreen`'s `initialTab` seed, clamped to the live tab list).

## CampBnbApp (`@Composable fun CampBnbApp(deps: Dependencies)`) — the `NavHost` graph

`startDestination = Screen.Start`. The graph is SPLIT (this branch's restructure): the
`graphs/` sub-package holds one `NavGraphBuilder` extension function per feature area —
`startupGraph(nav callbacks…)` registers Start/Login/SignUp/Role, `exploreGraph` registers
Search (its own `Screen.Explore` node is a DEAD bridge — Home registers the live Explore
content and nothing in the app graph navigates to it), `campsiteGraph` registers
CampsiteRegistration/EditCampsite/CampsiteDetailsScreen (and a `Screen.MyCampsites` node
that is the dead twin of Home's live tab content), `lotteryGraph` registers
LotteryEdit/LotteryMonitor/LotteryDetails/LotteryWaitlist. Each takes the exit callbacks
its screens use (the app root passes `nav.navigate…`/`nav.goHome` lambdas into them) and is
CALLED by name inside the app `NavHost`. The app `NavHost` itself registers the remaining
windows directly: Home (with its nested tab graph, below), CampsiteMonitor, Reservations,
Settings, QrScanner, and the string-route bridges. Per-screen model/controller instances
stay `remember`-constructed wherever the window now lives: fresh `CampsiteModel()`/
`LotteryModel()` + `CampsiteController(model, deps.campsiteRepository)` or
`LotteryController(model, LotteryRepository(), Database.Lotteries.readEngine(), deps.notificationController)
{ System.currentTimeMillis() }` (the `nowProvider` is literally wall-clock — models.md's
`Lottery.statusAt` depends on it; each lottery window still builds its OWN controller over
its OWN fresh model/repository). The lottery the owner picked on the list is carried into
the edit/details/waitlist windows by a SHARED `LotterySelectedViewModel` (viewModels.md —
`selectedLottery` read-only StateFlow + `updateLottery(new)`), built once at the app root
and passed into `lotteryGraph`: the monitor clears it before opening edit and sets it before
opening details or the waitlist — there is no app-root `var` any more.

Routing rules that matter to readers and future integration tests (graph edges — one
line each; `nav` is the app controller, `homeNav` the nested one, all calls as stated):
- Start → `Screen.Login` on get-started. Login = `DeviceSignInScreen(deps.deviceID,
  userController = deps.userController, onEntered, onNotFound)`. **onEntered**: privileges
  = `userModel.currentUser?.roles`; role picks by PRECEDENCE `ADMIN` beats `OWNER` beats
  else `ENTRANT`; `deps.appViewSettings.applyViewDefaultIfNoneChosen(viewFor(role))` (the
  role-derived view is only a DEFAULT: a user who saved or switched a view keeps it across
  lifetimes — see viewModels.md's gate; the sign-in default clobbering a saved choice was
  the reported failure of the admin's settings view) then navigate
  `Home(toUserRole(role))` `popUpTo(Start) inclusive` — the start/login frames are cleared.
  **onNotFound** → `Screen.SignUp`.
- SignUp: builds `User(deviceId = deps.deviceID, name, email, phone, roles = listOf(Role.ADMIN))`
  via `Database.Users.writeEngine().save` (flagged in-source: sign-up currently grants
  ADMIN; TODO marker) then navigates `Screen.Role`.
- Role: `RoleSelectScreen(onRoleSelected)` maps `UserRole→AppView` INLINE (ADMIN→AdminView,
  OWNER→OwnerView, CAMPER→EntrantView — same as ui.md's `toAppView`), sets the view,
  navigates `Home(role)` popping up to Start inclusive.
- Home (`Screen.Home(role, tab)`): `entry.toRoute<Screen.Home>()`. Home's OWN body loads the
  tab data once (cards via one `getAll`; bookings via `getForEntrant` + a second `getAll` for
  names, filling `confirmedBookings`), then builds
  `HomeScreen(role, deps.appViewSettings.toAppViewSetting(), initialTab = route.tab, homeNavController = homeNav)`
  and passes a builder registering ONE NODE PER BOTTOM-BAR TAB (ui.md `tabsFor` — every tab the
  bar can name is registered here, so pressing any circle lands on real content). `homeNav =
  rememberNavController()` is a NESTED graph inside Home: the bar moves between tabs on THAT
  graph and the app `nav` never enters or leaves a tab, so a tab change is never stacked on the
  last one, and while tab content shows the live APP-level frame IS Home. The nested graph OPENS
  on `tabs[route.tab]` clamped (the `initialTab` seed: `goHome` names a tab — CampsiteMonitor's
  back names My Campsites, reported regression 9; the details-page picker names the tapped tab;
  the default 0 opens Explore). Nodes below are the tab content:
  - `Screen.Explore`: `ExploreScreen(recommended = campsiteCards, newSites = campsiteCards)`
    (the cards come from Home's own `getAll` read; the empty/loading semantics of the screen are
    ui.md's). Callbacks navigate on the APP graph:
    settings→`Settings(role)` PLAIN `nav.navigate` — NO popUpTo, NO launchSingleTop: this tab's
    content only ever shows while Home is the live app-level frame, so a bare push leaves Home
    mounted under Settings, and Settings' `navigateUp` back pops Settings and re-exposes the SAME
    live Home (Explore still selected, cards intact). The earlier typed `popUpTo<Screen.Home>
    { inclusive = false }` could never match a live Home frame — Home is a parameterized route and
    a typed pop matches a class as a pattern no live instance equals — so it popped the WHOLE
    stack, leaving Settings the sole entry: back went to the start and the back gesture closed the
    app (reported regression 7).
    campsite click→`CampsiteDetailsScreen(role, id)` PLAIN `nav.navigate`, same reasoning (that
    recurring back bug was reported again for this entry); search→`Search(role, text)`,
    filter→`Search(role, "", true)`, history→`Search(role, "", false, true)`
    (the Explore burger and the search view's burger list the SAME items from the one shared
    ui.md menu source — fix 11; each item opens the search view with its section showing),
    QR circle→`QrScanner(role)`.
  - `Screen.Bookings`: `BookingsScreen` reading Home's values; confirmed list:
    `deps.reservationRepository.getForEntrant(entrantId = deps.deviceID, …)` then keep
    `status == ReservationStatus.ACTIVE` only, mapped to `BookingCardData` with
    `status = "Confirmed"`, `campsiteNames = listOf(found name or "Campsite")`,
    `lotteryEntryId` as id; tab selection kept in Home's `selectedBookingTab` var (`onTabSelected`
    mutates it). Failure Toasts, exact: `"Could not load bookings: ${error.message}"`,
    `"Could not load campsite details: …"`, `"Could not load campsites: …"` — Toast copy is
    integration text, NOT unit assertions.
  - `Screen.Reservations` (the camper tab): `ReservationsPanel(deps)` HOSTED DIRECTLY under the
    tab — pressing the tab lands in the view itself, no second button asking again; the panel's
    back is `homeNav.navigateUp()` (pops the tab's own frame; Explore stays under every other
    tab, so the first tab lands live — the same net effect as the old `goBack = nav.goHome(role)`).
  - `Screen.MyCampsites`: `MyCampsitesTab(onOpenMyCampsites = { nav.navigate(Screen.CampsiteMonitor(role)) },
    onOpenLotteries = { nav.navigate(Screen.LotteryMonitor) }, onOpenReservations = { nav.navigate(Screen.Reservations) })`
    — those three buttons cross out of Home onto the APP graph.
  - MESSAGES→`CorrespondenceDestination(openTab = CorrespondenceTabs.MESSAGES_TAB, onNavBack = { homeNav.navigateUp() }, deps)`;
    INBOX→same with NOTIFICATIONS_TAB — in-Home back affordances stay on the NESTED graph.
  - ADMIN_PROFILE_VIEW→`loadAllUsers()` then `AdminProfilesScreen({nav.goHome(role)}, deps.userModel)`;
    ADMIN_NOTIFICATION_VIEW→`NotificationLogScreen(title = "Notification Debugging", notificationSource = deps.notificationRepository,
    profileText = { userId -> profileLabelOf(userId, deps.userModel.allUsers) })` (the debug log
    resolves profile references via ui.md's `profileLabelOf`); PROFILE/REPORTS/FLAGGED→`PlaceholderTab(label)`.
  (The graph files' own `Screen.Explore` and `Screen.MyCampsites` nodes are the DEAD twins of the
  two live nodes above — nothing in the app graph navigates to them; they belong to the owner's
  half-finished restructure and stay registered.)
- Search (registered by `exploreGraph` — the live node; that file's own `Screen.Explore` node
  is the dead twin mentioned above): `observeModel(searchModel)` then `loadAndSearch(query)`; the screen opens with
  `initialShowFilter = route.openFilter` and `initialShowHistory = route.openHistory` so the
  Explore burger's items land the view with that section showing (fix 11); results drawn with
  `toCardList(searchModel.visibleCampsites, searchModel)` (ui.md); `onClose`→navigateUp.
- QrScanner screen: `QrScannerScreen(controller = …)` `onOpenCampsite`→that campsite's details;
  the stack keeps this screen under the details page, so back returns here (US 01.12.01).
- CampsiteDetailsScreen (registered by `campsiteGraph`): **shownRole follows the CURRENT app view**
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
  (controller.md owns the record-building rules). The action area is one hamburger per role (feature 2):
  the CAMPER's whole "Lottery" action Column exists ONLY while the campsite has a lottery
  active for the signed-in camper — the graph passes `lotteryButtonEnabled =
  detailsLotteryModel.entrantLottery != null` and the ui half gates the ENTIRE Column on it,
  so with no lottery there is no bar at all rather than a dead hamburger with an empty menu
  inside (reported regression 3). The single item inside is LABELLED by ui.md's
  `lotteryButtonText(model)`, is-leave by `lotteryButtonIsLeave = entrantIsOnWaitlist`;
  `onEnterLottery`→`detailsLotteryController.toggleEntrantMembership(deviceID)`.
  The PolicyReason menu renders the CHOSEN reason in the theme's `AdminInteractionColor`
  once `adminReasonPicked == reason`, and the action runs with that chosen reason — admin
  interaction visible in the admin colour (reported regression 5; precedent: NotificationLogScreen
  renders admin copy in that colour). The details page carries the bottom-bar PICKER:
  `onTabSelected = { index -> onCampsiteDetailsClicked(route.role, index) }`, the graph's callback
  passed by the app root as `{ role, tab -> nav.goHome(role, tab) }` — Home opens on the named tab.
  Entrant content = `CampsiteRatingSection` + `LotteryJoinSection`.
- Lottery edit/monitor/details/waitlist (registered by `lotteryGraph`, entered at LotteryMonitor):
  each composable `remember`s an ACTIVITY CLASS
  (`LotteryEditActivity(...)` etc. — ui.md: constructor = return callbacks + `appViewSettings =
  deps.appViewSettings.toAppViewSetting()`), then calls the activity's method
  (`activity.LotteryEdit(model, controller, userRepository = UserRepository(), initialLottery = selectedLottery.value, deviceID)`,
  `LotteryMonitor(model, controller, deviceID)`, `LotteryWaitlist(model, controller, selectedLottery.value)`,
  `LotteryDetails(model, selectedLottery.value)`), where `selectedLottery` is read via
  `lottery.selectedLottery.collectAsStateWithLifecycle()` off the SHARED `LotterySelectedViewModel`
  (viewModels.md) that the app root builds once and passes into `lotteryGraph`. The cross-screen
  carry: Monitor's ctor calls `lottery.updateLottery(null)` before opening edit, and
  `lottery.updateLottery(newLottery)` before opening details — there is no graph-local var any
  more. Edit mode
  offers the lottery's own admin (its creator, never a joining owner) a Force Close button
  calling `controller.forceCloseLottery(initialLottery, nameOfCampsite resolved from the
  loaded owner campsites)` — controller.md owns the draw (feature 1).
- `Screen.CampsiteMonitor` (same-name collision: `Screen.CampsiteMonitor` the route,
  ui's activity class the renderer; registered at the app root): ctor gets `goHome`/`goBack` —
  both `{ nav.goHome(route.role, tabIndexOf(route.role, Screen.MyCampsites)) }`, so back opens
  Home NAMING the My Campsites tab and the bar lands there (reported regression 9: back used to
  open the first tab; the app root's `tabIndexOf` resolves the index by route equality against
  `tabsFor`, and a miss coerces to the first tab) — plus `goToRegisterCampsite` (plain push →
  `Screen.CampsiteRegistration`)/`goToCampsiteDetails`/`goToEditCampsite`/`fileDownloadWriter =
  deps.fileDownloadWriter` (file-download wiring, item 8)/
  `appViewSettings`/`deviceID`; renders via `activity.CampsiteMonitor(model, controller)`.
- `Screen.Reservations` → `ReservationsPanel(deps)`: a user OWNING ANY of the sites (decided
  by the loaded sites: `allCampsites.any { it.ownerId == deps.deviceID }`, NOT the owner-role
  flag — a plain owner whose profile lacks the flag still auto-populates their sites in the
  top screen, which the role-flag gate had regressed) browses THEIR OWN sites
  (`ownerId == deps.deviceID`) and sees EVERY reservation at a site; every other viewer
  browses only sites carrying their own reservations (`getForEntrant` … `any { it.campsiteId == campsite.id }`)
  and gets only `loaded.filter { it.entrantId == deps.deviceID }`. Filter state is
  `ReservationFilter.ACTIVE/PAST/CANCELLED` (controller.md); changing campsite/filter
  re-runs the matching read; Toasts `"Could not load your reservations: …"` / `"Could not load campsites: …"` /
  `"Could not load reservations: …"`. Hands `ReservationScreen(... csvPreferences = deps.notificationPreferencesService,
  currentUserId, fileDownloadWriter …)` (CSV download ↔ controller.md's `generateReceiptCsv`).
- Settings composable passes `viewSettings = deps.appViewSettings, entrantSettings,
  notificationPreferences = deps.notificationPreferencesService, userIdForSettings = deps.currentUserId`.
  Its `back = { nav.navigateUp() }` pairs with the Explore bridge's PLAIN push above: nothing
  was popped there, so Home is still mounted underneath and `navigateUp` pops Settings and
  re-exposes the SAME live Home (Explore selected, cards intact). This was the
  Explore-not-restored complaint; the design stays owner-approved as push-not-hoist — only the
  mechanism changed: a typed pop can never name a parameterized Home frame (see the
  regression-7 note in the Explore bridge), so the bridge simply does not pop.
- `private fun NavController.goHome(role, tab = 0)` = navigate `Home(role, tab)` with
  `popUpTo<Screen.Home> { inclusive = true }; launchSingleTop = true` — kept as-is: even though
  the typed pop cannot name a live PARAMETERIZED Home frame (no instance route equals the class
  pattern, so it empties the stack), the following push re-establishes the Home frame, and the
  net effect is a fresh `[Home]` whose NESTED graph restarts at `tabs[tab]` clamped
  (`HomeScreen.initialTab`): callers that name a tab (CampsiteMonitor's back, the details-page
  picker) land the bar there; the default opens the first tab.

**String routes** (runtime-checked, NOT the Screen seal) — these are exactly the
`NotificationRoutes` constants from notifications.md plus the `qrLinkRoute`/`qrOldCodeRoute`
link routes from models.md, so `SystemNotifier`'s tap AND the phone's own camera opening a
code's link (resolved by MainActivity) land here, and `pendingRoute` bridges MainActivity →
graph: a `LaunchedEffect(deps.pendingRoute)` navigates `deps.pendingRoute!!` (string) then
calls `onPendingRouteHandled()`:
- `"notifications"` → Correspondence tab `RECEIVED_TAB` if owner else `NOTIFICATIONS_TAB` (the
  owner's OWN sent notices live under Received — see tab wiring below).
- `"messages"` → `CorrespondenceDestination(MESSAGES_TAB)`.
- `"reservations"` → the bridge's `LaunchedEffect` runs `nav.navigateUp()` (pops its OWN string
  frame) then a PLAIN `nav.navigate(Screen.Reservations)` — no popUpTo at all: the typed
  `popUpTo<Screen.Home> { inclusive = false }` form could never match a live Home frame
  (Home is a parameterized route; the pop emptied the whole stack and left the reservations
  view as the only entry — the reservations half of reported regression 7). The self-pop keeps
  the old white-screen fix (no invisible string frame stays under the view), so the first back
  press goes to whatever the notification was tapped from, under Home; `launchSingleTop` still
  unused.
- `"booking/{lotteryId}"` → `InvitationDestination`: reads the lottery via
  `Database.Lotteries.readEngine().getById`; the invitation panel shows ONLY while
  `viewerId in invited.invitedOwnerIds` and NOT `in joinedOwnerIds`; Accept button →
  `invitationController.acceptInvitation(lottery, userId, onSuccess = navigate `Screen.LotteryMonitor`)`;
  "Not now" → `Screen.Reservations`; otherwise (already joined / resolved / not invited) it
  navigates `Screen.Reservations` (`popUpTo(Screen.Reservations) inclusive`) — taps from WIN
  notifications keep behaving that way (the reservation-then-reservations path). Exact panel
  copy: header `Lottery Invitation`, body `You were invited to join this lottery. Accept to
  see it in your list and add your own campsites.`, buttons `Accept invitation` / `Not now`.
- `"chat/{reservationId}"` → `CorrespondenceDestination(MESSAGES_TAB, openReservationId = …)`.
- `"qr-link/{campsiteId}"` (models.md `qrLinkRoute`, queued by MainActivity after resolving a
  LIVE code's opened-link text) → `campsiteId = entry.arguments?.getString("campsiteId") ?: ""`;
  `LaunchedEffect(campsiteId)` and when it is NOT blank: `nav.navigateUp()` (self-pop, same
  pattern as the reservations bridge — the typed `popUpTo<Screen.Home> { inclusive = false }`
  could never name the parameterized Home frame and emptied the stack, reported regression 7)
  then a PLAIN `nav.navigate(Screen.CampsiteDetailsScreen(
  toUserRole(role), campsiteId))` — no popUpTo, NO `launchSingleTop`
  (the self-pop keeps the white-screen fix: no invisible string frame stays under the details
  page, so the first back press goes Home, not to a blank frame; `launchSingleTop` would launch
  the details screen as the single top and leave nothing under back). The key's role = the signed-in user's
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
