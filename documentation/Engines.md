# Engines

UML documentation for `QueryEngine<T>` and `WriteEngine<T>` and their helper classes
(`CampBNB/app/src/main/java/com/example/campbnb/database/`). The `FirebaseFirestore`,
`CollectionReference`, `Query`, and `Result` classes are library types, not part of this
repository.

```mermaid
classDiagram
    class QueryEngine~T~ {
        -db FirebaseFirestore
        -collectionName string
        -classInstance Class~T~
        -collection CollectionReference
        +query() QueryChain~T~
    }
    class QueryChain~T~ {
        -query Query
        -classInstance Class~T~
        +whereEquals(property, value) QueryChain~T~
        +whereGreaterThan(property, value) QueryChain~T~
        +whereLessThan(property, value) QueryChain~T~
        +orderBy(property, direction) QueryChain~T~
        +limitResults(count) QueryChain~T~
        +execute(onSuccess, onFailure)
    }
    class WriteEngine~T~ {
        -db FirebaseFirestore
        -collectionName string
        -collection CollectionReference
        +save(item, onComplete)
        +update(id, fieldMap, onComplete)
        +delete(id, onComplete)
    }
    class Identifiable <<abstract>>
    Identifiable : +id string
    class FirebaseFirestore
    class CollectionReference
    class Query
    class Result

    QueryEngine o-- FirebaseFirestore : db instance provided on construction
    WriteEngine o-- FirebaseFirestore : db instance provided on construction
    QueryEngine o-- CollectionReference : collection opened from the db
    WriteEngine o-- CollectionReference : collection opened from the db
    QueryChain o-- Query : clauses build on an existing Query

    QueryEngine ..> QueryChain : query() returns a new chain
    QueryChain ..> QueryChain : clause methods return updated chains
    WriteEngine ..> Result : onComplete receives success(null) or failure(Exception)
    QueryEngine ..> Identifiable : type parameter T bounded to Identifiable subclasses
    WriteEngine ..> Identifiable : same bound
```

Reading the diagram:

- Aggregation (open diamond, `o--`): every collaborator is passed in or opened at
  construction and exists independently of the engine, so no filled composition
  markers appear here; engines hold references, they never own lifecycle.
- Dependency (dotted arrow, `..>`): `query()` produces a `QueryChain` rather than
  holding one; chained clause methods depend on further chains of the same kind;
  the `T` type parameter is constrained to subclasses of `Identifiable`.
- There are no interface implementations in the engine code, so no dotted
  interface lines appear; the notation (`<|..`) would be used if there were.
- `whereEquals`/`orderBy` take `KProperty1` references, so the queried property is
  guaranteed to belong to `T`.
- `Identifiable` is instantiated with a random UUID; Firestore repopulates `id`
  from the `DocumentId` annotation on read, which is why the class is abstract and
  never instantiated by hand.
- Engines are constructed inline via the extension functions
  `db.readEngineFor(collectionName)` and `db.writeEngineFor(collectionName)`;
  write documents are keyed on the `Identifiable` id.
