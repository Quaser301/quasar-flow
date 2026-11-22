# spec — the CampBNB systems specification (for writing tests without reading code)

This directory is a complete, self-contained description of every system in the CampBNB
app. Its purpose: a test-writing agent can produce COMPILING Kotlin test files from these
files alone, never opening production sources. Each area file carries the exact API
surface (names, signatures, parameter names), the behavior contracts stated as testable
rules, the test seams and fakes that make each system reachable on the JVM, and the
vocabulary (string literals, enum members, labels) a test needs to name things.

Cross-references between these files are part of the contract — follow them. The spec is
written so a reader who follows every pointer never hits a gap.

Written against branch `wyatt-us-01.05.02` (tip `c985351` plus its still-uncommitted
working tree), 2026-11-22. The branch's night-run changes are uncommitted on the tip;
the spec describes that state (tip + working tree) as it is.

## The area files (all complete — none are stubs)

| File | Area | Depth |
|---|---|---|
| `data.md` | package `com.example.campbnb.data` — repositories, preferences services, file-download sinks | full: API + behavior + the fake-idiom section that makes every consumer testable |
| `database.md` | package `database` — Firestore-backed engines and adapters (`Database.*.readEngine()` etc.), the wire format | full: API + why engine-backed members need live engines and what does not |
| `models.md` | package `models` — record data classes (campsite, lottery, waiting list, notification, reservation, user, role enum), the live models, model observation, QR codec, date helpers | full |
| `controller.md` | package `controller` — the behavior owners: record-building rules, read filters, ordering rules, opt-outs, receipts | full, including the standing-up-real-controllers-over-fake-stores pattern the test idiom follows |
| `notifications.md` | package `notifications` — channels, routes, watcher, system notifier; the banner baseline rule | full |
| `viewModels.md` | package `viewModels` — the ViewModel-backed state holders (view/setting controllers, inbox message model, display controllers, factory helpers) | full |
| `app.md` | root package — `Dependencies` seam, `AppContainer` (the only provider), `MainActivity` startup order, the `Screen` sealed navigation vocabulary, the `CampBnbApp` route graph and its seven string routes | wiring-contract depth (this area is glue; its rules point into the full files above) |
| `ui.md` | packages `ui.*` — screens and shared components | split ON PURPOSE: the pure helpers (role maps, tab/group vocabularies, label and text functions, the inbox ViewModel) at full depth — they are the testable surface; Composable screens at wiring-contract depth (parameters + what they call) |
| `functions.md` | `CampBNB/functions/` (TypeScript) — the scheduled lottery draw | descriptive only: it writes the same WIN records `models.md`/`database.md` parse; NOT reachable from the Kotlin suites, and the file says so |

## How the test-writing agent uses this spec

1. Read ONLY these files. Never open production sources, and never read the existing
   test files either — the spec is the truth for compiling against the current tree.
2. The test idiom (real construction shapes, `class FakeListingStore : CampsiteRepository()`
   with parens for open classes and none for interfaces, snake-case underscore-free
   `@Test` names, `assertEquals(expected, actual)`) is quoted working-shaped in
   `data.md` and `controller.md` — follow those sections; they compile against this Kotlin.
3. Test ONLY what the area files mark unit-reachable. Anything needing a Context, a
   Composable, `MainActivity`, or a live Firestore engine is marked not-JVM — skip it.
   File-`private` helpers are not nameable from a test; where the spec gives a private
   helper's rule, assert it THROUGH the public entry point that draws it, or skip.
4. Exact string literals: assert the ones the spec presents as behavior contracts
   (record copies, list labels, ViewModel `Error` messages). What the spec flags as
   integration copy (Toast text, panel display text) is NOT a unit assertion.
5. Where the spec flags a behavior as chosen, current-defect, or PLACEHOLDER, pin the
   CURRENT behavior in the test and carry that flag in a comment — do not "fix" it.
6. Kotlin dialect gotchas (hex constants above `Int` as `Long`, `!in`, no bare `return`
   in non-inlined lambdas, top-level function imports as `package.functionName`, sealed
   plain-`class` vs `open class`, `copy()` only on `data class`) are encoded where they
   matter, inside the area files — honor them; they are what keeps generated tests
   compiling.

## Complements, not substitutes

- `documentation/UML_Diagram.md` — a class-level architecture map (inheritance/composition arrows).
  Useful background; it carries no behavior rules, so it is not a test source.
- `documentation/AutomaticDraws.md` — the design doc and no-paid-plan investigation behind
  `functions.md`'s scheduled draw.
- `AI_Disclosure/Wyatt_AI_Use.md` — every AI-assisted prompt and the files it touched; it
  records which work in this tree came from which prompt.

## Coverage truth

All eight Kotlin package areas plus the app root and the TypeScript functions directory are
covered by the files above; every file listed exists and is complete. At the time this spec
was written, the project's own test task reported 26 suites / 195 tests / 0 failures / 0
errors (fresh result XML) — new tests written from this spec are additions to that set.
No part of the spec is aspirational: where the code has a placeholder or a chosen quirk,
the spec says so rather than smoothing it over.
