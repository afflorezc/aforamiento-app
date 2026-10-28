package com.uniban.campo.ui.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniban.campo.data.db.ParcelEntity
import com.uniban.campo.data.repo.ServiceLocator
import com.uniban.campo.data.repo.SyncManager
import com.uniban.campo.data.repo.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ParcelsViewModel : ViewModel() {

    private val repository = ServiceLocator.repository
    private val syncManager: SyncManager = ServiceLocator.syncManager

    val parcels: StateFlow<List<ParcelEntity>> = repository.parcels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatus: StateFlow<SyncStatus> = syncManager.status

    fun syncNow() {
        syncManager.syncNow()
    }
}
