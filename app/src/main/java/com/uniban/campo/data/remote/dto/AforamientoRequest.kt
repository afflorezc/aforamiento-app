package com.uniban.campo.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AforamientoRequest(
    @SerializedName("id") val id: String,
    @SerializedName("parcelId") val parcelId: String,
    @SerializedName("date") val date: String,
    @SerializedName("areaHa") val areaHa: Double,
    @SerializedName("estimatedPlants") val estimatedPlants: Int,
    @SerializedName("confidence") val confidence: Double?
)
