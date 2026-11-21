package com.privacyhub.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.db.FlashcardEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ReviewLogEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.domain.spaced.RecallRating
import com.privacyhub.app.domain.spaced.ReviewScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(private val repo: FocusMapRepository) : ViewModel() {
    val now = MutableStateFlow(System.currentTimeMillis())
    val cards = repo.cards.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val due = now.flatMapLatest { repo.cards.observeDue(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = repo.reviewLogs.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = repo.observeSchedulerSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
        com.privacyhub.app.data.db.SchedulerSettingsEntity())
    val projects = repo.projects.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val nodes = repo.nodes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() { now.value = System.currentTimeMillis() }

    fun dueFor(nodeId: Long? = null, projectId: Long? = null, overdueOnly: Boolean = false, newOnly: Boolean = false): List<FlashcardEntity> {
        val t = now.value
        return due.value.filter { c ->
            (nodeId == null || c.nodeId == nodeId) &&
            (projectId == null || c.projectId == projectId) &&
            (!overdueOnly || c.dueTimestamp < t) &&
            (!newOnly || c.lastReviewTimestamp == 0L)
        }
    }

    fun preview(card: FlashcardEntity, rating: RecallRating): Pair<Long, Int> {
        val s = settings.value
        return ReviewScheduler.preview(now.value, card.lastReviewTimestamp, card.learningState,
            card.learningStep, card.stability, card.difficulty, card.repetitions, card.lapses,
            rating, s.desiredRetention, s.maximumInterval)
    }

    fun rate(card: FlashcardEntity, rating: RecallRating, startedAt: Long) = viewModelScope.launch {
        repo.submitReview(card, rating, System.currentTimeMillis(),
            ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceIn(0, 3600))
        refresh()
    }

    fun create(projectId: Long, nodeId: Long, q: String, a: String, hint: String, explanation: String) =
        viewModelScope.launch { repo.createCard(projectId, nodeId, q, a, hint, explanation); refresh() }
    fun update(card: FlashcardEntity) = viewModelScope.launch { repo.cards.update(card.copy(updatedAt = System.currentTimeMillis())); refresh() }
    fun delete(card: FlashcardEntity) = viewModelScope.launch { repo.cards.delete(card) }
    fun duplicate(card: FlashcardEntity) = viewModelScope.launch { repo.duplicateCard(card.id) }
    fun archive(card: FlashcardEntity) = viewModelScope.launch { repo.cards.archive(card.id, System.currentTimeMillis()) }
    fun saveSettings(s: com.privacyhub.app.data.db.SchedulerSettingsEntity) = viewModelScope.launch { repo.schedulerSettings.upsert(s) }
}
