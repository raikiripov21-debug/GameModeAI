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
    var ramBefore by remember { mutableStateOf(0L) }
    var ramAfter by remember { mutableStateOf(getAvailableRamMb(context)) }
    var shizukuStatus by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val green = Color(0xFF43A047)
    val red = Color(0xFFC62828)
    val darkGreen = Color(0xFF1B5E20)
    val grey = Color(0xFF37474F)
    val blue = Color(0xFF1A237E)

    LaunchedEffect(Unit) {
        shizukuStatus = when {
            !ShizukuHelper.isShizukuAvailable() -> "Shizuku no disponible"
            !ShizukuHelper.hasPermission() -> "Sin permiso Shizuku"
            else -> "Shizuku listo"
        }
        ramAfter = getAvailableRamMb(context)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            Text("GameModeAI", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Samsung Galaxy A06  ·  Free Fire",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.45f)
            )

            // ── Estado principal ─────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isLoading -> grey
                        isActive  -> darkGreen
                        else      -> grey
                    }
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("MODO JUEGO", fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)

                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text("Aplicando optimizaciones...",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    } else {
                        Text(
                            if (isActive) "ACTIVO" else "INACTIVO",
                            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                }
            }

            // ── Info del sistema ─────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isActive && ramBefore > 0) {
                        val freed = ramAfter - ramBefore
                        val sign = if (freed >= 0) "+" else ""
                        InfoRow("RAM liberada", "${sign}${freed} MB",
                            valueColor = if (freed > 0) green else Color.White)
                    }
                    InfoRow("RAM libre ahora", "$ramAfter MB")
                    InfoRow("Sistema", "Android ${Build.VERSION.RELEASE}")
                    InfoRow(
                        "Shizuku", shizukuStatus,
                        valueColor = if (shizukuStatus.contains("listo")) green
                                     else MaterialTheme.colorScheme.error
                    )
                }
            }

            // ── Optimizaciones activas ───────────────────────────────────────
            if (isActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = blue)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("OPTIMIZACIONES ACTIVAS", fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                        OptItem("Animaciones del sistema eliminadas")
                        OptItem("Respuesta táctil mejorada → aim más preciso")
                        OptItem("Procesos en segundo plano limitados a 1")
                        OptItem("Sincronización automática pausada")
                        OptItem("Notificaciones emergentes silenciadas")
                        OptItem("WiFi y datos siempre activos")
                        OptItem("RAM limpiada dos veces")
                        OptItem("Actividades inactivas destruidas")
                    }
                }
            }

            // ── Botón principal ──────────────────────────────────────────────
            Button(
                onClick = {
                    if (isLoading) return@Button
                    val activate = !isActive
                    if (activate) ramBefore = getAvailableRamMb(context)
                    isLoading = true
                    onToggle(activate) { success ->
                        isLoading = false
                        if (success) {
                            isActive = activate
                            Prefs.setActive(context, activate)
                            if (activate) {
                                GameService.start(context)
                                ramAfter = getAvailableRamMb(context)
                            } else {
                                GameService.stop(context)
                                ramBefore = 0L
                                ramAfter = getAvailableRamMb(context)
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) red else green,
                    disabledContainerColor = grey
                )
            ) {
                Text(
                    when {
                        isLoading -> "APLICANDO..."
                        isActive  -> "DESACTIVAR MODO"
                        else      -> "ACTIVAR MODO JUEGO"
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Activa ANTES de abrir Free Fire para mejor resultado",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun OptItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✓", fontSize = 13.sp, color = Color(0xFF66BB6A))
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
