package com.example.gerenciadordeparquinho.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "toys")
data class Toy(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val price: Double = 0.0,
    val timeMinutes: Int = 0,
    val imageBase64: String? = null,
    val isAlwaysFree: Boolean = false
)