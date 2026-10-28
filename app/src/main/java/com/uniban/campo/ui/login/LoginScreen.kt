package com.uniban.campo.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uniban.campo.R
import com.uniban.campo.ui.components.BannerType
import com.uniban.campo.ui.components.InfoBanner
import com.uniban.campo.ui.components.LabeledTextField
import com.uniban.campo.ui.components.PrimaryButton
import com.uniban.campo.ui.theme.GreenPrimary
import com.uniban.campo.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onLoggedIn: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Encabezado verde con logo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenPrimary)
                .padding(top = 64.dp, bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Tarjeta blanca superpuesta
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-24).dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Iniciar sesión",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(18.dp))

                    LabeledTextField(
                        label = "Correo electrónico",
                        value = email,
                        onValueChange = {
                            email = it
                            error = null
                        },
                        placeholder = "usuario@unibanagro.com",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                    )
                    Spacer(Modifier.height(14.dp))
                    LabeledTextField(
                        label = "Contraseña",
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        placeholder = "••••••••",
                        password = true,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    )

                    if (error != null) {
                        Spacer(Modifier.height(14.dp))
                        InfoBanner(BannerType.ERROR, error ?: "")
                    }

                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        text = if (loading) "Ingresando…" else "Iniciar sesión",
                        loading = loading,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                error = "Por favor ingrese sus credenciales."
                            } else {
                                error = null
                                loading = true
                                // Cualquier credencial es aceptada (modo demo sin backend de auth)
                                onLoggedIn(email.trim())
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "¿Problemas para ingresar? Contacte a su asesor Unibán.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
