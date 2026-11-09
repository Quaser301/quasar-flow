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

    @Before
    fun setup() {
        mockDb = mockk(relaxed = true)
        mockCollection = mockk(relaxed = true)
        mockQuery = mockk(relaxed = true)

        every { mockDb.collection("Users") } returns mockCollection
        every { mockCollection.whereEqualTo(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereEqualTo(any<String>(), any()) } returns mockQuery
        every { mockCollection.whereGreaterThan(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereGreaterThan(any<String>(), any()) } returns mockQuery
        every { mockCollection.whereLessThan(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereLessThan(any<String>(), any()) } returns mockQuery
        every { mockCollection.orderBy(any<String>(), any<Query.Direction>()) } returns mockQuery
        every { mockQuery.orderBy(any<String>(), any<Query.Direction>()) } returns mockQuery
        every { mockCollection.limit(any()) } returns mockQuery
        every { mockQuery.limit(any()) } returns mockQuery


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

        // Assert 1: The very first call hit the base collection reference
        verify(exactly = 1) { mockCollection.whereEqualTo("admin", true) }
        verify(exactly = 2) { mockQuery.whereEqualTo("admin", true) }
        verify(exactly = 2) { mockQuery.whereEqualTo("admin", false) }
    }

    @Test
    fun test_QueryManyOfSameQuery_Limit() {
        queryEngine.query()
            .limitResults(10)
            .limitResults(10)
            .limitResults(10)
            .limitResults(20)
            .limitResults(20)


        verify(exactly = 1) { mockCollection.limit(10) }
        verify(exactly = 2) { mockQuery.limit(10) }
        verify(exactly = 2) { mockQuery.limit(20) }
    }

    @Test
    fun test_QueryManyOfSameQuery_GreaterThan() {
        queryEngine.query()
            .whereGreaterThan(TestUser::age, 18)
            .whereGreaterThan(TestUser::age, 18)
            .whereGreaterThan(TestUser::age, 18)
            .whereGreaterThan(TestUser::age, 36)
            .whereGreaterThan(TestUser::age, 36)


        verify(exactly = 1) { mockCollection.whereGreaterThan("age", 18) }
        verify(exactly = 2) { mockQuery.whereGreaterThan("age", 18) }
        verify(exactly = 2) { mockQuery.whereGreaterThan("age", 36) }
    }

    @Test
    fun test_QueryManyOfSameQuery_LessThan() {
        queryEngine.query()
            .whereLessThan(TestUser::age, 18)
            .whereLessThan(TestUser::age, 18)
            .whereLessThan(TestUser::age, 18)
            .whereLessThan(TestUser::age, 36)
            .whereLessThan(TestUser::age, 36)


        verify(exactly = 1) { mockCollection.whereLessThan("age", 18) }
        verify(exactly = 2) { mockQuery.whereLessThan("age", 18) }
        verify(exactly = 2) { mockQuery.whereLessThan("age", 36) }
    }

    @Test
    fun test_QueryManyOfSameQuery_orderBy() {
        queryEngine.query()
            .orderBy(TestUser::name)
            .orderBy(TestUser::name)
            .orderBy(TestUser::name)
            .orderBy(TestUser::age)
            .orderBy(TestUser::age)

        verify(exactly = 1) { mockCollection.orderBy("name", Query.Direction.ASCENDING) }
        verify(exactly = 2) { mockQuery.orderBy("name", Query.Direction.ASCENDING) }
        verify(exactly = 2) { mockQuery.orderBy("age", Query.Direction.ASCENDING) }
    }

    @Test
    fun test_QueryAcceptsAlternateDirection() {
        queryEngine.query()
            .orderBy(TestUser::name)
            .orderBy(TestUser::name, Query.Direction.DESCENDING)
            .orderBy(TestUser::name)
            .orderBy(TestUser::age, Query.Direction.DESCENDING)
            .orderBy(TestUser::age)

        verify(exactly = 1) { mockCollection.orderBy("name", Query.Direction.ASCENDING) }
        verify(exactly = 1) { mockQuery.orderBy("name", Query.Direction.ASCENDING) }
        verify(exactly = 1) { mockQuery.orderBy("age", Query.Direction.ASCENDING) }
        verify(exactly = 1) { mockQuery.orderBy("name", Query.Direction.DESCENDING) }
        verify(exactly = 1) { mockQuery.orderBy("age", Query.Direction.DESCENDING) }
    }

    // Adversarial additions: no conditions, extreme limits, and a value the caller cannot control
    @Test
    fun test_AQueryWithNoConditions_StillRunsExactlyOnce() {
        queryEngine.query()
            .execute(onSuccess = {}, onFailure = {})

        verify(exactly = 1) { mockCollection.get() }
    }

    @Test
    fun test_LimitZeroAndCeiling_MapToFirestoreWithoutAdjustment() {
        queryEngine.query()
            .limitResults(0L)
            .execute(onSuccess = {}, onFailure = {})

        queryEngine.query()
            .limitResults(Int.MAX_VALUE.toLong())
            .execute(onSuccess = {}, onFailure = {})

        verify(exactly = 1) { mockCollection.limit(0) }
        verify(exactly = 1) { mockCollection.limit(2147483647) }
    }

    @Test
    fun test_WhereEqualsWithWeirdValue_PassesTheValueThroughUnchanged() {
        val odd = "Bob \"Zed\" ١٢ — ünquoted"

        queryEngine.query()
            .whereEquals(TestUser::name, odd)
            .execute(onSuccess = {}, onFailure = {})

        verify(exactly = 1) { mockCollection.whereEqualTo("name", odd) }
    }
}