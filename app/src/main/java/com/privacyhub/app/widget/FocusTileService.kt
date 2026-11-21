package com.privacyhub.app.widget

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.MainActivity
import com.privacyhub.app.domain.pomodoro.FocusSessionManager
import com.privacyhub.app.service.FocusSessionService
import com.privacyhub.app.domain.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/** Delegates to the existing FocusSessionManager: repeated taps cannot duplicate a timer. */
class FocusTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() { super.onStartListening(); sync() }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val repo = (application as FocusMapApplication).repository
            val m = FocusSessionManager(repo)
            val s = m.active()
            when (s?.status) {
                null -> {
                    val node = withContext(Dispatchers.IO) { repo.nodes.observeRecent().firstOrNull() }
                    if (node != null) {
                        val cfg = withContext(Dispatchers.IO) { repo.settings.get() }
                        m.start(node.projectId, node.id, com.privacyhub.app.domain.model.SessionType.FOCUS,
                            cfg?.focusDurationSeconds ?: 1500)
                    } else {
                        startActivityAndCollapse(Intent(this@FocusTileService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                SessionStatus.RUNNING -> m.pause(s)
                SessionStatus.PAUSED -> m.resume(s)
                else -> m.active()?.let { }
            }
            try { FocusSessionService.start(this@FocusTileService) } catch (_: Exception) { }
            sync()
            BaseWidgetProvider.refresh(this@FocusTileService, "all")
        }
    }

    private fun sync() {
        scope.launch {
            val repo = (application as FocusMapApplication).repository
            val s = withContext(Dispatchers.IO) { repo.sessions.getActive() }
            val tile = qsTile ?: return@launch
            tile.state = when (s?.status) {
                SessionStatus.RUNNING -> Tile.STATE_ACTIVE
                SessionStatus.PAUSED -> Tile.STATE_INACTIVE
                else -> Tile.STATE_INACTIVE
            }
            tile.label = when (s?.status) {
                SessionStatus.RUNNING -> "Focus running"
                SessionStatus.PAUSED -> "Focus paused"
                else -> "Start focus"
            }
            tile.contentDescription = tile.label
            tile.icon = android.graphics.drawable.Icon.createWithResource(
                this@FocusTileService, if (s?.status == SessionStatus.RUNNING)
                    android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            tile.updateTile()
        }
    }
}
