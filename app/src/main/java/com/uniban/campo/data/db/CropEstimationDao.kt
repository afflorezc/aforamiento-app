package com.uniban.campo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CropEstimationDao {
    @Query("SELECT * FROM crop_estimation ORDER BY date DESC, rowid DESC")
    fun getAll(): Flow<List<CropEstimationEntity>>

    @Query("SELECT * FROM crop_estimation WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CropEstimationEntity?

    @Query("SELECT * FROM crop_estimation WHERE status = 'PENDING'")
    suspend fun getPending(): List<CropEstimationEntity>

    @Query("SELECT COUNT(*) FROM crop_estimation WHERE status = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crop_estimation")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(estimate: CropEstimationEntity)

    @Query("UPDATE crop_estimation SET status = 'SENT' WHERE id = :id")
    suspend fun markSent(id: String)
}
