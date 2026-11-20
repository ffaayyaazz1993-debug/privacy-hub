package com.privacyhub.app.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CanvasViewModel(repo: FocusMapRepository, projectId: Long) : ViewModel() {
    val project: StateFlow<ProjectEntity?> = repo.projects.observe(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val nodes: StateFlow<List<NodeEntity>> = repo.nodes.observeByProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
