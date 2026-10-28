package com.uniban.campo.data.repo

import android.content.Context
import com.uniban.campo.data.db.AppDatabase
import com.uniban.campo.data.prefs.SessionManager
import com.uniban.campo.data.remote.RetrofitClient

object ServiceLocator {

    lateinit var sessionManager: SessionManager
        private set
    lateinit var repository: UnibanRepository
        private set
    lateinit var syncManager: SyncManager
        private set

    fun init(context: Context) {
        if (::repository.isInitialized) return
        val appContext = context.applicationContext
        sessionManager = SessionManager(appContext)
        val database = AppDatabase.get(appContext)
        repository = UnibanRepository(appContext, RetrofitClient.api, database)
        syncManager = SyncManager(appContext, repository)
    }
}
