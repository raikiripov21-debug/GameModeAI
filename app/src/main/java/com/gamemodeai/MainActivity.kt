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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

private val BgDark      = Color(0xFF0A0A0A)
private val CardDark    = Color(0xFF141414)
private val GreenBright = Color(0xFF00E676)
private val GreenDark   = Color(0xFF00C853)
private val RedBright   = Color(0xFFFF1744)
private val BlueAcc     = Color(0xFF42A5F5)
private val YellowAcc   = Color(0xFFFFD600)
private val PurpleAcc   = Color(0xFFCE93D8)
private val OrangeAcc   = Color(0xFFFF9800)
private val CyanAcc     = Color(0xFF00E5FF)
private val GreyText    = Color(0xFF757575)

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

    var sensGeneral by remember { mutableIntStateOf(Prefs.getSensGeneral(context)) }
    var sensRedDot  by remember { mutableIntStateOf(Prefs.getSensRedDot(context)) }
    var sens2x      by remember { mutableIntStateOf(Prefs.getSens2x(context)) }
    var sens4x      by remember { mutableIntStateOf(Prefs.getSens4x(context)) }
    var sensSniper  by remember { mutableIntStateOf(Prefs.getSensSniper(context)) }

    LaunchedEffect(Unit) {
        shizuku = when {
            !ShizukuHelper.isShizukuAvailable() -> "No disponible"
            !ShizukuHelper.hasPermission()       -> "Sin permiso"
            else                                  -> "Listo"
        }
        ramFree = getAvailableRamMb(context)
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) { ramFree = getAvailableRamMb(context); delay(8_000) }
        }
    }

    val ramPct = if (totalRam > 0) ramFree.toFloat() / totalRam.toFloat() else 0f
    val ramColor = when {
        ramPct > 0.4f -> GreenBright
        ramPct > 0.2f -> YellowAcc
        else          -> RedBright
    }

    Scaffold(containerColor = BgDark, modifier = Modifier.fillMaxSize()) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Header ────────────────────────────────────────────────────────
            Text("GameModeAI", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Galaxy A06  ·  Free Fire  ·  sin anticheat", fontSize = 11.sp, color = GreyText)

            // ── Estado ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isActive)
                            Brush.horizontalGradient(listOf(Color(0xFF0D3B1E), Color(0xFF1B5E20)))
                        else
                            Brush.horizontalGradient(listOf(Color(0xFF141414), Color(0xFF1E1E1E)))
                    )
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MODO JUEGO", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    if (isLoading) {
                        CircularProgressIndicator(color = GreenBright,
                            modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                        Text("Aplicando 35+ optimizaciones...", fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f))
                    } else {
                        Text(if (isActive) "● ACTIVO" else "○ INACTIVO",
                            fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            color = if (isActive) GreenBright else GreyText)
                        if (isActive)
                            Text("GOS desactivado · Mira sin saltos · A06 optimizado",
                                fontSize = 11.sp, color = GreenBright.copy(alpha = 0.65f))
                    }
                }
            }

            // ── RAM + estado ──────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RAM Libre", fontSize = 13.sp, color = GreyText)
                        Text("$ramFree MB / $totalRam MB", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = ramColor)
                    }
                    LinearProgressIndicator(
                        progress = { ramPct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = ramColor, trackColor = Color(0xFF222222))
                    if (isActive && ramBefore > 0) {
                        val freed = ramFree - ramBefore
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RAM ganada", fontSize = 12.sp, color = GreyText)
                            Text("${if (freed >= 0) "+" else ""}$freed MB", fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (freed >= 0) GreenBright else RedBright)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1E1E1E))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Shizuku", fontSize = 12.sp, color = GreyText)
                        Text(shizuku, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (shizuku == "Listo") GreenBright else RedBright)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Android", fontSize = 12.sp, color = GreyText)
                        Text("${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            // ── SLIDERS DE SENSIBILIDAD ───────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0018)),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("SENSIBILIDAD DE MIRA", fontSize = 10.sp,
                            color = PurpleAcc, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("se guarda sola", fontSize = 10.sp, color = GreyText)
                    }
                    Text("Ajusta · prueba en sala · vuelve a ajustar",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
                    Spacer(Modifier.height(8.dp))
                    SensSlider("General",           "rec. 85–95",  sensGeneral, 60,  100, PurpleAcc) { sensGeneral = it; Prefs.setSensGeneral(context, it) }
                    SensSlider("Punto rojo / Mira", "rec. 90–100", sensRedDot,  70,  100, RedBright) { sensRedDot  = it; Prefs.setSensRedDot(context, it) }
                    SensSlider("Vista 2x",          "rec. 65–75",  sens2x,      40,   90, BlueAcc)   { sens2x      = it; Prefs.setSens2x(context, it) }
                    SensSlider("Vista 4x",          "rec. 45–55",  sens4x,      20,   70, YellowAcc) { sens4x      = it; Prefs.setSens4x(context, it) }
                    SensSlider("Francotirador",     "rec. 20–30",  sensSniper,   5,   50, OrangeAcc) { sensSniper  = it; Prefs.setSensSniper(context, it) }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color(0xFF1E1E1E))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Si la mira salta al levantar → baja General 5 pts.\n" +
                        "Si va lenta o no gira bien → súbelo 5 pts.\n" +
                        "Prueba siempre en sala de entrenamiento antes de la partida.",
                        fontSize = 11.sp, color = PurpleAcc.copy(alpha = 0.45f)
                    )
                }
            }

            // ── Optimizaciones (solo cuando activo) ───────────────────────────
            if (isActive) {

                // GOS Samsung
                OptCard("⚠ GAME OPTIMIZING SERVICE — DESACTIVADO", CyanAcc, Color(0xFF001820)) {
                    GOSItem("GOS Samsung parado → CPU/GPU al 100 % en todo momento")
                    GOSItem("Game Launcher Samsung parado → no pausa ni limita el juego")
                    GOSItem("Game Tools parado → sin overlay que robe frames")
                    GOSItem("10 servicios Samsung de fondo parados (Bixby, cámara...)")
                    GOSItem("Batería adaptativa OFF → sin límite de CPU por 'hábitos'")
                    GOSItem("Ahorro de energía auto OFF")
                    GOSItem("Gestión térmica agresiva aplazada")
                }

                // AIM
                OptCard("◈ MIRA SIN SALTOS — AIM PROFESIONAL", PurpleAcc, Color(0xFF0E0018)) {
                    AimItem("Touch smoothing Samsung OFF → dedo sin interpolación")
                    AimItem("Ajuste auto de sensibilidad Samsung OFF")
                    AimItem("Rebotes táctiles → 0 ms (sin micro-saltos)")
                    AimItem("Debounce táctil → 0 ms (respuesta instantánea)")
                    AimItem("Eventos táctiles parásitos eliminados")
                    AimItem("Frecuencia de pantalla fija 60 Hz → sin jitter por cambio de Hz")
                    AimItem("Vision Booster OFF → GPU sin procesado extra de color")
                    AimItem("Vibración OFF → dedo más estable sobre la mira")
                    AimItem("GPU: Vulkan optimizado, layers de debug OFF")
                    AimItem("Free Fire en máxima prioridad del scheduler")
                    AimItem("Acelerómetro OFF → sin interrupciones por rotación")
                }

                // A06
                OptCard("⚡ FALLOS SAMSUNG A06 — CORREGIDOS", OrangeAcc, Color(0xFF180900)) {
                    A06Item("Panel lateral → OFF (no se activa al tocar el borde)")
                    A06Item("Navegación → 3 botones (sin conflicto con controles FF)")
                    A06Item("Modo inmersivo forzado → barra de nav oculta siempre")
                    A06Item("Botón lateral doble toque → cámara desactivada")
                    A06Item("Asistente de voz → desactivado")
                    A06Item("Modo una mano → desactivado")
                    A06Item("Prevención toque accidental → OFF")
                    A06Item("WiFi watchdog → OFF (sin cortes de red de 1-2 s)")
                    A06Item("Doze del sistema aplazado durante la partida")
                    A06Item("CPU responsiveness Samsung mejorado")
                }

                // General
                OptCard("✓ RENDIMIENTO GENERAL", BlueAcc, Color(0xFF000D1A)) {
                    OptItem("Animaciones → 0 (cero lag visual)")
                    OptItem("Apps de fondo congeladas")
                    OptItem("Solo 1 proceso extra permitido")
                    OptItem("Sincronización, GPS, notificaciones pausados")
                    OptItem("WiFi estable sin escaneos")
                    OptItem("RAM limpiada dos veces")
                    OptItem("Mantenimiento del sistema aplazado")
                }
            }

            // ── Botón ─────────────────────────────────────────────────────────
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
                            if (activate) { GameService.start(context); ramFree = getAvailableRamMb(context) }
                            else { GameService.stop(context); ramBefore = 0L; ramFree = getAvailableRamMb(context) }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) RedBright else GreenDark,
                    disabledContainerColor = Color(0xFF1E1E1E)
                )
            ) {
                Text(when {
                    isLoading -> "APLICANDO OPTIMIZACIONES..."
                    isActive  -> "DESACTIVAR MODO JUEGO"
                    else      -> "ACTIVAR MODO JUEGO"
                }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // ── Tips ──────────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141000)),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ANTES DE CADA PARTIDA", fontSize = 10.sp,
                        color = YellowAcc.copy(alpha = 0.8f), fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp)
                    TipItem("Activa esta app PRIMERO → luego abre Free Fire")
                    TipItem("Cierra todas las apps del historial reciente")
                    TipItem("Activa modo avión → desactívalo (ping más limpio)")
                    TipItem("Batería mínimo 50 %")
                    TipItem("No uses dos dedos al levantar la mira")
                    TipItem("Gráficos FF: Suave · Velocidad: Máxima · Sombras: OFF")
                }
            }

            Text("Solo ajustes del sistema Android · no toca archivos del juego",
                fontSize = 10.sp, color = GreyText.copy(alpha = 0.45f),
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Slider ────────────────────────────────────────────────────────────────────
@Composable
private fun SensSlider(label: String, hint: String, value: Int, min: Int, max: Int,
                       color: Color, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, fontSize = 10.sp, color = GreyText)
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center) {
                    Text("$value", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(), steps = (max - min) - 1,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = color,
                activeTrackColor = color, inactiveTrackColor = Color(0xFF222222))
        )
    }
}

// ── Items ─────────────────────────────────────────────────────────────────────
@Composable
private fun OptCard(title: String, titleColor: Color, bg: Color,
                    content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 10.sp, color = titleColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable private fun GOSItem(text: String) =
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("◉", fontSize = 11.sp, color = CyanAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) }

@Composable private fun AimItem(text: String) =
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("◈", fontSize = 12.sp, color = PurpleAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) }

@Composable private fun A06Item(text: String) =
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚡", fontSize = 11.sp, color = OrangeAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) }

@Composable private fun OptItem(text: String) =
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✓", fontSize = 12.sp, color = GreenBright)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) }

@Composable private fun TipItem(text: String) =
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("›", fontSize = 13.sp, color = YellowAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)) }

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
