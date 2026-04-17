package com.gamemodeai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
                            val success = if (activate) ShizukuHelper.enableGameMode()
                                          else ShizukuHelper.disableGameMode()
                            onResult(success)
                        }
                    }
                )
            }
        }
    }
}

fun getAvailableRamMb(context: Context): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.availMem / (1024 * 1024)
}

fun getTotalRamMb(context: Context): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.totalMem / (1024 * 1024)
}

// ── Paleta de colores ────────────────────────────────────────────────────────
private val BgDark      = Color(0xFF0D0D0D)
private val CardDark    = Color(0xFF181818)
private val GreenBright = Color(0xFF00E676)
private val GreenDark   = Color(0xFF00C853)
private val RedBright   = Color(0xFFFF1744)
private val BlueDark    = Color(0xFF1565C0)
private val BlueAcc     = Color(0xFF42A5F5)
private val YellowAcc   = Color(0xFFFFD600)
private val PurpleAcc   = Color(0xFFCE93D8)
private val GreyText    = Color(0xFF9E9E9E)

@Composable
fun GameModeScreen(
    context: Context,
    onToggle: (Boolean, (Boolean) -> Unit) -> Unit
) {
    var isActive  by remember { mutableStateOf(Prefs.isActive(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var ramFree   by remember { mutableStateOf(getAvailableRamMb(context)) }
    var ramBefore by remember { mutableStateOf(0L) }
    val totalRam  = remember { getTotalRamMb(context) }
    var shizuku   by remember { mutableStateOf("Verificando...") }

    // Verificar Shizuku al inicio
    LaunchedEffect(Unit) {
        shizuku = when {
            !ShizukuHelper.isShizukuAvailable() -> "No disponible"
            !ShizukuHelper.hasPermission()       -> "Sin permiso"
            else                                  -> "Listo"
        }
        ramFree = getAvailableRamMb(context)
    }

    // Monitoreo de RAM en tiempo real mientras el modo está activo
    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                ramFree = getAvailableRamMb(context)
                delay(8_000)
            }
        }
    }

    val ramPct = if (totalRam > 0) (ramFree.toFloat() / totalRam.toFloat()) else 0f
    val ramColor = when {
        ramPct > 0.4f -> GreenBright
        ramPct > 0.2f -> YellowAcc
        else          -> RedBright
    }

    Scaffold(
        containerColor = BgDark,
        modifier = Modifier.fillMaxSize()
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Header ───────────────────────────────────────────────────────
            Text("GameModeAI", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Galaxy A06  ·  Free Fire", fontSize = 11.sp, color = GreyText)

            // ── Tarjeta de estado ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isActive)
                            Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)))
                        else
                            Brush.horizontalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF2A2A2A)))
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("MODO JUEGO", fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp)
                    if (isLoading) {
                        CircularProgressIndicator(color = GreenBright,
                            modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text("Optimizando sistema...", fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f))
                    } else {
                        Text(
                            if (isActive) "● ACTIVO" else "○ INACTIVO",
                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            color = if (isActive) GreenBright else GreyText
                        )
                        if (isActive) {
                            Text("13 optimizaciones aplicadas · Aim estabilizado", fontSize = 12.sp,
                                color = GreenBright.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // ── RAM en tiempo real ────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RAM Libre", fontSize = 13.sp, color = GreyText)
                        Text("$ramFree MB / $totalRam MB",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ramColor)
                    }

                    LinearProgressIndicator(
                        progress = { ramPct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ramColor,
                        trackColor = Color(0xFF2A2A2A)
                    )

                    if (isActive && ramBefore > 0) {
                        val freed = ramFree - ramBefore
                        val sign = if (freed >= 0) "+" else ""
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RAM ganada al activar", fontSize = 12.sp, color = GreyText)
                            Text("$sign$freed MB", fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (freed >= 0) GreenBright else RedBright)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2A2A2A))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Shizuku", fontSize = 12.sp, color = GreyText)
                        Text(shizuku, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (shizuku == "Listo") GreenBright else RedBright)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Android", fontSize = 12.sp, color = GreyText)
                        Text("${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                            fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            // ── Optimizaciones activas ─────────────────────────────────────────
            if (isActive) {
                // Bloque AIM
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12001F)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {

                        Text("ESTABILIZACIÓN DE MIRA (AIM)", fontSize = 10.sp,
                            color = PurpleAcc.copy(alpha = 0.85f), fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp)

                        val aimOpts = listOf(
                            "Rebotes táctiles eliminados → sin micro-saltos",
                            "Debounce táctil en 0 → respuesta instantánea",
                            "Eventos parásitos bloqueados → mira suave",
                            "Frecuencia de pantalla fija 60 Hz → sin jitter",
                            "Vibración desactivada → dedo más estable",
                            "Zoom de accesibilidad desactivado",
                            "Free Fire en máxima prioridad de proceso",
                            "Renderizado GPU sin capas de debug"
                        )
                        aimOpts.forEach { AimItem(it) }
                    }
                }

                // Bloque rendimiento general
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {

                        Text("RENDIMIENTO GENERAL", fontSize = 10.sp,
                            color = BlueAcc.copy(alpha = 0.7f), fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp)

                        val opts = listOf(
                            "Animaciones eliminadas → 0 lag visual",
                            "CPU en modo rendimiento máximo",
                            "Apps en segundo plano congeladas",
                            "Solo 1 proceso extra permitido",
                            "Sincronización y GPS pausados",
                            "WiFi estable, sin escaneos de red",
                            "Notificaciones emergentes silenciadas",
                            "RAM limpiada dos veces",
                            "Mantenimiento del sistema aplazado"
                        )
                        opts.forEach { OptItem(it) }
                    }
                }
            }

            // ── Botón principal ───────────────────────────────────────────────
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
                                ramFree = getAvailableRamMb(context)
                            } else {
                                GameService.stop(context)
                                ramBefore = 0L
                                ramFree = getAvailableRamMb(context)
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) RedBright else GreenDark,
                    disabledContainerColor = Color(0xFF2A2A2A)
                )
            ) {
                Text(
                    when {
                        isLoading -> "OPTIMIZANDO..."
                        isActive  -> "DESACTIVAR MODO"
                        else      -> "ACTIVAR MODO JUEGO"
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }

            // ── Sensibilidad recomendada para AIM ────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12001F)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    Text("SENSIBILIDAD RECOMENDADA · GALAXY A06",
                        fontSize = 10.sp, color = PurpleAcc.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)

                    Text("Para mira suave sin micro-saltos:", fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f))

                    SensRow("General",           "85 – 95")
                    SensRow("Punto rojo / Mira", "90 – 100")
                    SensRow("Vista 2x",          "65 – 75")
                    SensRow("Vista 4x",          "45 – 55")
                    SensRow("Francotirador",     "20 – 30")

                    HorizontalDivider(color = Color(0xFF2A2A2A))

                    Text("En Free Fire: Gráficos → Suave · Velocidad → Máxima\n" +
                         "Desactivar: efectos clima, sangre y sombras de personaje",
                        fontSize = 11.sp, color = PurpleAcc.copy(alpha = 0.6f))
                }
            }

            // ── Consejos antes de jugar ────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {

                    Text("ANTES DE CADA PARTIDA",
                        fontSize = 10.sp, color = YellowAcc.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)

                    TipItem("Activa esta app primero, luego abre Free Fire")
                    TipItem("Cierra todas las apps del historial")
                    TipItem("Activa modo avión y desactívalo (ping más limpio)")
                    TipItem("Carga mínimo al 50 % de batería")
                    TipItem("No toques la pantalla con dos dedos al levantar la mira")
                }
            }

            Text(
                "Activa ANTES de abrir Free Fire · Galaxy A06",
                fontSize = 10.sp, color = GreyText.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AimItem(text: String) {
    Row(verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("◈", fontSize = 12.sp, color = PurpleAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun OptItem(text: String) {
    Row(verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✓", fontSize = 12.sp, color = GreenBright)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun TipItem(text: String) {
    Row(verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("›", fontSize = 13.sp, color = YellowAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun SensRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PurpleAcc)
    }
}

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
