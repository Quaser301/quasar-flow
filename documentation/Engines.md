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

    QueryEngine ..> QueryChain : query() starts a new chain
    QueryChain ..> QueryChain : where*/orderBy/limitResults return updated chains
    QueryChain ..> Query : clauses build on a Query
    QueryEngine --> CollectionReference : opens it via collectionName
    WriteEngine --> CollectionReference : opens it via collectionName
    QueryEngine --> FirebaseFirestore : provided instance
    WriteEngine --> FirebaseFirestore : provided instance
    QueryEngine ..> Identifiable : only schema types T implementing Identifiable
    WriteEngine ..> Identifiable : only schema types T implementing Identifiable
    WriteEngine ..> Result : onComplete receives success(null) or failure(Exception)

    note for QueryEngine "constructed inline via the extension function db.readEngineFor(collectionName)"
    note for WriteEngine "constructed inline via db.writeEngineFor(collectionName); documents are keyed on the Identifiable id"
    note for QueryChain "where*/orderBy take KProperty1 references, so the queried property is guaranteed to belong to T"
    note for Identifiable "instantiated with a random UUID; Firestore repopulates id from the DocumentId annotation on read"
```
