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
    val startDate: Long = 0L,
    val dueDate: Long = 0L,
    val taskStatus: String = "",
    val dependencies: String = "",
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

/** External material captured through the share target and attached to a node. */
@Entity(
    tableName = "references",
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("nodeId"), Index("projectId"), Index("title")],
)
data class ReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val nodeId: Long,
    val title: String,
    val content: String = "",
    val url: String = "",
    val mimeType: String = "text/plain",
    val uriString: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Recurring study block; weekdays are Monday-first indexes 0..6, comma separated. */
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("projectId"), Index("active")],
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val projectId: Long,
    val nodeId: Long? = null,
    val weekdays: String = "0,1,2,3,4",
    val durationMinutes: Int = 25,
    val weeklyTarget: Int = 1,
    val reminderMinutes: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One concrete planned activity generated by a routine. Never auto-completed. */
@Entity(
    tableName = "planned_sessions",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("routineId"), Index("scheduledAt"), Index("status")],
)
data class PlannedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val projectId: Long,
    val nodeId: Long,
    val scheduledAt: Long,
    val durationMinutes: Int = 25,
    val status: String = "PLANNED",
    val focusSessionId: Long? = null,
)

@Entity(tableName = "weekly_reflections")
data class WeeklyReflectionEntity(
    @PrimaryKey val weekStart: Long,
    val accomplished: String = "",
    val incomplete: String = "",
    val concepts: String = "",
    val priority: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A reusable attention-protection profile. */
@Entity(
    tableName = "detox_profiles",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index("isDefault"), Index("projectId")],
)
data class DetoxProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val dndEnabled: Boolean = false,
    val pauseBehaviour: String = "KEEP", // KEEP | SUSPEND
    val emergencyEnabled: Boolean = true,
    val autoActivate: Boolean = true,
    val projectId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One restricted package inside a profile. */
@Entity(
    tableName = "detox_apps",
    foreignKeys = [ForeignKey(entity = DetoxProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId"), Index("packageName")],
)
data class DetoxAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val packageName: String,
    val label: String = "",
)

/** Optional per-node override of the project profile. */
@Entity(tableName = "node_detox", indices = [Index(value = ["nodeId", "profileId"])])
data class NodeDetoxEntity(
    @PrimaryKey val nodeId: Long,
    val profileId: Long? = null,
    val autoActivate: Boolean = true,
)

/** Recurring independent Detox schedule (minutes since midnight). */
@Entity(
    tableName = "detox_schedules",
    foreignKeys = [ForeignKey(entity = DetoxProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index("enabled")],
)
data class DetoxScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val profileId: Long,
    val weekdays: String = "0,1,2,3,4,5,6",
    val startMinute: Int = 18 * 60,
    val endMinute: Int = 19 * 60,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One activation source. Never collapse Detox to a single boolean:
 * POMODORO / SCHEDULED / MANUAL rows stay independently addressable.
 */
@Entity(
    tableName = "detox_activations",
    indices = [Index("sourceType"), Index("sourceId"), Index("status")],
)
data class DetoxActivationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceId: String,
    val profileId: Long,
    val startedAt: Long = System.currentTimeMillis(),
    val endsAt: Long = 0L,
    val overrideUntil: Long = 0L,
    val status: String = "ACTIVE",
    val focusSessionId: Long? = null,
)

/** Template provenance and suggested settings; never overwrites the project itself. */
@Entity(
    tableName = "project_templates",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE)],
)
data class ProjectTemplateEntity(
    @PrimaryKey val projectId: Long,
    val templateId: String,
    val templateVersion: Int,
    val pomodoroPresets: String = "",
    val detoxSuggestion: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
