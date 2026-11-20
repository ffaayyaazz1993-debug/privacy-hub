package com.privacyhub.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.util.startOfDay
import com.privacyhub.app.util.startOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AnalyticsViewModel(repo: FocusMapRepository) : ViewModel() {
    val todaySeconds: StateFlow<Long> = repo.sessions.focusSecondsBetween(startOfDay(), Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val weekSeconds: StateFlow<Long> = repo.sessions.focusSecondsBetween(startOfWeek(), Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val totalCompleted: StateFlow<Int> = repo.sessions.completedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
