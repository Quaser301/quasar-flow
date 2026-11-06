package com.example.campbnb.notificationTests

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.OptOutChecker
import org.junit.Assert
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
        Assert.assertTrue(n.title.contains("Banff"))
        Assert.assertTrue(n.message.contains("Banff"))
        Assert.assertTrue(n.message.contains("Action needed"))
    }

    @Test
    fun duplicate_and_black_ids_ignored() {
        val r = result<Int> { done ->
            controller.notifyWinners("lot1", "Hinton", listOf("u1", "u1", " ", " "), done)
        }
        Assert.assertEquals(1, r.getOrNull())
        Assert.assertEquals(1, repo.saved.size)
    }

    @Test
    fun running_same_draw_twice() {
        val ids = listOf("u1", "u2")
        result<Int> { done -> controller.notifyWinners("lot1", "Jasper", ids, done) }
        result<Int> { done -> controller.notifyWinners("lot1", "Jasper", ids, done) }
        Assert.assertEquals(2, repo.saved.size)
        Assert.assertEquals(2, repo.saveCalls)
    }

    @Test
    fun opted_out_get_record_no_banner() {
        val checker = OptOutChecker { ids, done -> done(ids.filter { it != "u2" }) }
        controller = NotificationController(repo, checker)
        result<Int> { done -> controller.notifyWinners("lot1", "Banff", listOf("u1", "u2"), done) }
        Assert.assertTrue(repo.saved.values.first { it.recipientId == "u1" }.pushAllowed)
        Assert.assertFalse(repo.saved.values.first { it.recipientId == "u2" }.pushAllowed)
        Assert.assertEquals(
            2,
            repo.saved.size
        ) // the record exists for both since admin log needs it
    }

    @Test
    fun blank_lottery_id_refused_nothing_saved() {
        val r = result<Int> {done -> controller.notifyWinners(" ", "X", listOf("u1"), done)}
        Assert.assertTrue(r.isFailure)
        Assert.assertEquals(0, repo.saveCalls)
    }

    @Test
    fun no_winners_is_succes_with_zero_saved() {
        val r = result<Int> { done -> controller.notifyWinners("lot1", "X", emptyList(), done)}
        Assert.assertEquals(0, r.getOrNull())
        Assert.assertEquals(0, repo.saveCalls)
    }

    @Test
    fun storage_failure_is_reported_not_hidden() {
        repo.failNextSave = true
        val r = result<Int> { done -> controller.notifyWinners("lot1", "X", listOf("u1"), done)}
        Assert.assertTrue(r.isFailure)
    }
}