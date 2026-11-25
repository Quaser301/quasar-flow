# functions — the scheduled lottery draw (TypeScript, `CampBNB/functions/`)

Spec file for `CampBNB/functions/` — `index.ts` (135 lines) plus `package.json`
(package name `campbnb-lottery-draws`, node 20, build `tsc`, deploy entry `lib/index.js`).
This is NOT Kotlin: it runs as a Firebase scheduled function and is therefore NOT reachable
from the Kotlin test suites at all. It is specced because it writes the SAME records the
Kotlin side reads — it is the other producer of the wire formats `data.md`/`database.md`/
`models.md` define.

The file header's NOTICE already points at `AI_Disclosure/Wyatt_AI_Use.md` for its AI-assist
prompts, and at `documentation/AutomaticDraws.md` for the design and the no-Blaze
investigation (executed under the ASSUMED Blaze plan, US 02.05.01). Its wire fields mirror
the Kotlin data classes exactly (`Lottery`, `WaitingListEntry`, `Notification`).

## Documented contracts (for integration reading — no Kotlin test may import this)

- `pickWinners(ids, count)`: a partial Fisher–Yates over a COPY of the pool — each pick
  uniform over the remaining pool, so every entrant has equal chance; picks `count` DISTINCT
  ids but stops when the pool empties (count above pool size ⇒ the whole pool, shuffled-picked).
- `campsiteNameOf(campsiteId)`: empty id ⇒ `''`; reads the `campsites/<id>` doc; exists ⇒
  `String(campName)`, otherwise `''`.
- `drawAndClaim(lotteryId, numberOfWinners)`: collects entrant ids from the `waitingList`
  collection where `lotteryId` matches, dropping empty strings; picks winners; then ONE
  Firestore transaction on `lotteries/<id>`: if `drawn === true` it returns false without
  touching anything (another run drew first); otherwise sets `drawn: true, winnerIds: winners`.
  Returns the winners ONLY if this run claimed — the winner set is decided exactly once even
  if two runs ever see the same due lottery.
- The scheduled job `drawDueLotteries = onSchedule('everyMinute')` selects lotteries with
  `drawn == false` AND `registrationEnd <= Date.now()` — the draw happens while every app is
  closed, and the app side only ever reads the result (`LotteryController.checkIfWon` and the
  notification inbox; controller.md/models.md own that side — no Kotlin change is needed to
  receive it). Each lottery is isolated in its own try/catch: one failure must NOT leave
  later due ones behind. Zero-winner draws are skipped.
- The naming of a won notification: `campsiteIds[0]`'s name, fallback `the campsite` —
  "same fallback as NotificationController" (controller.md's WIN copy).

## The WIN record it writes — exact wire contract

One record per winner at doc id **`<lotteryId>_<userId>_WIN`** (deterministic id keeps
re-writes idempotent):
`recipientId = userId`, `senderId = ''` (system-created draw — matches the Kotlin model docs),
`type = 'WIN'`, `title = "You won the lottery for <named>!"`,
`message = "Congratulations! You were selected in the draw for <named>. Action needed: open the app and choose your dates before the booking window closes."`
(identical copy to controller.md's WIN record — both producers write the same thing),
`lotteryId`, `reservationId = ''`, `campsiteName = <unresolved name or ''>`,
`status = 'UNREAD'`, `pushAllowed = false` (whether to push is the CLIENT's opt-out decision),
`sentAt = firestore Timestamp.now()`, `readAt = null`.

Where that record lands in Kotlin: it parses through the Firestore `Notification` adapters
(`database.md`) exactly like Kotlin-built WIN records — the field NAMES are the Notification
fields (models.md), the doc-id form is the same id pattern (models.md's record ids). A
Kotlin test that wants the cross-producer seam asserts the PARSER accepts this shape
(fixtures per data.md/database.md), not the TypeScript itself.

## Test reach

None — the Kotlin suites cannot call TypeScript. Every claim above exists so the spec stays
one system: if a test-writing agent reads this file, it should treat it as READ-ONLY
context for why WIN records may exist that no Kotlin code made. Anything the app promises
about WIN records (ordering, marking read, banner rules, routes) is specified — and tested —
in `models.md`, `controller.md`, `notifications.md`.
