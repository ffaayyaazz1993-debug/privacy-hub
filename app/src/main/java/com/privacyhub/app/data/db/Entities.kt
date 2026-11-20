package com.privacyhub.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "nodes",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("projectId"), Index("parentId")],
)
data class NodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val parentId: Long? = null,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val estimatedSessions: Int = 0,
    val completedSessions: Int = 0,
    val totalFocusSeconds: Long = 0,
    val status: NodeStatus = NodeStatus.NOT_STARTED,
    val priority: Int = 0,
    val collapsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "sessions",
    indices = [Index("projectId"), Index("nodeId"), Index("startTimestamp")],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val nodeId: Long,
    val type: SessionType = SessionType.FOCUS,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int = 0,
    val status: SessionStatus = SessionStatus.RUNNING,
    val notes: String = "",
    val review: String? = null,
    val understood: Boolean? = null,
    // Pause/resume accuracy: banked focused seconds + start of the current running segment.
    val accumulatedSeconds: Long = 0,
    val resumeTimestamp: Long? = null,
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val focusDurationSeconds: Int = 25 * 60,
    val shortBreakSeconds: Int = 5 * 60,
    val longBreakSeconds: Int = 15 * 60,
    val longBreakInterval: Int = 4,
    val soundEnabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    val focusWindowEnabled: Boolean = false,
    val blackoutTransparency: Float = 0.92f,
    val theme: String = "system",
)
