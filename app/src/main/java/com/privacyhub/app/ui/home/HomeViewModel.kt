package com.privacyhub.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.util.startOfDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repo: FocusMapRepository) : ViewModel() {
    val recentProjects: StateFlow<List<ProjectEntity>> =
        repo.projects.observeAll().map { it.take(5) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todaySeconds: StateFlow<Long> =
        repo.sessions.focusSecondsBetween(startOfDay(), Long.MAX_VALUE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val todaySessions: StateFlow<Int> =
        repo.sessions.completedFocusBetween(startOfDay(), Long.MAX_VALUE).map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val activeSession: StateFlow<FocusSessionEntity?> =
        repo.sessions.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
