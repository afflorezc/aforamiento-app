package com.uniban.campo.data.repo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uniban.campo.util.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class SyncStateStatus { IDLE, SYNCING, ERROR }

data class SyncStatus(
    val status: SyncStateStatus = SyncStateStatus.IDLE,
    val lastSyncAt: String? = null,
    val lastError: String? = null
)

/**
 * Coordina las dos rutinas de sincronización cuando hay conexión:
 *  - descarga/actualiza el listado de parcelas,
 *  - envía las estimaciones PENDING al servicio de aforamientos y las marca SENT.
 */
class SyncManager(
    private val context: Context,
    private val repository: UnibanRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Sincroniza en segundo plano si hay conexión. No lanza excepciones. */
    fun syncNow() {
        scope.launch { runSync() }
    }

    /** Ejecuta la sincronización y devuelve el resultado, lanzando excepción si algo falla. */
    suspend fun runSync(): Pair<Int, Int> {
        if (_status.value.status == SyncStateStatus.SYNCING) return 0 to 0
        if (!Connectivity.isOnline(context)) {
            _status.update { it.copy(lastError = "Sin conexión") }
            return 0 to 0
        }

        _status.update { it.copy(status = SyncStateStatus.SYNCING, lastError = null) }
        var parcelsUpdated = 0
        var sentCount = 0
        var error: String? = null

        try {
            parcelsUpdated = repository.syncParcels()
        } catch (e: Exception) {
            error = e.message
        }

        try {
            sentCount = repository.sendPendingEstimations()
        } catch (e: Exception) {
            error = error ?: e.message
        }

        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        _status.update {
            it.copy(
                status = if (error != null) SyncStateStatus.ERROR else SyncStateStatus.IDLE,
                lastSyncAt = if (error == null) now else it.lastSyncAt,
                lastError = error
            )
        }
        return parcelsUpdated to sentCount
    }

    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "uniban_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
