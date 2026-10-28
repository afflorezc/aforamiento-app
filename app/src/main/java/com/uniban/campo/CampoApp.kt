package com.uniban.campo

import android.app.Application
import com.uniban.campo.data.repo.ServiceLocator
import com.uniban.campo.util.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CampoApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.syncManager.schedulePeriodicSync()
        appScope.launch {
            if (Connectivity.isOnline(this@CampoApp)) {
                ServiceLocator.syncManager.runSync()
            }
        }
    }
}
