package com.uniban.campo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniban.campo.data.db.CropEstimationEntity
import com.uniban.campo.data.db.EstimationStatus
import com.uniban.campo.data.db.ParcelEntity
import com.uniban.campo.data.repo.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryItem(
    val estimation: CropEstimationEntity,
    val parcel: ParcelEntity?
)

class HistoryViewModel : ViewModel() {

    private val repository = ServiceLocator.repository

    val items: StateFlow<List<HistoryItem>> =
        combine(repository.estimations, repository.parcels) { estimations, parcels ->
            estimations.map { estimation ->
                HistoryItem(estimation, parcels.find { it.id == estimation.parcelId })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun statusOf(estimation: CropEstimationEntity): EstimationStatus = estimation.status
}
