# app — the whole-app wiring (root package `com.example.campbnb`)

Spec file for the five files at the root of `CampBNB/app/src/main/java/com/example/campbnb/`:
`Dependencies.kt` (contract), `AppContainer.kt` (the only production provider),
`MainActivity.kt` (Android entry), `Screen.kt` (the navigation vocabulary),
`CampBNBApp.kt` (the `NavHost` graph), PLUS the `graphs/` sub-package
(`com.example.campbnb.graphs`): `StartupGraph.kt`, `ExploreGraph.kt`, `CampsiteGraph.kt`,
`LotteryGraph.kt`, `NotificationGraph.kt`, `ReservationGraph.kt`, `MiscGraph.kt`,
`DebuggingGraph.kt` — one or more `NavGraphBuilder` extension functions per feature
area, CALLED by name inside the app `NavHost` AND again inside Home's nested tab graph
(both mounts are specified below), plus `GraphUtils.kt` — the shared `NavGraphNodes`
sub-graph registry — PLUS the shared-window sub-packages the graphs and Home call
directly: `destinations/` (`CorrespondenceDestination`, `InvitationDestination`),
`tabs/` (`MyCampsitesTab`, `MessagesTab`, `ReceivedNoticesTab`, `SentNoticesTab`),
`panels/` (`ReservationsPanel`).

This package is GLUE: it owns no behavior of its own — it creates the shared services
once and connects the packages below it. Nothing here is a JVM unit target (see the last
section); every semantic a screen depends on is specified in the lower-area spec files,
which is where tests belong. This file is written so the test agent can see the
SEAMS and the app's routing vocabulary without reading the code.

Written against branch `wyatt-us-01.05.02` (tip `03519ac` plus working tree), 2026-11-24.

## Dependencies (interface) — the app-level seam

`interface Dependencies` — one member per shared service or session value, all doc-commented:
`deviceID: String`, `userModel: UserModel`, `pendingScreen: Screen?` (null = no screen
waiting), `onPendingRouteHandled: () -> Unit` (clears the pending screen once the graph
opens it), `var currentUserId: String`, `currentUserIsOwner: Boolean`,
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
`pendingScreen`) are mutable by the provider; the rest are stable values.

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
  `OwnerSettingsController()` (the Profile view gets the SHARED owner instance now,
  not a per-visit default — there is no Settings screen to hand one to);
  `notificationPreferencesService = FirestoreNotificationPreferencesService(read, write engines)`;
  `appSettingsService = FirestoreAppSettingsService(read, write engines)` (data.md);
  `fileDownloadWriter = PublicDownloadsFileDownloadWriter()` (data.md).
- Demo constants `const val DEMO_ENTRANT = "demo-entrant"` / `DEMO_OWNER = "demo-owner"`;
  `currentUserId` starts `DEMO_ENTRANT` but `init` overwrites (marked temporary in-source).
- `currentUserIsOwner get() = userModel.currentUser?.roles?.contains(Role.OWNER) ?: false` —
  profile-role based, so an owner reaches it however they signed in, not only as a demo user.
  Nothing reads it any more: the correspondence shape moved to the live `AppView` setting
  (tab wiring below), which is a different question from what the signed-in profile holds.
- `pendingScreen: Screen? by mutableStateOf(null)`;
  `onPendingRouteHandled: () -> Unit = { pendingScreen = null }`.

`fun init(context)`: `deviceID = DeviceId.get(context)` (ui/shared), `currentUserId = deviceID`,
then `loadNotificationSettings(deviceID, { enabled -> entrantSettings.applyLoadedNotificationSettings(enabled) }, no-op failure)` and
`loadAppSettings(deviceID, { record -> appViewSettings.applyLoadedSettings(record);
entrantSettings.applyLoadedSettings(record); ownerSettings.applyLoadedSettings(record) },
no-op failure)`: NEITHER load is warm-up any more — the loaded prefs SEEDED into the
entrant controller (the entrant's saved notification toggles are therefore alive when
the Profile view first reads them), and the loaded record is SEEDED into
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
4b. `requestPermissions(arrayOf(android.Manifest.permission.CAMERA), cameraAnswerId)` — only
   the running activity can ask a device for its camera, so the ask lives here, once, at
   start (NOT in the scanner screen, and no `ActivityResultContracts` ask is in this flow).
   `onRequestPermissionsResult` carries ONLY a refusal, into `AppContainer.cameraRefused`
   (default false): a device that shows no dialog and never calls back is not a refusal, so
   the scanner still opens its camera and the camera's own failure is what the screen reports.
5. `setContent { CampBnbTheme { LaunchedEffect(AppContainer.deviceID) { watcher.start(deviceID) };
   CampBnbApp(deps = AppContainer) } }` — the banner watcher starts for THIS device id
   (notifications.md's baseline rule then governs what banners appear).

`onNewIntent` = `setIntent` + `handleNotificationIntent(intent)` + `handleQrLinkIntent(intent)`
(same handlers as the cold-launch calls above).
`handleNotificationIntent(intent)`: null → return; missing `NotificationRoutes.EXTRA_ROUTE`
extra → return (a notification without a route opens nothing); `EXTRA_NOTIFICATION_ID`
NOT null-or-blank → `AppContainer.notificationController.markRead(id)` (the in-app record
marks itself read when the tap opens the app); finally decode
`NotificationRoutes.screenFromIntentText(route)` and set it on `pendingScreen` ONLY when
the text names a known screen (unknown text queues nothing): the graph below navigates
that typed screen and calls `onPendingRouteHandled`. So: tap → (mark read) + (queue
screen) → graph opens the screen.
`handleQrLinkIntent(intent)` (US 01.05.02 — the phone's own camera opened a code's link):
null → return; the text is the `models.qrPayloadExtraKey` extra when a launcher set it, and
otherwise the OPENED URI ITSELF (`intent.data`) — the manifest registers the code's own scheme,
so a camera app opens a code's text as a link and hands this app the URI, not a private extra;
nothing at all → return. The text is read back into code text by `models.codeTextOfOpenedLink`
first (models.md owns THAT: text that is already code text answers as itself, a URI whose scheme
is this app's name answers as that scheme's prefix plus the path it names — a URI's scheme
carries no case of its own, so the prefix is this app's, not the arrived spelling; text naming
no link of this app names no code and queues nothing). That code text then resolves
`models.qrScreenOfLinkPayload` — there is no opened moment to pass, a code carries no
expiry (models.md): it resolves THROUGH the tested text
decision `models.qrRouteOfLinkPayload` (models.md owns THAT) and decodes the answer into a
typed screen: ANY code text names `Screen.QrLinkRoute(campsiteId)` — there is no dead/expired
arm any more, so `Screen.OldQRCode` is never queued from a code — and ONLY a non-null
screen is set on `pendingScreen` (a link that resolves nothing never overwrites an already-queued
notification screen). The graph then holds that screen until sign-in has ended and opens it
once the user is logged in (app.md's pending bridge, `signedIn`): MainActivity runs before any
render, so an opened link's user data populates when the graph opens.
`onDestroy`: `watcher.stop()` before `super.onDestroy()`.
`watcher` is the lazy `NotificationWatcher(AppContainer.notificationController,
SystemNotifier(applicationContext))`.

## App manifest (`app/src/main/AndroidManifest.xml`)

The manifest is the app's own file, not a generated default: it names the permission asks
(`POST_NOTIFICATIONS`, `CAMERA`, `WRITE_EXTERNAL_STORAGE` up to API 28 with raw external
access above it) and the ONE `MainActivity` activity. That activity carries the launcher
`intent-filter` AND the code-link filters: a `VIEW`/`DEFAULT`/`BROWSABLE` filter for
`<data android:scheme="campbnb"/>` and the same filter for the upper-case spelling. A code's
text IS a URI in this app's own scheme — the scheme is the code's prefix, the path is the rest
of the text — so registering it is what lets the phone's own camera open a code's link at all.
Both spellings are registered because a launcher is free to normalise a scheme's case either way
and `models.codeTextOfOpenedLink` reads the text case-free, so the match must not depend on
which way a given launcher chose.

## Screen (`@Serializable sealed interface Screen`) — the navigation vocabulary

One named type per screen the app can display; the seal makes every `is`-check
exhaustible at COMPILE time (developer note: a renamed/removed screen breaks every
window still referring to it — that is what keeps `navigate()` calls safe). Members,
exact: `Home(role: UserRole)`, `CampsiteRegistration`,
`EditCampsite(campsiteId: String)`, `LotteryEdit`, `LotteryMonitor`,
`LotteryDetails`, `LotteryWaitlist`, `Start`, `Login`, `SignUp`,
`Role`, `Search(role, query: String = "", openFilter: Boolean = false,
openHistory: Boolean = false)`,
`QrScanner(role: UserRole)`, `Reservations`, `CampsiteDetail(role, campsiteId)` (declared;
the graph below opens `CampsiteDetailsScreen` instead — see graph section),
`CampsiteMonitor(role: UserRole)`, `CampsiteDetailsScreen(role: UserRole, campsiteId: String)`,
plus the Home bottom-bar tab routes as data objects: `Explore`, `Messages`, `Bookings`,
`Inbox`, `Profile`, `MyCampsites`, `Reports`, `Flagged`, `AdminProfileView`,
`AdminNotificationView`, `AdminImages` (the admin's browser of every uploaded picture, US 03.06.01),
plus the notices/bridge entry routes: `Notifications` (the
notices view itself), `NotifReservations` (the invisible bridge a tapped reservation-type
notice names — the typed twin of notifications.md's `"reservations"` text),
`BookingEntry(lotteryID)` (the bridge tapped WIN/INVITE notices name),
`ChatReservationEntry(reservationID)` (the bridge a tapped MESSAGE notice names),
`QrLinkRoute(campsiteID)` (what MainActivity queues when the phone's own camera opens a
code's link — any code, none of them dead any more), `OldQRCode` (the dead-code notice, registered
but reachable from nothing) — the graph below registers
content and bridge frames for every one of these.
`UserRole` is the UI role (ui/components — ui.md); data-class payloads ARE the route
arguments (`toRoute<T>()` reads them back). A route argument can only hold a
primitive or an enum — the runtime's route serializer resolves one `NavType` per
argument and knows only those (proven against the pinned 2.8.0 jars: anything else
throws `could not find any NavType for argument …`). So the tab Home opens on is NOT
a route argument: `Home` carries only the role, and the tab travels as session state
through `Dependencies.homeTab` (`Screen?`, null names no tab), which `HomeScreen`
reads as its `initialTab` — there is no index vocabulary anywhere.

## CampBnbApp (`@Composable fun CampBnbApp(deps: Dependencies)`) — the `NavHost` graph

`startDestination = StartupGraph` — the SIGN-IN GRAPH NODE, not a screen inside it
(graphs.md: the rule is that a `NavHost` may only start on a DIRECT child of its own
graph; entering a graph node loads that node's own start destination — here
`Screen.Start` — by itself, so naming the screen at the root would crash at
instantiation with "navigation destination … is not a direct child of this NavGraph").
The graph is SPLIT (this branch's restructure, completed this batch): the `graphs/`
sub-package holds one or more `NavGraphBuilder` extension functions per feature area,
and the app calls EVERY one of them —
`startupGraph`, `exploreGraph`, `campsiteGraph`, `LotteryGraph` (capital L),
`CorrespondenceGraph` (from `NotificationGraph.kt`), `ReservationGraph`, `MiscGraph`,
`DebugGraph` (from `DebuggingGraph.kt`) — TWICE where the feature has tab content: once
inside the app `NavHost`, and once again inside the builder block HomeScreen is handed
(below). The two mounts split the graph's job: the APP mount hosts every destination a
jump out of tab content lands on (details pages, search, scanner, settings, reservations,
lotteries, the notice bridges), the HOME mount hosts the bottom-bar tab content itself.
The app `NavHost` registers no window directly any more — its only own frame is
`composable<Screen.Home>` itself. Entry-function NAMES follow their files exactly:
`startupGraph`/`exploreGraph`/`campsiteGraph` lower-case,
`LotteryGraph`/`CorrespondenceGraph`/`ReservationGraph`/`MiscGraph`/`DebugGraph`
capitalised — the caller spelling IS the declared spelling, so a renamed entry breaks
the call site at compile time. `GraphUtils.kt` holds the sealed `@Serializable
NavGraphNodes`: `ExploreGraph`/`CampsiteGraph`/`LotteryGraph`/`CorrespondenceGraph`/
`ReservationGraph`/`DebugGraph` each wrap their nodes in
`navigation<NavGraphNodes.X>(startDestination = …)`; `startupGraph` wraps its own in
`navigation<StartupGraph>` — `StartupGraph` is a top-level `@Serializable object` in
`StartupGraph.kt`, NOT a `NavGraphNodes` member (it is package-visible only so the app root
can name it in `goHomeAfterSignIn`'s pop — app.md; graphs.md names every node route and
start destination); `MiscGraph` alone registers bare top-level composables and declares
no node route at all.

Model/controller instances: the `CampsiteModel`s the graph nodes run over are built at
the app root and passed in — `exploreCampsiteModel` + `exploreCampsiteController` over
`deps.campsiteRepository` into both mounts of `exploreGraph` (its
`campsiteModel`/`campsiteControl` parameters), a `campsiteGraphModel` into both mounts of
`campsiteGraph` — so the two mounts of one graph share ONE pool of loaded state. The
fresh per-window instances stay inside the graph files as before: the QR node builds its
own model + controller, `campsiteGraph`'s registration/edit/details nodes build
`CampsiteController(campsiteModel, campsiteRepo)` off the passed model (its details node
also builds its own `LotteryModel` + `LotteryController` for the lottery gate — models.md
`Lottery.statusAt` semantics), its monitor node builds its own fresh pair, and each
lottery window still builds its OWN controller over its OWN fresh model with the
`nowProvider` literally wall-clock (`{ System.currentTimeMillis() }` — models.md's
`Lottery.statusAt` depends on it). `startupGraph` takes `deps.userController`/
`deps.userModel` as parameters.
The shared `LotterySelectedViewModel` carry is unchanged (viewModels.md —
`selectedLottery` read-only StateFlow + `updateLottery(new)`), built once at the app root
as a `viewModel()` default parameter and passed into the app mount of `LotteryGraph`:
the monitor clears it before opening edit and sets it before opening details or the
waitlist — there is no app-root `var` any more. Home's nested graph never mounts lotteries
or sign-in: no bottom-bar tab names one.

Routing rules that matter to readers and future integration tests (graph edges — one
line each; `nav` is the app controller, `homeNav` the nested one, all calls as stated):
- Start → `Screen.Login` on get-started. Login = `DeviceSignInScreen(deps.deviceID,
  userController = deps.userController, onEntered, onNotFound)`. **onEntered**: privileges
  = `userModel.currentUser?.roles`; role picks by PRECEDENCE `ADMIN` beats `OWNER` beats
  else `ENTRANT`; `deps.appViewSettings.applyViewDefaultIfNoneChosen(viewFor(role))` (the
  role-derived view is only a DEFAULT: a user who saved or switched a view keeps it across
  lifetimes — see viewModels.md's gate; the sign-in default clobbering a saved choice was
  the reported failure of the admin's settings view) then `nav.goHomeAfterSignIn(toUserRole(role))`
  — Home opens with the SIGN-IN GRAPH NODE popped off the stack inclusive, which is what clears
  the start/login frames (popping to `Screen.Start` would not: a pop whose target is absent
  leaves the stack untouched, and the node frame sits BELOW the Start frame, so a back gesture
  from Home would re-enter the node and show Start again).
  **onNotFound** → `Screen.SignUp`.
- SignUp: builds `User(deviceId = deps.deviceID, name, email, phone, roles = listOf(Role.ADMIN))`
  via `Database.Users.writeEngine().save` (flagged in-source: sign-up currently grants
  ADMIN; TODO marker) then navigates `Screen.Role`.
- Role: `RoleSelectScreen(onRoleSelected)` maps `UserRole→AppView` INLINE (ADMIN→AdminView,
  OWNER→OwnerView, CAMPER→EntrantView — same as ui.md's `toAppView`), sets the view,
  navigates `Home(role)` through `goHomeAfterSignIn`, popping the sign-in node inclusive (same
  eviction as above).
- Home (`Screen.Home(role)`): `entry.toRoute<Screen.Home>()` for the role; then
  `HomeScreen(role = role, appViewSettings = deps.appViewSettings.toAppViewSetting(),
  initialTab = deps.homeTab, homeNavController = homeNav)` with `homeNav =
  rememberNavController()` a NESTED graph inside Home: the bottom bar moves between tabs on
  THAT graph (ui.md `tabsFor` circles; a chosen circle navigates `tab.destination` with
  `popUpTo(findStartDestination)` + `launchSingleTop`, ui.md) and the app `nav` never
  enters or leaves a tab, so a tab change is never stacked on the last one, and while tab
  content shows the live APP-level frame IS Home. Home's body loads NOTHING and registers
  NO window of its own: the builder block RE-MOUNTS the six graphs whose screens the bar
  can name — `exploreGraph`, `campsiteGraph`, `CorrespondenceGraph`, `ReservationGraph`,
  `MiscGraph`, `DebugGraph` (not `startupGraph`/`LotteryGraph`: no bottom-bar tab names
  sign-in or lottery screens) — with the same parameters the app mount above passes (the
  root's `toast`, repos, controllers, models), so every tab's content is the same graph
  the app mount registers and the same shared components behind it. Only callback OWNERSHIP
  differs between the two mounts: in this HOME mount the graphs' back controls run on
  `homeNav` (a tab's Back pops the tab's own frame on the bar's graph and lands the bar
  where it was — Explore stays under every other tab), while outward jumps — details
  pages, search, QR, the notice bridges — still run on the app `nav`, keeping this live
  Home frame mounted underneath them. The one exception is `DebugGraph`: its `onNavBack =
  nav.goHome(currentRole())` at BOTH mounts (a bare Home re-entry — the admin tabs' back,
  as before). The nested graph OPENS on the NODE that owns the tab `initialTab` NAMES
  (graphs.md's `graphNodeFor` in `GraphUtils.kt`: a `NavHost` may only start on a direct
  child of its own graph, and the Home builder registers whole feature graphs as nodes —
  entering a node loads its start destination, so the start NAMES the node:
  Explore→`NavGraphNodes.ExploreGraph`, MyCampsites→`NavGraphNodes.CampsiteGraph`,
  Messages/Bookings/Inbox→`NavGraphNodes.CorrespondenceGraph`,
  Reservations→`NavGraphNodes.ReservationGraph`, the admin's Profiles/Notifications
  tabs→`NavGraphNodes.DebugGraph`; the Misc tabs Profile/Reports/Flagged open on the
  screen itself, being bare direct children). The `initialTab` seed is a SCREEN, never an
  index: callers that NAME a tab land the bar there — the root passes `campsiteGraph`'s
  `onNavHome` as `nav.goHome(currentRole(), Screen.MyCampsites)` so the campsite
  monitor's back names My Campsites (reported regression 9: a bare Home pop re-entered
  through the start = first tab and landed on Explore; naming the tab is what keeps that
  fixed). `HomeScreen` resolves the named screen against the live bar (`firstOrNull` on
  `tabsFor`'s destinations), and no name or a screen the live bar does not show opens the
  first tab, Explore).
  Which graph node serves which tab:
  - EXPLORE→`exploreGraph`'s start node: `ExploreScreen(recommended = cards, newSites = cards)`
    — the node loads `campsiteRepo.getAll` into its own card state itself
    (`campsite.toCampsiteCardData()`; the failure Toast `"Could not load campsites: …"` is
    the graph's `toastGenerator`), the empty/loading semantics of the screen are ui.md's,
    and the node's callbacks ARE the graph's parameters, so they navigate on the APP graph
    in either mount:
    NO settings entry at all: the Explore tab's Settings button and its `onSettingsClick`
    were removed once the whole settings surface moved onto the Profile view, so `navToSettings`
    is no longer a parameter of `exploreGraph` or of `MiscGraph` and no navigation names
    `Screen.Settings` — that member is gone from the sealed `Screen` set and the node is gone
    from `MiscGraph` (the owner asked for the screen to be removed once every setting was read
    from the Profile view). The earlier reasoning about a bare push leaving Home mounted under
    Settings no longer applies because nothing pushes Settings.
    campsite click→`CampsiteDetailsScreen(role, id)` PLAIN `nav.navigate`, same reasoning
    (that recurring back bug was reported again for this entry); search→`Search(role, text)`,
    filter→`Search(role, "", true)`, history→`Search(role, "", false, true)` (the Explore
    burger and the search view's burger list the SAME items from the one shared ui.md menu
    source — fix 11; each item opens the search view with its section showing),
    QR circle→`QrScanner(role)`.
  - MY CAMPSITES→`campsiteGraph`'s start node: `MyCampsitesTab(onOpenMyCampsites =
    { nav.navigate(Screen.CampsiteMonitor(currentRole())) }, onOpenLotteries =
    { nav.navigate(Screen.LotteryMonitor) }, onOpenReservations =
    { nav.navigate(Screen.Reservations) })` — those three buttons cross out of Home onto
    the APP graph, so the live monitor is always the APP mount's node (its back is that
    mount's `onNavHome` — the named-My-Campsites goHome above).
  - MESSAGES→`CorrespondenceGraph`'s `Screen.Messages` node (`CorrespondenceDestination`,
    `openTab = CorrespondenceTabs.MESSAGES_TAB`); INBOX→its `Screen.Inbox` node
    (NOTIFICATIONS_TAB) — in-Home back affordances are that mount's `onNavBack` =
    `homeNav.navigateUp()`. The notices view `Screen.Notifications` is the sub-graph's
    START: reachable at the APP level only — a notice whose record names no id falls back
    to it (`screenFor` → `Notifications`, and a MESSAGE with no reservation id →
    `Screen.Messages`); it opens `CorrespondenceDestination(RECEIVED_TAB)` for an owner
    else `NOTIFICATIONS_TAB`.
  - BOOKINGS→`CorrespondenceGraph`'s `Screen.Bookings` node: `BookingsScreen` reading the
    node's own values — `reservationRepo.getForEntrant(entrantId = userId, …)` then keep
    `status == ReservationStatus.ACTIVE` only, mapped to `BookingCardData` with
    `status = "Confirmed"`, `campsiteNames = listOf(found name or "Campsite")` (names from
    a nested `campsiteRepo.getAll`), `lotteryEntryId` as id, dates/guests copied; a second
    top-level `getAll` maps `CampsiteCardData` cards (the node's `pendingBookings` stays
    empty); tab selection kept in the node's `selectedBookingTab` var (`onTabSelected`
    mutates it). Failure Toasts, exact: `"Could not load bookings: ${error.message}"`,
    `"Could not load campsite details: …"`, `"Could not load campsites: …"` — Toast copy is
    integration text, NOT unit assertions.
  - RESERVATIONS→`ReservationGraph`'s start node hosts the view itself:
    `ReservationsPanel(userID = deps.deviceID, campsiteRepo, reservationRepo,
    notificationPreferences, currentUserId, fileDownloadWriter, goBack = onNavBack)` —
    pressing the tab lands in the view itself, no second button asking again; the panel's
    back is that mount's `onNavBack` = `homeNav.navigateUp()` (pops the tab's own frame;
    Explore stays under every other tab — the same net effect as the old
    `goBack = nav.goHome(role)`). The view's grouping rules are the graph's, below.
  - PROFILE/REPORTS/FLAGGED→`MiscGraph`'s nodes; PROFILE is `ProfileScreen`, which now
    carries the whole settings surface (see the Profile node below). There is NO Settings
    node: the member and the mount were removed once every setting was read from the Profile
    view, so `MiscGraph` registers bare `Profile`, `Reports`, `Flagged`, `QrLinkRoute` and
    `OldQRCode` only. The `role` the Profile shows is the role the signed-in profile holds
    (`deps.userModel.currentUser`'s roles, ADMIN > OWNER > ENTRANT — the same pick the sign-in
    makes), while its view comes from the shared `AppViewSettingController`. The two are
    different things and the settings surface the Profile draws needs both: a view-derived role
    would hide the app-view switch, which is what locked an admin out of their admin abilities
    for good after they switched views and closed the app.
  - ADMIN_PROFILE_VIEW→`DebugGraph`'s start node: `loadAllUsers()` then
    `AdminProfilesScreen(onNavBack, userModel, adminController)` (the shared controller carries
    out the removals an admin confirms from the list); ADMIN_IMAGES→
    `AdminImagesScreen(back = onNavBack, adminController)` (US 03.06.01: the admin's browser of
    every uploaded picture, reading the users and campsites through the controller and drawing
    `uploadedImagesFor`, each row offering the shared `AdminImageRemoval` panel); ADMIN_NOTIFICATION_VIEW→
    `NotificationLogScreen(title = "Notification Debugging", notificationSource =
    deps.notificationRepository, profileText = { userId -> profileLabelOf(userId,
    userModel.allUsers) }, deleteNotice = …)` (the debug log resolves profile references via ui.md's
    `profileLabelOf`, against the saved users loaded above; it is the ONLY view that keeps the
    raw `DB ID` segment, and its own `deleteNotice` runs the same `NotificationCommand.Delete`
    with no toast channel of its own — the admin reading raw fields removes an entry outright,
    and reports whether the store confirmed it, which is what lets the debug log re-read
    itself so a removed entry stops showing without the admin tapping Refresh). `DebugGraph`'s screens are
    only ever reachable through the bar — its app-mount copy stays registered and
    unreachable.
  (Screens with no entry at the mount that registers them — the APP mount's tab nodes
  (`Screen.Explore`, `Screen.MyCampsites`, Messages/Inbox/Bookings) and the HOME mount's
  bridge nodes (`NotifReservations`, `BookingEntry`, `ChatReservationEntry`,
  `QrLinkRoute`, `OldQRCode` — the last reachable from nothing now a code carries no expiry), its `Settings`, and its `DebugGraph` copies — stay
  registered where nothing navigates. These are NOT dead windows as in the earlier
  half-finished restructure: the SAME graph is live at the mount whose callbacks point
  there, and the root passes shared models into both mounts, so the two copies read one
  pool of loaded state.)
- Search (`exploreGraph`'s node; the reachable copy is the APP mount's — every search from
  the Explore tab or the QR half arrives through the app `nav`, and the home mount's copy
  stays registered with no entry): `observeModel(searchModel)` then `loadAndSearch(query)`; the screen opens with
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
  (controller.md owns the record-building rules). A tag removal RELOADS the page: the node's
  listing read is one step (`reloadListing()`) that the node runs when the page opens AND again
  after a successful `removeTag`, so the page re-reads the SAVED record and the tag that was
  taken out is gone from the drawn chips — the page drew its tags when it opened, and the
  removal edited the record, not that copy. The reload re-runs the same `getAll` + ratings
  read, so a failed reload keeps the last good view and its own error strings. The picture
  (US 03.03.01): the header's picture is clickable ONLY for an admin viewing a campsite that
  HAS one (`role == UserRole.ADMIN && campsite.imageUrl` not blank — otherwise the callback is
  `null` and the picture is inert); tapping it opens the shared `AdminImageRemoval` panel
  (ui.md), whose confirm calls `onRemoveImage(reason)` — the node runs
  `admin.removeImage(ImageKind.CAMPSITE, route.campsiteId, reason)`, toasts the message the
  controller answers with, and on success redraws the page with `imageUrl = ""` (the record
  itself is cleared by the controller; the page only drops its own copy). DIVERGENCE, left as the owner's call rather
  than silently changed: the live `onDeleteSite` calls `adminController.deleteCampsite(campsiteId, reason)`
  — the whole-record deletion that cascades through `CampsiteRemovalRepository` (controller.md
  documents BOTH `removeCampsite` as the policy action and `deleteCampsite` as the whole-record
  deletion; only the second is wired here). The tag picker no longer guesses from the drawn
  list: the graph passes `removableTags = policyCampsite?.tags ?: emptyList()` (the SAVED
  record's own tags — what `removeTag` edits), and the screen's Remove Tag menu offers
  exactly those, so an amenity the page shows beside them can no longer be picked and
  answer the failure toast (the page's display fold stays; the menu and the removal now
  agree). The action area is one hamburger per role (feature 2):
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
  `onTabSelected = { tab -> onCampsiteDetailsClicked(route.role, tab) }` — the picker hands
  over the tab's SCREEN (ui.md's `CampsiteDetailsScreen.onTabSelected: (Screen) -> Unit`), the
  graph's callback passed by the app root as `{ role, tab -> nav.goHome(role, tab) }` — Home
  opens on the named tab.
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
  ui's activity class the renderer; registered by `campsiteGraph` — reached ONLY from the
  My Campsites tab's `onOpenMyCampsites`, a push on the app graph, so the live node is
  always the APP mount's): ctor gets `goHome` = the graph's `onNavHome` (the root passes
  `nav.goHome(currentRole(), Screen.MyCampsites)` — back opens Home NAMING the My Campsites
  tab and the bar lands there; reported regression 9: back used to open the first tab. The
  name is a Screen, so no index is resolved anywhere: `HomeScreen` matches it against the
  live bar's `tabsFor` destinations, a screen that bar does not show opening the first tab)
  and `goBack` = the graph's
  `onNavBack` (the app mount passes `nav.navigateUp()` — pops the Monitor frame and
  re-exposes the live Home, whose nested graph is still on My Campsites) — plus
  `goToRegisterCampsite` = `onOpenCampsiteReg` (plain push →
  `Screen.CampsiteRegistration`)/`goToCampsiteDetails` = `onOpenCampsiteDetails(route.role,
  campsite.id)`/`goToEditCampsite` = `onOpenCampsiteEdit(campsite.id)`/`fileDownloadWriter =
  deps.fileDownloadWriter` (file-download wiring, item 8 — without it the screen offers no
  download at all)/`appViewSettings`/`deviceID`, all now the graph's parameters; renders via
  `activity.CampsiteMonitor(model, controller)` over the node's OWN fresh pair. Its
  `campsite_code_download` button writes `qrImageFileBytes(QRCode(campsiteId = campsite.id))`
  under `code.fileName()` through `fileDownloadWriter`; on success the screen stays silent
  AND raises a real device notification (`notifySavedCode`, a private helper in the file:
  androidx `NotificationManagerCompat`/`NotificationCompat` on `NotificationChannels.GENERAL`,
  guarded by `NotificationPermission.isGranted(LocalContext.current)`, no content intent —
  the saved file is the record, the notice only says it is there).
- `Screen.Reservations` (`ReservationGraph` start; entered by a notice through its bridge,
  by the My Campsites tab's link, and by the camper's tab) hosts the `ReservationsPanel`
  (panels/) DIRECTLY: `ReservationsPanel(userId = deps.deviceID, campsiteRepo, reservationRepo,
  notificationPreferencesService = deps.notificationPreferencesService, currentUserID = deps.currentUserId,
  fileDownloadWriter, goBack = onNavBack)`. A user OWNING ANY of the sites (decided
  by the loaded sites: `allCampsites.any { it.ownerId == userId }`, NOT the owner-role
  flag — a plain owner whose profile lacks the flag still auto-populates their sites in the
  top screen, which the role-flag gate had regressed) browses THEIR OWN sites
  (`ownerId == userId`) and sees EVERY reservation at a site; every other viewer
  browses only sites carrying their own reservations (`getForEntrant` … `any { it.campsiteId == campsite.id }`)
  and gets only `loaded.filter { it.entrantId == userId }`. Filter state is
  `ReservationFilter.ACTIVE/PAST/CANCELLED` (controller.md); changing campsite/filter
  re-runs the matching read; Toasts `"Could not load your reservations: …"` / `"Could not load campsites: …"` /
  `"Could not load reservations: …"`. Hands `ReservationScreen(... csvPreferences = notificationPreferencesService,
  currentUserId = currentUserID, fileDownloadWriter …)` (CSV download ↔ controller.md's `generateReceiptCsv`).
- NO `Settings` node: the member, the mount and the `SettingsScreen` composable were all
  removed once every setting was readable from the Profile view below (the owner asked for the
  screen to be removed). `MiscGraph` therefore takes the settings controllers for the
  `Profile` mount alone, and nothing in the app names `Screen.Settings` any more.
- The `Profile` node (`MiscGraph`, bare): `ProfileScreen(role, initialName/initialEmail/initialPhone
  from `userModel.currentUser`, onSave writes ONLY those three fields through
  `Database.Users.writeEngine().update`, onDeleteProfile deletes the profile and calls
  `navToStart`, onManageCampsites opens My Campsites for an owner, isSaving/isDeleting guard
  repeat taps)` PLUS the settings surface: the signed-in viewer's own settings (`EntrantSettingsItems`
  for a camper, `OwnerSettingsItems` for an owner — the same public entries the settings package
  owns, so the words live in one place), the app view switch for an ADMIN only (the same public
  `SettingsDropdown` + `ViewSwitchMenu`, through a `SettingsDisplayController()` the view holds
  itself, with `viewSettings = appViewSettings` and `appSettings = deps.appSettingsService`),
  and the `App Policy` bar every role gets, opening the shared `AppPolicyDialog`. An admin
  holds no settings of their own, so where their own-settings section would sit is the switch.
  The settings controllers are handed to this mount and to nothing else — there is no
  settings screen of their own any more — which is why the app root builds them here.
  The profile's OWN picture (US 01.08.02): `profileImage = userModel.currentUser`'s stored
  picture text (empty when none — drawn as a round `StoredImage`, and the initials placeholder
  when blank) and `onImageChanged` writes ONLY `profileImg` through the same update path and
  answers the screen with the new text so the picture redraws; the screen's picker reads a
  picture through `ImageRepository` (data.md) and refuses one it cannot get under the size
  limit with a message rather than a silent no.
- `fun NavController.goHome(role, tab = null)` — a LOCAL extension of the app's `nav`, inside
  `CampBnbApp`, because it needs `deps`: it writes `deps.homeTab = tab` and THEN navigates
  `Home(role)` with `popUpTo<Screen.Home> { inclusive = true }; launchSingleTop = true`. The
  tab cannot ride the route (a route argument holds a primitive or an enum, never a Screen —
  see the route-argument note above), so the write and the navigate are one step: the value an
  entry asks for is the value Home reads. The typed pop still cannot name a live PARAMETERIZED
  Home frame (the role keeps Home parameterized, so it empties the stack), and the following
  push re-establishes it: the net effect is a fresh `[Home]` whose NESTED graph restarts at the
  node owning the NAMED tab (`HomeScreen.initialTab`, a Screen): callers that name a tab
  (CampsiteMonitor's back, the details-page picker) land the bar there; naming none opens the
  first tab.
- `fun NavController.goHomeAfterSignIn(role)` — the SIBLING local extension, used ONLY by
  `startupGraph`'s `navToHomePage`: it writes `deps.homeTab = null` and navigates `Home(role)`
  with `popUpTo<StartupGraph> { inclusive = true }; launchSingleTop = true`. Popping to the
  sign-in NODE is the eviction the back gesture needs (see the sign-in bullets above): the node
  is the only frame ever sitting below the first Home while sign-in runs, and it is absent from
  the stack at every other time Home opens, so this pop answers as nothing at all for ordinary
  tab navigation — which is why `goHome` keeps its own `popUpTo<Screen.Home>` and the two stay
  separate. Naming the node needs `com.example.campbnb.graphs.StartupGraph` imported at the app
  root; the node is package-visible, it is only NOT a `NavGraphNodes` member.

**Pending-SCREEN bridge (typed)** — there are NO string route nodes left in the graph: the
app's routing vocabulary is the sealed `Screen` set alone. Route TEXTS exist at one crossing
only — the Android Intent extra: `SystemNotifier` writes `NotificationRoutes.routeFor(n)`
there (notifications.md owns the format), MainActivity reads it back through
`NotificationRoutes.screenFromIntentText` (null when the text names no known route —
nothing queues), and the QR half resolves through `models.qrScreenOfLinkPayload` (models.md
owns that resolver — it delegates to the tested string decision `qrRouteOfLinkPayload` and
decodes it; the two forms never disagree). The bridge into the graph is
`LaunchedEffect(deps.pendingScreen, signedIn)`: the member is an interface property so a null-check
cannot smart-cast it (this batch's compile error), so it captures `deps.pendingScreen` into
a local first; when non-null AND `signedIn` it runs `nav.navigate(pending)` on the APP graph then
`deps.onPendingRouteHandled()`. `signedIn` is an app-level flag the local `goHomeAfterSignIn`
extension sets the moment the FIRST Home opens — i.e. exactly when sign-in ends, on both the
found-device path and the sign-up path, since both end at `navToHomePage`. The gate is what
makes a scanned code log the user in before it lands them anywhere: a code's link the device's
own camera opened at cold start queues a screen BEFORE this app has run, and opening it there
would show a campsite to a user who is not logged in and has no data loaded (the reported
"scanning does none of that"). Re-watching on `signedIn` is what re-runs the bridge the moment
the first Home opens, so the user is signed in and THEN taken to the campsite. A notice that
arrives during sign-in waits for the same reason. The screens it can queue, and the nodes that answer them:
- `Screen.Notifications` / `Screen.Messages` (`CorrespondenceGraph` sub-graph START and its
  tab node): a notice whose record names no id falls back to one of these
  (`screenFor` → `Notifications`, or → `Messages` for a MESSAGE with no reservation id);
  `Notifications` opens `CorrespondenceDestination(RECEIVED_TAB)` for an owner else
  `NOTIFICATIONS_TAB` (the owner's OWN sent notices live under Received — see tab wiring
  below), `Messages` opens `CorrespondenceDestination(MESSAGES_TAB)`.
- `Screen.NotifReservations` (`CorrespondenceGraph`; a tapped record whose `screenFor`
  resolves to it — LOSE, OWNER_NOTICE, CONFIRMATION, CANCELLATION, REJECTION): invisible
  typed frame; its `LaunchedEffect` runs the graph's `popNoticeFrame()` — the bridge POPS
  ITS OWN FRAME before any push, and the pop is asked of the SAME navigation the pending
  frame was pushed onto (`navToNotif`'s), NOT of `onNavBack`. Under the home bar the WHOLE
  notice path now runs on the bar's own navigation — `navToNotif`, `popNoticeFrame`,
  `navToReservations` and `goToReservations` are all `homeNav` there — so the frame a tap
  pushes, the pop that clears it and the reservations view it then opens sit on the same
  stack the bar's back controls pop, and ONE back press returns the viewer to the tab they
  tapped from. Splitting the path across the app's navigation is what left frames on a stack
  the bar never popped — the reported back-button extra press, which the earlier fix of the
  POP alone did not settle. `popNoticeFrame` defaults to `onNavBack` (the standalone mount's
  `onNavBack` already IS the app nav). The one jump that cannot move is `navToLotteryMon`:
  the lottery monitor is not a bottom bar tab, so the bar's graph does not register it and
  that jump still goes on the app's navigation. A
  frame left on the stack — even this invisible typed one — would otherwise keep it under
  the reservations screen, and the first back press would land on it instead of the view
  the notice was tapped from; naming the live Home frame with a typed `popUpTo` could
  never work (Home is a parameterized route; the pop matched nothing, emptied the whole
  stack and left the reservations view as the only entry — the reservations half of
  reported regression 7). The self-pop keeps the old white-screen fix (no invisible frame
  stays under the view), so the first back press goes to whatever the notification was
  tapped from, under Home — then the graph's `navToReservations()` = PLAIN `nav.navigate(Screen.Reservations)`
  (the `ReservationGraph` start), no popUpTo at all, `launchSingleTop` still unused.
- `Screen.BookingEntry(lotteryID)` (`CorrespondenceGraph`; taps from WIN and INVITE records
  that name a lottery id — `screenFor` names it): `InvitationDestination` (destinations/,
  contract below) reads the lottery via `Database.Lotteries.readEngine().getById` and shows
  the panel ONLY while `viewerId in invited.invitedOwnerIds` and NOT `in joinedOwnerIds`;
  `Accept invitation` → `invitationController.acceptInvitation(lottery = invited, userId =
  viewerId, onSuccess = navToLotteryMon)` — the root's callback is PLAIN
  `nav.navigate(Screen.LotteryMonitor)` (the panel builds its own fresh
  `LotteryModel`/`LotteryRepository`/`LotteryController` with the wall-clock `nowProvider`
  to run the accept); `Not now` → `goToReservations` (the root: plain
  `nav.navigate(Screen.Reservations)`); already joined / resolved / not invited — so EVERY
  WIN-notice tap — keeps running `navToReservations` (the root: plain
  `nav.navigate(Screen.Reservations)`). Exact panel copy: header `Lottery Invitation`, body
  `You were invited to join this lottery. Accept to see it in your list and add your own
  campsites.`, buttons `Accept invitation` / `Not now` — integration copy, NOT unit
  assertions.
- `Screen.ChatReservationEntry(reservationID)` (`CorrespondenceGraph`, tapping a MESSAGE
  record that names a reservation id): `CorrespondenceDestination(MESSAGES_TAB,
  openReservationId = reservationID)` — the same chat-open the Messages tab gets. UNDER THE
  HOME BAR this frame is never reached at all: a chat is state the tab opens, not a place the
  app navigates to, so the destination's own `openNotice` wrapper intercepts this screen BEFORE
  any navigation and calls the app-level `openChatHere(reservationID)` instead of `onNavToNotif`
  (US 01.05.02, item 2 — pushing a frame that redraws the same tab is what made the first back
  press look like nothing). The composable stays registered and unreachable under the bar, like
  every other node the mount it is registered in never navigates to.
- `Screen.QrLinkRoute(campsiteID)` (`MiscGraph`, queued by MainActivity after resolving a
  LIVE code's opened-link text): invisible typed frame; `LaunchedEffect(campsiteID)` when
  it is NOT blank: role = the signed-in user's OWN privileges with the login precedence
  (`userModel.currentUser?.roles`: ADMIN then OWNER else ENTRANT — the same derivation the
  Login block runs); then the graph's `onNavBack()` (self-pop, the same pattern as the
  reservations bridge — a typed pop could never name the parameterized Home frame and
  emptied the stack, reported regression 7) then the graph's
  `navToCampsiteDetails(toUserRole(role), campsiteID)` = PLAIN
  `nav.navigate(Screen.CampsiteDetailsScreen(...))` — no popUpTo, NO `launchSingleTop`
  (the self-pop keeps the white-screen fix: no invisible frame stays under the details page,
  so the first back press goes Home, not to a blank frame; `launchSingleTop` would launch
  the details screen as the single top and leave nothing under back). The details composable
  follows the CURRENT app view regardless, so the key's role is only a carrier. A live
  code's link reaches the SAME details screen a scan of that code opens — a code names a
  campsite.
- `Screen.OldQRCode` (`MiscGraph`, still REGISTERED but reachable from nothing: a code carries
  no expiry, so no code's link is a dead one — whether to delete the notice screen and its
  route is NEEDS INFO, parked): `QrOldCodeNoticeScreen(back = onNavBack)` (the root: `nav.navigateUp()`) in
  place of any destination. The two QR screens are distinct typed routes, so the old
  one-segment-wide collision rule against the two-segment campsite link route no longer
  applies.

Correspondence/message tab wiring (`CorrespondenceDestination` in destinations/ owns WHERE
each list comes from — the screens themselves only draw what they are handed, ui.md): a
viewer whose VIEW acts as an owner gets THREE tabs (`CorrespondenceTabs.NOTIFICATIONS_TAB`
= `SentNoticesTab(userId, repo, deleteNotice, unresolvedProfileText)` from tabs/, drawing
`NotificationLogScreen(title = "Sent Notifications")` over
`SentNotificationsRepository(deps.notificationRepository, userId)` (data.md — a scoped
wrapper); RECEIVED = `ReceivedNoticesTab(userId, notificationController, onNavToNotif,
deleteNotice)`; MESSAGES = `MessagesTab(openReservationId, userId, controller)`), every
other viewer gets TWO (NOTIFICATIONS = the received notices). The VIEW decides this, not
the signed-in role — both mounts pass `ownerViewActive = { currentRole() ==
UserRole.OWNER || currentRole() == UserRole.ADMIN }`, where `currentRole()` is the app
root's derivation from the live `AppView` setting — so switching views moves the tabs and
an entrant never reaches the sent notices. Under the HOME mount the correspondence graph is
also handed the app-level chat state — `openChatHere: (String?) -> Unit` (writes
`AppContainer.openChatReservationId`; null closes the chat) and `chatOpenFor: () -> String?`
(reads it) — which only that mount passes, so the standalone mount keeps navigating the
bridge frame as before. `ReceivedNoticesTab` = `viewModel(key =
"notification-inbox-$userId", factory = simpleViewModelFactory {
NotificationViewModel(notificationController, userId) })` → `NotificationInboxScreen(navToNotification
= { notification -> onNavToNotif(NotificationRoutes.screenFor(notification)) })` — `viewModel()`
keeps the same inbox (and its Firestore listener) across recompositions (viewModels.md's
`SimpleFactory`), and a notice tap hands the CALLER the TYPED screen that record names
(notifications.md's `screenFor`), so an in-app notice tap runs the same bridges the banner
tap above does. `MessagesTab` = same pattern with `MessageViewModel(controller, userId)` +
`simpleViewModelFactory`; a non-null `openReservationId` calls `messageViewModel.open(id)`;
draws `MessagesScreen(viewModel, currentUserId = userId)`. Under the home bar the tab's open
id is the app-level fact instead — `chatOpenFor() ?: openReservationId` — so the chat a notice
tap opened stays open while the tab is showing, and the graph clears the fact on ENTRY to the
Messages tab (`LaunchedEffect(Unit) { openChatHere(null) }` in the `composable<Screen.Messages>`),
so a chat never carries over into the next visit.

## Shared window components (`destinations/`, `tabs/`, `panels/`) — pick per viewer, delegate to ui

These packages hold what BOTH mounts of the graphs call, so every entry to a screen shares
one wiring; every return/exit affordance is passed IN — the mount or root that opened the
destination owns where back and outward jumps go. Signatures (as declared):

- `destinations/CorrespondenceDest.kt` — `CorrespondenceDestination(openTab, openReservationId =
  null, onNavBack = {}, ownerViewActive, userId, repo, notifController, msgController,
  onNavToNotif: (screen: Screen) -> Unit, deleteNotice: (String, (removed: Boolean) ->
  Unit) -> Unit = { _, _ -> }, unresolvedProfileText: (String) -> String =
  unresolvableProfileLabel, openChatHere: ((String?) -> Unit)? = null, chatOpenFor:
  () -> String? = { null })`: ui's `CorrespondenceScreen` with the tabs this viewer has (tab
  wiring above); `openTab` names which one opens (`CorrespondenceTabs`, ui.md); a non-null
  `openReservationId` opens the Messages tab straight into that reservation's chat. The last
  two are the home-bar chat state, and only that mount passes them: `openNotice` is the
  destination's own wrapper over `onNavToNotif`, and when `openChatHere` is present it
  intercepts `Screen.ChatReservationEntry` — a chat tap sets the app-level state
  (`openChatHere(reservationID)`) instead of navigating — while every other tap still calls
  `onNavToNotif`; the Messages tab's open id is then `chatOpenFor() ?: openReservationId`, so
  the chat a tap opened stays open while the tab shows. Every
  branch is handed a `deleteNotice` that answers with whether the store confirmed the removal:
  the graph builds one that runs data.md's `NotificationCommand.Delete` over the entry the
  viewer was shown, toasts `"Could not remove that notice: <reason>"` on failure, then reports
  the outcome, so a log re-reads itself
  once the entry really is gone (see ui.md). `unresolvedProfileText` is what a profile
  reference the sent listing cannot look up draws — the app root hands both mounts a resolver
  that shows the raw key only while the admin view is the live one, and the bare `"unknown"`
  otherwise. The sent half also draws its bars with `rawIdsAllowed = false`: a person reading
  the notices they caused is not reading database keys, and only the admin's own debug view
  leaves that default true.
- `destinations/InvitationDest.kt` — `InvitationDestination(viewerId, notificationController,
  navToLotteryMon, navToReservations, goToReservations, lotteryId = null)`: one lottery's
  invitation panel (bridge section above; the accept runs through a fresh
  `LotteryModel`/`LotteryRepository`/`LotteryController` with wall-clock `nowProvider`).
- `tabs/CampsiteTab.kt` — `MyCampsitesTab(onOpenMyCampsites, onOpenLotteries,
  onOpenReservations)`: the owner's tab screen, naming the three links it offers (copy
  `My Campsites`/`My Lotteries`/`Reservations`).
- `tabs/NoticeTabs.kt` — `ReceivedNoticesTab(userId, notificationController, onNavToNotif:
  (Screen) -> Unit, deleteNotice: (String, (removed: Boolean) -> Unit) -> Unit = { _, _ -> })`
  and `SentNoticesTab(userId, repo, deleteNotice = { _, _ -> }, unresolvedProfileText =
  unresolvableProfileLabel)`: the notice tabs (wiring above). The sent tab passes its
  `unresolvedProfileText` on as the log's `profileText`, so the view that may read raw keys
  is the caller's to say.
- `tabs/MessagesTab.kt` — `MessagesTab(openReservationId = null, userId, controller)`:
  chat-tab content (wiring above).
- `panels/ReservationPanel.kt` — `ReservationsPanel(userId, campsiteRepo, reservationRepo,
  notificationPreferencesService, currentUserID, fileDownloadWriter, goBack)`: the
  reservations view; its grouping rules are the `Screen.Reservations` rule above, read
  against the panel's parameter names.

## Test reach (this package)

NOTHING in this package is a JVM unit target: MainActivity and every Composable need an
Android Context and the androidx runtime; AppContainer's members demand live Firestore
engines. The exact strings above (Toast copy, invitation panel copy) are integration copy
— do not turn them into unit assertions. What IS testable and where it lives: every
rule a screen exercises (read filters, role precedence, banner baseline, route maps,
list grouping) is fully specified in `data.md`, `database.md`, `models.md`,
`controller.md`, `notifications.md`, `viewModels.md` — tests go there. This file's role
for the test agent: know that `Dependencies` is the app-level seam (a test provider MAY
implement it with fakes — nobody in the house does yet) and that the sealed `Screen` set
ALONE is the app's routing vocabulary (route TEXTS exist only at the Android Intent
boundary — notifications.md and models.md own that format).
