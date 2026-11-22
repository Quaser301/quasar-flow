# controller — the single control point per feature

Spec file for the `com.example.campbnb.controller` package
(`CampBNB/app/src/main/java/com/example/campbnb/controller/`).

Every screen talks to exactly one controller; every controller decides and orchestrates,
reaching data only through the seams of `data.md` and reporting events only through
`NotificationController`. The records it builds are the `Notification`/`NotificationIntent`
types of `models.md`.

Written against branch `wyatt-us-01.05.02` (tip `c985351` plus working tree), 2026-11-22.

## Shared shape — controllers are not seams

- Every controller here is a plain `class`: NOT subclassable and NOT replaceable by a fake.
  A test constructs the REAL controller over fakes of its seam-typed constructor
  parameters (`MessageRepository`, `NotificationRepository`, `CampsiteRepository`,
  `LotteryRepository`, `UserRepository` — see "Standing them up").
- Async work answers through `onSuccess`/`onFailure`/`onDone`/`onComplete` continuations
  exactly as `data.md`'s shared contract says; guard clauses answer `onFailure` and
  `return` before any store is touched.
- Status strings in the rules below are EXACT: the model's `statusMessage` is the user-
  visible channel, and tests assert these strings. Error strings built from a lookup
  append `" " + error.message` to the stated prefix — assert the prefix, not the whole.
- Several constructor parameters carry defaults that turn a feature off; tests may
  override them with named arguments instead.

## Adapters and fun interfaces (small files first)

```kotlin
// NotificationController.kt
fun interface OptOutChecker {
    fun filterAllowed(userIds: List<String>, onResult: (List<String>) -> Unit)
    companion object { val AllowAll = OptOutChecker { ids, done -> done(ids) } }
}
// MessageNotificationAdapter.kt
fun interface NewMessageNotifier {
    fun messageSent(message: Message, campsiteName: String)
}
```

`UserController` IMPLEMENTS `OptOutChecker` (so it can serve as NotificationController's
opt-out checker); a test fake is built directly: `OptOutChecker { ids, done ->
done(ids.filter { ... }) }`. Fun interfaces are constructed from a lambda, not subclassed.

`MessageNotificationAdapter(private val notifications: NotificationController) :
NewMessageNotifier` — `messageSent(message, campsiteName)` forwards
`notifications.notify(NotificationIntent.MessageArrived(message, campsiteName))` and
nothing else.

```kotlin
// Conversation.kt — data in the controller package, not a controller
data class Conversation(reservationId: String, otherUserId = "", campsiteId = "",
    ownerId = "", entrantId = "", lastText = "", lastSentAtSeconds: Long? = null,
    unreadCount: Int = 0)
val hasBothParticipants: Boolean   // ownerId AND entrantId both non-blank

object Conversations {
    fun group(userId: String, all: List<Message>): List<Conversation>
    fun thread(userId: String, reservationId: String, all: List<Message>): List<Message>
}
```

- Internal sort key: `sentAt.seconds * 1000 + nanoseconds / 1_000_000`; a message with NO
  stamp gets `Long.MAX_VALUE` (so it sorts FIRST among newest).
- `group`: keep messages where `userId in participantIds`; one `Conversation` per
  `reservationId`: `otherUserId` = the newest message's other side (if newest sender is
  the user, its recipient, else its sender); `lastText`/`lastSentAtSeconds` from the
  newest message; `unreadCount` = messages whose `recipientId == userId` and `status
  == UNREAD`. Chats ordered NEWEST FIRST by the newest message's key.
- `thread`: that chat's messages for that user, oldest first by the same key.

## NotificationController — events become records

```kotlin
class NotificationController(private val repo: NotificationRepository,
    private val optOutChecker: OptOutChecker = OptOutChecker.AllowAll)
fun notify(intent: NotificationIntent, onDone: (Result<Int>) -> Unit = {})
fun listen(userId, onUpdate: (NotificationUpdate) -> Unit, onError: (Exception) -> Unit): (() -> Unit)
fun readAll(onSuccess: (List<Notification>) -> Unit, onFailure: (Exception) -> Unit)
fun markRead(notificationId: String, onDone: (Result<Unit>) -> Unit = {})
companion: fun sortNewestFirst(list): List<Notification>  // sentAt.seconds desc, null → Long.MAX_VALUE (unstamped first)
```

`notify` steps, in order:
1. A `LotteryDrawn` with blank `lotteryId` → `Result.failure(IllegalArgumentException("lotteryId is required"))`, nothing stored.
2. Compute the audience (`recipientsOf` below); after dropping blanks and folding
   duplicates it is empty → `Result.success(0)`.
3. Ask `optOutChecker.filterAllowed(recipients)`; then build ONE record for EVERY
   recipient — the allowed list only sets the record's `pushAllowed` flag, it never
   suppresses a record. An opt-out (and a failing check, which allows nobody) still
   stores the records; it only forbids pushing.
4. `repo.run(NotificationCommand.Save(records, …))` and
   `onDone(the save result mapped to records.size)` — success carries how many records
   were HANDED to the store.

Audience — exact per intent (`recipientsOf`, then blank-drop + distinct):

| intent | told |
| --- | --- |
| ReservationConfirmed | BOTH `reservation.ownerId` and `reservation.entrantId` (same person twice → told once) |
| ReservationCancelled | only the `ownerId` (the cause was the entrant) |
| ReservationRejected | only the `entrantId` (the cause was the owner) |
| MessageArrived | only `message.recipientId` |
| LotteryDrawn | `winnerIds` + `loserIds` minus any name also in winners (an entrant in both lists is told only they won) |
| LotteryInvited | `lottery.invitedOwnerIds` minus `lottery.ownerId` (the selecting owner is the cause, never told) |
| CampsitePolicyRemoved / CampsiteTagRemoved | only `campsite.ownerId` (the admin actor is the cause) |

Record wording — exact per family (`recordFor`; a family not listed for a type throws
`IllegalArgumentException`):

- Reservation records (`type` ∈ CONFIRMATION/CANCELLATION/REJECTION):
  - CONFIRMATION: title `Reservation confirmed for the campsite`, body `Your reservation has been confirmed.`
  - CANCELLATION: title `Reservation cancelled for the campsite`, body `The reservation has been cancelled.`
  - REJECTION: title `Reservation rejected for the campsite`, body `The reservation request has been rejected.`
  - `senderId` (the cause): the `ownerId` for REJECTION, the `entrantId` otherwise.
  - `reservationId = reservation.lotteryEntryId`; the campsite is NOT named (a
    ReservationModel carries only its id — the wording says "the campsite"); id stays the
    fresh UUID.
- MessageArrived → `type = MESSAGE`: title `New message about <place>` where `<place>` is
  the campsite name or, when blank, `the campsite`; body: first 100 chars of `message.content`
  as preview — blank preview → `You received a new message. Open CampBNB to read it.`,
  otherwise `You received a new message: <preview>`; `senderId = message.senderId`;
  carries `reservationId = message.reservationId` and `campsiteName = <place>`.
- LotteryDrawn per recipient: `type = WIN` iff the recipient is in `winnerIds` else `LOSE`;
  - WIN title `You won the lottery for <place>!`, body `Congratulations! You were selected in the draw for <place>. Action needed: open the app and choose your dates before the booking window closes.`
  - LOSE title `Lottery result for <place>`, body `Unfortunately you were not selected in the draw this time.Thank you for entering` (NOTE: no space between the two parts — that is the literal string).
  - UNATTRIBUTED (`senderId` stays empty — a draw has no single cause), `lotteryId` set,
    `campsiteName = <place>`, and the record's ID is forced deterministic:
    `"<lotteryId>_<userId>_<WIN|LOSE>"` — drawing the same lottery twice over one entrant
    stores one record, not a copy.
- LotteryInvited → `type = INVITE`, recipient = one invited owner: `<places>` is
  `the campsite` for 0 campsites, `one campsite` for 1, else `<count> campsites`;
  title `You were invited to a lottery for <places>`; body verbatim
  `You have been invited to join a campsite lottery. Open CampBNB to see the lottery and its campsites. If you accept, you can add your own campsites to it.`;
  `senderId = lottery.ownerId`, `lotteryId = lottery.id`; ID forced
  `"<lottery.id>_<recipientId>_INVITE"`.
- Policy notices (`type = OWNER_NOTICE` — deliberately NOT a new type; the channel reuses
  the owner-notice category): `<place>` = `campsite.campName` or `the campsite` when blank.
  - Whole listing (removedTag blank): title `Your campsite was removed`;
    body `Your campsite <place> was removed from the app because <policyReasonText(reason)>.`
  - One tag: title `A tag was removed from your campsite`;
    body `The tag <removedTag> was removed from your campsite <place> because <policyReasonText(reason)>.`
  - `senderId = actorUserId` (the admin), `campsiteName = <place>`.

`listen`: runs `Watch` and returns the stop function the repository hands to
`onListening` — the repository MUST invoke `onListening` synchronously inside `run`
(the provider does); if it never does, `listen` returns a no-op `{}`. A fake store that
will be `listen`ed to must call `onListening`.

`readAll` passes `Read` straight through (one read, never followed — the debug log's own
rule); `markRead` passes `MarkRead` through.

## AdminController — policy actions over someone else's listing

```kotlin
class AdminController(private val campsiteRepository: CampsiteRepository,
    private val notificationController: NotificationController)
fun removeCampsite(campsite, reason: PolicyReason, actorUserId, onDone: (Boolean) -> Unit = {})
fun removeTag(campsite, tag: String, reason: PolicyReason, actorUserId, onDone: (Boolean) -> Unit = {})
```

- `removeCampsite`: deletes at `campsite.id`. Store failure → `onDone(false)` and NO
  notice (an action that did not happen is never announced). Success → tell the
  `CampsitePolicyRemoved` event, THEN `onDone(true)`; a notice failure does not undo the
  action (`notify`'s own `onDone` is omitted here).
- `removeTag`: `tag` not in `campsite.tags` → `onDone(false)`, nothing written (a blank
  tag on an untagged listing is the same refusal). Otherwise a rebuilt `Campsite` equal in
  every field except `tags` minus that tag, with `kept.id = campsite.id` explicitly carried
  — same record, one tag fewer; an edit must not open a second listing. Save failure →
  false. Success → the `CampsiteTagRemoved` event carries the ORIGINAL campsite (the notice
  describes what happened to it), then true.

## CampsiteController — browse, details, ratings, registering, QR codes

```kotlin
class CampsiteController(private val model: CampsiteModel, private val repo: CampsiteRepository)
```
Reads/filters operate on `model.allCampsites` — the controller never re-reads the store
except where stated. All model writes go through the setters specified in `viewModels.md`
(and `models.md` for `CampsiteModel`'s state).

- `loadAll()` / `loadRatings()`: whole store into the model; a store failure sets
  `Could not load campsites: <msg>` / `Could not load ratings: <msg>`.
- `search(query, filters: SearchFilters)`: keep a campsite only if ALL of:
  - text: blank `query` (after trim) matches everything; otherwise it is
    `contains(ignoreCase)` in `campName` OR `campLoc` OR ANY tag.
  - location filter: blank matches; else `campLoc.contains(trimmed, ignoreCase)`.
  - guests: `null` matches; else `maxOccupancy >= guests`.
  - dates: empty `startDate` matches everything; empty `endDate` counts as `startDate`
    (a single date); a campsite with either availability bound empty matches NO date
    range; the whole requested range must sit inside `availableStartDate..availableEndDate`
    (TEXT comparison, correct for ISO), and no `blockedDates` entry may fall inside the
    range. Then `setVisibleCampsites(results)`.
- Legacy overload `search(query, guests)` delegates with `SearchFilters(guests = guests)`.
- `loadAndSearch(query, filters = SearchFilters())`: `getAll` → setCampsites → search, then `loadRatings`.
- `selectCampsite(campsiteId)`: the LAST match in `allCampsites` by id (or null →
  deselect). `loadAndSelect(campsiteId)`: getAll + setCampsites + select + `loadRatings`.
- `saveRating(campsiteId, userId, stars)`: blank userId → `Please sign in before rating`;
  `stars < 1 || stars > 5` → `Rating must be between 1 and 5 stars` (guard first, no store
  call either way). Else `Rating(campsiteId, userId, stars)` through `saveRating`; success
  ALSO replaces the same user's old rating inside the model list (drop entries with the
  same rating id, add the new one — the model must not show two) and says
  `Thanks for rating!`; failure says `Could not save your rating`.
- `registerCampsite(campsite, ownerId, onComplete: (Boolean))`: blank owner →
  `Please log in to register a campsite` + false. A NEW Campsite is built copying name/loc/
  desc/amenities/geo/img/maxOccupancy/tags/cancelPolicy/price from the argument but with
  `ownerId` from the argument and WITHOUT the availability fields (the argument's
  `availableStartDate/EndDate/blockedDates` are NOT carried — defaults apply) and with a
  fresh id. Success → `Campsite registered successfully` + true; failure → `Failed to register campsite` + false.
- `updateCampsite(campsite, ownerId, onComplete: (Boolean))`: blank `ownerId` or blank
  `campsite.id` → `Invalid owner or campsite` + false. Then `getAll`: no document with that
  id, or its `ownerId != ownerId` → `You cannot update this campsite` + false; asking with
  a DIFFERENT `campsite.ownerId` than the stored one → `Campsite owner cannot be changed` +
  false. Otherwise an update copy: every field from the argument (including availability)
  but `ownerId` and `id` from the EXISTING record. Store-read failure →
  `Could not verify campsite owner` + false; save result → `Campsite updated successfully`
  true / `Failed to update campsite` false.
- `loadOwnerCampsites(ownerId)`: `getByOwner` → `setOwnedCampsites`; failure prefix
  `Could not load your campsites: `.
- `makeQrCode(campsiteId, stopsAtMillis = 0, onPayload: (String) -> Unit = {})`: blank id →
  `Please pick a campsite to make a code for`; the store must contain that id (only
  registered campsites get codes): missing → `That campsite is not registered yet`; present →
  `QR code made for that campsite` and `onPayload(QRCode(campsiteId, stopsAtMillis).payload())`
  (text from `models.md`'s QRCode contract); failure prefix `Could not make the QR code: `.
- `scanQr(payload, onOpened: (Campsite), onExpired: (QRCode), onUnreadable: (String),
  clock: () -> Long = { System.currentTimeMillis() })` — `clock` is an injected time
  seam: tests pass a fixed moment.
  - undecodable text → `That text is not CampBNB code text` + `onUnreadable(payload)`;
  - decoded but `!code.isValidAt(clock())` → `That code stopped working` + `onExpired(code)`
    (expired-but-real codes reach this arm, NOT the unreadable one; a code carrying no expiry
    at all reaches it too — the reserved 0 makes such a code old, never working (models.md));
  - otherwise `getAll`: no campsite named by `code.campsiteId` → `No campsite matches that
    code` + `onUnreadable(payload)`; found → model setCampsites + select it and `onOpened(named.first())`.
  Exactly one of the three callbacks runs — except when the store read fails, where only
  the status message says `Could not open that campsite: <msg>`.

## LotteryController — entries, waitlists, invitations

```kotlin
class LotteryController(private val model: LotteryModel, private val repo: LotteryRepository,
    private val lotteries: QueryEngine<Lottery>, private val notifications: NotificationController,
    private val clock: () -> Long = System::currentTimeMillis)   // clock is an injected time seam
```

- `checkIfWon(lotteryId, entrantId, campsiteId, onSuccess: (Boolean), onFailure)`:
  true ONLY when the document exists AND `drawn` AND `campsiteId in campsiteIds` AND
  `entrantId in winnerIds`; absent/undrawn answers FALSE, not an error.
- `isBookingOpen(lotteryId, onSuccess: (Boolean), onFailure)`: true only when it exists,
  `drawn`, and `bookingStart <= clock() <= bookingEnd` (BOTH ends inclusive).
- `loadEntrantStatus(campsiteId, userId)`: `getAllLotteries` → FIRST lottery in the list
  covering the campsite with registration open; none → `setEntrantStatus(null, false, 0)`;
  found → `loadWaitlist`. Failure message `Could not load lotteries: <msg>`.
  Registration open = NOT `drawn` AND `statusAt(clock()) == OPEN` (`models.md`'s boundaries).
- `join(lottery, userId)`, all reported through `model.statusMessage`: blank userId →
  `Please sign in before joining a lottery`; registration not open → `Registration is
  closed`; already on the list (`entry.userId == userId` over `getEntries`) → `You are
  already on the waiting list`; full (`waitlistCapacity != null && size >= capacity`;
  null capacity is UNLIMITED) → `The waiting list is full`; else `addEntry(WaitingListEntry(lottery.id, userId, now))` —
  success `You joined the waiting list` and refreshes entrant state via `loadWaitlist`;
  failure `Could not join the waiting list`; the entries read failing → `Could not join: <msg>`.
- `leave(lottery, userId)`: blank → `Please sign in before leaving a lottery`; `removeEntry`;
  success → `You left the waiting list` + refresh; failure → `Could not leave the waiting list`.
- `loadWaitlist` after any membership change: `setEntrantStatus(lottery, onList, entries.size)`
  — the model shows the live count.
- `loadOwnerLotteries(ownerId)`: every lottery filtered by `Lottery.visibleTo(ownerId)` into
  the model (created BY or joined BY — the boundary filters, since one query cannot say it).
- `saveLottery(lottery, onSuccess: (), onFailure)`: save; ONLY on success report
  `NotificationIntent.LotteryInvited(lottery)` then `onSuccess()` — a failed save reports
  nothing. Re-saving an updated lottery re-reports invitations; the records' deterministic
  ids keep one record per invited owner.
- `acceptInvitation(lottery, userId, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {})`
  — callbacks default to no-ops, so model-state-only tests work. Refusals BOTH set the status
  message AND fail the callback (exactly one path runs, including refusals, so a caller's
  next step waits for the store): blank userId → `Please sign in before accepting an
  invitation` + `IllegalArgumentException(same)`; `userId` not in `invitedOwnerIds` →
  `This lottery does not invite you` + `IllegalArgumentException(same)`. Otherwise save
  `lottery.withInvitationAcceptedBy(userId)` (`models.md`: moves invited→joining, parameters
  untouched, id preserved); success → `You joined this lottery` + `onSuccess()`; failure →
  `Could not join the lottery: <msg>` + `onFailure(error)`.
- `saveCampsitesEditedBy(editorId, lottery, selectedCampsiteIds, onSuccess, onFailure)`:
  loads the editor's OWN campsites (read failure → `onFailure`); any selected id not owned
  → `IllegalArgumentException("Only your own campsites can be added to a lottery")`;
  the new list = campsites NOT owned by the editor, kept, plus the selection, deduplicated;
  empty result → `IllegalArgumentException("A lottery must cover at least one campsite")`;
  otherwise save `lottery.withCampsiteIds(that list)` (only the campsite field changes —
  `models.md`).
- `toggleEntrantMembership(userId)`: reads `model.entrantLottery`; no open lottery → does
  NOTHING; on the list → `leave`, else `join`.
- `loadOwnerCampsites(ownerId, onResult: (List<Campsite>) -> Unit)` and
  `loadWaitlistEntries(lotteryId, onResult: (List<WaitingListEntry>) -> Unit)`: boundary
  helpers that SWALLOW read failures — a failure delivers the EMPTY list, not an error.

## ReservationController — the booking flow

```kotlin
class ReservationController(private val lotteryController: LotteryController,
    private val availability: Availability, private val reservations: ReservationRepository,
    private val notifications: NotificationController)
```

Holds `policiesConfirmed: mutableSetOf<String>` in memory — session state, reset on restart.

- `checkEligibility` (private, via `getAvailableDates`): not a winner →
  `IllegalStateException("Entrant did not win this lottery for this campsite")`; booking
  not open → `IllegalStateException("Booking period is not open")`; lookup errors pass
  straight to `onFailure`.
- `getAvailableDates(lotteryEntryId, entrantId, campsiteId, onSuccess: (Set<String>), onFailure)`:
  eligible → `openDates − bookedDates` (`models.md` Availability minus `data.md`
  getBookedDates).
- `confirmPolicies(lotteryEntryId)`: records acceptance; no callbacks, no store.
- `createReservation(lotteryEntryId, entrantId, ownerId, campsiteId, dates: Set<String>,
  onSuccess: (ReservationModel), onFailure: (Exception), guests: Int, refundRecorded: Boolean,
  createdAt: Long = System.currentTimeMillis(), rules: Boolean)` — NOTE the parameter order:
  callbacks sit BEFORE the trailing data parameters; call with named arguments.
  1. `lotteryEntryId` not in `policiesConfirmed` → `IllegalStateException("Policies must be confirmed before reserving")`, nothing runs.
  2. `dates.isEmpty()` → `IllegalArgumentException("Select at least one date")`.
  3. re-check at booking time: available set missing ANY requested date →
  `IllegalStateException("One or more dates are no longer available")`.
  4. build the model: `dates` = the set SORTED (ISO text order), `startDate` first,
  `endDate` last, status ACTIVE; then `reservations.save` (whose own rules are `data.md`'s:
  duplicate entry, per-date claim conflicts, rollback). Save success → report
  `ReservationConfirmed(reservation)` THEN `onSuccess(reservation)`; failure → `onFailure`.
- `cancelReservation(reservationId, requesterId, onSuccess: (), onFailure)`: absent →
  `NoSuchElementException("No reservation $reservationId")`; `entrantId != requesterId` →
  `SecurityException("Only the entrant can cancel this reservation")`; else `markCancelled`
  → on success report `ReservationCancelled` + `onSuccess()`. `rejectReservation` mirrors it
  with `SecurityException("Only the campsite owner can reject this reservation")`,
  `markRejected`, `ReservationRejected`.
- `listReservations(entrantId, onSuccess, onFailure)` = `getForEntrant`; the owner overload
  dispatches `ReservationFilter`: ACTIVE→`getActive`, PAST→`getPast`,
  CANCELLED→`getCancelled` (orders as `data.md` states).
- `generateReceiptCsv(reservation): String` — a second CSV shape, its OWN contract (do not
  confuse with `models.md`'s reservationCsvText): header line
  `reservationId,campsiteId,entrantId,ownerId,startDate,endDate,nights,status,refundRecorded,generatedOn`
  then ONE row, values joined by commas, EVERY value quoted with inner `"` doubled
  (unlike the model CSV's conditional quoting). `nights` = `dates.size`; `status` = enum
  `.name`; `generatedOn` = device date `yyyy-MM-dd` (US locale). Single `\n` between lines,
  no trailing newline.

`unit-test reach here`: members that touch no store (`confirmPolicies`,
`generateReceiptCsv`) are directly testable — but constructing the controller still
requires standing up `ReservationRepository`/`Availability`, which need real engines; see
"Standing them up".

## UserController — device identity, profile, roles (implements OptOutChecker)

```kotlin
class UserController(private val model: UserModel, private val repo: UserRepository) : OptOutChecker
```

- `identify(deviceId, onFound: (User), onNotFound: (), onError: (String))`: blank deviceId →
  `onError("Could not read this device's id")` (a STRING channel); a found user becomes
  `model.currentUser` before `onFound`; absent → `onNotFound()`; store failure →
  `onError("Could not reach the server: <msg>")`.
- `register(deviceId, name, email, phone = "", onDone: (Boolean))`: `validateProfile` first —
  blank trimmed name → `Please enter a name`; invalid email (below) →
  `Please enter a valid email address`; either sets that status message and `onDone(false)`.
  Else `User(deviceId, trimmed name/email/phone)` — no roles yet.
- `chooseRole(role, onDone: (Boolean))`: no signed-in user → `Please start again` + false;
  ALREADY holding the role → true with no write; `Role.ADMIN` not held →
  `This device is not registered as an admin` + false — chooseRole can NEVER grant ADMIN,
  only re-confirm it; otherwise the role is appended and saved (`Role added` /
  `Could not save your role`).
- `updateProfile(name, email, phone, onDone)` / `setNotificationsEnabled(enabled, onDone)`:
  same guard (`Please start again` when no user), same validation for updateProfile; the
  edits TRIM name/email/phone and save. Success strings: `Profile saved`,
  `Notification setting saved` (failures: `Could not save your profile`,
  `Could not save the setting`).
- `deleteProfile(onDone)`: no user → false, NO status message; delete at `user.id`:
  success → model's currentUser becomes null, `Profile deleted`, true; failure →
  `Could not delete your profile`, false.
- `loadAllUsers()`: whole store into the model; failure `Could not load users: <msg>`.
- `filterAllowed(userIds, onResult)` (the OptOutChecker member): the store's `getOptedOut`
  users are blocked; anything NOT opted out is allowed; a read failure allows NOBODY —
  a failure can never push to someone who opted out. Note the pairs of `AllowAll` and the
  empty list are the two ends a test will probe.
- `validateProfile(name, email): String?` is public (see above) and `companion isValidEmail`:
  trimmed; first `@` at index ≥ 1; a `.` searched AFTER the `@` must satisfy
  `at+1 < dot < len−1` — so `a@b.c` passes, `a@.com` and `a@b.` fail.
- every successful save (`saveUser`, private) sets the saved user as `model.currentUser`
  and one of: `Profile created`, `Role added`, `Profile saved`, `Notification setting saved`.

## MessageController — chats and their notifications

```kotlin
class MessageController(private val repo: MessageRepository,
    private val reservationRepository: ReservationRepository,
    private val notifier: NewMessageNotifier? = null,                 // null = notifications off (tests)
    private val campsiteNameLookup: (String, onName: (String) -> Unit) -> Unit =
        { _, onName -> onName("") })                                // default hands a blank name
```

- `sendMessage(reservationId, senderId, content, onDone: (Result<Message)))`: content is
  TRIMMED first; `validate` failure → `Result.failure(IllegalArgumentException(<message>))`
  with exact texts: blank reservation → `Reservation ID is required`; blank sender →
  `Sender ID is required`; empty trimmed text → `Message cannot be empty`; over
  `MAX_LENGTH = 1000` → `Message is too long (max 1000 characters)`. Then look the
  reservation UP by its document key (= lottery entry id): absent →
  `Result.failure(NoSuchElementException("Reservation not found"))`; the recipient is the
  OTHER party (owner→entrant / entrant→owner), a sender who is neither →
  `IllegalArgumentException("Sender is not part of this reservation")`. The built
  `Message` carries `reservationId = reservation.lotteryEntryId`, both ids, `participantIds
  = [ownerId, entrantId]` in THAT order, the trimmed content, `status = UNREAD`.
- `reply(conversation: Conversation, ...)` is the same without the lookup (the chat already
  knows both people — see `Conversation.hasBothParticipants`).
- Saving: `repo.send`; `onDone` answers the store result mapped to the message; the
  NOTIFIER runs ONLY after a successful save, and its own failure never changes the message
  result. Name lookup → `notifier.messageSent(message, name)` (an adapter instance
  reports `MessageArrived`, and `MessageArrived` tells only `message.recipientId` —
  `controller.md#notificationcontroller`).
- `listenForUser` / `listenForConversation` return the repository's stop function
  (conversation = the reservation-scoped listener); `markMessagesRead` delegates.
- Companion sorts on `sentAt.seconds` with NULL → `0L`: `sortOldestFirst` ASC,
  `sortNewestFirst` DESC (unstamped sort LAST newest-first — NOTE this differs from
  `NotificationController.sortNewestFirst`, which sorts unstamped FIRST; they are different
  members with different null rules).

## Standing controllers up in tests

The reference file is `AdminPolicyTest.kt` — the verified idiom, in `class AdminPolicyTest`
in test package `com.example.campbnb`:

```kotlin
@import org.junit.Assert / Before / Test

class FakeListingStore : CampsiteRepository() { …override members… }   // open class: inherit with ()
class NoticeStore : NotificationRepository { …override run… }          // interface: no ()

private lateinit var adminController: AdminController
@Before fun setup() {
    adminController = AdminController(listings, NotificationController(notices))
}

@Test fun removedListing_isToldToItsCreatorWithTheReason() {
    var done = false
    adminController.removeCampsite(campsite, reason, "admin9") { ok -> done = ok }
    Assert.assertTrue(done)                                    // expected value FIRST, actual second
    Assert.assertEquals("owner1", notice.recipientId)
}
```

Rules:
- A plain-class controller/model/collaborator is always the REAL one, constructed over faked
  seams. `NotificationController(NoticeStore())` is how a test drives record-building without
  Firestore.
- Repository fakes record their arguments in plain mutable fields and assert through them
  (`deletedIds`, `savedListings`, `saved`).
- Callback continuation values flow back into local `var done` style flags and are asserted.
- Time-dependent members (`LotteryController` via its constructor `clock`, `CampsiteController.scanQr`
  via its `clock` parameter) take an injected fixed moment — never read real time in a test.
- Observed state (models) is asserted directly off the constructed model, and a
  `ViewObserver` in-file fake can witness the notify contract (`models.md`).
- Members that cannot avoid a real engine (`ReservationRepository`, `Availability`) stay
  outside the unit surface; assert their rules at the seam level (`data.md`/`models.md`)
  and controller-level only through what avoids them.
