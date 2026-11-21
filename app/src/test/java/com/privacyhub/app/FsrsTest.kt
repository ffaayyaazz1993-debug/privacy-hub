package com.privacyhub.app

import com.privacyhub.app.domain.spaced.Fsrs
import com.privacyhub.app.domain.spaced.MemoryState
import com.privacyhub.app.domain.spaced.RecallRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    private val fsrs = Fsrs()

    @Test fun `identity retrievability at stability is desired retention`() {
        assertTrue(kotlin.math.abs(fsrs.retrievability(10.0, 10.0) - 0.9) < 0.0001)
    }

    @Test fun `interval at stability equals stability for default retention`() {
        assertEquals(10, fsrs.nextInterval(10.0))
        assertEquals(30, fsrs.nextInterval(30.0))
    }

    @Test fun `initial stability and difficulty order ratings`() {
        val s = RecallRating.entries.map { fsrs.initialStability(it) }
        assertTrue(s[0] < s[1] && s[1] < s[2] && s[2] < s[3])
        val d = RecallRating.entries.map { fsrs.initialDifficulty(it) }
        assertTrue(d[0] > d[1] && d[1] > d[2] && d[3] < d[2])
        assertTrue(d.all { it in 1.0..10.0 })
    }

    @Test fun `lapse shrinks stability and easy beats good`() {
        val m = MemoryState(10.0, 5.0)
        val again = fsrs.review(m, RecallRating.AGAIN, 1.0)
        val good = fsrs.review(m, RecallRating.GOOD, 1.0)
        val easy = fsrs.review(m, RecallRating.EASY, 1.0)
        assertTrue(again.memory.stability < m.stability)
        assertTrue(easy.memory.stability > good.memory.stability)
        assertTrue(easy.memory.difficulty < good.memory.difficulty)
    }

    @Test fun `repeated successful reviews grow stability`() {
        var m: MemoryState? = null
        var last = 0.0
        repeat(6) {
            val r = fsrs.review(m, RecallRating.GOOD, if (m == null) 0.0 else 1.0)
            m = r.memory; last = r.memory.stability
        }
        assertTrue(last > fsrs.initialStability(RecallRating.GOOD))
        assertEquals(6, 6)
    }

    @Test fun `higher desired retention shortens interval`() {
        val strict = Fsrs(desiredRetention = 0.98)
        val loose = Fsrs(desiredRetention = 0.85)
        assertTrue(strict.nextInterval(20.0) < loose.nextInterval(20.0))
    }

    @Test fun `maximum interval is respected`() {
        val capped = Fsrs(maximumInterval = 30)
        assertTrue(capped.nextInterval(5000.0) <= 30)
    }

    @Test fun `delayed review changes memory update`() {
        val m = MemoryState(5.0, 5.0)
        val immediate = fsrs.review(m, RecallRating.GOOD, 0.0)
        val delayed = fsrs.review(m, RecallRating.GOOD, 12.0)
        assertTrue(delayed.memory.stability > immediate.memory.stability)
        assertTrue(delayed.retrievability < immediate.retrievability)
    }

    @Test fun `zero stability short term update stays finite`() {
        RecallRating.entries.forEach { r ->
            val s = fsrs.shortTermStability(0.0, r)
            assertTrue(s.isFinite() && s > 0.0)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bad weight count rejected`() { Fsrs(w = DoubleArray(3)) }
}
