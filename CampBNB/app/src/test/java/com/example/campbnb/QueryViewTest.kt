package com.example.campbnb

import com.example.campbnb.database.FirestoreQueryViewModel
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.database.QueryState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueryViewTest {

    // since we need to deal with coroutines.
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockEngine: QueryEngine<TestUser>
    private lateinit var mockChain: QueryEngine.QueryChain<TestUser>

    private lateinit var view: FirestoreQueryViewModel<TestUser>


    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockEngine = mockk(relaxed = true)
        mockChain = mockk(relaxed = true)


        every { mockEngine.query() } returns mockChain

        view = FirestoreQueryViewModel(mockEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_InitStateCorrect() {
        testStateIsX(QueryState.NoQuery)
    }


    @Test
    fun test_StateTransitionsOnViewSuccess() {
        val data = listOf(TestUser("Bob", 2, false), TestUser("Alice", 4, true))

        val resultSlot = slot<(List<TestUser>) -> Unit>()
        every {
            mockChain.execute(
                capture(resultSlot),
                any())
        } returns Unit

        val stateHistory = mutableListOf<QueryState<TestUser>>()
        val jobCollector = CoroutineScope(testDispatcher).launch {
            view.queryState.collect { stateHistory.add(it) }
        }

        view.fetchAll()
        testDispatcher.scheduler.advanceUntilIdle()

        resultSlot.captured.invoke(data)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, stateHistory.size)
        assertTrue("Expected First State to be Idle", stateHistory[0] is QueryState.NoQuery)
        assertTrue("Expected Second State to be Loading", stateHistory[1] is QueryState.Loading)
        assertTrue("Expected Third State to be Successful", stateHistory[2] is QueryState.Success)

        assertEquals(data, (stateHistory[2] as QueryState.Success).data)
        jobCollector.cancel()
    }

    @Test
    fun test_StateTransitionsOnViewFailure() {
        val exception = Exception("Goblins in the Wires Again")

        val resultSlot = slot<(Exception) -> Unit>()
        every {
            mockChain.execute(
                any(),
                capture(resultSlot)
            )
        } returns Unit

        val stateHistory = mutableListOf<QueryState<TestUser>>()
        val jobCollector = CoroutineScope(testDispatcher).launch {
            view.queryState.collect { stateHistory.add(it) }
        }

        view.fetchAll()
        testDispatcher.scheduler.advanceUntilIdle()

        resultSlot.captured.invoke(exception)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, stateHistory.size)
        assertTrue("Expected First State to be Idle", stateHistory[0] is QueryState.NoQuery)
        assertTrue("Expected Second State to be Loading", stateHistory[1] is QueryState.Loading)
        assertTrue("Expected Third State to be Fault", stateHistory[2] is QueryState.Fault)
        assertEquals(exception, (stateHistory[2] as QueryState.Fault).exception)
        jobCollector.cancel()
    }

    // Adversarial additions: the smallest result the success path can carry, and a good
    // fetch following a faulted one
    @Test
    fun test_EmptyResultSetIsSuccessWithNothingToShow() {
        val resultSlot = slot<(List<TestUser>) -> Unit>()
        every {
            mockChain.execute(
                capture(resultSlot),
                any())
        } returns Unit

        val stateHistory = mutableListOf<QueryState<TestUser>>()
        val jobCollector = CoroutineScope(testDispatcher).launch {
            view.queryState.collect { stateHistory.add(it) }
        }

        view.fetchAll()
        testDispatcher.scheduler.advanceUntilIdle()

        resultSlot.captured.invoke(emptyList<TestUser>())

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, stateHistory.size)
        assertTrue("Expected Third State to be Successful", stateHistory[2] is QueryState.Success)
        assertEquals(emptyList<TestUser>(), (stateHistory[2] as QueryState.Success).data)
        jobCollector.cancel()
    }

    @Test
    fun test_AFaultedFetchThenASuccessfulOneEndsShowingData() {
        val exception = Exception("First attempt failed")
        val failSlot = slot<(Exception) -> Unit>()
        every {
            mockChain.execute(
                any(),
                capture(failSlot)
            )
        } returns Unit

        view.fetchAll()
        testDispatcher.scheduler.advanceUntilIdle()

        failSlot.captured.invoke(exception)

        testDispatcher.scheduler.advanceUntilIdle()

        val data = listOf(TestUser("Zed", 1, true))
        val okSlot = slot<(List<TestUser>) -> Unit>()
        every {
            mockChain.execute(
                capture(okSlot),
                any())
        } returns Unit

        view.fetchAll()
        testDispatcher.scheduler.advanceUntilIdle()

        okSlot.captured.invoke(data)

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("a good fetch after a fault leaves the view showing data",
            view.queryState.value is QueryState.Success)
        assertEquals(data, (view.queryState.value as QueryState.Success).data)
    }

    fun testStateIsX(state: QueryState<TestUser>) {
        assertEquals(state, view.queryState.value)
    }
}