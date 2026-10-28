package com.uniban.campo.data.repo

import android.content.Context
import com.uniban.campo.data.db.AppDatabase
import com.uniban.campo.data.db.ParcelEntity
import com.uniban.campo.data.remote.UnibanApi
import com.uniban.campo.data.remote.dto.AforamientoRequest
import com.uniban.campo.data.db.CropEstimationEntity
import com.google.gson.JsonElement
import java.io.IOException

class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnibanRepository(
    private val context: Context,
    private val api: UnibanApi,
    private val database: AppDatabase
) {

    val parcels = database.parcelDao().getAll()
    val estimations = database.cropEstimationDao().getAll()
    val pendingCount = database.cropEstimationDao().pendingCount()
    val parcelCount = database.parcelDao().count()
    val estimationCount = database.cropEstimationDao().count()

    suspend fun getParcel(id: String): ParcelEntity? = database.parcelDao().getById(id)

    suspend fun insertEstimation(entity: CropEstimationEntity) {
        database.cropEstimationDao().insert(entity)
    }

    /**
     * Descarga el listado de parcelas del servicio en la nube y lo actualiza en la BD local.
     * El campo area_ha del servicio llega como string y se convierte a float.
     */
    suspend fun syncParcels(): Int {
        val response = try {
            api.getParcels()
        } catch (e: IOException) {
            throw SyncException("Sin conexión con el servidor", e)
        } catch (e: Exception) {
            throw SyncException("Error consultando parcelas: ${e.message}", e)
        }

        if (!response.isSuccessful) {
            throw SyncException("El servidor respondió ${response.code()} al consultar parcelas")
        }

        val body = response.body() ?: throw SyncException("Respuesta vacía de parcelas")
        val parcels = parseParcels(body)
        if (parcels.isNotEmpty()) {
            database.parcelDao().upsertAll(parcels)
        }
        return parcels.size
    }

    /** Envía todas las estimaciones con estado PENDING y las marca como SENT. */
    suspend fun sendPendingEstimations(): Int {
        val pending = database.cropEstimationDao().getPending()
        var sent = 0
        for (estimate in pending) {
            val request = AforamientoRequest(
                id = estimate.id,
                parcelId = estimate.parcelId,
                date = estimate.date,
                areaHa = estimate.measuredArea?.toDouble() ?: 0.0,
                estimatedPlants = estimate.estimatedPlants,
                confidence = estimate.confidence?.toDouble()
            )
            val response = try {
                api.postAforamiento(request)
            } catch (e: IOException) {
                throw SyncException("Sin conexión con el servidor", e)
            } catch (e: Exception) {
                throw SyncException("Error enviando estimación: ${e.message}", e)
            }

            if (!response.isSuccessful) {
                throw SyncException(
                    "El servidor respondió ${response.code()} al enviar la estimación ${estimate.id}"
                )
            }
            database.cropEstimationDao().markSent(estimate.id)
            sent++
        }
        return sent
    }

    private fun parseParcels(root: JsonElement): List<ParcelEntity> {
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                listOf("data", "parcels", "results", "content", "items")
                    .firstNotNullOfOrNull { key ->
                        obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
                    }
                    ?: throw SyncException("Formato de respuesta de parcelas no reconocido")
            }
            else -> throw SyncException("Formato de respuesta de parcelas no reconocido")
        }

        return array.mapNotNull { item ->
            try {
                val obj = item.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "Sin nombre"
                val areaRaw = obj.get("area_ha") ?: obj.get("areaHa") ?: obj.get("area")
                val areaHa = when {
                    areaRaw == null || areaRaw.isJsonNull -> return@mapNotNull null
                    areaRaw.asJsonPrimitive.isNumber -> areaRaw.asDouble.toFloat()
                    else -> areaRaw.asString.replace(",", ".").toFloatOrNull() ?: return@mapNotNull null
                }
                ParcelEntity(id = id, name = name, areaHa = areaHa)
            } catch (e: Exception) {
                null
            }
        }
    }
}
