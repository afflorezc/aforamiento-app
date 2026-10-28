package com.uniban.campo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {
    @Query("SELECT * FROM parcel ORDER BY name ASC")
    fun getAll(): Flow<List<ParcelEntity>>

    @Query("SELECT * FROM parcel WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ParcelEntity?

    @Query("SELECT COUNT(*) FROM parcel")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(parcels: List<ParcelEntity>)

    @Query("DELETE FROM parcel")
    suspend fun clear()
}
