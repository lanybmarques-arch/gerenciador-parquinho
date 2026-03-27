package com.example.gerenciadordeparquinho.data.repository

import androidx.room.*
import com.example.gerenciadordeparquinho.data.model.UserAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :userName LIMIT 1")
    suspend fun getUserByUsername(userName: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccount)

    @Update
    suspend fun updateUser(user: UserAccount)

    @Delete
    suspend fun deleteUser(user: UserAccount)

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserAccount>>
    
    @Query("UPDATE users SET pass = :newPass WHERE username = :userName")
    suspend fun updatePassword(userName: String, newPass: String)
}
