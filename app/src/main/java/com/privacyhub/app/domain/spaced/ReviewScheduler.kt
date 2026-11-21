package com.privacyhub.app.domain.spaced

/** Learning state persisted on a card. Distinct from Pomodoro/node status. */
enum class LearnState { NEW, LEARNING, REVIEW, RELEARNING;
    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: NEW } }

data class CardSchedule(
    val state: LearnState,
    val step: Int,
    val stability: Double,
    val difficulty: Double,
    val dueAt: Long,
    val intervalDays: Int,
    val retrievability: Double,
    val lastReviewAt: Long,
    val repetitions: Int,
    val lapses: Int,
)

/**
 * Applies FSRS-6 after learning/relearning steps. All time is epoch milliseconds,
 * so daylight-saving and timezone changes cannot shift a due date; delayed reviews
 * use the real elapsed time instead of the originally planned interval.
 */
object ReviewScheduler {
    const val VERSION = 1
    const val DAY_MS = 86_400_000L
    private val LEARNING_MINUTES = intArrayOf(1, 10)
    private val RELEARNING_MINUTES = intArrayOf(10)

    private fun minutes(m: Int) = m.toLong() * 60_000L

    fun schedule(
        now: Long,
        lastReviewAt: Long,
        stateName: String,
        step: Int,
        stability: Double,
        difficulty: Double,
        repetitions: Int,
        lapses: Int,
        rating: RecallRating,
        desiredRetention: Double,
        maximumInterval: Int,
    ): CardSchedule {
        val fsrs = Fsrs(desiredRetention = desiredRetention, maximumInterval = maximumInterval)
        val state = LearnState.of(stateName)
        val elapsedDays = if (lastReviewAt <= 0L) 0.0 else
            (now - lastReviewAt).toDouble() / DAY_MS
        val reps = repetitions + 1
        val lapsesNext = lapses + if (rating == RecallRating.AGAIN) 1 else 0

        if (state == LearnState.NEW || state == LearnState.LEARNING) {
            val baseStability = if (stability > 0.0) stability else fsrs.initialStability(rating)
            val baseDifficulty = if (difficulty > 0.0) difficulty else fsrs.initialDifficulty(rating)
            if (state == LearnState.NEW && rating == RecallRating.EASY) {
                val r = fsrs.review(null, RecallRating.EASY)
                return CardSchedule(LearnState.REVIEW, 0, r.memory.stability, r.memory.difficulty,
                    now + r.intervalDays * DAY_MS, r.intervalDays, 1.0, now, reps, lapsesNext)
            }
            if (rating == RecallRating.AGAIN) {
                val s = if (state == LearnState.NEW) fsrs.initialStability(RecallRating.AGAIN)
                        else fsrs.shortTermStability(baseStability, RecallRating.AGAIN)
                val d = if (state == LearnState.NEW) fsrs.initialDifficulty(RecallRating.AGAIN)
                        else baseDifficulty
                return CardSchedule(LearnState.LEARNING, 0, s, d, now + minutes(LEARNING_MINUTES[0]),
                    0, 1.0, now, reps, lapsesNext)
            }
            val nextStep = when (rating) {
                RecallRating.HARD -> step
                RecallRating.GOOD -> step + 1
                RecallRating.EASY -> LEARNING_MINUTES.size
                RecallRating.AGAIN -> 0
            }
            if (nextStep >= LEARNING_MINUTES.size) {
                val r = fsrs.review(MemoryState(baseStability, baseDifficulty), rating, 0.0)
                return CardSchedule(LearnState.REVIEW, 0, r.memory.stability, r.memory.difficulty,
                    now + r.intervalDays * DAY_MS, r.intervalDays, r.retrievability, now, reps, lapsesNext)
            }
            val s = fsrs.shortTermStability(baseStability, rating)
            return CardSchedule(LearnState.LEARNING, nextStep, s, baseDifficulty,
                now + minutes(LEARNING_MINUTES[nextStep]), 0, 1.0, now, reps, lapsesNext)
        }

        val r = fsrs.review(MemoryState(stability, difficulty), rating, elapsedDays)
        if (state == LearnState.RELEARNING || (state == LearnState.REVIEW && rating == RecallRating.AGAIN)) {
            if (rating != RecallRating.EASY) {
                val nextStep = when (rating) {
                    RecallRating.AGAIN -> 0
                    RecallRating.HARD -> step
                    RecallRating.GOOD -> step + 1
                    RecallRating.EASY -> RELEARNING_MINUTES.size
                }
                if (nextStep < RELEARNING_MINUTES.size) {
                    return CardSchedule(LearnState.RELEARNING, nextStep, r.memory.stability,
                        r.memory.difficulty, now + minutes(RELEARNING_MINUTES[nextStep]),
                        0, r.retrievability, now, reps, lapsesNext)
                }
            }
        }
        return CardSchedule(LearnState.REVIEW, 0, r.memory.stability, r.memory.difficulty,
            now + r.intervalDays * DAY_MS, r.intervalDays, r.retrievability, now, reps, lapsesNext)
    }

    /** Non-mutating preview so the UI can show the interval before the user confirms. */
    fun preview(
        now: Long, lastReviewAt: Long, stateName: String, step: Int,
        stability: Double, difficulty: Double, repetitions: Int, lapses: Int,
        rating: RecallRating, desiredRetention: Double, maximumInterval: Int,
    ): Pair<Long, Int> = schedule(now, lastReviewAt, stateName, step, stability, difficulty,
        repetitions, lapses, rating, desiredRetention, maximumInterval).let { it.dueAt to it.intervalDays }
}
