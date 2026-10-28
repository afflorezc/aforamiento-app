package com.uniban.campo.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniban.campo.ui.components.AppCard
import com.uniban.campo.ui.components.EmptyState
import com.uniban.campo.ui.components.ImagePreviewDialog
import com.uniban.campo.ui.components.StatusChip
import com.uniban.campo.ui.components.ThumbnailImage
import com.uniban.campo.ui.theme.BorderSubtle
import com.uniban.campo.ui.theme.GreenPrimary
import com.uniban.campo.ui.theme.TextPrimary
import com.uniban.campo.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var previewPath by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 8.dp)) {
            Text(
                text = "Historial",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = "${items.size} estimación(es) registradas",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = "Sin estimaciones",
                message = "Genere una estimación desde la pestaña Estimar y aparecerá aquí."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.estimation.id }) { item ->
                    HistoryCard(
                        item = item,
                        onOpenImage = { path, title ->
                            previewPath = path
                            previewTitle = title
                        }
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
private fun HistoryCard(
    item: HistoryItem,
    onOpenImage: (String, String) -> Unit
) {
    val estimation = item.estimation
    val date = remember(estimation.date) {
        try {
            LocalDate.parse(estimation.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("es")))
                .replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            estimation.date
        }
    }

    AppCard {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = {
                    onOpenImage(estimation.outputImg, "Estimación $date")
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                ThumbnailImage(
                    model = estimation.outputImg,
                    contentDescription = "Imagen de salida",
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.parcel?.name ?: "Parcela no encontrada",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Numbers,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = GreenPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${estimation.estimatedPlants} plantas",
                        style = MaterialTheme.typography.labelLarge,
                        color = GreenPrimary
                    )
                    if (estimation.confidence != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "· %.0f%% conf.".format(estimation.confidence * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            StatusChip(estimation.status)
        }
    }
}
