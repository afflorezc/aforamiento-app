package com.uniban.campo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parcel")
data class ParcelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val areaHa: Float
)
