package com.privacyhub.app.ui.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.UserSettingsEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.domain.pomodoro.FocusSessionManager
import com.privacyhub.app.domain.pomodoro.Pomodoro
import com.privacyhub.app.service.FocusSessionService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusViewModel(private val repo: FocusMapRepository) : ViewModel() {
    private val app get() = FocusMapApplication.instance
    private val manager = FocusSessionManager(repo)

    val settings: StateFlow<UserSettingsEntity> = repo.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettingsEntity())
    val suggestedNode: StateFlow<NodeEntity?> = repo.nodes.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val active: StateFlow<FocusSessionEntity?> = repo.sessions.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val ticker: Flow<Long> = flow {
        while (currentCoroutineContext().isActive) { emit(System.currentTimeMillis()); delay(500) }
    }
    val remaining: StateFlow<Long> = combine(active, ticker) { s, now -> s?.let { Pomodoro.remainingSeconds(it, now) } ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val progress: StateFlow<Float> = combine(active, ticker) { s, now -> s?.let { Pomodoro.progress(it, now) } ?: 0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeProject: StateFlow<ProjectEntity?> = active.flatMapLatest { s ->
        if (s != null) repo.projects.observe(s.projectId) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeNode: StateFlow<NodeEntity?> = active.flatMapLatest { s ->
        if (s != null) repo.nodes.observe(s.nodeId) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startFocus(projectId: Long, nodeId: Long) {
        viewModelScope.launch {
            val settings = repo.settings.get() ?: com.privacyhub.app.data.db.UserSettingsEntity()
            manager.start(projectId, nodeId, SessionType.FOCUS, settings.focusDurationSeconds)
            FocusSessionService.start(app)
        }
    }
    fun pause() { FocusSessionService.send(app, FocusSessionService.ACTION_PAUSE) }
    fun resume() { FocusSessionService.send(app, FocusSessionService.ACTION_RESUME) }
    fun restart() { FocusSessionService.send(app, FocusSessionService.ACTION_RESTART) }
    fun skip() { FocusSessionService.send(app, FocusSessionService.ACTION_SKIP) }
    fun complete() { FocusSessionService.send(app, FocusSessionService.ACTION_COMPLETE) }
    fun stop() { FocusSessionService.send(app, FocusSessionService.ACTION_STOP) }

    val latestCompletedFocus: StateFlow<FocusSessionEntity?> = repo.sessions.latestCompletedFocus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    var reviewDismissedId by mutableStateOf<Long?>(null)
    fun dismissReview(id: Long) { reviewDismissedId = id }
    fun saveReview(sessionId: Long, text: String, understood: Boolean?, revision: Boolean) {
        viewModelScope.launch {
            val s = repo.sessions.get(sessionId) ?: return@launch
            repo.sessions.update(s.copy(review = text, understood = understood))
            if (revision) repo.nodes.get(s.nodeId)?.let { repo.nodes.update(it.copy(status = NodeStatus.NEEDS_REVISION)) }
            reviewDismissedId = sessionId
        }
    }

    fun startSuggested() {
        val n = suggestedNode.value ?: return
        startFocus(n.projectId, n.id)
    }
    fun toggleNodeDone(node: NodeEntity) {
        viewModelScope.launch {
            val next = if (node.status == NodeStatus.COMPLETED) NodeStatus.IN_PROGRESS else NodeStatus.COMPLETED
            repo.nodes.update(node.copy(status = next, updatedAt = System.currentTimeMillis()))
        }
    }
    fun hasActiveSession(): Boolean = active.value?.status.let { it == SessionStatus.RUNNING || it == SessionStatus.PAUSED }

    fun updateNotes(text: String) {
        val s = active.value ?: return
        viewModelScope.launch { repo.sessions.update(s.copy(notes = text)) }
    }
}
