package com.example.campbnb

import com.example.campbnb.database.Identifiable
import com.example.campbnb.database.QueryEngine
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

data class TestUser(
    val name: String,
    val age: Int,
    val admin: Boolean
): Identifiable()

class QueryEngineTest {

    private lateinit var mockDb: FirebaseFirestore
    private lateinit var mockCollection: CollectionReference
    private lateinit var mockQuery: Query
    private lateinit var queryEngine: QueryEngine<TestUser>

    @Before()
    fun setup() {
        mockDb = mockk(relaxed = true)
        mockCollection = mockk(relaxed = true)
        mockQuery = mockk(relaxed = true)

        every { mockDb.collection("Users") } returns mockCollection
        every { mockCollection.whereEqualTo(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereGreaterThan(any<String>(), any()) } returns mockQuery
        every { mockQuery.orderBy(any<String>(), any<Query.Direction>()) } returns mockQuery
        every { mockQuery.limit(any<Long>()) } returns mockQuery


        queryEngine = QueryEngine(mockDb, "Users", TestUser::class.java)
    }

    // note: this test is a little bare-bones since all we are testing is that we convert correctly
    // testing if Firestore works is left to the capable hands of Google.

    @Test
    fun test_QueryChainCorrectlyMapsPropertiesToFirestoreFields() {
        queryEngine.query()
            .whereEquals(TestUser::admin, true)
            .whereGreaterThan(TestUser::age, 18)
            .whereLessThan(TestUser::age, 36)
            .orderBy(TestUser::name)
            .limitResults(10)
            .execute(onSuccess = {}, onFailure = {})

        verify(exactly = 1) { mockCollection.whereEqualTo("admin", true) }
        verify(exactly = 1) { mockQuery.whereGreaterThan("age", 18) }
        verify(exactly = 1) { mockQuery.whereLessThan("age", 36) }
        verify(exactly = 1) { mockQuery.orderBy("name", Query.Direction.ASCENDING) }
        verify(exactly = 1) { mockQuery.limit(10) }
        verify(exactly = 1) { mockQuery.get() }
    }

    @Test
    fun test_QueryManyOfSameQuery_WhereEquals() {
        queryEngine.query()
            .whereEquals(TestUser::admin, true)
            .whereEquals(TestUser::admin, true)
            .whereEquals(TestUser::admin, true)
            .whereEquals(TestUser::admin, false)
            .whereEquals(TestUser::admin, false)


        verify(exactly = 3) { mockCollection.whereEqualTo("admin", true) }
        verify(exactly = 2) { mockCollection.whereEqualTo("admin", false) }
    }

    @Test
    fun test_QueryManyOfSameQuery_Limit() {
        queryEngine.query()
            .limitResults(10)
            .limitResults(10)
            .limitResults(10)
            .limitResults(20)
            .limitResults(20)



        verify(exactly = 3) { mockCollection.limit(10) }
        verify(exactly = 2) { mockCollection.limit(20) }
    }

    @Test
    fun test_QueryManyOfSameQuery_GreaterThan() {
        queryEngine.query()
            .whereGreaterThan(TestUser::age, 18)
            .whereGreaterThan(TestUser::age, 18)
            .whereGreaterThan(TestUser::age, 18)
            .whereGreaterThan(TestUser::age, 36)
            .whereGreaterThan(TestUser::age, 36)


        verify(exactly = 3) { mockCollection.whereGreaterThan("age", 18) }
        verify(exactly = 2) { mockCollection.whereGreaterThan("age", 36) }
    }

    @Test
    fun test_QueryManyOfSameQuery_LessThan() {
        queryEngine.query()
            .whereLessThan(TestUser::age, 18)
            .whereLessThan(TestUser::age, 18)
            .whereLessThan(TestUser::age, 18)
            .whereLessThan(TestUser::age, 36)
            .whereLessThan(TestUser::age, 36)


        verify(exactly = 3) { mockCollection.whereLessThan("age", 18) }
        verify(exactly = 2) { mockCollection.whereLessThan("age", 36) }
    }

    @Test
    fun test_QueryManyOfSameQuery_orderBy() {
        queryEngine.query()
            .orderBy(TestUser::name)
            .orderBy(TestUser::name)
            .orderBy(TestUser::name)
            .orderBy(TestUser::age)
            .orderBy(TestUser::age)


        verify(exactly = 3) { mockCollection.orderBy("name", Query.Direction.ASCENDING) }
        verify(exactly = 2) { mockCollection.orderBy("age", Query.Direction.ASCENDING) }
    }

    @Test
    fun test_QueryAcceptsAlternateDirection() {
        queryEngine.query()
            .orderBy(TestUser::name)
            .orderBy(TestUser::name, Query.Direction.DESCENDING)
            .orderBy(TestUser::name)
            .orderBy(TestUser::age, Query.Direction.DESCENDING)
            .orderBy(TestUser::age)


        verify(exactly = 2) { mockCollection.orderBy("name", Query.Direction.ASCENDING) }
        verify(exactly = 1) { mockCollection.orderBy("age", Query.Direction.ASCENDING) }
        verify(exactly = 1) { mockCollection.orderBy("name", Query.Direction.DESCENDING) }
        verify(exactly = 1) { mockCollection.orderBy("age", Query.Direction.DESCENDING) }
    }
}