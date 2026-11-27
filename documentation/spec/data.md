# data — stores and storage boundaries

Spec file for the `com.example.campbnb.data` package
(`CampBNB/app/src/main/java/com/example/campbnb/data/`).

This is a contract, not prose background: every section states the exact API surface and
the behavior that must hold of it. Types referenced here (`Campsite`, `Rating`, `Lottery`,
`WaitingListEntry`, `Message`, `Notification`, `NotificationSettings`,
`NotificationPreferenceModel`, `ReservationModel`, `ReservationStatus`, `BookedDate`,
`DateUtils`, the engines behind `Database.<Collection>`, and the top-level function
`sentNotificationsFor`) are specified in the sibling spec files `models.md` and
`database.md`.

Written against the repository state at branch `wyatt-us-01.05.02` (tip `c985351` plus the
night-run working tree), 2026-11-22.

## Shared contract — continuations, not return values

Every member that does I/O-shaped work answers through callback parameters; nothing comes
back up the call stack. The callback types shown in the signatures below are exact: they
are the shapes a caller (or a test) must be able to supply, and the only points where
behavior is observable.

- `Result<X>` is the answer envelope: `Result.success(v)` / `Result.failure(e)`.
  Several store seams use `Result<Exception?>`: success carries `null`, failure carries
  the exception.
- Completion callbacks (`onDone`, `onComplete`) are called exactly once per call, with
  either the success or the failure answer — never both, never neither.
- Listener callbacks (`onUpdate`) may be called once now and again after every change.
- An `open class` member may be replaced by a test subclass; a plain `class` member cannot
  be replaced at all. Interface implementers implement every member. This is the house's
  fake mechanism; see "Standing them up".

## CampsiteRepository.kt — campsites and ratings

`open class CampsiteRepository` — no constructor parameters; every member `open`.

```kotlin
open fun SaveCampsite(campsite: Campsite, onComplete: (Result<Exception?>) -> Unit)
open fun deleteCampsite(campsiteId: String, onComplete: (Result<Exception?>) -> Unit)
open fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit)
open fun getAllRatings(onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit)
open fun saveRating(rating: Rating, onComplete: (Result<Exception?>) -> Unit)
open fun getByOwner(ownerId: String, onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit)
```

Contracts:
- `SaveCampsite` stores under `campsite.id`; deleting uses the same id.
- `getAll` / `getAllRatings`: the whole collection, no filter, no stated order.
- `getByOwner`: exactly the campsites whose `ownerId` field equals the argument.
- `saveRating` re-save of an id replaces the previous rating (same id, overwrite) — a
  re-rate does not append.

## LotteryRepository.kt — lotteries and waiting lists

`open class LotteryRepository` — no constructor parameters.

```kotlin
fun loadCampsitesByOwner(onSuccess: (List<Campsite>) -> Unit,
                         onFailure: (Exception) -> Unit,
                         ownerId: String)                       // NOT open; callbacks come FIRST
fun save(lottery: Lottery, onSuccess: () -> Unit, onFailure: (Exception) -> Unit)  // NOT open
open fun getAllLotteries(onSuccess: (List<Lottery>) -> Unit, onFailure: (Exception) -> Unit)
open fun getEntries(lotteryId: String, onSuccess: (List<WaitingListEntry>) -> Unit, onFailure: (Exception) -> Unit)
open fun getEntriesByUser(userId: String, onSuccess: (List<WaitingListEntry>) -> Unit, onFailure: (Exception) -> Unit)
open fun addEntry(entry: WaitingListEntry, onComplete: (Result<Exception?>) -> Unit)
open fun updateEntry(entry: WaitingListEntry, onComplete: (Result<Exception?>) -> Unit)
open fun removeEntry(lotteryId: String, userId: String, onComplete: (Result<Exception?>) -> Unit)
open fun deleteLottery(lotteryId: String, onComplete: (Result<Exception?>) -> Unit)
```

Contracts:
- `loadCampsitesByOwner` filters by `Campsite.ownerId == ownerId`. Watch the parameter
  order: callbacks first, `ownerId` last — unlike every other query member here.
- `save` assumes the lottery is already validated. Its `onSuccess` takes no value. A write
  failure with no exception payload reports `Exception("Unknown error")`.
- `getEntries` filters by `WaitingListEntry.lotteryId == lotteryId`.
- `getEntriesByUser` filters by `WaitingListEntry.userId == userId` — every list an entrant is
  on, across lotteries. It is what an admin's cascade reads before deleting a profile, and what
  the bookings view and `checkEntrantAccepted` read to find one entrant's entry in a named
  lottery.
- `addEntry` and `updateEntry` are the SAME write (`writeEngine().save(entry, …)`) under two
  names — main's US 02.08.02 added the second one so a draw can rewrite an entrant's `status`
  without implying a new place on the list. Because the id is derived from `lotteryId`+`userId`,
  both names address the SAME document: updating an entry never creates a second one, and it
  cannot change which list an entrant is on.
- `removeEntry` deletes the entry whose id is `WaitingListEntry.makeId(lotteryId, userId)`
  (see `models.md` for the id format) — removing an entrant never deletes another user's
  entry.
- `deleteLottery` deletes the ONE lottery record whose id is `lotteryId` (the records'
  deterministic ids, `database.md`) — it never touches that lottery's waiting-list entries,
  which stay addressable by their own ids. controller.md owns when a lottery may be deleted.

## MessageRepository.kt — the conversation seam (interface)

```kotlin
interface MessageRepository {
    fun send(message: Message, onDone: (Result<Unit>) -> Unit)
    fun listenForUser(userId: String, onUpdate: (List<Message>) -> Unit, onError: (Exception) -> Unit): () -> Unit
    fun listenForReservation(reservationId: String, onUpdate: (List<Message>) -> Unit, onError: (Exception) -> Unit): () -> Unit
    fun markRead(messageIds: List<String>, onDone: (Result<Unit>) -> Unit)
}
```

Contracts the seam itself states (true of every implementation):
- `send` assigns a fresh unique document id into `message.id` before storing, so after a
  successful `send` the caller can read the assigned id off the object it passed in.
- `listenForUser` / `listenForReservation` return a function that stops the listener; the
  caller must call it when its screen goes away.
- An empty `markRead` list is a success: `onDone(Result.success(Unit))` with no write.

## FirestoreMessageRepository.kt — the real provider

`class FirestoreMessageRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : MessageRepository`.
Stores to the messages collection named by `Database.Messages.COLLECTION_NAME`.

Provider-specific contracts (only observable against a live Firestore; unit tests implement
the interface instead):
- `listenForUser` filters documents whose `participantIds` array contains the userId and
  delivers messages sorted by `sentAt` DESCENDING (newest first; a null `sentAt` sorts as
  epoch 0).
- `listenForReservation` filters by field `reservationId` equals and delivers sorted by
  `sentAt` ASCENDING (oldest first).
- `markRead` drops blank ids, deduplicates, and writes in batches of at most 500, setting
  field `status` to `MessageStatus.READ.name`. One answer only: the first failure wins and
  suppresses later success reports; success is reported when every batch has completed.

## NotificationRepository.kt — the one-entry-point store seam

This area's seam is deliberately not a method per operation. A request is data:

```kotlin
data class NotificationUpdate(val all: List<Notification>, val added: List<Notification>)

sealed interface NotificationCommand {
    data class Save(val records: List<Notification>, val onDone: (Result<Unit>) -> Unit) : NotificationCommand
    data class Read(val onSuccess: (List<Notification>) -> Unit, val onFailure: (Exception) -> Unit) : NotificationCommand
    data class Watch(val userId: String,
                     val onUpdate: (NotificationUpdate) -> Unit,
                     val onError: (Exception) -> Unit,
                     val onListening: (stop: () -> Unit) -> Unit) : NotificationCommand
    data class MarkRead(val notificationId: String, val onDone: (Result<Unit>) -> Unit) : NotificationCommand
    data class Delete(val notificationId: String, val onDone: (Result<Unit>) -> Unit) : NotificationCommand
}

interface NotificationRepository {
    fun run(command: NotificationCommand)
}
```

Contracts:
- A store answers ONLY through `run`. The request names its own continuations, so
  `run` returns nothing and results reach the caller through the request.
- `Save` stores all records in one request; `onDone` exactly once.
- `Read` is one read of the WHOLE store — every saved notification, whichever user
  receives it. It is asked once per explicit tap, never followed.
- `Watch` follows one user live; `onListening` is called exactly once with a function that
  stops following.
- `Delete` removes ONE saved entry by its id — the entry stops existing, it is not marked as
  read. A blank `notificationId` answers `Result.failure(IllegalArgumentException("notificationId is required"))`
  with no write; a missing entry is left to the store (the real provider's document delete
  succeeds on a missing id, the fake answers a failure so a test can see the miss).
- `NotificationUpdate.all` = every notification that user currently has;
  `NotificationUpdate.added` = only the ones new since the previous update.

## FirestoreNotificationRepository.kt — the real provider

`class FirestoreNotificationRepository(...) : NotificationRepository`, collection
`Database.Notifications.COLLECTION_NAME`. `run` dispatches each request kind.

Provider contracts:
- `Save` with an empty list answers success without writing. Otherwise it writes in
  batches of at most 500, each record stored AT ITS OWN `notification.id` (records must
  come already-id'd), and uses the same one-answer discipline as batching everywhere
  (first failure wins).
- `Read` executes once through the read engine — nothing is subscribed for it.
- `Watch` filters `recipientId == userId`; `onUpdate` receives all snapshot records as
  `all` and only `documentChanges` of type ADDED as `added`.
- `MarkRead` writes field `status = NotificationStatus.READ.name` and
  `readAt = server timestamp`.
- `Delete` removes the document at `notificationId` (`collection.document(id).delete()`), so
  the entry stops appearing in every listing — the sent half, the received half and the log
  alike. A blank id answers the `IllegalArgumentException` above without touching the store.

## SentNotificationsRepository.kt — a permission-scoped VIEW over a store

```kotlin
class SentNotificationsRepository(private val source: NotificationRepository, private val senderId: String) : NotificationRepository
```

Contracts:
- It is NOT a store; it wraps another `NotificationRepository` and changes exactly one
  request kind: a `Read` asked of this view answers, through the same continuations, with
  only the records `sentNotificationsFor(records, senderId)` keeps (see `models.md` for
  what that filter selects).
- `Save`, `Watch`, `MarkRead` and any other request run against the source UNCHANGED —
  storing, following and marking belong to whoever can reach the source.
- The test standup is two layers: a fake store (implements `run` over in-memory records)
  wrapped by `SentNotificationsRepository(fakeStore, "user-…")`, then assert the narrowing.

## ReservationRepository.kt — reservations and booked dates

Not a seam: a plain `class` built from engines, so its behavior is tested through its
engine arguments' answers.

```kotlin
class ReservationRepository(
    private val engine: QueryEngine<ReservationModel>,
    private val reservationWriter: WriteEngine<ReservationModel>,
    private val bookedDateWriter: WriteEngine<BookedDate>
)
```

All members return nothing; each takes `onSuccess`/`onFailure` continuations. Dates are
ISO strings `yyyy-MM-dd`, which sort as text; every stated order below is the contract the
result must satisfy.

Queries (filter predicates are exact):

```kotlin
fun getActive(campsiteId, onSuccess: (List<ReservationModel>) -> Unit, onFailure: (Exception) -> Unit,
              today: String = DateUtils.today())
```
- filter: `campsiteId` equals; `status` equals `ReservationStatus.ACTIVE.name`;
  `endDate >= today`. Order: `endDate` ascending — soonest ending first.
- `today` defaults to the device date; tests pass it explicitly.

```kotlin
fun getPast(campsiteId, onSuccess, onFailure, today: String = DateUtils.today())
```
- Same filters but `endDate < today`; ordered `endDate` ascending (oldest first).

```kotlin
fun getPending(campsiteId, onSuccess, onFailure)
```
- filter: `campsiteId` equals; `status == ReservationStatus.PENDING.name`. Order: `startDate`
  ascending. (main's US 02.08.02 addition: a reservation the entrant has created but whose dates
  have not started yet is its own state, which is what the bookings screen's PENDING half and
  `activateDueReservations` need.)

```kotlin
fun activateDueReservations(today: String = DateUtils.today(), onComplete: (Result<Int>) -> Unit)
```
- The read is ONE chain, stated exactly: `engine.query()` → ONE clause
  `whereEquals(ReservationModel::status, ReservationStatus.PENDING.name)` → `execute(onSuccess, onFailure)`.
  This is the raw engine chain, NOT a `FirestoreQueryViewModel` run — `fetchAll()` and `executeQuery(block)`
  (database.md's ViewModel forms) are not involved and no `queryState` is observable here.
  There is NO date clause and there cannot be one: `whereLessThanOrEqual` is not in the engine
  (the clause list in database.md is the whole set — `whereEquals`/`whereIn`/`whereGreaterThanOrEqual`/
  `whereGreaterThan`/`whereLessThan`/`orderBy`/`limitResults`), so the query selects by STATUS ONLY
  and the due-check never runs in the query. The clause's value is the STRING
  `ReservationStatus.PENDING.name` — the document field holds the status NAME as a string (`save`
  and every `update` write `status.name`), so a mock that filters its own fixtures against the
  clause must compare against that string, not against the enum.
- `onSuccess` receives the `List<ReservationModel>` the chain matched; the due-check is a client-side
  predicate over THOSE objects, verbatim: `it.startDate.isNotBlank() && it.startDate <= today`.
  `today` is a `yyyy-MM-dd` STRING (`models.DateUtils.today()` is `SimpleFormatter("yyyy-MM-dd", Locale.US)`
  over the current `Date()`), so the comparison is LEXICOGRAPHIC — it orders correctly only for that
  spelling, and a record whose `startDate` is blank or spelled otherwise is NOT due and never moves.
- The survivors are handed to the private walker `updateDueReservationStatuses(reservations, index,
  updatedCount, onComplete)`, which re-checks EACH record again and asks `DateUtils.today()` AGAIN
  per record — the `today` PARAMETER reaches only the filter above, never the walker, so a mock that
  pins the clock by passing that parameter still gets the walker's own clock. The walker's transition
  table: `PENDING` → `ACTIVE` when `startDate <= today`, else no transition; `ACTIVE` → `PAST` when
  `endDate < today`, else none; any other status → none. This entry point hands it only PENDING
  records (the clause and the filter both keep them PENDING), so its `ACTIVE → PAST` row is
  unreachable here — `getPast` reads `status == ACTIVE` with `endDate < today` instead, and nothing
  in this app ever writes `PAST` by hand.
- Each record with a due transition is written ONE AT A TIME, in the order the chain answered, through
  `reservationWriter.update(id = reservation.id, fieldMap = mapOf("status" to newStatus.name))` — ONLY
  the `status` field changes — and the walk continues only while that write succeeds.
- `onComplete` receives `Result.success(count)` where count is the number of records whose write
  SUCCEEDED (a record with no due transition is skipped and never counted), or `Result.failure(…)`
  from the FIRST failing write — the write's own exception, or `Exception("Could not update reservation
  status")` when the envelope carries none — or from a failed read, the read's exception.
- Observable for a mock, and the reason a scripted chain can move nothing: records whose `startDate`
  is blank or not `yyyy-MM-dd` are NOT due, so the answer is `Result.success(0)` and `reservationWriter.update`
  is never called at all. A chain that answers records whose `status` is not `PENDING` moves none of
  them either — that is the clause, not the filter.

```kotlin
fun getCancelled(campsiteId, onSuccess, onFailure)
```
- filter: `campsiteId` equals; `status in {CANCELLED.name, REJECTED.name}`. No order stated.

```kotlin
fun getBookedDates(campsiteId, onSuccess: (Set<String>) -> Unit, onFailure)
```
- filter: `campsiteId` equals; `status in {PENDING.name, ACTIVE.name}` — a stay that has been
  booked but has not started yet still holds its dates, so both states block them (main's US 02.08.02;
  the branch blocked only ACTIVE). `onSuccess` receives the SET of
  every date across those reservations' `dates` fields.

```kotlin
fun getForOwner(ownerId, onSuccess, onFailure)   // filter ownerId equals; order startDate asc
fun getForEntrant(entrantId, onSuccess, onFailure) // filter entrantId equals; order startDate asc
fun getByLotteryEntry(lotteryEntryId, onSuccess: (ReservationModel?) -> Unit, onFailure) // null when absent
fun CheckIfReserved(lotteryEntryId, onSuccess: (Boolean) -> Unit, onFailure)  // existence only
```

Commands — these carry the storage rules:

```kotlin
fun save(reservation: ReservationModel, onSuccess: () -> Unit, onFailure: (Exception) -> Unit)
```
1. `reservation.id` is set to `reservation.lotteryEntryId` — one reservation per lottery
   entry, mutated on the object itself. `save` then mutates `reservation.status` to `PENDING`
   as well (main's US 02.08.02): whatever state the caller built is overwritten, so the store
   cannot be handed a reservation that claims to be ACTIVE before its dates have started.
2. If `CheckIfReserved(lotteryEntryId)` is true:
   `onFailure(IllegalStateException("Entry already has a reservation"))`, nothing written.
3. Otherwise dates are claimed ONE AT A TIME in order. Each claim writes a `BookedDate`
   document whose id is `bookedKey(campsiteId, date)` — exactly `"<campsiteId>_<yyyy-MM-dd>"`.
   Storage rules reject a second claim of the same key, so a taken date surfaces as a
   failed write.
4. On any claim failure: the dates THIS call already claimed (`dates.take(index)`) are
   released, then
   `onFailure(IllegalStateException("One or more dates are already booked"))`;
   no reservation is written.
5. After all claims succeed, the reservation is saved. If that save fails, ALL the
   claimed dates are released and `onFailure` gets the save's exception.
6. Success order: all dates booked AND the reservation saved, then `onSuccess()`.

```kotlin
fun markCancelled(reservationId, onSuccess: () -> Unit, onFailure)   // == close(.., CANCELLED)
fun markRejected(reservationId, onSuccess, onFailure)                // == close(.., REJECTED)
```
`close` steps, in order:
1. Read the reservation by id. Absent → `NoSuchElementException("No reservation $reservationId")`.
2. `status` is NEITHER `PENDING` nor `ACTIVE` → `IllegalStateException("Reservation is already ${status}")`
   (the enum name is interpolated). A stay that has not started yet can still be cancelled or
   rejected — main's US 02.08.02 widened this from `status != ACTIVE`, because PENDING is now a
   state a real reservation can be in.
3. Write fields `{"status": newStatus.name, "refundRecorded": true}` FIRST. If that write
   fails, `onFailure` answers and the status change has still been attempted.
4. Only after the status write succeeds are the reservation's dates released (one at a
   time, failures ignored, always carrying on), then `onSuccess()`.

Internal rules worth knowing for fake engines: date release never blocks — a failed delete
is skipped, and an empty date list calls its continuation immediately.

## CampsiteRemovalRepository.kt — the clean-up boundary after an admin deletes a campsite

`open class CampsiteRemovalRepository` — no constructor parameters; all members `open`. It
wraps the existing `ReservationRepository` and the lottery engines rather than changing them,
so an admin's deletion (US 03.01.01) has one seam to fake. The wrapped `ReservationRepository`
is built on first use, so constructing this class never touches Firebase.

```kotlin
open fun getActiveReservations(campsiteId, onSuccess: (List<ReservationModel>) -> Unit,
    onFailure: (Exception) -> Unit)                    // delegates to reservations.getActive
open fun getActiveReservationsOfEntrant(entrantId, onSuccess: (List<ReservationModel>) -> Unit,
    onFailure: (Exception) -> Unit)                    // reservations.getForEntrant, then kept
                                                      // only if status == ACTIVE AND endDate >= today
open fun cancelReservation(reservationId, onComplete: (Result<Unit>) -> Unit)  // markCancelled, mapped
open fun deleteLottery(lotteryId, onComplete: (Result<Exception?>) -> Unit)
open fun saveLottery(lottery, onComplete: (Result<Exception?>) -> Unit)       // id already set: replaces
```

`cancelReservation` maps the wrapped store's continuations into one `Result<Unit>`; the
reservation's id IS the lottery entry id, which is what makes cancelling it free the dates.

## UserRepository.kt — users

`open class UserRepository` — no constructor parameters; all members `open`.

```kotlin
open fun findByDeviceId(deviceId: String, onSuccess: (User?) -> Unit, onFailure: (Exception) -> Unit)
open fun save(user: User, onComplete: (Result<Exception?>) -> Unit)
open fun delete(userId: String, onComplete: (Result<Exception?>) -> Unit)
open fun getAll(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit)
open fun getOptedOut(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit)
open fun listenForRemoval(userId, onRemoved: () -> Unit): (() -> Unit)
```

- `findByDeviceId` reads by document id; a missing id answers `null`, not an error.
- `save` creates or replaces; `delete` removes.
- `getOptedOut` answers users whose `notificationsEnabled` field is false.
- `listenForRemoval` follows ONE document and answers `onRemoved` only on the transition
  from existing to gone — the first snapshot that shows the document sets a flag, and only a
  later snapshot without it fires. A blank `userId` never watches and hands back a no-op `{}`.
  The return value is the stop function (`registration.remove()`); a caller must call it when
  the screen that started watching goes away.

## FileDownloadWriter.kt — the shared download sink

```kotlin
fun downloadSafeName(value: String): String   // top-level; import as com.example.campbnb.data.downloadSafeName
```
- Letters and digits are kept; `. _ -` are kept; every other character becomes `-`.

```kotlin
interface FileDownloadWriter {
    fun downloadFile(fileName: String, content: ByteArray, onDone: (Result<Unit>) -> Unit)
}
```
- The seam never renames: feature models call `downloadSafeName` BEFORE handing the name
  here (see `models.md`), so a provider receives an already-safe name and must use it as-is.
- Content is always bytes; a CSV's bytes are its text, a QR file's bytes are a real PNG.

Providers:
- `class NoFileDownloadWriter` — answers success, writes nothing. Screens keep working.
- `class InMemoryFileDownloadWriter` — public `val writtenFiles = mutableMapOf<String, ByteArray>`;
  stores by exact name (same name overwrites). Success. Tests inspect `writtenFiles`.
- `class PublicDownloadsFileDownloadWriter` — the real Android provider: writes the bytes to
  the platform public Downloads directory; any platform failure answers
  `Result.failure(the exception)`. Not exercisable from JVM unit tests (android runtime only).
  The write is only allowed because the manifest carries the grant: `WRITE_EXTERNAL_STORAGE`
  capped at `maxSdkVersion="28"` (legacy targets) and `android:requestRawExternalStorageAccess="true"`
  on the `android:application` element — an element form AAPT rejects; the attribute form is the
  world's vocabulary. The capped grant is never given on modern targets, and raw access is a
  26–28 feature, so on API 29+ the shared path REFUSES the write (the media-library grant
  would cover it, but this platform carries no media-library classes to name it). The provider
  then falls back to the application's own directory inside the SAME public tree —
  `<external storage root>/Android/data/com.example.campbnb`, created with `java.io.File.mkdirs()`
  — which needs no grant and still shows in the user's file manager; only a second refusal
  answers `Result.failure`.

## NotificationPreferencesService.kt — per-user notification settings

```kotlin
interface NotificationPreferencesService {
    fun notificationSettingsFor(userId: String): List<NotificationSettings>   // SYNCHRONOUS snapshot
    fun loadNotificationSettings(userId: String, onSettings: (List<NotificationSettings>) -> Unit, onFailure: (Exception) -> Unit)
    fun setNotificationSettings(userId: String, enabled: List<NotificationSettings>, onDone: (Result<Unit>) -> Unit)
}
```

The shared settings rules (what every implementation agrees on):
- Default for anything unknown: `[NotificationSettings.PUSH]`.
- A settings list maps to a stored model by membership: `allowPushNotifications` is true
  iff `PUSH` is in the list; `allowCsvReservationDownload` is true iff `CSV` is in it.
  A model maps back the same way; a missing model is the default.

Implementations, with their exact differences:
- `NoNotificationPreferencesService` — every read answers the default; every set answers
  success and stores nothing.
- `InMemoryNotificationPreferencesService` — reads answer stored-or-default;
  `loadNotificationSettings` never fails; `setNotificationSettings` with a blank userId
  answers `Result.failure(Exception("Cannot save settings without a user ID"))` and stores
  nothing; a non-blank set stores and answers success.
- `FirestoreNotificationPreferencesService(readEngine: QueryEngine<NotificationPreferenceModel>,
  writeEngine: WriteEngine<NotificationPreferenceModel>)` — keeps a client cache:
  - `notificationSettingsFor` answers cached-or-default WITHOUT any read (the sync getter is
    a cache read; never assert it queried storage).
  - `loadNotificationSettings` with a blank userId answers the default immediately, no read.
    Otherwise it reads by id; a missing model answers the default; answers are cached.
  - `setNotificationSettings` blank userId → same failure message as InMemory. Otherwise the
    cache updates, then a write of the mapped model runs: write failure answers
    `Result.failure(Exception("Could not save notification preferences"))`.

## AppSettingsService.kt — the app settings kept between app lifetimes

```kotlin
interface AppSettingsService {
    fun appSettingsFor(userId: String): AppSettingsModel                        // SYNCHRONOUS snapshot
    fun loadAppSettings(userId: String, onSettings: (AppSettingsModel) -> Unit, onFailure: (Exception) -> Unit)
    fun setAppViewChoice(userId: String, appViewChoice: AppView, onDone: (Result<Unit>) -> Unit)
    fun setEntrantSettings(userId: String, bookingsOrder: String, inboxAlerts: String, messagesFilter: String, onDone: (Result<Unit>) -> Unit)
    fun setOwnerSettings(userId: String, listingVisibility: String, lotteryAccepting: Boolean, availabilityNotice: String, onDone: (Result<Unit>) -> Unit)
}
```

The shared settings rules (what every implementation agrees on):
- The record type is `AppSettingsModel` (models.md); an EMPTY string field means "not
  chosen" and the seeding methods leave that setting's controller default untouched
  (viewModels.md).
- Each `set…` saves ONLY its own group: a later group's save never erases an earlier
  group's fields (group merge — assert it; it is the point of this surface).
- A blank (whitespace counts as blank) userId answers
  `Result.failure(Exception("Cannot save app settings without a user ID"))` and stores
  nothing.

Implementations, with their exact differences:
- `NoAppSettingsService` — every read answers a default `AppSettingsModel()`; every set
  answers success and stores nothing.
- `InMemoryAppSettingsService` — reads answer stored-or-default; `loadAppSettings` never
  fails (blank answers default immediately; a stored record is answered as stored); the
  `set…` methods merge into the stored record and answer success.
- `FirestoreAppSettingsService(readEngine: QueryEngine<AppSettingsModel>,
  writeEngine: WriteEngine<AppSettingsModel>)` — keeps a client cache like its
  notification-prefs sibling: reads answer cached-or-default WITHOUT querying; `loadAppSettings`
  reads by id and caches (a missing record answers a default model built with that userId);
  each `set…` merges, caches, then writes the merged record — write failure answers
  `Result.failure(Exception("Could not save app settings"))`.

## ImageRepository.kt — the picture-to-text boundary (US 02.01.02, US 01.08.02)

`open class ImageRepository(private val resolver: ContentResolver)` — turns a picture the
user picked (a `Uri` from the phone's picker) into the text that is stored INSIDE the
record that owns it (`Campsite.campImg` or `User.profileImg`); nothing else. Pictures are
not stored as separate files: Firestore allows about 1 MiB per document, so a stored
picture has to stay small.

- `open fun encode(uri: Uri, maxEdge: Int): String?` — the one member. Shrinks the
  picture so its longest side is at most `maxEdge` pixels, turns a sideways photo
  upright from its EXIF tag, draws transparency on white (a JPEG has none), and
  re-encodes as JPEG at stepwise lower quality until the bytes are at most
  `MAX_BYTES`. Answers the Base64 text (no wrap) on success, and `null` when the
  `Uri` is not a readable image or no quality made it small enough.
- companion constants: `CAMPSITE_EDGE = 800` (the Explore cards show about 600),
  `PROFILE_EDGE = 256` (a profile image is only ever shown small), `MAX_BYTES = 400_000`
  (as Base64 text that keeps a whole document under Firestore's 1 MiB limit).

JVM-reachable: the class is `open`, so a test subclasses it and overrides `encode` with a
fake that answers a chosen text (or null) — the real member reads device picture
decoders and is not run in a JVM test. Moving pictures to Firebase Storage later changes
only this class: `encode` would upload the file and return its link, and the same text
fields would hold the link.

## Standing them up in tests

Test files sit in package `com.example.campbnb` (the test root; see
`AdminPolicyTest.kt` as the worked reference). Rules the house follows:
- Every fake lives IN the test file that uses it; there is no shared fake library. Only
  production imports appear at the top.
- Assert through `import org.junit.Assert` (`Assert.assertEquals`, `assertTrue`,
  `assertFalse`); cases are `@Test` functions with snake_sentence names; shared setup is
  `@Before` with `private lateinit` fields.
- Functions are invoked through `.invoke`; callbacks are passed as trailing lambdas.

The two fake shapes, exactly:

```kotlin
class FakeListingStore : CampsiteRepository() {          // open class: inherit with ()
    val deletedIds = mutableSetOf<String>()
    override fun deleteCampsite(                        // override replaces an open member
        campsiteId: String,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        deletedIds.add(campsiteId)
        onComplete(Result.success(null))
    }
}

class NoticeStore : NotificationRepository {             // interface: no ()
    val saved = mutableMapOf<String, Notification>()
    override fun run(command: NotificationCommand) {
        when (command) {
            is NotificationCommand.Save -> {
                command.records.forEach { saved[it.id] = it }
                command.onDone.invoke(Result.success(Unit))
            }
            is NotificationCommand.Read -> command.onSuccess.invoke(saved.values.toList())
            else -> {}
        }
    }
}
```

A plain `class` (like `ReservationRepository`) is never faked — it is stood up from the
engines it takes, and those engines' answers drive the observable behavior.
