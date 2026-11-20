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
    @Query("SELECT * FROM nodes WHERE projectId = :projectId") suspend fun getByProject(projectId: Long): List<NodeEntity>
    @Query("SELECT * FROM nodes WHERE parentId = :parentId") suspend fun childrenOf(parentId: Long): List<NodeEntity>
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
    @Query("DELETE FROM sessions WHERE projectId = :projectId") suspend fun deleteByProject(projectId: Long)
    @Query("DELETE FROM sessions WHERE nodeId = :nodeId") suspend fun deleteByNode(nodeId: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1") fun observe(): Flow<UserSettingsEntity?>
    @Query("SELECT * FROM user_settings WHERE id = 1") suspend fun get(): UserSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: UserSettingsEntity)
}
