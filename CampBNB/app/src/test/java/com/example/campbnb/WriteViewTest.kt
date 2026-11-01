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

    // Note: this test is ok for all IF and ONLY IF the other 2 methods (Update, Delete) all use the same
    // state handling code.
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


    fun testStateIsX(state: WriteState) {
        assertEquals(state, view.writeState.value)
    }
}