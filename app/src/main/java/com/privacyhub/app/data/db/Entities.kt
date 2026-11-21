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

/** A recall question attached to one mind-map node. Extensible content type for future media. */
@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("nodeId"), Index("projectId"), Index("dueTimestamp"), Index("archived")],
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val nodeId: Long,
    val question: String,
    val answer: String,
    val hint: String = "",
    val explanation: String = "",
    val contentType: String = "TEXT",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueTimestamp: Long = System.currentTimeMillis(),
    val lastReviewTimestamp: Long = 0L,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val learningState: String = "NEW",
    val learningStep: Int = 0,
    val intervalDays: Int = 0,
    val schedulerVersion: Int = 0,
    val reviewStatus: String = "NEW",
    val archived: Boolean = false,
)

/** One immutable review record. Never updated, so history cannot be rewritten. */
@Entity(
    tableName = "review_logs",
    foreignKeys = [ForeignKey(
        entity = FlashcardEntity::class, parentColumns = ["id"], childColumns = ["flashcardId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("flashcardId"), Index("reviewedAt"), Index("rating")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flashcardId: Long,
    val reviewedAt: Long,
    val rating: String,
    val previousDueTimestamp: Long,
    val nextDueTimestamp: Long,
    val elapsedDays: Double = 0.0,
    val previousStability: Double = 0.0,
    val newStability: Double = 0.0,
    val previousDifficulty: Double = 0.0,
    val newDifficulty: Double = 0.0,
    val elapsedDurationSeconds: Int = 0,
    val schedulerVersion: Int = 0,
)

@Entity(tableName = "scheduler_settings")
data class SchedulerSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val desiredRetention: Double = 0.9,
    val maximumInterval: Int = 36500,
    val newCardsPerDay: Int = 20,
    val reviewLimitPerDay: Int = 100,
    val reminderEnabled: Boolean = false,
    val preferredReviewHour: Int = 9,
)
