package com.example.gerenciadordeparquinho.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserAccount(
    @PrimaryKey val username: String = "",
    val pass: String = "",
    val isBlocked: Boolean = false,
    val canRegister: Boolean = true,
    val canReport: Boolean = true,
    val canSettings: Boolean = true,
    val canLayout: Boolean = true,
    val role: String = "OPERATOR"
)
