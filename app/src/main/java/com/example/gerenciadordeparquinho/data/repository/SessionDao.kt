package com.example.gerenciadordeparquinho.data.repository

import androidx.room.*
import com.example.gerenciadordeparquinho.data.model.PlaySession
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM play_sessions WHERE date = :date ORDER BY startTime DESC")
    fun getSessionsByDate(date: String): Flow<List<PlaySession>>

    @Query("SELECT * FROM play_sessions WHERE isFinished = 0 AND isCancelled = 0 ORDER BY remainingSeconds ASC")
    fun getActiveSessions(): Flow<List<PlaySession>>

    @Query("SELECT * FROM play_sessions WHERE isFinished = 0 AND isCancelled = 0")
    suspend fun getActiveSessionsList(): List<PlaySession>

    @Query("SELECT * FROM play_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): PlaySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PlaySession)

    @Delete
    suspend fun deleteSession(session: PlaySession)

    @Query("DELETE FROM play_sessions WHERE date = :date")
    suspend fun clearHistoryByDate(date: String)
}
