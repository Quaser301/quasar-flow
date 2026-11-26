# graphs — the feature sub-graphs the app graph mounts

Spec file for the `com.example.campbnb.graphs` package
(`CampBNB/app/src/main/java/com/example/campbnb/graphs/`, 9 files).

Written against branch `wyatt-us-01.05.02` (tip `8bc8b2d` plus working tree), 2026-11-25.

NOT JVM-reachable: every entry here is a `NavGraphBuilder` extension function that needs
the Compose/nav runtime. This file is WIRING-CONTRACT depth — what each graph is, what
it registers, and the name traps a reader (or a test agent pointed at the app graph)
would otherwise trip on. The app-level call sites and every callback's routing rule live
in `app.md` (the graph section); the bottom-bar tab vocabulary lives in `ui.md` (`tabsFor`).

## The start rule (why the roots start where they do)

The nav runtime (`androidx.navigation` 2.8.0, linked) resolves a graph's start
destination among the DIRECT children of that graph only; a start destination nested
inside a sub-graph is not found and the app crashes at instantiation with
`"navigation destination … is not a direct child of this NavGraph"`. Entering a
`navigation<…>` NODE, however, loads that node's own start destination automatically.
So:

- The app `NavHost` starts on `StartupGraph` — the sign-in GRAPH NODE (`app.md`), not
  on `Screen.Start` inside it.
- Home's nested `NavHost` starts on the node that owns the opening tab's screen
  (`graphNodeFor`, this file's `GraphUtils.kt` — the UI file no longer keeps that map),
  not on the tab's screen — except for the
  Misc tabs, whose screens ARE direct children (MiscGraph registers bare).

## GraphUtils.kt — the shared node registry

```kotlin
@Serializable
sealed interface NavGraphNodes {
    @Serializable data object CorrespondenceGraph : NavGraphNodes
    @Serializable data object ExploreGraph        : NavGraphNodes
    @Serializable data object ReservationGraph    : NavGraphNodes
    @Serializable data object CampsiteGraph       : NavGraphNodes
    @Serializable data object LotteryGraph        : NavGraphNodes
    @Serializable data object DebugGraph          : NavGraphNodes
}
```

Six members. `StartupGraph` and `MiscGraph` are NOT members (see below).

The same file also owns the ONE screen→node lookup, `graphNodeFor(screen): Any` — the
graph node a screen's content is registered under (Explore→`ExploreGraph`, MyCampsites→
`CampsiteGraph`, Messages/Bookings/Inbox→`CorrespondenceGraph`, Reservations→
`ReservationGraph`, AdminProfileView/AdminNotificationView→`DebugGraph`, anything else
— MiscGraph's bare screens — → the screen itself). It lives here rather than in a UI
file because it is graph knowledge: which node a mount registers each graph under. `HomeScreen`
calls it to pick its nested start.

## Every graph: file → entry → node route → start → registered screens

| File | Entry (`NavGraphBuilder.X(…)`) | Node route | `startDestination` | Registers (`composable<Screen.…>`) |
|---|---|---|---|---|
| `StartupGraph.kt` | `startupGraph` (lower-case) | `StartupGraph` — a top-level `@Serializable object StartupGraph`, NOT a `NavGraphNodes` member | `Screen.Start` | `Start`, `Login`, `SignUp`, `Role` |
| `ExploreGraph.kt` | `exploreGraph` (lower-case) | `NavGraphNodes.ExploreGraph` | `Screen.Explore` | `Explore`, `Search`, `QrScanner` |
| `CampsiteGraph.kt` | `campsiteGraph` (lower-case) | `NavGraphNodes.CampsiteGraph` | `Screen.MyCampsites` | `MyCampsites`, `CampsiteRegistration`, `EditCampsite`, `CampsiteDetailsScreen`, `CampsiteMonitor` |
| `LotteryGraph.kt` | `LotteryGraph` (capital) | `NavGraphNodes.LotteryGraph` | `Screen.LotteryMonitor` | `LotteryMonitor`, `LotteryEdit`, `LotteryDetails`, `LotteryWaitlist` |
| `NotificationGraph.kt` | `CorrespondenceGraph` (capital) | `NavGraphNodes.CorrespondenceGraph` | `Screen.Notifications` | `Notifications`, `Bookings`, `Messages`, `Inbox`, `NotifReservations`, `BookingEntry`, `ChatReservationEntry` |
| `ReservationGraph.kt` | `ReservationGraph` (capital) | `NavGraphNodes.ReservationGraph` | `Screen.Reservations` | `Reservations` |
| `MiscGraph.kt` | `MiscGraph` (capital) | NONE — no `navigation<…>` wrapper | n/a | bare `Profile`, `Reports`, `Flagged`, `QrLinkRoute`, `OldQRCode` at the mount site |
| `DebuggingGraph.kt` | `DebugGraph` (capital) | `NavGraphNodes.DebugGraph` | `Screen.AdminProfileView` | `AdminProfileView`, `AdminNotificationView` |

Name traps (file name ≠ member name, in two files):

- `NotificationGraph.kt` defines `CorrespondenceGraph` — there is no graph member named
  `NotificationGraph` (the `notifications` PACKAGE of `notifications.md` is unrelated).
- `DebuggingGraph.kt` defines `DebugGraph` — there is no member named `DebuggingGraph`.
- `StartupGraph.kt`'s node route is the top-level `object StartupGraph`; the
  `NavGraphNodes` interface has no `StartupGraph` member. It is package-visible only because
  the app root names it in `goHomeAfterSignIn`'s pop (app.md); it is still not a member.
- `MiscGraph.kt` declares no node route at all — the graph has no wrapper node; its
  screens register bare at every mount.

## Mounts

Each entry is called at most twice (`app.md`): once inside the app `NavHost` (the APP
mount) and once inside the builder block `HomeScreen` is handed (the HOME mount) —
`startupGraph` and `LotteryGraph` at the app mount ONLY (no bottom-bar tab names a
sign-in or lottery screen). Both mounts of one graph receive the SAME shared models/
controllers built at the app root, so they share one pool of loaded state. Only
callback OWNERSHIP differs: in the HOME mount a graph's back controls run on `homeNav`
(the bar's graph), outward jumps still run on the app `nav`. `DebugGraph`'s `onNavBack`
is `nav.goHome(currentRole())` at BOTH mounts.

The callback PARAMETERS each entry takes (names, arities, what each may call) are
specified at the call sites in `app.md`; the screens they open are specified in the
area files of their own packages (`ui.md` etc.). Nothing here is unit-reachable on the
JVM; the graph configuration is verified by clean compile plus the registration sweep
described in `quasar-flow/scratch/harness/` records.
