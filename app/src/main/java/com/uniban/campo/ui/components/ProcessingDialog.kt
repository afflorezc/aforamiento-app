package com.uniban.campo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uniban.campo.ui.theme.BorderSubtle
import com.uniban.campo.ui.theme.GreenAccent
import com.uniban.campo.ui.theme.SurfaceCard
import com.uniban.campo.ui.theme.TextPrimary
import com.uniban.campo.ui.theme.TextSecondary

/**
 * Diálogo modal que muestra el avance del procesamiento (etapa + barra de progreso)
 * mientras se ejecuta la estimación, para que no pase desapercibido.
 */
@Composable
fun ProcessingDialog(
    stage: String,
    fraction: Float?,
    title: String = "Generando estimación…"
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.Spa,
                    contentDescription = null,
                    tint = GreenAccent,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = GreenAccent,
                        trackColor = BorderSubtle
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = GreenAccent,
                        trackColor = BorderSubtle
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
