package com.example.gerenciadordeparquinho.data.repository

import androidx.room.*
import com.example.gerenciadordeparquinho.data.model.Toy
import kotlinx.coroutines.flow.Flow

@Dao
interface ToyDao {
    @Query("SELECT * FROM toys")
    fun getAllToys(): Flow<List<Toy>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToy(toy: Toy)

    @Delete
    suspend fun deleteToy(toy: Toy)
}
