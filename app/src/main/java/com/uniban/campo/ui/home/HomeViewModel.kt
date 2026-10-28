package com.uniban.campo.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniban.campo.data.prefs.SessionManager
import com.uniban.campo.data.repo.ServiceLocator
import com.uniban.campo.data.repo.SyncManager
import com.uniban.campo.data.repo.SyncStatus
import com.uniban.campo.ml.YoloTfliteDetector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.repository
    private val syncManager: SyncManager = ServiceLocator.syncManager
    private val session: SessionManager = ServiceLocator.sessionManager

    val parcelsCount: StateFlow<Int> = repository.parcelCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val estimationsCount: StateFlow<Int> = repository.estimationCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingCount: StateFlow<Int> = repository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncStatus: StateFlow<SyncStatus> = syncManager.status

    val userEmail: String get() = session.email ?: "Usuario"

    val isModelAvailable: Boolean
        get() = YoloTfliteDetector.isModelAvailable(getApplication())

    fun syncNow() {
        syncManager.syncNow()
    }

    fun logout() {
        session.clear()
    }
}
