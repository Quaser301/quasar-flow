# database — Firestore plumbing: the registry, engines, and lifecycle views

Spec file for the `com.example.campbnb.database` package
(`CampBNB/app/src/main/java/com/example/campbnb/database/`).

The data-package seams (`com.example.campbnb.data.*`) delegate their reads/writes here;
this area is the plumbing they delegate to. The domain data classes stored through these
engines are specified in `models.md` (see also `documentation/Engines.md` and
`Transactions.md` as the older per-feature UML background).

Written against branch `wyatt-us-01.05.02` (tip `c985351` plus working tree), 2026-11-22.

## Reachability — what a test can and cannot drive here

This dialect seals what it does not mark open:
- `object`, plain `class`, `sealed class` members below are NOT extendable and have no
  fake: `Database`, `QueryEngine`, `WriteEngine`, `TransactionEngine`, the three state
  classes, `QueryChain`/`TransactionChain`.
- `open class` (the three ViewModels) and `abstract class Identifiable` ARE extendable.
- Therefore: the engines and the registry are production plumbing, observable from unit
  tests only THROUGH the seams of the data package (a seam that touches Firestore can be
  replaced with an in-file fake — that is where all unit behavior is asserted).
  Direct unit tests of `QueryEngine`/`WriteEngine`/`TransactionEngine` logic would need a
  live Firestore and are not part of the unit surface.

## Database.kt — the single collection registry

```kotlin
object Database {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance() // resolved ONCE, shared
    object Users                  { COLLECTION_NAME = "users";                   readEngine(): QueryEngine<User>; writeEngine(): WriteEngine<User> }
    object Notifications          { COLLECTION_NAME = "notifications";           readEngine/writeEngine of Notification }
    object Ratings                { COLLECTION_NAME = "ratings";                 Rating }
    object Lotteries              { COLLECTION_NAME = "lotteries";               Lottery }
    object Reservations           { COLLECTION_NAME = "reservations";            ReservationModel }
    object NotificationPreferences{ COLLECTION_NAME = "notificationPreferences"; NotificationPreferenceModel }
    object AppSettings             { COLLECTION_NAME = "appSettings";               AppSettingsModel }
    object BookedDates            { COLLECTION_NAME = "bookedDates";             writeEngine(): WriteEngine<BookedDate> ONLY — no read engine }
    object Campsites              { COLLECTION_NAME = "campsites";               Campsite }
    object WaitingListEntries     { COLLECTION_NAME = "waitingList";             WaitingListEntry }
    object Messages               { COLLECTION_NAME = "messages";                Message }
    fun transactionEngine(): TransactionEngine                                   // one engine for ALL collections
}
```

Contracts:
- Every schema below `Database.<Schema>` is reachable as listed; the collection NAME is the
  exact Firestore path string above. Note the one name mismatch: the object is
  `WaitingListEntries` but the collection is `"waitingList"`.
- `BookedDates` deliberately has NO read engine: booked dates are never queried through
  this registry (availability is derived from reservations' `dates` fields — see
  `data.md#getBookedDates`).
- `transactionEngine()` is not per-schema: one engine spans every collection, because a
  transaction holds invariants ACROSS collections (a reservation and its booked dates).
- All engines this object hands out share the single resolved Firestore instance.
- Adding a schema follows the commented template in the file and requires the stored class
  to be `Identifiable`.

## Identifiable.kt — the id provider for stored data

```kotlin
abstract class Identifiable {
    @get:DocumentId @set:DocumentId
    open var id: String = UUID.randomUUID().toString()
}
```

Contracts:
- Every data class that is stored through the engines inherits `Identifiable`
  (`data class X(...) : Identifiable()`); this is what enables `WriteEngine.save`.
- A FRESH instance carries a random UUID as `id`.
- Write path: the engine stores at document key `item.id` — the id field IS the document
  key (see `WriteEngine.save` below).
- Read path: the annotations re-populate `id` from Firestore's `DocumentId` when an
  instance is built from a read, so an id read back equals the document key. The transient
  random UUID that exists between instantiation and re-population is documented as not
  expected to cause observable behavior.

## QueryEngine.kt — read plumbing

```kotlin
class QueryEngine<T: Identifiable>(db: FirebaseFirestore, collectionName: String, classInstance: Class<T>)
```
- `query(): QueryChain<T>` — a fresh chain over the collection.
- `getById(id: String, onSuccess: (T?) -> Unit, onFailure: (Exception) -> Unit)` — the
  document by key; a MISSING document answers `null`, not a failure.
- `exists(id: String, onSuccess: (Boolean) -> Unit, onFailure: (Exception) -> Unit)` —
  existence only, no value delivered.

`class QueryChain<T:Any>(query, classInstance)` — every clause RETURNS A NEW chain;
clauses accumulate in any order until one `execute`:

```kotlin
whereEquals(property: KProperty1<T, *>, value: Any?)            // field == value
whereIn(property, values: List<Any>)                            // field in list; Firestore caps at 30 values
whereGreaterThanOrEqual(property, value)                        // field >= value
whereGreaterThan(property, value)
whereLessThan(property, value)
orderBy(property, direction: Query.Direction = ASCENDING)       // the order clauses state
limitResults(limit: Long)
execute(onSuccess: (List<T>) -> Unit, onFailure: (Exception) -> Unit)
```

Contracts:
- Properties are named as class-property references (`Campsite::ownerId`); the field name
  sent to Firestore is the declared property name.
- `execute` converts matched documents to `T` via `classInstance` and answers
  exactly once: success with the (possibly empty) list, or failure with the error.
- Chained clauses compose as Firestore composes them: all filters must match (AND);
  `orderBy` orders the results; `limitResults` keeps the first N after ordering.

Also in the file: `inline fun <reified T: Identifiable> FirebaseFirestore.readEngineFor(collectionName)` —
the constructor helper the registry uses; not part of any test surface.

The registry hands out ONE engine per collection, not a new one per call: each object holds
`private val readEngineCached/writeEngineCached: <Type> by lazy { db.readEngineFor<T>(COLLECTION_NAME) }`
and `readEngine()`/`writeEngine()` are one-line accessors to it. This is safe because both
engines are stateless — a `QueryEngine`/`WriteEngine` holds only the shared `db`, the
collection name, the class instance and a `CollectionReference`, and every call's state lives
in the call itself — and because building one per call was the profile's hot spot (the same
document deserialized 653 times behind engines re-allocated on every read). No test references
`Database.` at all, so this changes no test surface; the accessors keep their exact signatures.

## WriteEngine.kt — write plumbing

```kotlin
class WriteEngine<T: Identifiable>(db: FirebaseFirestore, collectionName: String)
fun save(item: T, onComplete: (Result<Exception?>) -> Unit)               // set at document key item.id
fun delete(id: String, onComplete: (Result<Exception?>) -> Unit)
fun update(id: String, fieldMap: Map<String, Any?>, onComplete: (Result<Exception?>) -> Unit)  // ONLY the fields in fieldMap change
```

Contract: the `Result<Exception?>` envelope every data seam passes on — success carries
`null`, failure carries the error; called exactly once per operation. `update` changes
exactly the named fields and nothing else.

## TransactionEngine.kt — atomic multi-document work

```kotlin
class TransactionEngine(db: FirebaseFirestore)
fun commit(block: TransactionEngine.TransactionChain.() -> Any, onComplete: (Result<Exception?>) -> Unit)
```

The block runs with a `TransactionChain` as receiver; inside it these are the only
operations:

```kotlin
read(document: DocumentReference): DocumentSnapshot   // exists() / toObject(Class) on the snapshot
save(document, item: Any)                             // whole-document write (data class or field map)
update(document, fieldMap: Map<String, Any?>)         // named fields only
delete(document)
```

Rules the code enforces (a test may assert the failure at the mistake's point):
- ALL reads before any write. The chain tracks `writesStarted`; a `read` issued after a
  write aborts with the message
  `"Reads must be issued before writes inside a transaction."` at the point of the
  mistake — the transaction fails before that read would answer.
- A block is ordinary Kotlin between calls: it may convert a read result, test it, and
  derive later writes from what it read. Aborts use `check(condition) { "message" }`.
- Atomic: if any part fails, NONE of the transaction is written.
- `onComplete` receives `Result.success(null)` on commit, `Result.failure(error)`
  otherwise — exactly once.

The file's worked examples (worth keeping as the reference shape) show the two reservation
invariants as transactions: claim (check entry absent, check each date absent, save
reservation + date documents) and close (read → convert → check ACTIVE → update
status/refundRecorded → delete date documents). `fun FirebaseFirestore.transactionEngine()`
is the construction helper.

## Lifecycle views: TransactionView, TransactionViewState

```kotlin
abstract class TransactionView { }                         // contract base screens/ViewModels latch on
sealed class TransactionViewState : TransactionView() {
    object Idle                                          // no transaction has run
    object Loading                                       // running; Firestore retries stay Loading
    object Success                                       // latest run committed
    data class Fault(val exception: Exception)           // could not complete; reason provided
}
```

The sealed state classes (also `QueryState`, `WriteState`) exist so observers depend on the
state CONTRACT, not one implementation: `TransactionViewState` implements `TransactionView`
and a `StateFlow` is typed against the base.

## QueryState.kt / WriteState.kt — the other two state contracts

`sealed class QueryState<out T>`: `NoQuery` (no query has run yet), `Loading`
(query running), `data class Success<T>(val data: List<T>)` (query completed —
NOTE: SUCCESS DOES NOT MEAN NON-EMPTY: `data` may be empty and still be Success),
`data class Fault(val exception: Exception)`.

`sealed class WriteState`: `Idle`, `Loading`, `Success` (latest writes executed),
`Fault(exception)`. All sealed against outside modification.

## FirestoreQueryViewModel.kt

```kotlin
open class FirestoreQueryViewModel<T: Identifiable>(private val engine: QueryEngine<T>) : ViewModel()
val queryState: StateFlow<QueryState<T>>                       // starts QueryState.NoQuery
fun fetchAll()                                                 // == executeQuery of the bare base query
fun executeQuery(block: QueryEngine.QueryChain<T>.() -> QueryEngine.QueryChain<T>)
```

Contract (observable through `queryState`): on a run — `Loading`, then the block-built
chain executes through the engine, then `Success(items)` or `Fault(error)`. The block
starts INSIDE the base `engine.query()`, so a superview states only its extra clauses.
Runs inside `viewModelScope` (a destroyed view disposes the running query).
`open class`: extendable by screens/superviews.

## FirestoreWriteViewModel.kt

```kotlin
open class FirestoreWriteViewModel<T: Identifiable>(private val engine: WriteEngine<T>) : ViewModel()
val writeState: StateFlow<WriteState>                          // starts WriteState.Idle
fun create(item: T)                             // engine.save(item)
fun update(item: T, updatedFields: Map<String, Any?>)          // engine.update(item.id, fields)
fun delete(item: T)                             // engine.delete(item.id)
fun resetWriteState()                           // back to Idle directly
```

Each operation drives `Loading` → (`Success` | `Fault(exception)`); a Result failure is
carried into `Fault` with its exception. `resetWriteState()` sets `Idle` synchronously.

## FirestoreTransactionViewModel.kt

```kotlin
open class FirestoreTransactionViewModel(private val engine: TransactionEngine) : ViewModel()
val transactionViewState: StateFlow<TransactionViewState>      // starts Idle
fun runTransaction(block: TransactionEngine.TransactionChain.() -> Any)   // engine.commit(block), states as TransactionViewState
fun resetTransactionViewState()                               // back to Idle directly
```

## What this area contributes to tests

- Assert engine behavior only through data-seam fakes (`data.md`'s "Standing them up").
- The three state contracts are pure data: a test may construct `QueryState.Success([])`
  vs `Fault(...)` values directly when standing up a fake view's state flow, and a screen
  under test must read them through the named `StateFlow` property.
- The `open` ViewModels may be subclassed in a test file to hand a screen a scripted
  state sequence without touching Firestore (the subclass overrides the members;
  a screen must still get the StateFlow the view exposes).
