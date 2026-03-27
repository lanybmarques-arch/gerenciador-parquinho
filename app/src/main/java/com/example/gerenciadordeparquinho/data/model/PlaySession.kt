package com.example.gerenciadordeparquinho.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "play_sessions")
data class PlaySession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val personName: String = "",
    val toyName: String = "",
    val toyPrice: Double = 0.0,
    val toyTimeMinutes: Int = 0,
    val totalValueAccumulated: Double = 0.0,
    val elapsedSecondsInCurrentCycle: Long = 0,
    val remainingSeconds: Long = 0,
    val lastUpdateTimestamp: Long = System.currentTimeMillis(),
    val date: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
    val startTime: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val endTime: String? = null,
    val isCancelled: Boolean = false,
    val isFinished: Boolean = false,
    val isPaused: Boolean = false,
    val notified: Boolean = false
) {
    fun calculateCurrentProportionalValue(): Double {
        val totalSecondsInCycle = (toyTimeMinutes * 60).toDouble()
        if (totalSecondsInCycle <= 0) return totalValueAccumulated

        val currentCycleValue = (elapsedSecondsInCurrentCycle.toDouble() / totalSecondsInCycle) * toyPrice
        return totalValueAccumulated + currentCycleValue
    }
}
