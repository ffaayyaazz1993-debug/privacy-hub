package com.privacyhub.app

import com.privacyhub.app.domain.spaced.LearnState
import com.privacyhub.app.domain.spaced.RecallRating
import com.privacyhub.app.domain.spaced.ReviewScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ReviewSchedulerTest {
    private val now = 1_800_000_000_000L
    private fun run(state: String, rating: RecallRating, last: Long = 0L, step: Int = 0, s: Double = 0.0, d: Double = 0.0) =
        ReviewScheduler.schedule(now, last, state, step, s, d, 0, 0, rating, 0.9, 36500)

    @Test fun `new card again goes to one minute learning`() {
        val r = run("NEW", RecallRating.AGAIN)
        assertEquals(LearnState.LEARNING, r.state)
        assertEquals(0, r.step)
        assertEquals(60_000L, r.dueAt - now)
    }

    @Test fun `new card good advances learning step`() {
        val r = run("LEARNING", RecallRating.GOOD, step = 0, s = 1.0, d = 5.0)
        assertEquals(LearnState.LEARNING, r.state)
        assertEquals(1, r.step)
        assertEquals(600_000L, r.dueAt - now)
    }

    @Test fun `easy graduates a new card to review`() {
        val r = run("NEW", RecallRating.EASY)
        assertEquals(LearnState.REVIEW, r.state)
        assertTrue(r.intervalDays >= 1)
    }

    @Test fun `forgotten review card enters relearning with a lapse`() {
        val r = run("REVIEW", RecallRating.AGAIN, last = now - 5 * ReviewScheduler.DAY_MS, s = 10.0, d = 5.0)
        assertEquals(LearnState.RELEARNING, r.state)
        assertEquals(1, r.lapses)
        assertEquals(600_000L, r.dueAt - now)
    }

    @Test fun `due dates are timezone independent`() {
        val original = TimeZone.getDefault()
        val a = run("NEW", RecallRating.GOOD).dueAt
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            assertEquals(a, run("NEW", RecallRating.GOOD).dueAt)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(a, run("NEW", RecallRating.GOOD).dueAt)
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun `missed review schedules from actual review time`() {
        val oldDue = now - 30 * ReviewScheduler.DAY_MS
        val r = run("REVIEW", RecallRating.GOOD, last = now - 40 * ReviewScheduler.DAY_MS, s = 12.0, d = 4.0)
        assertTrue(r.dueAt > now)
        assertTrue(r.intervalDays >= 1)
        assertTrue(oldDue < now)
    }

    @Test fun `preview matches schedule without mutation`() {
        val p = ReviewScheduler.preview(now, now - ReviewScheduler.DAY_MS, "REVIEW", 0, 8.0, 4.0,
            3, 1, RecallRating.GOOD, 0.9, 36500)
        val s = ReviewScheduler.schedule(now, now - ReviewScheduler.DAY_MS, "REVIEW", 0, 8.0, 4.0,
            3, 1, RecallRating.GOOD, 0.9, 36500)
        assertEquals(s.dueAt, p.first)
        assertEquals(s.intervalDays, p.second)
        assertEquals(1, ReviewScheduler.VERSION)
    }
}
