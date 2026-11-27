# Application

UML documentation for the CampBNB application
(`CampBNB/app/src/main/java/com/example/campbnb/`),
covering its architecture, domain entities, observable
models, controllers, repositories, notifications,
messaging and navigation.

The database layer is documented separately in Engines,
FirestoreViews and Transactions.

## Layers

```mermaid
classDiagram
    namespace ui {
        class CampBnbApp
        class Screen {
            <<interface>>
        }
        class ComposeScreens
    }
    namespace viewModels {
        class AppViewSettingController
        class NotificationViewModel
    }
    namespace controller {
        class UserController
        class CampsiteController
        class LotteryController
        class ReservationController
        class NotificationController
        class MessageController
    }
    namespace models {
        class ObservableModel~M~
        class Identifiable {
            <<abstract>>
        }
    }
    namespace data {
        class UserRepository
        class CampsiteRepository
        class LotteryRepository
        class ReservationRepository
        class NotificationRepository {
            <<interface>>
        }
        class MessageRepository {
            <<interface>>
        }
    }
    namespace database {
        class Database {
            <<object>>
        }
        class QueryEngine~T~
        class WriteEngine~T~
        class TransactionEngine
    }
    namespace notifications {
        class NotificationWatcher
        class SystemNotifier
    }

    class MainActivity
    class AppContainer {
        <<object>>
    }

    MainActivity ..> CampBnbApp : setContent mounts the navigation host
    MainActivity *-- NotificationWatcher : created lazily, lives with the activity
    CampBnbApp ..> Screen : one composable destination per route
    CampBnbApp ..> ComposeScreens : mounts them, handing in lambdas and models
    ComposeScreens ..> UserController : user actions call controllers
    ComposeScreens ..> ObservableModel : observeModel re-renders on notifyViews
    UserController o-- UserRepository
    CampsiteController o-- CampsiteRepository
    LotteryController o-- LotteryRepository
    ReservationController o-- ReservationRepository
    NotificationController o-- NotificationRepository
    MessageController o-- MessageRepository
    UserRepository ..> Database : reads and writes through engines
    Database ..> QueryEngine : readEngine()
    Database ..> WriteEngine : writeEngine()
    Database ..> TransactionEngine : transactionEngine()
    NotificationWatcher o-- NotificationController
    NotificationWatcher o-- SystemNotifier
    AppContainer ..> NotificationController : app-wide singleton instances
    QueryEngine ..> Identifiable : T bounded to Identifiable
```

Reading the diagram:

- The namespaces represent the source packages. The app
  follows MVC, with controllers handling business rules,
  models holding state, and the UI displaying data.
- Repositories access Firestore through the database layer.
- Aggregation (o--) represents shared collaborators passed
  into controllers.
- Composition (*--) represents the NotificationWatcher
  owned by MainActivity.
- ComposeScreens represents the Compose screen functions.
- AppContainer provides shared application instances.


## Domain entities

The Firestore schemas in `models/`, every one an `Identifiable` subclass registered with
`Database`, plus the plain value classes and enums they use.

```mermaid
classDiagram
    class Identifiable {
        <<abstract>>
        +id String
    }

    class User {
        +deviceId String
        +name String
        +email String
        +phone String
        +roles List~Role~
        +notificationsEnabled Boolean
        +hasRole(role Role) Boolean
    }
    class Campsite {
        +ownerId String
        +campName String
        +campLoc String
        +campDesc String
        +campAmenities List~String~
        +geoLocation String
        +campImg String
        +maxOccupancy Int
        +tags List~String~
        +cancelPolicy String
        +campPrice Double
        +availableStartDate String
        +availableEndDate String
        +blockedDates List~String~
    }
    class Lottery {
        +ownerId String
        +campsiteIds List~String~
        +registrationStart Long
        +registrationEnd Long
        +bookingStart Long
        +bookingEnd Long
        +numberOfWinners Int
        +waitlistCapacity Int?
        +drawn Boolean
        +winnerIds List~String~
        +statusAt(now Long) LotteryStatus
        +isValid(now Long) Boolean
    }
    class WaitingListEntry {
        +lotteryId String
        +userId String
        +joinedAt Long
        +makeId(lotteryId String, userId String)$ String
    }
    class ReservationModel {
        +lotteryEntryId String
        +campsiteId String
        +entrantId String
        +ownerId String
        +guests Int
        +dates List~String~
        +startDate String
        +endDate String
        +status ReservationStatus
        +refundRecorded Boolean
        +createdAt Long
        +rules Boolean
    }
    class BookedDate {
        +campsiteId String
        +reservationId String
    }
    class Rating {
        +campsiteId String
        +userId String
        +stars Int
        +makeId(campsiteId String, userId String)$ String
    }
    class Message {
        +reservationId String
        +campsiteId String
        +ownerId String
        +entrantId String
        +participantIds List~String~
        +senderId String
        +recipientId String
        +content String
        +sentAt Timestamp?
        +status MessageStatus
    }
    class Notification {
        +recipientId String
        +senderId String
        +type NotificationType
        +title String
        +message String
        +lotteryId String
        +reservationId String
        +campsiteName String
        +status NotificationStatus
        +pushAllowed Boolean
        +sentAt Timestamp?
        +readAt Timestamp?
    }

    class Role {
        <<enumeration>>
        ENTRANT
        OWNER
        ADMIN
    }
    class LotteryStatus {
        <<enumeration>>
        UPCOMING
        OPEN
        DRAWN
        BOOKING
        CLOSED
    }
    class ReservationStatus {
        <<enumeration>>
        ACTIVE
        CANCELLED
        REJECTED
    }
    class NotificationType {
        <<enumeration>>
        WIN
        LOSE
        CONFIRMATION
        CANCELLATION
        REJECTION
        OWNER_NOTICE
        MESSAGE
    }
    class NotificationStatus {
        <<enumeration>>
        UNREAD
        READ
    }
    class MessageStatus {
        <<enumeration>>
        UNREAD
        READ
    }

    class SearchFilters {
        +location String
        +startDate String
        +endDate String
        +guests Int?
        +activeCount() Int
    }
    class Conversation {
        +reservationId String
        +otherUserId String
        +campsiteId String
        +ownerId String
        +entrantId String
        +lastText String
        +lastSentAtSeconds Long?
        +unreadCount Int
        +hasBothParticipants Boolean
    }
    class Conversations {
        <<object>>
        +group(userId String, all List~Message~) List~Conversation~
        +thread(userId String, reservationId String, all List~Message~) List~Message~
    }

    Identifiable <|-- User
    Identifiable <|-- Campsite
    Identifiable <|-- Lottery
    Identifiable <|-- WaitingListEntry
    Identifiable <|-- ReservationModel
    Identifiable <|-- BookedDate
    Identifiable <|-- Rating
    Identifiable <|-- Message
    Identifiable <|-- Notification

    User "1" -- "0..*" Campsite : owns, by ownerId
    User "1" -- "0..*" Lottery : runs, by ownerId
    Lottery "0..*" -- "1..*" Campsite : covers, by campsiteIds
    Lottery "1" -- "0..*" WaitingListEntry : waitlist, by lotteryId
    User "1" -- "0..*" WaitingListEntry : joins, by userId
    Lottery "0..*" -- "0..*" User : winners, by winnerIds
    WaitingListEntry "1" -- "0..1" ReservationModel : reservation id equals entry id
    ReservationModel "1" -- "0..*" BookedDate : claims one per night
    Campsite "1" -- "0..*" BookedDate : keyed campsiteId_yyyy-MM-dd
    Campsite "1" -- "0..*" Rating : rated, one per user
    ReservationModel "1" -- "0..*" Message : thread, by reservationId
    User "1" -- "0..*" Notification : inbox, by recipientId

    User --> Role
    Lottery ..> LotteryStatus : statusAt derives it from the clock
    ReservationModel --> ReservationStatus
    Notification --> NotificationType
    Notification --> NotificationStatus
    Message --> MessageStatus
    Conversations ..> Conversation : group() builds one per reservation
    Conversations ..> Message : reads them, never writes
```
Reading the diagram:

- Stored Firestore entities extend Identifiable.
- Deterministic IDs prevent duplicate entries, ratings,
  reservations and booked nights.
- Associations represent references between documents
  through ID fields.
- Directed associations represent enum-typed fields.
- LotteryStatus is derived from timestamps rather than stored.
- Dates use ISO yyyy-MM-dd strings, while lottery windows
  use epoch milliseconds.
- SearchFilters and Conversation are not stored in Firestore.


## Lottery and reservation lifecycles

```mermaid
stateDiagram-v2
    [*] --> UPCOMING
    UPCOMING --> OPEN : now reaches registrationStart
    OPEN --> DRAWN : now reaches registrationEnd, scheduled draw sets drawn and winnerIds
    DRAWN --> BOOKING : now reaches bookingStart
    BOOKING --> CLOSED : now passes bookingEnd
    CLOSED --> [*]

    state BOOKING {
        [*] --> ACTIVE : createReservation by a winner
        ACTIVE --> CANCELLED : cancelReservation by the entrant
        ACTIVE --> REJECTED : rejectReservation by the owner
    }
```

Reading the diagram:

- Lottery.statusAt(now) determines the current lottery
  status from its timestamps.
- Entrants can join or leave the waitlist while registration
  is OPEN.
- The scheduled function draws winners after registration
  closes (see AutomaticDraws).
- Cancelling or rejecting a reservation releases its
  booked dates. Cancellation also records the refund.

## Observable models

The hand-rolled observer pattern in `models/` and the Compose bridge in
`ui/shared/ObserveModel.kt`.

```mermaid
classDiagram
    class ObservableModel~M~ {
        -views MutableList~ViewObserver~
        +addView(v ViewObserver~M~)
        +removeView(v ViewObserver~M~)
        #notifyViews()
    }
    class ViewObserver~M~ {
        <<interface>>
        +update(model M)
    }

    class UserModel {
        +currentUser User?
        +allUsers List~User~
        +statusMessage String
        +setCurrentUser(user User?)
        +setAllUsers(list List~User~)
        +setStatusMessage(message String)
        +currentUserId() String
    }
    class CampsiteModel {
        +allCampsites List~Campsite~
        +visibleCampsites List~Campsite~
        +selectedCampsite Campsite?
        +ratings List~Rating~
        +statusMessage String
        +ownerId String
        +ownedCampsites List~Campsite~
        +setCampsites(list List~Campsite~)
        +setVisibleCampsites(list List~Campsite~)
        +selectCampsite(campsite Campsite?)
        +setRatings(list List~Rating~)
        +setStatusMessage(message String)
        +setOwnedCampsites(list List~Campsite~)
        +averageRating(campsiteId String) Double
        +ratingCount(campsiteId String) Int
        +myRating(campsiteId String, userId String) Int
    }
    class LotteryModel {
        +lotteryList List~Lottery~
        +statusMessage String
        +entrantLottery Lottery?
        +entrantIsOnWaitlist Boolean
        +entrantPosition Int
        +setLotteries(list List~Lottery~)
        +setStatusMessage(msg String)
        +setEntrantStatus(lottery Lottery?, onWaitlist Boolean, position Int)
    }

    class observeModel {
        <<function>>
        +observeModel(model ObservableModel~M~) Int
    }
    class CampsiteMonitorActivity
    class LotteryEditActivity
    class LotteryMonitorActivity

    ObservableModel <|-- UserModel
    ObservableModel <|-- CampsiteModel
    ObservableModel <|-- LotteryModel
    ViewObserver <|.. CampsiteMonitorActivity
    ViewObserver <|.. LotteryEditActivity
    ViewObserver <|.. LotteryMonitorActivity
    ObservableModel o-- "0..*" ViewObserver : registered views, told on every change
    observeModel ..> ViewObserver : registers an anonymous one, removed when the screen leaves

    UserModel o-- "0..*" User
    CampsiteModel o-- "0..*" Campsite
    CampsiteModel o-- "0..*" Rating
    LotteryModel o-- "0..*" Lottery
```

Reading the diagram:

- UserModel, CampsiteModel and LotteryModel extend
  ObservableModel.
- UserModel and CampsiteModel notify registered observers
  when their state changes.
- LotteryModel uses Compose mutableStateOf instead.
- observeModel connects the observer pattern to Compose
  and removes its observer when the screen leaves composition.
- Aggregation represents entities held by models that
  exist independently in Firestore.## Controllers and repositories

The business rules in `controller/` and the storage rules in `data/`.

```mermaid
classDiagram
    class UserController {
        -model UserModel
        -repo UserRepository
        +identify(deviceId String, onFound User -> Unit, onNotFound () -> Unit, onError String -> Unit)
        +register(deviceId String, name String, email String, phone String, onDone Boolean -> Unit)
        +chooseRole(role Role, onDone Boolean -> Unit)
        +updateProfile(name String, email String, phone String, onDone Boolean -> Unit)
        +setNotificationsEnabled(enabled Boolean, onDone Boolean -> Unit)
        +deleteProfile(onDone Boolean -> Unit)
        +loadAllUsers()
        +filterAllowed(userIds List~String~, onResult List~String~ -> Unit)
        +validateProfile(name String, email String) String?
    }
    class OptOutChecker {
        <<interface>>
        +filterAllowed(userIds List~String~, onResult List~String~ -> Unit)
        +AllowAll$ OptOutChecker
    }
    class CampsiteController {
        -model CampsiteModel
        -repo CampsiteRepository
        +loadAll()
        +loadRatings()
        +search(query String, filters SearchFilters)
        +search(query String, guests Int?)
        +loadAndSearch(query String, filters SearchFilters)
        +selectCampsite(campsiteId String)
        +saveRating(campsiteId String, userId String, stars Int)
        +registerCampsite(...)
        +updateCampsite(...)
        +loadAndSelect(campsiteId String)
        +loadOwnerCampsites(ownerId String)
    }
    class LotteryController {
        -model LotteryModel
        -repo LotteryRepository
        -lotteries QueryEngine~Lottery~
        -clock () -> Long
        +checkIfWon(...)
        +isBookingOpen(...)
        +loadEntrantStatus(campsiteId String, userId String)
        +findOpenLottery(allLotteries List~Lottery~, campsiteId String) Lottery?
        +join(lottery Lottery, userId String)
        +leave(lottery Lottery, userId String)
        +toggleEntrantMembership(userId String)
        +saveLottery(lottery Lottery, onSuccess () -> Unit, onFailure Exception -> Unit)
        +loadOwnerCampsites(ownerId String, onResult List~Campsite~ -> Unit)
        +loadOwnerLotteries(ownerId String)
        +loadWaitlistEntries(lotteryId String, onResult List~WaitingListEntry~ -> Unit)
    }
    class ReservationController {
        -lotteryController LotteryController
        -availability Availability
        -reservations ReservationRepository
        -notifications NotificationController
        -policiesConfirmed MutableSet~String~
        +getAvailableDates(lotteryEntryId String, entrantId String, campsiteId String, onSuccess Set~String~ -> Unit, onFailure Exception -> Unit)
        +confirmPolicies(lotteryEntryId String)
        +createReservation(lotteryEntryId String, entrantId String, ownerId String, campsiteId String, dates Set~String~, ...)
        +cancelReservation(reservationId String, requesterId String, onSuccess () -> Unit, onFailure Exception -> Unit)
        +rejectReservation(reservationId String, requesterId String, onSuccess () -> Unit, onFailure Exception -> Unit)
        +listReservations(...)
        +sendNotification(recipientId String, type String, reservation ReservationModel)
        +generateReceiptCsv(reservation ReservationModel) String
    }
    class ReservationFilter {
        <<enumeration>>
        ACTIVE
        PAST
        CANCELLED
    }
    class Availability {
        -campsites QueryEngine~Campsite~
        +getOpenDates(campsiteId String, onSuccess Set~String~ -> Unit, onFailure Exception -> Unit)
    }

    class UserRepository {
        +findByDeviceId(deviceId String, onSuccess User? -> Unit, onFailure Exception -> Unit)
        +save(user User, onComplete Result~Exception?~ -> Unit)
        +delete(userId String, onComplete Result~Exception?~ -> Unit)
        +getAll(onSuccess List~User~ -> Unit, onFailure Exception -> Unit)
        +getOptedOut(onSuccess List~User~ -> Unit, onFailure Exception -> Unit)
    }
    class CampsiteRepository {
        +SaveCampsite(campsite Campsite, onComplete Result~Exception?~ -> Unit)
        +deleteCampsite(...)
        +getAll(onSuccess List~Campsite~ -> Unit, onFailure Exception -> Unit)
        +getByOwner(ownerId String, onSuccess List~Campsite~ -> Unit, onFailure Exception -> Unit)
        +getAllRatings(onSuccess List~Rating~ -> Unit, onFailure Exception -> Unit)
        +saveRating(rating Rating, onComplete Result~Exception?~ -> Unit)
    }
    class LotteryRepository {
        +loadCampsitesByOwner(...)
        +save(lottery Lottery, onSuccess () -> Unit, onFailure Exception -> Unit)
        +getAllLotteries(onSuccess List~Lottery~ -> Unit, onFailure Exception -> Unit)
        +getLotteriesForOwner(ownerId String, ...)
        +getEntries(...)
        +addEntry(entry WaitingListEntry, onComplete Result~Exception?~ -> Unit)
        +removeEntry(...)
    }
    class ReservationRepository {
        -engine QueryEngine~ReservationModel~
        -reservationWriter WriteEngine~ReservationModel~
        -bookedDateWriter WriteEngine~BookedDate~
        +getActive(...)
        +getPast(...)
        +getCancelled(...)
        +getForOwner(...)
        +getForEntrant(...)
        +getByLotteryEntry(...)
        +getBookedDates(campsiteId String, ...)
        +CheckIfReserved(lotteryEntryId String, ...)
        +save(reservation ReservationModel, onSuccess () -> Unit, onFailure Exception -> Unit)
        +markCancelled(reservationId String, onSuccess () -> Unit, onFailure Exception -> Unit)
        +markRejected(reservationId String, onSuccess () -> Unit, onFailure Exception -> Unit)
    }
    class Database {
        <<object>>
        +Users
        +Campsites
        +Ratings
        +Lotteries
        +WaitingListEntries
        +Reservations
        +BookedDates
        +Notifications
        +Messages
        +transactionEngine() TransactionEngine
    }
    class UserModel
    class CampsiteModel
    class LotteryModel
    class NotificationController

    OptOutChecker <|.. UserController
    UserController o-- UserModel
    UserController o-- UserRepository
    CampsiteController o-- CampsiteModel
    CampsiteController o-- CampsiteRepository
    LotteryController o-- LotteryModel
    LotteryController o-- LotteryRepository
    ReservationController o-- LotteryController : checks the entrant won before booking
    ReservationController o-- Availability : open dates of the campsite
    ReservationController o-- ReservationRepository
    ReservationController o-- NotificationController : tells the other party
    ReservationController ..> ReservationFilter : listReservations slices by it
    UserRepository ..> Database : Users
    CampsiteRepository ..> Database : Campsites and Ratings
    LotteryRepository ..> Database : Lotteries, WaitingListEntries, Campsites
    Availability ..> Database : Campsites read engine
    ReservationRepository ..> Database : Reservations and BookedDates engines
```

Reading the diagram:

- Controllers receive their collaborators through their
  constructors.
- LotteryController accepts a clock for testing time-based rules.
- UserController implements OptOutChecker to respect
  notification preferences.
- Repositories access Firestore through database engines.
  ReservationRepository receives its engines directly.
- Callback parameters are shortened where necessary
  for readability.
- ReservationController checks eligibility, policies
  and date availability before saving a reservation.

## Booking a won campsite

```mermaid
sequenceDiagram
    participant Screen as Booking screen
    participant RC as ReservationController
    participant LC as LotteryController
    participant AV as Availability
    participant RR as ReservationRepository
    participant NC as NotificationController

    Screen->>RC: confirmPolicies(lotteryEntryId)
    Screen->>RC: createReservation(entry, entrant, owner, campsite, dates, ...)
    RC->>LC: eligibility: drawn, entrant in winnerIds, booking window open
    LC-->>RC: eligible
    RC->>AV: getOpenDates(campsiteId)
    AV-->>RC: open nights
    RC->>RR: getBookedDates(campsiteId)
    RR-->>RC: booked nights
    RC->>RC: requested dates within open minus booked
    RC->>RR: save(reservation)
    RR->>RR: CheckIfReserved, then claim one BookedDate per night
    RR-->>RC: saved
    RC->>NC: send(ownerId, CONFIRMATION, details)
    RC-->>Screen: onSuccess(reservation)
```

Reading the diagram:

- A failed validation calls onFailure and stops the
  reservation process.
- Deterministic BookedDate IDs and Firestore security
  rules prevent two entrants from booking the same night.

## Notifications and messaging

The `notifications/` package, the inbox and message controllers, and their Firestore
repositories.

```mermaid
classDiagram
    class NotificationRepository {
        <<interface>>
        +saveAll(notifications List~Notification~, onDone Result~Unit~ -> Unit)
        +listenForUser(userId String, onUpdate NotificationUpdate -> Unit, onError Exception -> Unit) () -> Unit
        +markRead(notificationId String, onDone Result~Unit~ -> Unit)
    }
    class FirestoreNotificationRepository {
        -db FirebaseFirestore
    }
    class NotificationUpdate {
        +all List~Notification~
        +added List~Notification~
    }
    class NotificationController {
        -repo NotificationRepository
        -optOutChecker OptOutChecker
        +notifyWinners(lotteryId String, campsiteName String, winnerIds List~String~, onDone Result~Int~ -> Unit)
        +notifyLosers(lotteryId String, campsiteName String, loserIds List~String~, onDone Result~Int~ -> Unit)
        +notifyLotteryResults(lotteryId String, campsiteName String, winnerIds List~String~, loserIds List~String~, onDone Result~Int~ -> Unit)
        +send(recipientId String, type String, details Map~String~)
        +listen(userId String, onUpdate NotificationUpdate -> Unit, onError Exception -> Unit) () -> Unit
        +markRead(notificationId String, onDone Result~Unit~ -> Unit)
        +sortNewestFirst(list List~Notification~)$ List~Notification~
    }
    class OptOutChecker {
        <<interface>>
    }

    class MessageRepository {
        <<interface>>
        +send(message Message, onDone Result~Unit~ -> Unit)
        +listenForUser(...) () -> Unit
        +listenForReservation(...) () -> Unit
        +markRead(messageIds List~String~, onDone Result~Unit~ -> Unit)
    }
    class FirestoreMessageRepository {
        -db FirebaseFirestore
    }
    class MessageController {
        -repo MessageRepository
        -reservationRepository ReservationRepository
        -notifier NewMessageNotifier?
        -campsiteNameLookup (String, String -> Unit) -> Unit
        +sendMessage(reservationId String, senderId String, content String, onDone Result~Message~ -> Unit)
        +reply(conversation Conversation, senderId String, content String, onDone Result~Message~ -> Unit)
        +listenForUser(...) () -> Unit
        +listenForConversation(...) () -> Unit
        +markMessagesRead(...)
        +sortOldestFirst(messages List~Message~) List~Message~
        +sortNewestFirst(messages List~Message~) List~Message~
    }
    class NewMessageNotifier {
        <<interface>>
        +messageSent(message Message, campsiteName String)
    }
    class MessageNotificationAdapter {
        -notifications NotificationController
    }

    class NotificationWatcher {
        -controller NotificationController
        -notifier SystemNotifier
        -stopListening (() -> Unit)?
        +start(userId String)
        +stop()
    }
    class SystemNotifier {
        -context Context
        +show(n Notification)
    }
    class NotificationChannels {
        <<object>>
        +channelFor(type NotificationType) String
        +createAll(context Context)
    }
    class NotificationRoutes {
        <<object>>
        +routeFor(n Notification) String
    }
    class NotificationPermission {
        <<object>>
        +isGranted(context Context) Boolean
        +openSettings(context Context)
    }

    class ViewModel {
        <<abstract>>
    }
    class NotificationViewModel {
        -controller NotificationController
        -userId String
        -_state MutableStateFlow~InboxState~
        +state StateFlow~InboxState~
        +start()
        +open(notification Notification)
        +markAllRead()
        #onCleared()
    }
    class InboxState {
        <<interface>>
    }
    class InboxLoading
    class Ready {
        +items List~Notification~
    }
    class InboxError {
        +message String
    }
    class ReservationRepository

    NotificationRepository <|.. FirestoreNotificationRepository
    MessageRepository <|.. FirestoreMessageRepository
    NewMessageNotifier <|.. MessageNotificationAdapter
    ViewModel <|-- NotificationViewModel
    InboxState <|.. InboxLoading
    InboxState <|.. Ready
    InboxState <|.. InboxError

    NotificationController o-- NotificationRepository
    NotificationController o-- OptOutChecker : filters out opted-out recipients
    NotificationRepository ..> NotificationUpdate : listener delivers all plus added
    MessageController o-- MessageRepository
    MessageController o-- ReservationRepository : finds both participants of a thread
    MessageController o-- "0..1" NewMessageNotifier : told after each sent message
    MessageNotificationAdapter o-- NotificationController : turns a message into a MESSAGE notification
    NotificationWatcher o-- NotificationController : listens for the signed-in user
    NotificationWatcher o-- SystemNotifier : shows each added notification on the phone
    SystemNotifier ..> NotificationChannels : picks the channel per type
    SystemNotifier ..> NotificationRoutes : tap opens the matching screen
    NotificationViewModel o-- NotificationController
    NotificationViewModel *-- InboxState : private flow created by this class
```

Reading the diagram:

- Repository interfaces allow Firestore implementations
  to be replaced with fakes during testing.
- MessageNotificationAdapter connects messaging to
  notifications without directly coupling the controllers.
- Notification listeners are stopped when their owning
  screen or activity is destroyed.
- AppContainer provides shared controller instances.
- NotificationViewModel owns its private state flow.
- Notification stores the in-app record, while
  SystemNotifier displays phone notifications.

## Navigation and wiring

The entry point, the typed routes in `Screen.kt`, and the role and view switching that
decides what each user sees.

```mermaid
classDiagram
    class ComponentActivity
    class MainActivity {
        -watcher NotificationWatcher
        -pendingRoute String?
        #onCreate(savedInstanceState Bundle?)
        #onNewIntent(intent Intent)
        #onDestroy()
    }
    class CampBnbApp {
        <<composable>>
        +CampBnbApp(appViewSettings AppViewSettingController, pendingRoute String?, onPendingRouteHandled () -> Unit)
    }
    class AppContainer {
        <<object>>
        +currentUserId String
        +currentUserIsOwner Boolean
        +notificationRepository NotificationRepository
        +notificationController NotificationController
        +messageRepository MessageRepository
        +reservationRepository ReservationRepository
        +messageController MessageController
    }
    class DeviceId {
        <<object>>
        +get(context Context) String
    }

    class Screen {
        <<interface>>
    }
    class Start
    class Login
    class SignUp
    class RoleSelect {
        Screen.Role
    }
    class Home {
        +role UserRole
        +tab Int
    }
    class Settings {
        +role UserRole
    }
    class Search {
        +role UserRole
        +query String
        +openFilter Boolean
    }
    class Reservations
    class CampsiteDetailsScreen {
        +role UserRole
        +campsiteId String
    }
    class CampsiteMonitorActivity {
        +role UserRole
    }
    class CampsiteEditActivity
    class EditCampsite {
        +campsiteId String
    }
    class LotteryEditActivity
    class LotteryMonitorActivity
    class LotteryDetailsActivity
    class LotteryWaitlistActivity
    class AdminProfileView

    class UserRole {
        <<enumeration>>
        CAMPER
        OWNER
        ADMIN
    }
    class AppView {
        <<enumeration>>
        AdminView
        EntrantView
        OwnerView
    }
    class HomeDestination {
        <<enumeration>>
        EXPLORE
        MESSAGES
        BOOKINGS
        INBOX
        PROFILE
        MY_CAMPSITES
        REPORTS
        FLAGGED
        ADMIN_PROFILE_VIEW
    }
    class HomeTab {
        +label String
        +destination HomeDestination
    }
    class RoleMapping {
        <<functions>>
        +toUserRole(role Role) UserRole
        +toRole(userRole UserRole) Role
        +viewFor(role Role) AppView
        +roleFor(view AppView) UserRole
        +tabsFor(role UserRole) List~HomeTab~
    }
    class AppViewSettingController

    ComponentActivity <|-- MainActivity
    Screen <|.. Start
    Screen <|.. Login
    Screen <|.. SignUp
    Screen <|.. RoleSelect
    Screen <|.. Home
    Screen <|.. Settings
    Screen <|.. Search
    Screen <|.. Reservations
    Screen <|.. CampsiteDetailsScreen
    Screen <|.. CampsiteMonitorActivity
    Screen <|.. CampsiteEditActivity
    Screen <|.. EditCampsite
    Screen <|.. LotteryEditActivity
    Screen <|.. LotteryMonitorActivity
    Screen <|.. LotteryDetailsActivity
    Screen <|.. LotteryWaitlistActivity
    Screen <|.. AdminProfileView

    MainActivity ..> DeviceId : reads the device id once on create
    MainActivity ..> AppContainer : sets currentUserId to the device id
    MainActivity ..> CampBnbApp : setContent, passing a notification tap route
    CampBnbApp ..> Screen : NavHost destination per route type
    CampBnbApp o-- AppViewSettingController : view switch shared by every screen
    Home --> UserRole
    Home ..> HomeTab : bottom bar built by tabsFor(role)
    HomeTab --> HomeDestination
    RoleMapping ..> UserRole
    RoleMapping ..> AppView
    AppViewSettingController --> AppView : current perspective
```

Reading the diagram:

- Screen is a sealed interface providing typed
  navigation routes.
- Known users navigate to Home, while new users
  complete SignUp and role selection.
- Home displays navigation tabs based on the user's role.
- RoleMapping converts between Role, UserRole and AppView.
- Destination-specific controllers and models are created
  using remember, while shared instances come from AppContainer.
- MainActivity handles notification taps and passes
  the destination to CampBnbApp.

## Placeholders not yet built

These classes exist in the source as empty shells reserved for later stories, so they
carry no members and no relationships yet: `AdminController`, `ImageRepository`,
`Image`, `Location`, `QRCode`, `NotificationModel`, `AdminActivity`, `BookingActivity`,
`PolicyActivity`, `ProfileActivity`, `UserReservationActivity`,
`OwnerReservationActivity`, `CampsiteDetailsActivity` and `CampsiteSearchActivity`.
`Reservation` is an empty shell marked for deletion: `ReservationModel` is the
reservation schema. `Database.YourSchemaPluralized` is the template object showing how
to register a new collection.
