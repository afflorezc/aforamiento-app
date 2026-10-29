package com.uniban.campo.ui.estimate

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniban.campo.data.db.EstimationStatus
import com.uniban.campo.ml.CropType
import com.uniban.campo.ui.components.AppCard
import com.uniban.campo.ui.components.BannerType
import com.uniban.campo.ui.components.ImagePreviewDialog
import com.uniban.campo.ui.components.InfoBanner
import com.uniban.campo.ui.components.LabeledTextField
import com.uniban.campo.ui.components.PrimaryButton
import com.uniban.campo.ui.components.ProcessingDialog
import com.uniban.campo.ui.components.SecondaryButton
import com.uniban.campo.ui.components.SectionLabel
import com.uniban.campo.ui.components.StatusChip
import com.uniban.campo.ui.components.ThumbnailImage
import com.uniban.campo.ui.theme.BorderSubtle
import com.uniban.campo.ui.theme.DetectionBanano
import com.uniban.campo.ui.theme.DetectionPlatano
import com.uniban.campo.ui.theme.GreenAccent
import com.uniban.campo.ui.theme.SuccessBg
import com.uniban.campo.ui.theme.SurfaceCard
import com.uniban.campo.ui.theme.TextPrimary
import com.uniban.campo.ui.theme.TextSecondary
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EstimateScreen(viewModel: EstimateViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val parcels by viewModel.parcels.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var parcelMenuExpanded by remember { mutableStateOf(false) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf("") }

    val selectedParcel = parcels.find { it.id == ui.selectedParcelId }
    val areaValue = ui.areaText.toFloatOrNull()
    val areaError = when {
        ui.error?.contains("área") == true || ui.error?.contains("Area") == true -> ui.error
        selectedParcel != null && areaValue != null && areaValue > selectedParcel.areaHa ->
            "No puede superar ${selectedParcel.areaHa} ha"
        else -> null
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imagen"
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
            } catch (_: Exception) {
            }
            val size = if (bounds.outWidth > 0) bounds.outWidth to bounds.outHeight else null
            viewModel.selectImage(uri, name, size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Nueva estimación",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Text(
            text = "Conteo de plantas de plátano o banano a partir de una foto UAV en posición nadir.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (!viewModel.isModelAvailable) {
            InfoBanner(
                type = BannerType.WARNING,
                title = "Modelo no disponible",
                text = "Exporte el .pt con 'yolo export model=model.pt format=tflite imgsz=864' y coloque model.tflite en assets/models/."
            )
        }

        // Parcela
        AppCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionLabel("Parcela")
                ExposedDropdownMenuBox(
                    expanded = parcelMenuExpanded,
                    onExpandedChange = { parcelMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedParcel?.let { "${it.name} (${it.areaHa} ha)" } ?: "Seleccione una parcela",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = parcelMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard,
                            focusedBorderColor = GreenAccent,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = if (selectedParcel == null) TextSecondary else TextPrimary,
                            cursorColor = GreenAccent
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = parcelMenuExpanded,
                        onDismissRequest = { parcelMenuExpanded = false }
                    ) {
                        if (parcels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Sin parcelas. Sincronice desde Inicio.", color = TextSecondary) },
                                onClick = { parcelMenuExpanded = false }
                            )
                        }
                        parcels.forEach { parcel ->
                            DropdownMenuItem(
                                text = { Text("${parcel.name} · ${parcel.areaHa} ha") },
                                onClick = {
                                    viewModel.selectParcel(parcel.id)
                                    parcelMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                SectionLabel("Tipo de plantación")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CropFilterChip(
                        label = "Plátano",
                        selected = ui.cropType == CropType.PLATANO,
                        onClick = { viewModel.selectCropType(CropType.PLATANO) },
                        modifier = Modifier.weight(1f)
                    )
                    CropFilterChip(
                        label = "Banano",
                        selected = ui.cropType == CropType.BANANO,
                        onClick = { viewModel.selectCropType(CropType.BANANO) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))

                LabeledTextField(
                    label = "Área a estimar (ha)",
                    value = ui.areaText,
                    onValueChange = viewModel::setArea,
                    placeholder = selectedParcel?.let { "Máximo ${selectedParcel.areaHa}" } ?: "Ej. 2.40",
                    keyboardType = KeyboardType.Decimal,
                    errorText = areaError
                )
            }
        }

        // Imagen
        AppCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionLabel("Imagen UAV (nadir)")
                Text(
                    text = "Formatos: jpg, jpeg, png, tif, tiff, bmp",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                SecondaryButton(
                    text = if (ui.imageUri == null) "Seleccionar imagen" else "Cambiar imagen",
                    icon = Icons.Outlined.AddPhotoAlternate,
                    onClick = {
                        imagePicker.launch(
                            arrayOf(
                                "image/jpeg",
                                "image/png",
                                "image/bmp",
                                "image/tiff",
                                "image/*"
                            )
                        )
                    }
                )

                if (ui.imageUri != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThumbnailImage(
                            model = ui.imageUri,
                            contentDescription = "Imagen seleccionada",
                            modifier = Modifier
                                .size(84.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ui.imageLabel ?: "Imagen seleccionada",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                maxLines = 2
                            )
                            ui.imageSizePx?.let { (w, h) ->
                                Text(
                                    text = "${w} × ${h} px" +
                                        if (maxOf(w, h) > com.uniban.campo.ml.YoloConfig.TILE_THRESHOLD_PX) " · se procesará por tiles" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (ui.error != null && areaError == null) {
            InfoBanner(BannerType.ERROR, ui.error ?: "")
        }

        PrimaryButton(
            text = if (ui.running) "Generando…" else "Generar estimación",
            loading = ui.running,
            enabled = !ui.running && viewModel.isModelAvailable,
            onClick = viewModel::startEstimation
        )

        if (ui.running) {
            ProcessingDialog(
                stage = ui.progress?.stage ?: "Procesando…",
                fraction = ui.progress?.fraction
            )
        }

        ui.result?.let { result ->
            AppCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Resultado",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.weight(1f))
                        StatusChip(EstimationStatus.PENDING)
                    }
                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = result.totalPlants.toString(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "plantas estimadas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    if (result.totalPlants == 0) {
                        Spacer(Modifier.height(10.dp))
                        InfoBanner(
                            type = BannerType.WARNING,
                            text = "No se detectaron plantas. Verifique que el modelo corresponde a la parcela fotografiada, " +
                                "que la imagen es un nadir nítido y revise el logcat (etiquetas YoloDetector / PlantCounter) para ver los puntajes crudos."
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResultStat(icon = Icons.Outlined.Spa, label = "Plátano", value = result.platanoCount.toString(), tint = DetectionPlatano)
                        ResultStat(icon = Icons.Outlined.Spa, label = "Banano", value = result.bananoCount.toString(), tint = DetectionBanano)
                        ResultStat(
                            icon = Icons.Outlined.Verified,
                            label = "Confianza",
                            value = "%.0f%%".format(result.avgConfidence * 100),
                            tint = GreenAccent
                        )
                        ResultStat(
                            icon = Icons.Outlined.PieChart,
                            label = "Densidad",
                            value = if (areaValue != null && areaValue > 0) "%.0f /ha".format(result.totalPlants / areaValue) else "—",
                            tint = GreenAccent
                        )
                        ResultStat(
                            icon = Icons.Outlined.FilterNone,
                            label = "Tiles",
                            value = result.tilesUsed.toString(),
                            tint = GreenAccent
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    SectionLabel("Imagen de salida con detecciones")
                    Surface(
                        onClick = {
                            previewPath = result.outputImagePath
                            previewTitle = "Salida · ${result.totalPlants} plantas"
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        ThumbnailImage(
                            model = result.outputImagePath,
                            contentDescription = "Imagen de salida",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Nueva estimación",
                        onClick = viewModel::reset
                    )
                }
            }
        }

        if (previewPath != null) {
            ImagePreviewDialog(
                filePath = previewPath ?: "",
                title = previewTitle,
                onDismiss = { previewPath = null }
            )
        }
    }
}

@Composable
private fun CropFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SuccessBg else SurfaceCard,
        border = BorderStroke(1.dp, if (selected) GreenAccent else BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Spa,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) GreenAccent else TextSecondary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) GreenAccent else TextSecondary
            )
        }
    }
}

@Composable
private fun ResultStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
