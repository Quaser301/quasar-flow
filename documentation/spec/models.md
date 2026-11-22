# models — the domain vocabulary and the entities it names

Spec file for the `com.example.campbnb.models` package
(`CampBNB/app/src/main/java/com/example/campbnb/models/`).

Every other area speaks of these types; this file is the shared vocabulary. Constructors
here are always called with named arguments in tests (as every test file does).
The storage rules that use these ids are specified in `data.md`; who turns events on these
entities into notices is `controller.md`.

Written against branch `wyatt-us-01.05.02` (tip `c985351` plus working tree), 2026-11-22.

## Vocabulary — the enums, with their exact members

Order below is the declared order (enum ORDER is significant for `indexOf`/comparisons):

```kotlin
enum class Role { ENTRANT, OWNER, ADMIN }                       // users may hold several roles
enum class NotificationSettings { PUSH, CSV }
enum class NotificationStatus { UNREAD, READ }
enum class MessageStatus { UNREAD, READ }
enum class ReservationStatus { ACTIVE, CANCELLED, REJECTED }
enum class LotteryStatus { UPCOMING, OPEN, DRAWN, BOOKING, CLOSED }
enum class NotificationType { WIN, LOSE, CANCELLATION, REJECTION, OWNER_NOTICE, MESSAGE, INVITE }
enum class LogLineStyle { DROPDOWN, RECIPIENT, NOTICE_TITLE, NOTICE_BODY }
```

Status/type `.name` strings are stored in documents by the data seams (see `data.md`).
`NotificationType` members per doc: WIN/LOSE for draw results; CONFIRMATION/CANCELLATION/REJECTION
for reservation events; MESSAGE for a new conversation message; OWNER_NOTICE is the admin
policy notice channel (see `controller.md`); INVITE for lottery invitations.

## Entities — the stored records, and their id rules

Every entity inherits `Identifiable` (a fresh instance gets a random UUID `id`; a read-back
instance carries the document key — see `database.md`). Where a rule below says "document
id", that is the key the write seams use.

```kotlin
class Campsite(
    ownerId, campName, campLoc, campDesc: String = "",
    campAmenities: List<String> = [], geoLocation, campImg: String = "",
    maxOccupancy: Int = 0, tags: List<String> = [], cancelPolicy: String = "",
    campPrice: Double = 0.0,
    availableStartDate: String = "",        // yyyy-MM-dd, and availableEndDate the same
    availableEndDate: String = "",
    blockedDates: List<String> = []
) : Identifiable()                          // id stays the fresh UUID; nothing derives it
```
- `tags` are the listing's advertised words; admin tag removal edits this list and must
  keep the record's id (see `controller.md` / `ui.md` for the exact admin flow).

```kotlin
class Rating(campsiteId, userId: String = "", stars: Int = 0) : Identifiable()
// init: id = Rating.makeId(campsiteId, userId)  — the key is "<campsiteId>_<userId>"
```
- One rating per campsite+user: rating again replaces (the key matches — see `data.md`).
- `makeId(lotteryId, userId) = lotteryId + "_" + userId` is the companion's public helper
  (its parameter is generically named; used here with the campsite id).

```kotlin
class WaitingListEntry(lotteryId, userId: String = "", joinedAt: Long = 0) : Identifiable()
// init: id = WaitingListEntry.makeId(lotteryId, userId) — "<lotteryId>_<userId>"
```
- The same id rule is what makes "one entrant, one entry per lottery" and what `removeEntry` deletes.

```kotlin
data class User(deviceId, name, email, phone: String = "",
                roles: List<Role> = [], notificationsEnabled: Boolean = true) : Identifiable()
// init: when deviceId non-empty, id = deviceId
fun hasRole(role: Role): Boolean          // roles.contains
```
- Same device ⇒ same profile key. `roles` is the list `hasRole` reads.

```kotlin
data class NotificationPreferenceModel(
    var userId: String = "",
    var allowPushNotifications: Boolean = true,      // defaults: push ON, csv OFF
    var allowCsvReservationDownload: Boolean = false) : Identifiable()
// init: when userId non-empty, id = userId — one settings record per user
```

```kotlin
data class AppSettingsModel(
    var userId, appViewChoice, entrantBookingsOrder, entrantInboxAlerts,
    entrantMessagesFilter, ownerListingVisibility, ownerAvailabilityNotice: String = "",
    var ownerLotteryAccepting: Boolean = true) : Identifiable()
// init: when userId non-empty, id = userId — one app-settings record per user
```
- The app settings kept between app lifetimes (`AppSettingsService` in `data.md` owns the
  storage rules; the seeding consumers are in `viewModels.md`). An EMPTY string means
  "not chosen", and the boolean's default equals the owner controller's default — an
  absent choice changes nothing anywhere. `appViewChoice` names an `AppView` (viewModels.md)
  by its enum name.

```kotlin
class Notification(
    recipientId, senderId: String = "",
    type: NotificationType = OWNER_NOTICE, title, message: String = "",
    lotteryId, reservationId, campsiteName: String = "",
    status: NotificationStatus = UNREAD, pushAllowed: Boolean = false,
    @ServerTimestamp sentAt: Timestamp? = null, readAt: Timestamp? = null) : Identifiable()
```
- `senderId` empty means the SYSTEM created the record (no user caused it) — the basis for
  `sentNotificationsFor` and the log's "the system" wording.
- `@ServerTimestamp`: a null `sentAt` at SAVE time is filled by the server clock; it reads
  back non-null only after a real save round-trip. A model-built record has `sentAt == null`.

```kotlin
data class ReservationModel(
    var lotteryEntryId, campsiteId, entrantId, ownerId: String = "",
    var guests: Int = 1, var dates: List<String> = [],
    var startDate, endDate: String = "",                       // ISO yyyy-MM-dd; dates sort as text
    var status: ReservationStatus = ACTIVE,
    var refundRecorded: Boolean = false,
    var createdAt: Long = <now at construction>,
    var rules: Boolean = false) : Identifiable()               // NO init: id is the caller's to set
```
- `dates` is "fixed once created" by convention; `startDate`/`endDate` are the min/max
  mirrors kept for range queries; the document key is the `lotteryEntryId` — set by
  `ReservationRepository.save` (see `data.md`), never by the class.
- `status`/`refundRecorded` are `var` because close-commands patch the fields through a
  field map (`update`) — see `data.md` for the exact patch.

```kotlin
class BookedDate(campsiteId, reservationId: String = "") : Identifiable()
// id is set by the caller to "<campsiteId>_<yyyy-MM-dd>" (the repository's bookedKey)
```
- Creating the document claims the date; a second create on the same key is rejected by
  storage rules; cancelling/rejecting deletes it.

## DateUtils — the one date library

```kotlin
object DateUtils {
    fun today(): String                                      // device timezone, "yyyy-MM-dd"
    fun datesBetween(start: String, end: String): List<String>  // INCLUSIVE both ends, in order
}
```
- `end` before `start` → empty list. A malformed date string throws
  `java.text.ParseException` (callers decide — `Availability` routes it to its failure).
- Every date the app stores/compares is an ISO `yyyy-MM-dd` string; string comparison is
  the intended order relation everywhere.

## The observable-model idiom

```kotlin
interface ViewObserver<M> { fun update(model: M) }
open class ObservableModel<M> {
    fun addView(v: ViewObserver<M>)      // dedup: a view is never added twice
    fun removeView(v: ViewObserver<M>)   // no-op when absent
    protected fun notifyViews()          // calls update(this as M) on every view, in add order
}
```
- Model fields are declared `private set`: readable from OUTSIDE (tests read them directly),
  writable only inside the class. Every public setter that changes observed state calls
  `notifyViews()` — that is the testable "views hear changes" contract.

## UserModel, CampsiteModel, LotteryModel — the four observed states

`class UserModel : ObservableModel<UserModel>` — read: `currentUser: User?`,
`allUsers: List<User>`, `statusMessage: String`. Setters `setCurrentUser(user?)`,
`setAllUsers(list)`, `setStatusMessage(message)` — each notifies.
`fun currentUserId(): String` — the signed-in user's id, `""` when none.

`class CampsiteModel : ObservableModel<CampsiteModel>` — read:
`allCampsites`, `visibleCampsites`, `selectedCampsite: Campsite?`, `ratings`,
`statusMessage`, `ownerId`, `ownedCampsites`.
Setters (all notify): `setCampsites(list)` — sets ALL and VISIBLE to the same list;
`setVisibleCampsites(list)` — browse/search results only; `selectCampsite(campsite?)` —
Details screen; `setRatings(list)`; `setStatusMessage`; `setOwnedCampsites(list)`.
Pure readers (no notify):
- `averageRating(campsiteId): Double` — mean of `stars` over ratings whose `campsiteId`
  matches; `0.0` when nobody rated it.
- `ratingCount(campsiteId): Int` — how many rated it.
- `myRating(campsiteId, userId): Int` — the stars THAT user gave, `0` when they have not
  rated it (first match wins; ratings are one-per-pair anyway).

`class LotteryModel : ObservableModel<LotteryModel>` — androidx-backed state. Read
(getters): `lotteryList: List<Lottery>`, `statusMessage`, `entrantLottery: Lottery?`,
`entrantIsOnWaitlist: Boolean`, `entrantPosition: Int`.
Setters `setLotteries`, `setStatusMessage`, `setEntrantStatus(lottery?, onWaitlist, position)` —
these do NOT notify views (the notifyViews call is commented out in the source; there is
no observable-notification contract for this model).

## Lottery — the entity and its rules (the heart of the lottery feature)

```kotlin
data class Lottery(
    ownerId: String = "", campsiteIds: List<String> = [],
    registrationStart, registrationEnd, bookingStart, bookingEnd: Long = 0,   // epoch millis
    numberOfWinners: Int = 0,
    waitlistCapacity: Int? = null,          // null = UNLIMITED
    drawn: Boolean = false, winnerIds: List<String> = [],
    invitedOwnerIds: List<String> = [],     // selected, not yet accepted
    joinedOwnerIds: List<String> = []       // accepted sharers; the creator is in NEITHER
) : Identifiable()
```

`statusAt(now: Long): LotteryStatus` — exact boundaries (note the one inclusive edge):
- `now < registrationStart` → UPCOMING
- `now < registrationEnd` → OPEN
- `now < bookingStart` → DRAWN
- `now <= bookingEnd` → BOOKING   (a lottery at EXACTLY bookingEnd is still BOOKING)
- otherwise → CLOSED

`isValid(now: Long): Boolean` — false when ANY rule below fails:
1. `campsiteIds` empty; 2. `numberOfWinners < 1`; 3. `waitlistCapacity != null && waitlistCapacity < numberOfWinners`;
4. any of the four boundary stamps is `0L` (a range must be chosen);
5. `registrationEnd < now` (registration may not already have passed);
6. `bookingStart <= registrationEnd` (booking must start strictly after registration ends);
7. `bookingEnd < bookingStart`;
8. invitation hygiene (US 02.04.08): a blank name in either owner list; `ownerId` in
   either list (an owner cannot invite themselves); any name both invited and joined;
   duplicates within either list.

`visibleTo(userId): Boolean = userId == ownerId || userId in joinedOwnerIds` — invited-but-not-accepted
owners and outsiders may NOT see it.

`withInvitationAcceptedBy(userId): Lottery` — if `userId` is not in `invitedOwnerIds`:
returns THIS instance (unchanged). Otherwise a copy with the id PRESERVED and the same
fields, except `invitedOwnerIds` minus that user and `joinedOwnerIds` plus them —
accepting never changes the parameters.

`withCampsiteIds(chosen: List<String>): Lottery` — copy with ONLY `campsiteIds` changed,
id preserved (a joining owner edits their own campsites, never the parameters).

## Availability — which dates a campsite is open (pre-reservation)

```kotlin
class Availability(private val campsites: QueryEngine<Campsite>)
fun getOpenDates(campsiteId: String, onSuccess: (Set<String>) -> Unit, onFailure: (Exception) -> Unit)
```
- Campsite absent, or either availability bound empty → `onSuccess(emptySet())`.
- Otherwise: `DateUtils.datesBetween(availableStartDate, availableEndDate)` kept only where
  `date >= today` and `date not in blockedDates`, as a SET.
- A malformed stored range routes the `ParseException` to `onFailure` (not a silent empty).
- Subtracting already-booked dates is NOT here — ReservationController's job (`controller.md`).

## QR codes — the entity, the codec, and reading scanned text

```kotlin
data class QRCode(campsiteId: String = "", stopsAtMillis: Long = 0)
//  stopsAtMillis == 0 (January 1, 1970) means the code carries NO expiry: such a code
//  is an old one and works at NO time at all (US 01.05.02 answer; the old "0 = never
//  stops working" reading is replaced)
fun payload(): String = "CAMPBNB:" + campsiteId + ":" + stopsAtMillis   // prefix is a private const "CAMPBNB"
fun fileName(): String = "${downloadSafeName(campsiteId)}.qr.png"
fun isValidAt(atMillis: Long): Boolean   // a code without an expiry never works; otherwise atMillis <= stopsAtMillis
```
- Campsite ids are document ids and never contain colons, which is what makes the text
  always split back.

```kotlin
fun qrCodeOfScannedText(payload: String): QRCode?
```
- Split on ":"; must be exactly 3 parts, first exactly `CAMPBNB`, second NON-BLANK, third
  parseable as Long — otherwise `null` (an expired-but-real code DECODES; expiry is a
  property of the decoded code, not a decode failure).

```kotlin
val qrPayloadExtraKey = "campbnb.qrPayload"   // Intent extra MainActivity reads for opened links
val qrLinkRoute = "qr-link"                   // graph route prefix a live code's link opens under
val qrOldCodeRoute = "qr-link-old"            // dead-code notice route (ONE segment: can never collide with the two-segment link)
fun qrRouteOfLinkPayload(payload: String, atMillis: Long = System.currentTimeMillis()): String?
```
- The link story (US 01.05.02): the phone's own camera hands the app the opened code's TEXT
  through `qrPayloadExtraKey`, exactly as a notification tap hands over `EXTRA_ROUTE`;
  MainActivity resolves it and queues the result on `pendingRoute` (app.md). `qrRouteOfLinkPayload`
  rules: text that is not code text → `null` (nothing is queued, the app just opens); a DEAD
  code — its stop already past, OR no expiry at all (the reserved 0 means old, not never-working)
  → `qrOldCodeRoute`; a code that still works → `"$qrLinkRoute/$campsiteId"` (the same campsite
  a scan opens — a code carries its campsite, not a lottery). `atMillis` is the moment the link
  is opened, an injectable time seam (same convention as `scanQr`'s `clock`); tests pass fixed
  moments.

```kotlin
fun qrImageFileBytes(qrCode: QRCode): ByteArray          // the real PNG bytes of this code's grid
fun qrTextOfImageBytes(imageBytes: ByteArray): String?   // first QR's text in image bytes, or null
fun qrTextOfImageFile(imageFilePath: String): String?    // same through a real file path
```
- Write path (pure Kotlin, runs anywhere): the code is encoded with Barleader
  (`BarleaderQrCode.encode(payload, QrOptions.defaults())`) and its module grid is drawn
  straight into PNG bytes: an RGB 8-bit PNG, each module a `qrImageModulePixels`(=8)
  pixel square, ringed by Barleader's static `QUIET_ZONE` of light modules;
  side = `(symbol.size() + 2*QUIET_ZONE) * 8`; scanlines carry filter byte 0; the image
  bytes are zlib STORED blocks (≤65535 bytes each, header `0x78 0x01`, length+complement
  per block, Adler-32 modulus 65521), split into IDAT chunks of ≤65535 data bytes, each
  chunk CRC'd (polynomial 0xEDB88320 reflected). The file starts with the PNG signature
  `89 50 4E 47 0D 0A 1A 0A`.
- Read path uses the JDK image codecs and ONLY RUNS ON THE JVM TEST HOST (the Android
  runtime has no java.awt — this is why the write path avoids image classes).
- The practical contract a test drives is the ROUND TRIP:
  `qrTextOfImageBytes(qrImageFileBytes(code)) == code.payload()`, plus `null` for
  unscannable inputs (no image, unreadable bytes). The byte-level detail above is the
  format's derivation, not required assertions.
- Grid note for anyone hand-checking a symbol: `isDark(y, x)` is the order that scans
  rows; a transposed grid does not decode.

## Reservation CSV — text, bytes and file naming

```kotlin
fun reservationCsvText(reservation: ReservationModel, campsite: Campsite?): String
fun reservationCsvBytesOf(reservation: ReservationModel, campsite: Campsite?): ByteArray  // UTF-8 bytes of the text
fun reservationCsvFileName(reservation: ReservationModel): String   // "<downloadSafeName(id)>.csv"; id = lotteryEntryId, or "unspecified" when blank
```
- Text is exactly four lines (four CSV lines: a quoted value may carry physical newlines
  inside it, but no line ever starts with indentation), in this order, with NO trailing newline:
  `Start,<escaped startDate>` / `End,<escaped endDate>` /
  `Campsite Name/Location,<escaped location>` / `Owner,<escaped owner>`
- `location`: no campsite given → the raw `campsiteId`; otherwise `"<campName> / <campLoc>"`.
- `owner`: `reservation.ownerId` when non-blank; else `campsite.ownerId` when a campsite
  is given; else empty.
- CSV quoting: a value containing `,` `"` or newline is wrapped in quotes with inner
  `"` doubled; other values pass through as-is.

## NotificationIntent — events as data (the report-half's input contract)

```kotlin
sealed interface NotificationIntent {
    data class ReservationConfirmed(val reservation: ReservationModel)
    data class ReservationCancelled(val reservation: ReservationModel)
    data class ReservationRejected(val reservation: ReservationModel)
    data class MessageArrived(val message: Message, val campsiteName: String)
    data class LotteryDrawn(val lotteryId: String, val campsiteName: String,
                            val winnerIds: List<String>, val loserIds: List<String>)
    data class LotteryInvited(val lottery: Lottery)
    data class CampsitePolicyRemoved(val campsite: Campsite, val reason: PolicyReason, val actorUserId: String)
    data class CampsiteTagRemoved(val campsite: Campsite, val tag: String,
                                  val reason: PolicyReason, val actorUserId: String)
}
```
- An intent NEVER names a receiver; deciding who hears is NotificationController's job
  (`controller.md` states every event's audience). The intent's fields name the CAUSE where
  one exists (attributed `senderId`), per the doc rules above each member.

## PolicyReason — the admin policy vocabulary

```kotlin
enum class PolicyReason { DUPLICATE_LISTING, NOT_ALLOWED, OUTDATED_LISTING, INCOMPLETE_LISTING }
fun policyReasonText(reason: PolicyReason): String
fun policyReasonLabel(reason: PolicyReason): String
```
Exact wordings (`policyReasonText` — what a notice says; `policyReasonLabel` — what a menu
shows):
- DUPLICATE_LISTING → text `"it repeated another campsite already on the app"`, label `"Repeated listing"`
- NOT_ALLOWED → text `"it offers something the app does not allow"`, label `"Not allowed on the app"`
- OUTDATED_LISTING → text `"its details no longer hold"`, label `"Out of date"`
- INCOMPLETE_LISTING → text `"it was never complete enough to book"`, label `"Never complete"`
Both are top-level functions (import as `com.example.campbnb.models.policyReasonText` etc.).

## Notification log — grouping saved records into log lines

From `NotificationLogModel.kt` (a model area, not a class — data + pure functions):

```kotlin
data class NotificationLogGroup(logId, senderId: String, recipientIds: List<String>,
    sentAtLabel, title, message, routeText: String)
data class NotificationLogLine(lineKey, logId, text: String, style: LogLineStyle)
fun notificationLogKeyOf(notification: Notification): String
fun notificationLogGroupsFor(notifications: List<Notification>): List<NotificationLogGroup>
fun notificationLogLinesFor(logs, openLogIds: Set<String>, profileLabel: (String) -> String): List<NotificationLogLine>
```
- Group key joins the broadcast-describing fields, NOT the per-receiver ones:
  `"<senderId>_<type.name>_<title>_<message>_<lotteryId>_<reservationId>_<campsiteName>"`.
- `notificationLogGroupsFor`: groups records by that key; entry = first record's fields,
  receivers = distinct `recipientId`s IN FIRST-SEEN ORDER, `sentAtLabel` from the FIRST
  non-null `sentAt` (none → the literal `"no saved timestamp"`, else format
  `"yyyy-MM-dd HH:mm"` US locale), `routeText` via `NotificationRoutes.routeFor` (its package).
  Entry order = input order (the CALLER sorts newest-first; this function does not sort).
- `summaryLineOf` (collapsed bar text): `"sent by <senderId or \"the system\">  -  <n> recipient(s)  -  <sentAtLabel>  -  opens <routeText>"` — two spaces around the `-` separators.
- `notificationLogLinesFor`: a CLOSED entry yields only its bar (`"<summary> - tap to open"`,
  style DROPDOWN, key = logId); an OPEN one yields bar (`"... - open"`) then one line per
  receiver (`profileLabel(receiver)`, key `"<logId>#receiver#<receiver>"`, style RECIPIENT),
  then NOTICE_TITLE (`group.title`) and NOTICE_BODY (`group.message`) with keys
  `"<logId>#notice#title"` / `"<logId>#notice#body"`. Unknown openLogIds change nothing;
  an entry with no receivers adds no receiver lines; entry order is preserved.
- All private helpers (`sentAtOf`, `describeSavedAt`, `summaryLineOf`, `logLinesOf`) are
  observable only through the exported functions.

## Pure helpers

`fun sentNotificationsFor(records: List<Notification>, senderId: String): List<Notification>` —
a BLANK senderId matches nothing (empty list); otherwise keeps exactly the records whose
`senderId` equals, IN INPUT ORDER (a newest-first log relies on the caller's order;
system-created records with empty senderId belong to no sender).

## SearchFilters — browse filters and what "set" means

```kotlin
data class SearchFilters(location: String = "", startDate: String = "", endDate: String = "", guests: Int? = null)
fun activeCount(): Int   // counts location (non-blank), startDate (non-empty), guests (non-null) — endDate is NOT counted
```
- Empty text / null number is "no filter" for that field; the default lets every campsite
  through. (The matching itself lives in the screens; see `ui.md` for how these apply.)

## Display data and placeholders

- `data class BookingCardData(id, lotteryId: String, campsiteNames: List<String>, status: String, startDate = "", endDate = "", guests = 0)` —
  NOT Identifiable, pure display data (bookings screen).
- `Image`, `Location`, `NotificationModel`, `Reservation` (marked `// DELETE`) are empty
  placeholder classes — no behavior; tests do not stand them up.

## Standing models up in tests

- Entities/feature functions are constructible and directly testable — no fakes needed for
  pure logic (statusAt boundaries, isValid rules, quoting, payload round-trips, grouping).
- `private set` fields are READABLE from test code; to change them, call the setters.
- Observable models: a `ViewObserver` can be stood up as an in-file fake implementing
  `update(model)`; add it with `addView`, call a setter, assert it was called and then
  assert the model's new field state. `addView` dedup is itself an assertable contract.
- `createdAt` is now-at-construction: never assert an exact value; assert monotonic
  relations if needed.
