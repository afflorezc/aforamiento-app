package com.uniban.campo.data.remote

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface UnibanApi {

    @GET("api/v1/service/parcels")
    suspend fun getParcels(): Response<JsonElement>

    @POST("api/v1/aforamientos")
    suspend fun postAforamiento(@Body body: Any): Response<JsonElement>
}
