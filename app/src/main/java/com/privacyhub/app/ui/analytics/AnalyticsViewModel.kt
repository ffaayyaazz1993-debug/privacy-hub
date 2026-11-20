package com.privacyhub.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.util.startOfDay
import com.privacyhub.app.util.startOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DayFocus(val label: String, val seconds: Long)
data class ProjectFocus(val project: ProjectEntity, val seconds: Long, val sessions: Int)
data class HistoryItem(val session: FocusSessionEntity, val nodeTitle: String, val projectTitle: String)

class AnalyticsViewModel(repo: FocusMapRepository) : ViewModel() {
    val todaySeconds: StateFlow<Long> = repo.sessions.focusSecondsBetween(startOfDay(), Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val weekSeconds: StateFlow<Long> = repo.sessions.focusSecondsBetween(startOfWeek(), Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val totalCompleted: StateFlow<Int> = repo.sessions.completedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val sessions = repo.sessions.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val projects = repo.projects.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val nodes = repo.nodes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daily: StateFlow<List<DayFocus>> = sessions.map { list ->
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        (6 downTo 0).map { i ->
            val dayStart = startOfDay(now - i * 86_400_000L)
            val dayEnd = dayStart + 86_400_000L
            val secs = list.filter { it.status == SessionStatus.COMPLETED && it.type == SessionType.FOCUS && it.startTimestamp in dayStart until dayEnd }
                .sumOf { it.actualDurationSeconds.toLong() }
            DayFocus(fmt.format(Date(dayStart)), secs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val byProject: StateFlow<List<ProjectFocus>> = combine(projects, sessions) { ps, ss ->
        ps.map { p ->
            val f = ss.filter { it.projectId == p.id && it.status == SessionStatus.COMPLETED && it.type == SessionType.FOCUS }
            ProjectFocus(p, f.sumOf { it.actualDurationSeconds.toLong() }, f.size)
        }.sortedByDescending { it.seconds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = combine(sessions, projects, nodes) { ss, ps, ns ->
        val pmap = ps.associateBy { it.id }; val nmap = ns.associateBy { it.id }
        ss.filter { it.status == SessionStatus.COMPLETED || it.status == SessionStatus.STOPPED }.take(25)
            .map { HistoryItem(it, nmap[it.nodeId]?.title ?: "Node", pmap[it.projectId]?.title ?: "Project") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
