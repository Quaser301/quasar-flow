package com.example.campbnb

import com.example.campbnb.database.FirestoreWriteViewModel
import com.example.campbnb.database.WriteEngine
import com.example.campbnb.database.WriteState
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
class WriteViewTest {

    // since we need to deal with coroutines.
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockEngine: WriteEngine<TestUser> // because it doesn't override Identifiable
    private lateinit var view: FirestoreWriteViewModel<TestUser>


    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockEngine = mockk(relaxed = true)
        view = FirestoreWriteViewModel(mockEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_InitStateCorrect() {
        testStateIsX(WriteState.Idle)
    }

    // Note: the update and delete paths below carry their own state-machine coverage
    // now, so this test no longer has to stand in for them.
    @Test
    fun test_StateTransitionsOnWriteSuccess() {
        val item = TestUser("Tester", 18, false)

        val callbackSlot = slot<(Result<Exception?>) -> Unit>()
        every { mockEngine.save(eq(item), capture(callbackSlot)) } returns Unit

        val stateHistory = mutableListOf<WriteState>()
        val jobCollector = CoroutineScope(testDispatcher).launch {
            view.writeState.collect { stateHistory.add(it) }
        }

        view.create(item)
        testDispatcher.scheduler.advanceUntilIdle()

        callbackSlot.captured.invoke(Result.success(null))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, stateHistory.size)
        assertTrue("Expected First State to be Idle", stateHistory[0] is WriteState.Idle)
        assertTrue("Expected Second State to be Loading", stateHistory[1] is WriteState.Loading)
        assertTrue("Expected Third State to be Successful", stateHistory[2] is WriteState.Success)

        jobCollector.cancel()
    }

    @Test
    fun test_StateTransitionsOnWriteFailure() {
        val exception = Exception("Firestore Security issue :<")
        val item = TestUser("Tester", 18, false)

        val callbackSlot = slot<(Result<Exception?>) -> Unit>()
        every { mockEngine.save(eq(item), capture(callbackSlot)) } returns Unit

        val stateHistory = mutableListOf<WriteState>()
        val jobCollector = CoroutineScope(testDispatcher).launch {
            view.writeState.collect { stateHistory.add(it) }
        }

        view.create(item)
        testDispatcher.scheduler.advanceUntilIdle()

        callbackSlot.captured.invoke(Result.failure(exception))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, stateHistory.size)
        assertTrue("Expected First State to be Idle", stateHistory[0] is WriteState.Idle)
        assertTrue("Expected Second State to be Loading", stateHistory[1] is WriteState.Loading)
        assertTrue("Expected Third State to be Fault", stateHistory[2] is WriteState.Fault)

        assertEquals(exception, (stateHistory[2] as WriteState.Fault).exception)
        jobCollector.cancel()
    }

    // Adversarial additions: the update and delete paths get their own state-machine
    // coverage instead of relying on the create path standing in for them, and a
    // mashed pair of operations ends at the state the last one reached.
    @Test
    fun test_UpdateTransitionsThroughTheSameStates() {
        val item = TestUser("Tester", 18, false)
        val fields = mapOf("name" to "Renamed")

        val callbackSlot = slot<(Result<Exception?>) -> Unit>()
        every { mockEngine.update(any(), any(), capture(callbackSlot)) } returns Unit

        val stateHistory = mutableListOf<WriteState>()
        val jobCollector = CoroutineScope(testDispatcher).launch {
            view.writeState.collect { stateHistory.add(it) }
        }

        view.update(item, fields)
        testDispatcher.scheduler.advanceUntilIdle()

        callbackSlot.captured.invoke(Result.success(null))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, stateHistory.size)
        assertTrue("Expected First State to be Idle", stateHistory[0] is WriteState.Idle)
        assertTrue("Expected Second State to be Loading", stateHistory[1] is WriteState.Loading)
        assertTrue("Expected Third State to be Successful", stateHistory[2] is WriteState.Success)

        jobCollector.cancel()
    }

    @Test
    fun test_DeleteTransitionsThroughTheSameStates() {
        val item = TestUser("Tester", 18, false)

        val callbackSlot = slot<(Result<Exception?>) -> Unit>()
        every { mockEngine.delete(any(), capture(callbackSlot)) } returns Unit

        val stateHistory = mutableListOf<WriteState>()
        val jobCollector = CoroutineScope(testDispatcher).launch {
            view.writeState.collect { stateHistory.add(it) }
        }

        view.delete(item)
        testDispatcher.scheduler.advanceUntilIdle()

        callbackSlot.captured.invoke(Result.success(null))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, stateHistory.size)
        assertTrue("Expected First State to be Idle", stateHistory[0] is WriteState.Idle)
        assertTrue("Expected Second State to be Loading", stateHistory[1] is WriteState.Loading)
        assertTrue("Expected Third State to be Successful", stateHistory[2] is WriteState.Success)

        jobCollector.cancel()
    }

    @Test
    fun test_CreatingThenDeletingTheSameItemEndsAtSuccess() {
        val item = TestUser("Tester", 18, false)

        val saveSlot = slot<(Result<Exception?>) -> Unit>()
        every { mockEngine.save(eq(item), capture(saveSlot)) } returns Unit
        val deleteSlot = slot<(Result<Exception?>) -> Unit>()
        every { mockEngine.delete(any(), capture(deleteSlot)) } returns Unit

        view.create(item)
        testDispatcher.scheduler.advanceUntilIdle()
        saveSlot.captured.invoke(Result.success(null))
        testDispatcher.scheduler.advanceUntilIdle()

        view.delete(item)
        testDispatcher.scheduler.advanceUntilIdle()
        deleteSlot.captured.invoke(Result.success(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("the last mashed operation decides the state",
            view.writeState.value is WriteState.Success)
    }


    fun testStateIsX(state: WriteState) {
        assertEquals(state, view.writeState.value)
    }
}