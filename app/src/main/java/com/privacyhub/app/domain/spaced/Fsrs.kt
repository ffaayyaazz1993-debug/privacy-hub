package com.privacyhub.app.domain.spaced

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Ratings on the FSRS review scale. */
enum class RecallRating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);
    companion object { fun of(v: Int): RecallRating = entries.first { it.value == v } }
}

/** The two latent memory variables FSRS-6 tracks per card. */
data class MemoryState(val stability: Double, val difficulty: Double)

data class FsrsResult(val memory: MemoryState, val intervalDays: Int, val retrievability: Double)

/**
 * Pure, dependency-free FSRS-6 core. Formulas and the 21 default weights follow the
 * maintained open-spaced-repetition/py-fsrs reference (fsrs/scheduler.py):
 * R(t,S) = (1 + FACTOR*t/S)^DECAY, DECAY = -w[20], FACTOR = 0.9^(1/DECAY)-1.
 * No clock, no I/O, no Android dependency. Intervals are whole days.
 */
class Fsrs(
    private val w: DoubleArray = DEFAULT_W.copyOf(),
    val desiredRetention: Double = 0.9,
    val maximumInterval: Int = 36500,
) {
    init {
        require(w.size == 21) { "FSRS-6 needs 21 weights" }
        require(desiredRetention in 0.01..0.9999) { "desiredRetention out of range" }
        require(maximumInterval >= 1)
    }

    private val decay: Double = -w[20]
    private val factor: Double = 0.9.pow(1.0 / decay) - 1.0

    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val t = max(0.0, elapsedDays)
        return (1.0 + factor * t / stability).pow(decay)
    }

    fun initialStability(rating: RecallRating): Double = clampStability(w[rating.value - 1])

    fun initialDifficulty(rating: RecallRating, clamp: Boolean = true): Double {
        val d = w[4] - exp(w[5] * (rating.value - 1)) + 1.0
        return if (clamp) clampDifficulty(d) else d
    }

    fun nextInterval(stability: Double): Int {
        val days = stability / factor * (desiredRetention.pow(1.0 / decay) - 1.0)
        return days.roundToInt().coerceIn(1, maximumInterval)
    }

    private fun clampDifficulty(d: Double) = d.coerceIn(1.0, 10.0)
    private fun clampStability(s: Double) = max(0.001, s)

    private fun nextDifficulty(difficulty: Double, rating: RecallRating): Double {
        val linearDamping = { delta: Double -> (10.0 - difficulty) * delta / 9.0 }
        val meanReversion = { arg1: Double, arg2: Double -> w[7] * arg1 + (1.0 - w[7]) * arg2 }
        val arg1 = initialDifficulty(RecallRating.EASY, clamp = false)
        val arg2 = difficulty + linearDamping(-w[6] * (rating.value - 3))
        return clampDifficulty(meanReversion(arg1, arg2))
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, r: Double): Double {
        val longTerm = w[11] * difficulty.pow(-w[12]) * (((stability + 1.0).pow(w[13])) - 1.0) *
            exp((1.0 - r) * w[14])
        val shortTerm = stability / exp(w[17] * w[18])
        return minOf(longTerm, shortTerm)
    }

    private fun nextRecallStability(
        difficulty: Double, stability: Double, r: Double, rating: RecallRating,
    ): Double {
        val hardPenalty = if (rating == RecallRating.HARD) w[15] else 1.0
        val easyBonus = if (rating == RecallRating.EASY) w[16] else 1.0
        return stability * (1.0 + exp(w[8]) * (11.0 - difficulty) * stability.pow(-w[9]) *
            (exp((1.0 - r) * w[10]) - 1.0) * hardPenalty * easyBonus)
    }

    private fun nextStability(
        difficulty: Double, stability: Double, r: Double, rating: RecallRating,
    ): Double = clampStability(
        if (rating == RecallRating.AGAIN) nextForgetStability(difficulty, stability, r)
        else nextRecallStability(difficulty, stability, r, rating)
    )

    /** Same-day stability update, used by learning steps. */
    fun shortTermStability(stability: Double, rating: RecallRating): Double {
        val base = max(stability, 0.001)
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * base.pow(-w[19])
        if (rating != RecallRating.AGAIN) increase = max(1.0, increase)
        return clampStability(base * increase)
    }

    fun review(memory: MemoryState?, rating: RecallRating, elapsedDays: Double = 0.0): FsrsResult {
        if (memory == null) {
            val m = MemoryState(initialStability(rating), initialDifficulty(rating))
            return FsrsResult(m, nextInterval(m.stability), 1.0)
        }
        val r = retrievability(elapsedDays, memory.stability)
        val d = nextDifficulty(memory.difficulty, rating)
        val s = nextStability(memory.difficulty, memory.stability, r, rating)
        return FsrsResult(MemoryState(s, d), nextInterval(s), r)
    }

    companion object {
        /** FSRS-6 published default parameters (py-fsrs, decay = w[20]). */
        val DEFAULT_W = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )
    }
}
