package com.uniban.campo.ui.parcels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniban.campo.data.repo.SyncStateStatus
import com.uniban.campo.ui.components.AppCard
import com.uniban.campo.ui.components.EmptyState
import com.uniban.campo.ui.components.IconChip
import com.uniban.campo.ui.theme.BorderSubtle
import com.uniban.campo.ui.theme.GreenAccent
import com.uniban.campo.ui.theme.TextPrimary
import com.uniban.campo.ui.theme.TextSecondary

@Composable
fun ParcelsScreen(viewModel: ParcelsViewModel) {
    val parcels by viewModel.parcels.collectAsStateWithLifecycle()
    val sync by viewModel.syncStatus.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Parcelas",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "${parcels.size} parcela(s) · " +
                        if (sync.status == SyncStateStatus.SYNCING) "sincronizando…"
                        else "última sync: ${sync.lastSyncAt ?: "nunca"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            IconButton(onClick = viewModel::syncNow, enabled = sync.status != SyncStateStatus.SYNCING) {
                Icon(
                    Icons.Outlined.CloudSync,
                    contentDescription = "Sincronizar parcelas",
                    tint = GreenAccent
                )
            }
        }

        if (parcels.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Map,
                title = "Sin parcelas",
                message = "Toque el ícono de sincronización para descargar el listado del servidor."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(parcels, key = { it.id }) { parcel ->
                    AppCard {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconChip(Icons.Outlined.Map)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(parcel.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.SquareFoot,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = TextSecondary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Superficie: ${parcel.areaHa} ha",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
