package com.gamemodeai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameModeAITheme {
                GameModeScreen(
                    context = this,
                    onToggle = { activate, onResult ->
                        lifecycleScope.launch {
                            val success = if (activate) {
                                ShizukuHelper.enableGameMode()
                            } else {
                                ShizukuHelper.disableGameMode()
                            }
                            onResult(success)
                        }
                    }
                )
            }
        }
    }
}

fun getAvailableRamMb(context: Context): Long {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    return memInfo.availMem / (1024 * 1024)
}

@Composable
fun GameModeScreen(
    context: Context,
    onToggle: (activate: Boolean, onResult: (Boolean) -> Unit) -> Unit
) {
    var isActive by remember { mutableStateOf(Prefs.isActive(context)) }
    var ramMb by remember { mutableStateOf(getAvailableRamMb(context)) }
    var shizukuStatus by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shizukuStatus = when {
            !ShizukuHelper.isShizukuAvailable() -> "Shizuku no disponible"
            !ShizukuHelper.hasPermission() -> "Sin permiso Shizuku"
            else -> "Shizuku listo"
        }
        ramMb = getAvailableRamMb(context)
    }

    val activeColor = Color(0xFF1B5E20)
    val inactiveColor = Color(0xFF37474F)
    val accentGreen = Color(0xFF43A047)
    val accentRed = Color(0xFFC62828)

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "GameModeAI",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Samsung Galaxy A06 · Free Fire",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            // Estado principal
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) activeColor else inactiveColor
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MODO JUEGO",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Aplicando optimizaciones...",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = if (isActive) "ACTIVO" else "INACTIVO",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Info del sistema
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow(label = "RAM Libre", value = "$ramMb MB")
                    InfoRow(label = "Sistema", value = "Android ${Build.VERSION.RELEASE}")
                    InfoRow(
                        label = "Shizuku",
                        value = shizukuStatus,
                        valueColor = if (shizukuStatus.contains("listo")) accentGreen
                                     else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Optimizaciones activas
            if (isActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "OPTIMIZACIONES ACTIVAS",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        OptimizationItem("Animaciones del sistema desactivadas")
                        OptimizationItem("Respuesta táctil mejorada (aim más estable)")
                        OptimizationItem("Notificaciones silenciadas en juego")
                        OptimizationItem("WiFi sin cortes")
                        OptimizationItem("RAM liberada para Free Fire")
                        OptimizationItem("Renderizado por GPU forzado")
                    }
                }
            }

            // Botón principal
            Button(
                onClick = {
                    if (isLoading) return@Button
                    val activate = !isActive
                    isLoading = true
                    onToggle(activate) { success ->
                        isLoading = false
                        if (success) {
                            isActive = activate
                            Prefs.setActive(context, activate)
                            if (activate) {
                                GameService.start(context)
                            } else {
                                GameService.stop(context)
                            }
                            ramMb = getAvailableRamMb(context)
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) accentRed else accentGreen,
                    disabledContainerColor = Color(0xFF37474F)
                )
            ) {
                Text(
                    text = when {
                        isLoading -> "APLICANDO..."
                        isActive -> "DESACTIVAR MODO"
                        else -> "ACTIVAR MODO JUEGO"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Activa el modo antes de abrir Free Fire",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun OptimizationItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "✓", fontSize = 13.sp, color = Color(0xFF66BB6A))
        Text(text = text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
