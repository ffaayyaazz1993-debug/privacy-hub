package com.privacyhub.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert suspend fun insert(p: ProjectEntity): Long
    @Update suspend fun update(p: ProjectEntity)
    @Delete suspend fun delete(p: ProjectEntity)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM projects WHERE id = :id") fun observe(id: Long): Flow<ProjectEntity?>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: Long): ProjectEntity?
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE title LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    fun search(q: String): Flow<List<ProjectEntity>>
    @Query("SELECT DISTINCT category FROM projects WHERE category != '' ORDER BY category")
    fun categories(): Flow<List<String>>
    @Query("SELECT COUNT(*) FROM projects") fun count(): Flow<Int>
}

@Dao
interface NodeDao {
    @Insert suspend fun insert(n: NodeEntity): Long
    @Update suspend fun update(n: NodeEntity)
    @Delete suspend fun delete(n: NodeEntity)
    @Query("DELETE FROM nodes WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM nodes WHERE projectId = :projectId") suspend fun deleteByProject(projectId: Long)
    @Query("SELECT * FROM nodes WHERE id = :id") fun observe(id: Long): Flow<NodeEntity?>
    @Query("SELECT * FROM nodes WHERE id = :id") suspend fun get(id: Long): NodeEntity?
    @Query("SELECT * FROM nodes WHERE projectId = :projectId") fun observeByProject(projectId: Long): Flow<List<NodeEntity>>
    @Query("SELECT * FROM nodes") fun observeAll(): Flow<List<NodeEntity>>
    @Query("SELECT * FROM nodes WHERE title LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' OR notes LIKE '%' || :q || '%'") fun search(q: String): Flow<List<NodeEntity>>
    @Query("SELECT * FROM nodes WHERE projectId = :projectId") suspend fun getByProject(projectId: Long): List<NodeEntity>
    @Query("SELECT * FROM nodes WHERE parentId = :parentId") suspend fun childrenOf(parentId: Long): List<NodeEntity>
    @Query("SELECT * FROM nodes ORDER BY updatedAt DESC LIMIT 1") fun observeRecent(): Flow<NodeEntity?>
    @Query("SELECT COUNT(*) FROM nodes WHERE projectId = :projectId") fun countByProject(projectId: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM nodes WHERE projectId = :projectId AND status = :status")
    fun countByProjectAndStatus(projectId: Long, status: String): Flow<Int>
    @Query("UPDATE nodes SET completedSessions = :completed, totalFocusSeconds = :seconds, status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStats(id: Long, completed: Int, seconds: Long, status: String, now: Long)
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: FocusSessionEntity): Long
    @Update suspend fun update(s: FocusSessionEntity)
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun get(id: Long): FocusSessionEntity?
    @Query("SELECT * FROM sessions WHERE id = :id") fun observe(id: Long): Flow<FocusSessionEntity?>
    @Query("SELECT * FROM sessions WHERE status IN ('RUNNING','PAUSED') ORDER BY startTimestamp DESC LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>
    @Query("SELECT * FROM sessions WHERE status IN ('RUNNING','PAUSED') ORDER BY startTimestamp DESC LIMIT 1")
    suspend fun getActive(): FocusSessionEntity?
    @Query("SELECT * FROM sessions WHERE nodeId = :nodeId ORDER BY startTimestamp DESC") fun observeByNode(nodeId: Long): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY startTimestamp DESC") fun observeByProject(projectId: Long): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC") fun observeAll(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS' ORDER BY endTimestamp DESC LIMIT 1") fun latestCompletedFocus(): Flow<FocusSessionEntity?>
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS' AND startTimestamp BETWEEN :from AND :to ORDER BY startTimestamp DESC")
    fun completedFocusBetween(from: Long, to: Long): Flow<List<FocusSessionEntity>>
    @Query("SELECT COALESCE(SUM(actualDurationSeconds),0) FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS' AND startTimestamp BETWEEN :from AND :to")
    fun focusSecondsBetween(from: Long, to: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(actualDurationSeconds),0) FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS' AND projectId = :projectId")
    fun focusSecondsByProject(projectId: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(actualDurationSeconds),0) FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS' AND nodeId = :nodeId")
    fun focusSecondsByNode(nodeId: Long): Flow<Long>
    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS' AND projectId = :projectId")
    fun completedCountByProject(projectId: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS' AND nodeId = :nodeId")
    fun completedCountByNode(nodeId: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS'") fun completedCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED' AND type = 'FOCUS'") suspend fun completedFocusCount(): Int
    @Query("DELETE FROM sessions WHERE projectId = :projectId") suspend fun deleteByProject(projectId: Long)
    @Query("DELETE FROM sessions WHERE nodeId = :nodeId") suspend fun deleteByNode(nodeId: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1") fun observe(): Flow<UserSettingsEntity?>
    @Query("SELECT * FROM user_settings WHERE id = 1") suspend fun get(): UserSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: UserSettingsEntity)
}

@Dao
interface FlashcardDao {
    @Insert suspend fun insert(c: FlashcardEntity): Long
    @Update suspend fun update(c: FlashcardEntity)
    @Delete suspend fun delete(c: FlashcardEntity)
    @Query("SELECT * FROM flashcards WHERE id = :id") suspend fun get(id: Long): FlashcardEntity?
    @Query("SELECT * FROM flashcards WHERE id = :id") fun observe(id: Long): Flow<FlashcardEntity?>
    @Query("SELECT * FROM flashcards WHERE archived = 0 AND nodeId = :nodeId ORDER BY createdAt DESC") fun observeByNode(nodeId: Long): Flow<List<FlashcardEntity>>
    @Query("SELECT * FROM flashcards WHERE archived = 0 AND projectId = :projectId ORDER BY createdAt DESC") fun observeByProject(projectId: Long): Flow<List<FlashcardEntity>>
    @Query("SELECT * FROM flashcards WHERE archived = 0 ORDER BY createdAt DESC") fun observeAll(): Flow<List<FlashcardEntity>>
    @Query("SELECT * FROM flashcards WHERE archived = 0 AND (question LIKE '%' || :q || '%' OR answer LIKE '%' || :q || '%') ORDER BY updatedAt DESC") fun search(q: String): Flow<List<FlashcardEntity>>
    @Query("SELECT * FROM flashcards WHERE archived = 0 AND dueTimestamp <= :now ORDER BY dueTimestamp ASC") fun observeDue(now: Long): Flow<List<FlashcardEntity>>
    @Query("SELECT * FROM flashcards WHERE archived = 0 AND dueTimestamp <= :now ORDER BY dueTimestamp ASC") suspend fun due(now: Long): List<FlashcardEntity>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0") fun count(): Flow<Int>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0 AND dueTimestamp <= :now") fun dueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0 AND dueTimestamp <= :now AND lastReviewTimestamp = 0") fun newDueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0 AND dueTimestamp <= :now AND lastReviewTimestamp > 0") fun learnedDueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0 AND dueTimestamp < :now") fun overdueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0 AND nodeId = :nodeId AND dueTimestamp <= :now") fun dueCountByNode(nodeId: Long, now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0 AND nodeId = :nodeId") fun countByNode(nodeId: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM flashcards WHERE archived = 0 AND projectId = :projectId") fun countByProject(projectId: Long): Flow<Int>
    @Query("SELECT MAX(dueTimestamp) FROM flashcards WHERE archived = 0") fun nextDue(): Flow<Long?>
    @Query("UPDATE flashcards SET archived = 1, updatedAt = :now WHERE id = :id") suspend fun archive(id: Long, now: Long)
    @Query("DELETE FROM flashcards WHERE nodeId IN (:nodeIds)") suspend fun deleteByNodeIds(nodeIds: List<Long>)
    @Query("DELETE FROM flashcards WHERE projectId = :projectId") suspend fun deleteByProject(projectId: Long)
}

@Dao
interface ReviewLogDao {
    @Insert suspend fun insert(log: ReviewLogEntity): Long
    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC") fun observeAll(): Flow<List<ReviewLogEntity>>
    @Query("SELECT * FROM review_logs WHERE flashcardId = :cardId ORDER BY reviewedAt DESC") fun observeByCard(cardId: Long): Flow<List<ReviewLogEntity>>
    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :from") fun countSince(from: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :from AND rating IN ('GOOD','EASY')") fun rememberedSince(from: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :from AND rating = 'AGAIN'") fun forgottenSince(from: Long): Flow<Int>
    @Query("SELECT COALESCE(SUM(elapsedDurationSeconds),0) FROM review_logs WHERE reviewedAt >= :from") fun secondsSince(from: Long): Flow<Long>
    @Query("SELECT COUNT(*) FROM review_logs") suspend fun count(): Int
}

@Dao
interface SchedulerSettingsDao2 {
    @Query("SELECT * FROM scheduler_settings WHERE id = 1") fun observe(): Flow<SchedulerSettingsEntity?>
    @Query("SELECT * FROM scheduler_settings WHERE id = 1") suspend fun get(): SchedulerSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: SchedulerSettingsEntity)
}

@Dao
interface ReferenceDao {
    @Insert suspend fun insert(r: ReferenceEntity): Long
    @Delete suspend fun delete(r: ReferenceEntity)
    @Query("SELECT * FROM `references` ORDER BY createdAt DESC") fun observeAll(): Flow<List<ReferenceEntity>>
    @Query("SELECT * FROM `references` WHERE nodeId = :nodeId ORDER BY createdAt DESC") fun observeByNode(nodeId: Long): Flow<List<ReferenceEntity>>
    @Query("SELECT * FROM `references` WHERE projectId = :projectId ORDER BY createdAt DESC") fun observeByProject(projectId: Long): Flow<List<ReferenceEntity>>
    @Query("SELECT * FROM `references` WHERE title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' ORDER BY createdAt DESC") fun search(q: String): Flow<List<ReferenceEntity>>
}

@Dao
interface RoutineDao {
    @Insert suspend fun insert(r: RoutineEntity): Long
    @Update suspend fun update(r: RoutineEntity)
    @Delete suspend fun delete(r: RoutineEntity)
    @Query("SELECT * FROM routines ORDER BY createdAt DESC") fun observeAll(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun get(id: Long): RoutineEntity?
}

@Dao
interface PlannedSessionDao {
    @Insert suspend fun insert(p: PlannedSessionEntity): Long
    @Update suspend fun update(p: PlannedSessionEntity)
    @Query("SELECT * FROM planned_sessions ORDER BY scheduledAt ASC") fun observeAll(): Flow<List<PlannedSessionEntity>>
    @Query("SELECT * FROM planned_sessions WHERE scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt ASC") fun observeBetween(from: Long, to: Long): Flow<List<PlannedSessionEntity>>
    @Query("SELECT * FROM planned_sessions WHERE routineId = :routineId AND scheduledAt = :at") suspend fun find(routineId: Long, at: Long): PlannedSessionEntity?
    @Query("SELECT * FROM planned_sessions WHERE id = :id") suspend fun get(id: Long): PlannedSessionEntity?
    @Query("UPDATE planned_sessions SET status = :status WHERE status = 'PLANNED' AND scheduledAt < :now") suspend fun markMissed(now: Long, status: String = "MISSED")
    @Query("DELETE FROM planned_sessions WHERE routineId = :routineId") suspend fun deleteByRoutine(routineId: Long)
}

@Dao
interface WeeklyReflectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(r: WeeklyReflectionEntity)
    @Query("SELECT * FROM weekly_reflections WHERE weekStart = :weekStart") fun observe(weekStart: Long): Flow<WeeklyReflectionEntity?>
    @Query("SELECT * FROM weekly_reflections ORDER BY weekStart DESC") fun observeAll(): Flow<List<WeeklyReflectionEntity>>
}

@Dao
interface DetoxDao {
    // profiles + apps
    @Insert suspend fun insertProfile(p: DetoxProfileEntity): Long
    @Update suspend fun updateProfile(p: DetoxProfileEntity)
    @Query("DELETE FROM detox_profiles WHERE id = :id") suspend fun deleteProfile(id: Long)
    @Query("SELECT * FROM detox_profiles ORDER BY isDefault DESC, createdAt ASC") fun observeProfiles(): Flow<List<DetoxProfileEntity>>
    @Query("SELECT * FROM detox_profiles WHERE id = :id") suspend fun profile(id: Long): DetoxProfileEntity?
    @Query("UPDATE detox_profiles SET isDefault = 0") suspend fun clearDefaults()
    @Query("SELECT * FROM detox_apps WHERE profileId = :pid ORDER BY label") fun observeApps(pid: Long): Flow<List<DetoxAppEntity>>
    @Insert suspend fun insertApp(a: DetoxAppEntity): Long
    @Query("DELETE FROM detox_apps WHERE profileId = :pid AND packageName = :pkg") suspend fun removeApp(pid: Long, pkg: String)
    @Query("DELETE FROM detox_apps WHERE profileId = :pid") suspend fun clearApps(pid: Long)
    @Query("SELECT packageName FROM detox_apps WHERE profileId IN (:ids)") suspend fun packagesFor(ids: List<Long>): List<String>
    @Query("SELECT * FROM detox_apps") suspend fun observeAllApps(): List<DetoxAppEntity>
    @Query("SELECT * FROM node_detox") suspend fun observeNodeConfigs(): List<NodeDetoxEntity>

    // node overrides
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNode(n: NodeDetoxEntity)
    @Query("SELECT * FROM node_detox WHERE nodeId = :nodeId") suspend fun nodeConfig(nodeId: Long): NodeDetoxEntity?

    // schedules
    @Insert suspend fun insertSchedule(s: DetoxScheduleEntity): Long
    @Update suspend fun updateSchedule(s: DetoxScheduleEntity)
    @Query("DELETE FROM detox_schedules WHERE id = :id") suspend fun deleteSchedule(id: Long)
    @Query("SELECT * FROM detox_schedules WHERE enabled = 1") fun observeSchedules(): Flow<List<DetoxScheduleEntity>>
    @Query("SELECT * FROM detox_schedules WHERE enabled = 1") suspend fun activeSchedules(): List<DetoxScheduleEntity>
    @Query("SELECT * FROM detox_schedules WHERE id = :id") suspend fun schedule(id: Long): DetoxScheduleEntity?

    // activations
    @Insert suspend fun insertActivation(a: DetoxActivationEntity): Long
    @Update suspend fun updateActivation(a: DetoxActivationEntity)
    @Query("SELECT * FROM detox_activations WHERE status = 'ACTIVE' ORDER BY startedAt ASC") fun observeActivations(): Flow<List<DetoxActivationEntity>>
    @Query("SELECT * FROM detox_activations WHERE status = 'ACTIVE'") suspend fun currentActivations(): List<DetoxActivationEntity>
    @Query("SELECT * FROM detox_activations WHERE sourceType = :type AND sourceId = :sid ORDER BY startedAt DESC LIMIT 1") suspend fun activationFor(type: String, sid: String): DetoxActivationEntity?
    @Query("SELECT * FROM detox_activations WHERE focusSessionId = :focusId AND status = 'ACTIVE'") suspend fun activationForFocus(focusId: Long): DetoxActivationEntity?
    @Query("SELECT * FROM detox_activations ORDER BY startedAt DESC") fun observeHistory(): Flow<List<DetoxActivationEntity>>
    @Query("SELECT COUNT(*) FROM detox_activations WHERE sourceType = 'POMODORO' AND status = 'RELEASED'") fun releasedCount(): Flow<Int>
}

@Dao
interface ProjectTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: ProjectTemplateEntity)
    @Query("SELECT * FROM project_templates WHERE projectId = :projectId")
    suspend fun get(projectId: Long): ProjectTemplateEntity?
    @Query("SELECT * FROM project_templates WHERE projectId = :projectId")
    fun observe(projectId: Long): Flow<ProjectTemplateEntity?>
    @Query("DELETE FROM project_templates WHERE projectId = :projectId")
    suspend fun delete(projectId: Long)
    @Query("SELECT * FROM project_templates")
    suspend fun getAll(): List<ProjectTemplateEntity>
}
