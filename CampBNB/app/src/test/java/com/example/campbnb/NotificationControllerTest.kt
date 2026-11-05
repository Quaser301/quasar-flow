package com.example.campbnb

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.OptOutChecker
import com.example.campbnb.models.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class NotificationControllerTest {
    private lateinit var repo: FakeNotificationRepository
    private lateinit var controller: NotificationController

    @Before
    fun setup() {
        repo = FakeNotificationRepository()
        controller = NotificationController(repo)
    }

    private fun <T> result(block: ((Result<T>) -> Unit) -> Unit): Result<T> {
        var captured: Result<T>? = null
        block { captured = it }
        return captured!!
    }

    @Test
    fun text_campsite_and_say_action_needed() {
        result<Int> { done -> controller.notifyWinners("lot1", "Banff", listOf("u1"), done) }
        val n = repo.saved.values.single()
        assertTrue(n.title.contains("Banff"))
        assertTrue(n.message.contains("Banff"))
        assertTrue(n.message.contains("Action needed"))
    }

    @Test
    fun duplicate_and_black_ids_ignored() {
        val r = result<Int> { done ->
            controller.notifyWinners("lot1", "Hinton", listOf("u1", "u1", " ", " "), done)
        }
        assertEquals(1, r.getOrNull())
        assertEquals(1, repo.saved.size)
    }

    @Test
    fun running_same_draw_twice() {
        val ids = listOf("u1", "u2")
        result<Int> { done -> controller.notifyWinners("lot1", "Jasper", ids, done) }
        result<Int> { done -> controller.notifyWinners("lot1", "Jasper", ids, done) }
        assertEquals(2, repo.saved.size)
        assertEquals(2, repo.saveCalls)
    }

    @Test
    fun opted_out_get_record_no_banner() {
        val checker = OptOutChecker { ids, done -> done(ids.filter { it != "u2" }) }
        controller = NotificationController(repo, checker)
        result<Int> { done -> controller.notifyWinners("lot1", "Banff", listOf("u1", "u2"), done) }
        assertTrue(repo.saved.values.first { it.recipientId == "u1" }.pushAllowed)
        assertFalse(repo.saved.values.first { it.recipientId == "u2" }.pushAllowed)
        assertEquals(2, repo.saved.size) // the record exists for both since admin log needs it
    }

    @Test
    fun blank_lottery_id_refused_nothing_saved() {
        val r = result<Int> {done -> controller.notifyWinners(" ", "X", listOf("u1"), done)}
        assertTrue(r.isFailure)
        assertEquals(0, repo.saveCalls)
    }

    @Test
    fun no_winners_is_succes_with_zero_saved() {
        val r = result<Int> { done -> controller.notifyWinners("lot1", "X", emptyList(), done)}
        assertEquals(0, r.getOrNull())
        assertEquals(0, repo.saveCalls)
    }

    @Test
    fun storage_failure_is_reported_not_hidden() {
        repo.failNextSave = true
        val r = result<Int> { done -> controller.notifyWinners("lot1", "X", listOf("u1"), done)}
        assertTrue(r.isFailure)
    }
}
