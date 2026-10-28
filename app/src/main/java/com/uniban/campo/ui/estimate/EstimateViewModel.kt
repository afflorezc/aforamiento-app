package com.uniban.campo.ui.estimate

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniban.campo.data.db.CropEstimationEntity
import com.uniban.campo.data.db.EstimationStatus
import com.uniban.campo.data.db.ParcelEntity
import com.uniban.campo.data.repo.ServiceLocator
import com.uniban.campo.ml.CropType
import com.uniban.campo.ml.EstimationProgress
import com.uniban.campo.ml.EstimationResult
import com.uniban.campo.ml.PlantCounter
import com.uniban.campo.ml.YoloTfliteDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

data class EstimateUiState(
    val selectedParcelId: String? = null,
    val cropType: String = CropType.PLATANO,
    val areaText: String = "",
    val imageUri: Uri? = null,
    val imageLabel: String? = null,
    val imageSizePx: Pair<Int, Int>? = null,
    val running: Boolean = false,
    val progress: EstimationProgress? = null,
    val result: EstimationResult? = null,
    val savedEstimationId: String? = null,
    val error: String? = null
)

class EstimateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.repository
    private var plantCounter: PlantCounter? = null

    val parcels: StateFlow<List<ParcelEntity>> = repository.parcels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ui = MutableStateFlow(EstimateUiState())
    val ui: StateFlow<EstimateUiState> = _ui.asStateFlow()

    val isModelAvailable: Boolean
        get() = YoloTfliteDetector.isModelAvailable(getApplication())

    fun selectParcel(id: String) {
        _ui.update { state ->
            val parcel = parcels.value.find { it.id == id }
            val area = state.areaText.toFloatOrNull()
            state.copy(
                selectedParcelId = id,
                areaText = if (area != null && parcel != null && area > parcel.areaHa) "" else state.areaText
            )
        }
    }

    fun selectCropType(cropType: String) {
        _ui.update { it.copy(cropType = cropType) }
    }

    fun setArea(text: String) {
        _ui.update { it.copy(areaText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.'), error = null) }
    }

    fun selectImage(uri: Uri, label: String, sizePx: Pair<Int, Int>?) {
        _ui.update {
            it.copy(imageUri = uri, imageLabel = label, imageSizePx = sizePx, result = null, error = null, savedEstimationId = null)
        }
    }

    fun startEstimation() {
        val state = _ui.value
        val parcel = parcels.value.find { it.id == state.selectedParcelId }
        val area = state.areaText.toFloatOrNull()

        if (parcel == null) {
            _ui.update { it.copy(error = "Seleccione una parcela.") }
            return
        }
        if (area == null || area <= 0f) {
            _ui.update { it.copy(error = "Ingrese un área válida en hectáreas.") }
            return
        }
        if (area > parcel.areaHa) {
            _ui.update { it.copy(error = "El área no puede superar la superficie de la parcela (${parcel.areaHa} ha).") }
            return
        }
        if (state.imageUri == null) {
            _ui.update { it.copy(error = "Seleccione una imagen (jpg, jpeg, tif, tiff o bmp).") }
            return
        }
        if (!isModelAvailable) {
            _ui.update { it.copy(error = "El modelo YOLO no está disponible. Coloque model.tflite en assets/models/.") }
            return
        }

        val uri = state.imageUri
        val estimationId = UUID.randomUUID().toString()
        val cropType = state.cropType

        _ui.update {
            it.copy(running = true, error = null, result = null, savedEstimationId = null, progress = EstimationProgress("Iniciando…", 0f))
        }

        viewModelScope.launch {
            try {
                val counter = plantCounter ?: PlantCounter(getApplication()).also { plantCounter = it }
                val result = withContext(Dispatchers.Default) {
                    counter.estimate(uri, estimationId, area) { progress ->
                        _ui.update { s -> s.copy(progress = progress) }
                    }
                }

                val entity = CropEstimationEntity(
                    id = estimationId,
                    date = LocalDate.now().toString(),
                    inputImg = result.inputImagePath,
                    measuredArea = area,
                    estimatedPlants = result.totalPlants,
                    confidence = result.avgConfidence,
                    outputImg = result.outputImagePath,
                    parcelId = parcel.id,
                    status = EstimationStatus.PENDING
                )
                repository.insertEstimation(entity)
                ServiceLocator.syncManager.syncNow()

                _ui.update {
                    it.copy(
                        running = false,
                        progress = null,
                        result = result,
                        savedEstimationId = estimationId
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        running = false,
                        progress = null,
                        error = e.message ?: "Error al procesar la imagen"
                    )
                }
            }
        }
    }

    fun reset() {
        _ui.update {
            EstimateUiState(
                selectedParcelId = it.selectedParcelId,
                cropType = it.cropType
            )
        }
    }

    override fun onCleared() {
        plantCounter?.close()
        super.onCleared()
    }
}
