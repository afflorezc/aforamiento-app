package com.uniban.campo.data.repo

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        ServiceLocator.syncManager.runSync()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
