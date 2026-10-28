package com.uniban.campo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EstimationStatus { PENDING, SENT }

@Entity(
    tableName = "crop_estimation",
    foreignKeys = [
        ForeignKey(
            entity = ParcelEntity::class,
            parentColumns = ["id"],
            childColumns = ["parcelId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("parcelId")]
)
data class CropEstimationEntity(
    @PrimaryKey val id: String,
    val date: String,
    val inputImg: String,
    val measuredArea: Float?,
    val estimatedPlants: Int,
    val confidence: Float?,
    val outputImg: String,
    val parcelId: String,
    val status: EstimationStatus = EstimationStatus.PENDING
)
