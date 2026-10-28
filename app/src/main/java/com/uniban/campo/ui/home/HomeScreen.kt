package com.uniban.campo.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniban.campo.ui.components.AppCard
import com.uniban.campo.ui.components.BannerType
import com.uniban.campo.ui.components.IconChip
import com.uniban.campo.ui.components.InfoBanner
import com.uniban.campo.ui.components.StatCard
import com.uniban.campo.ui.theme.BorderSubtle
import com.uniban.campo.ui.theme.GreenPrimary
import com.uniban.campo.ui.theme.SurfaceCard
import com.uniban.campo.ui.theme.TextSecondary
import com.uniban.campo.ui.theme.Warning

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val parcels by viewModel.parcelsCount.collectAsStateWithLifecycle()
    val estimations by viewModel.estimationsCount.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    val sync by viewModel.syncStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Encabezado verde
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenPrimary)
                .padding(start = 20.dp, end = 8.dp, top = 48.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconChip(Icons.Outlined.Agriculture, modifier = Modifier.size(44.dp), tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hola, ${viewModel.userEmail.substringBefore('@')}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unibán Campo · Gestión agrícola",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                sync.lastSyncAt?.let {
                    Text(
                        text = "Última sincronización: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            IconButton(onClick = {
                viewModel.logout()
                onLogout()
            }) {
                Icon(
                    Icons.Outlined.Logout,
                    contentDescription = "Cerrar sesión",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            if (!viewModel.isModelAvailable) {
                InfoBanner(
                    type = BannerType.WARNING,
                    title = "Modelo YOLO no configurado",
                    text = "Coloque el modelo exportado en assets/models/model.tflite para habilitar las estimaciones.",
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            if (pending > 0) {
                InfoBanner(
                    type = BannerType.WARNING,
                    title = "$pending estimación(es) pendientes",
                    text = "Se enviarán automáticamente al servidor cuando haya conexión.",
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            if (sync.lastError != null && sync.status == com.uniban.campo.data.repo.SyncStateStatus.ERROR) {
                InfoBanner(
                    type = BannerType.ERROR,
                    title = "Error de sincronización",
                    text = sync.lastError ?: "",
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Parcelas",
                    value = parcels.toString(),
                    icon = Icons.Outlined.Map,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Estimaciones",
                    value = estimations.toString(),
                    icon = Icons.AutoMirrored.Outlined.List,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(20.dp))

            Text(
                text = "ACCESOS RÁPIDOS",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
                modifier = Modifier.height(220.dp)
            ) {
                items(
                    listOf(
                        QuickAction("Nueva estimación", Icons.Outlined.ImageSearch) { onNavigate("estimate") },
                        QuickAction("Historial", Icons.Outlined.History) { onNavigate("history") },
                        QuickAction("Parcelas", Icons.Outlined.Map) { onNavigate("parcels") },
                        QuickAction("Sincronizar", Icons.Outlined.CloudSync) { viewModel.syncNow() }
                    )
                ) { action ->
                    QuickActionTile(action)
                }
            }

            Spacer(Modifier.height(16.dp))
            AppCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Conteo de plantas de plátano/banano", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Genere una estimación a partir de una foto UAV en posición nadir. " +
                            "Para ortomosaicos grandes el procesamiento se realiza por tiles con " +
                            "superposición para evitar contar la misma planta dos veces.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionTile(action: QuickAction) {
    Surface(
        onClick = action.onClick,
        shape = MaterialTheme.shapes.medium,
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconChip(action.icon, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
