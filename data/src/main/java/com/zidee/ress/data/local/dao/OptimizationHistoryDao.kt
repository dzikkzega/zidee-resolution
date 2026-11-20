package com.zidee.ress.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zidee.ress.data.local.entity.OptimizationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimizationHistoryDao {
    @Query("SELECT * FROM optimization_history ORDER BY created_at_epoch_ms DESC")
    fun observeAllDesc(): Flow<List<OptimizationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<OptimizationHistoryEntity>)

    @Query("DELETE FROM optimization_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM optimization_history")
    suspend fun clearAll()
}
