package com.privacyhub.app.ui.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MapsViewModel(private val repo: FocusMapRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    val projects: StateFlow<List<ProjectEntity>> =
        query.flatMapLatest { q -> if (q.isBlank()) repo.projects.observeAll() else repo.projects.search(q.trim()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { query.value = q }
    fun create(title: String, category: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.createProject(title.trim(), category = category.trim()) }
    }
    fun delete(id: Long) {
        viewModelScope.launch { repo.sessions.deleteByProject(id); repo.projects.deleteById(id) }
    }
}
