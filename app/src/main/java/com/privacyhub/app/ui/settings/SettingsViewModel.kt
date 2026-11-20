package com.privacyhub.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.db.UserSettingsEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: FocusMapRepository) : ViewModel() {
    val settings: StateFlow<UserSettingsEntity> = repo.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettingsEntity())
    fun update(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        viewModelScope.launch {
            val cur = repo.settings.get() ?: UserSettingsEntity()
            repo.settings.upsert(transform(cur))
        }
    }
}
