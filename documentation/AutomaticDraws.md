# Automatic Lottery Draws

UML documentation for the out-of-app draw of `CampBNB/functions/` (US 02.05.01): the
time-scheduled function that picks winners when a registration period ends, and the
notification records it writes for them. The Kotlin side only reads the result and was
not changed.

## What the requirement forces

US 02.05.01 says: *without opening the app*, winners are drawn and notified, and the
owner takes no action. Working through what that requires:

- Firestore has no scheduler of its own: a document does not change "when a time passes"
  unless something writes at that time. A client's snapshot listener (as in
  `FirestoreNotificationRepository.listenForUser`) only runs while the app is open.
- So the drawing must be done by an out-of-app scheduled actor. Firebase's only native
  one is a Cloud Function, and Cloud Functions require the Blaze plan: the official
  pricing-plans doc and two 2026 sources state Cloud Functions are not available on the
  free (Spark) plan. Checked 2026-11-06.
- Conclusion: there is no fully Blaze-free drawing inside Firebase that meets the
  criterion as written. The options were:
  1. Deferred client-side draw at next app open — keeps "no owner action", relaxes
     "without opening the app" (the notification arrives on next open). Not taken.
  2. An external free-tier scheduler hitting Firestore over REST — outside Firebase
     entirely, adds another moving part and another account. Not taken.
  3. Accept Blaze with one time-scheduled function doing all of it. Executed: the
     project is taken to be on the Blaze plan by the owner's decision of 2026-11-06.

```mermaid
sequenceDiagram
    participant App as Entrant app (open, listening)
    participant Scheduled as TimeServer scheduled function
    participant Lotteries as Firestore lotteries collection
    participant Waiting as Firestore waitingList collection
    participant Notifications as Firestore notifications collection

    Scheduled->>Lotteries: everyMinute: where drawn=false and registrationEnd<=now
    Lotteries->>Scheduled: due lotteries
    Scheduled->>Waiting: entries where lotteryId=id
    Waiting->>Scheduled: entrant userIds
    Scheduled->>Scheduled: partial Fisher-Yates uniform pick of numberOfWinners
    Scheduled->>Lotteries: update drawn=true, winnerIds
    Scheduled->>Notifications: one WIN record per winner
    Notifications~>>App: the existing snapshot listener sees the added record
```

Reading the diagram:

- One scheduled function handles the whole criterion: draw, mark, notify. Nothing else
  runs out-of-app.
- Claiming is transactional: the transaction re-reads the lottery and only sets
  `drawn=true` while it is still false, so the winner set is decided exactly once even
  if two runs ever see the same due lottery; notification records carry deterministic
  ids, so their writes are idempotent too. A failing lottery is logged and does not
  leave the later due ones behind.
- The app already consumes every artefact it produces (`LotteryController.checkIfWon`
  reads `drawn`/`winnerIds`, the inbox listener receives the added notification), so
  the Kotlin side needed no changes.
- The issue's uncertainty about not overloading the DB by polling is answered by the
  existing streaming snapshot listener: the client is told about added notification
  documents by Firestore itself, no polling loop.
- Scope note: the phone-level notification channel (Firebase Messaging pushes) has no
  token infrastructure in the app at all; `Notification` is documented as the permanent
  in-app record, separate from phone push. Telling a closed app's phone "you won" is
  a separate feature and out of scope here.

## Wire contract the function must match

The function writes the same documents the Kotlin data classes define, so field names
mirror them exactly (from `models/Lottery.kt`, `models/WaitingListEntry.kt`,
`models/Notification.kt`, collections named in `database/Database.kt`):

| Document | Where written by the app | Fields the function reads/writes |
| --- | --- | --- |
| lottery | `lotteries` collection | reads `drawn`, `registrationEnd`, `numberOfWinners`; writes `drawn=true`, `winnerIds` |
| entry | `waitingList` collection | reads `lotteryId`, `userId` |
| notification | `notifications` collection, id `${lotteryId}_${userId}_WIN` | all of `Notification`'s fields; enums as their names (`WIN`, `UNREAD`), `senderId` empty for system-created |

PLACEHOLDER: the exact encoded form (enum names, Long as epoch millis, the
`@ServerTimestamp` field) mirrors what the Kotlin serialization declares; the deployed
pairing has not been machine-checked against a live Firestore yet.

## Deployment

`CampBNB/functions/` holds the bare-bones source; deploying it is a manual
firebase-cli step the owner of the Firebase project runs, and it moves the project onto
Blaze as part of enabling functions.
