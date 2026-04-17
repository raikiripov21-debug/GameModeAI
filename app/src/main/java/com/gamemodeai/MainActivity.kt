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
import androidx.compose.foundation.shape.CircleShape
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
import java.io.File
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

// ── Lecturas del sistema ──────────────────────────────────────────────────────

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

fun readCpuFreqMhz(): Int {
    return try {
        val freq = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            .readText().trim().toLong()
        (freq / 1000).toInt()
    } catch (e: Exception) { 0 }
}

fun readMaxCpuFreqMhz(): Int {
    return try {
        val freq = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            .readText().trim().toLong()
        (freq / 1000).toInt()
    } catch (e: Exception) { 0 }
}

fun readCpuTempC(): Float {
    val zones = listOf(
        "/sys/class/thermal/thermal_zone5/temp",
        "/sys/class/thermal/thermal_zone4/temp",
        "/sys/class/thermal/thermal_zone3/temp",
        "/sys/class/thermal/thermal_zone1/temp",
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/power_supply/battery/temp"
    )
    for (path in zones) {
        try {
            val raw = File(path).readText().trim().toFloat()
            val temp = if (raw > 1000f) raw / 1000f else raw
            if (temp in 15f..80f) return temp
        } catch (_: Exception) {}
    }
    return 0f
}

// ── Paleta ────────────────────────────────────────────────────────────────────
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
    val maxFreq   = remember { readMaxCpuFreqMhz() }
    var shizuku   by remember { mutableStateOf("Verificando...") }

    // Monitor en tiempo real
    var fps        by remember { mutableIntStateOf(0) }
    var cpuMhz     by remember { mutableIntStateOf(0) }
    var cpuTemp    by remember { mutableStateOf(0f) }
    var fpsCounter by remember { mutableIntStateOf(0) }
    var lastFpsMs  by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Sensibilidades
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

    // Monitor continuo: FPS + CPU + temperatura cada segundo
    LaunchedEffect(isActive) {
        if (!isActive) { fps = 0; cpuMhz = 0; cpuTemp = 0f; return@LaunchedEffect }
        fpsCounter = 0
        lastFpsMs  = System.currentTimeMillis()
        while (isActive) {
            withFrameMillis { }            // espera al siguiente frame
            fpsCounter++
            val now = System.currentTimeMillis()
            if (now - lastFpsMs >= 1000L) {
                fps        = fpsCounter
                fpsCounter = 0
                lastFpsMs  = now
                cpuMhz     = readCpuFreqMhz()
                cpuTemp    = readCpuTempC()
                ramFree    = getAvailableRamMb(context)
            }
        }
    }

    val ramPct = if (totalRam > 0) ramFree.toFloat() / totalRam.toFloat() else 0f
    val ramColor = when {
        ramPct > 0.4f -> GreenBright
        ramPct > 0.2f -> YellowAcc
        else          -> RedBright
    }
    val tempColor = when {
        cpuTemp <= 0f  -> GreyText
        cpuTemp < 38f  -> GreenBright
        cpuTemp < 44f  -> YellowAcc
        else           -> RedBright
    }
    val tempLabel = when {
        cpuTemp <= 0f  -> "—"
        cpuTemp < 38f  -> "Frío"
        cpuTemp < 44f  -> "Tibio"
        else           -> "Caliente"
    }
    val freqPct = if (maxFreq > 0) cpuMhz.toFloat() / maxFreq.toFloat() else 0f
    val freqColor = when {
        freqPct > 0.75f -> GreenBright
        freqPct > 0.45f -> YellowAcc
        else            -> RedBright
    }
    val fpsColor = when {
        fps >= 58 -> GreenBright
        fps >= 45 -> YellowAcc
        fps > 0   -> RedBright
        else      -> GreyText
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
                        Text("Aplicando 40+ optimizaciones...", fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f))
                    } else {
                        Text(if (isActive) "● ACTIVO" else "○ INACTIVO",
                            fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            color = if (isActive) GreenBright else GreyText)
                        if (isActive)
                            Text("GOS·NFC·GPS off · Mira sin saltos · Temp controlada",
                                fontSize = 11.sp, color = GreenBright.copy(alpha = 0.6f))
                    }
                }
            }

            // ── MONITOR EN TIEMPO REAL ────────────────────────────────────────
            if (isActive) {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1400)),
                    shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("MONITOR EN VIVO", fontSize = 10.sp,
                                color = GreenBright.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            // punto pulsante de grabación
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(GreenBright))
                        }

                        // FPS · CPU · TEMP en una fila
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly) {

                            // FPS
                            MonitorMetric(
                                value   = if (fps > 0) "$fps" else "—",
                                label   = "FPS app",
                                hint    = if (fps >= 58) "Fluido" else if (fps > 0) "Bajo" else "—",
                                color   = fpsColor
                            )

                            // CPU
                            MonitorMetric(
                                value   = if (cpuMhz > 0) "$cpuMhz" else "—",
                                label   = "CPU MHz",
                                hint    = if (cpuMhz > 0 && maxFreq > 0)
                                              "${(freqPct * 100).roundToInt()}%"
                                          else "—",
                                color   = freqColor
                            )

                            // Temperatura
                            MonitorMetric(
                                value   = if (cpuTemp > 0f) "${cpuTemp.roundToInt()}°" else "—",
                                label   = "Temperatura",
                                hint    = tempLabel,
                                color   = tempColor
                            )

                            // RAM
                            MonitorMetric(
                                value   = "$ramFree",
                                label   = "RAM MB",
                                hint    = "${(ramPct * 100).roundToInt()}% libre",
                                color   = ramColor
                            )
                        }

                        // Barra de CPU
                        if (cpuMhz > 0 && maxFreq > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CPU $cpuMhz MHz",
                                        fontSize = 11.sp, color = GreyText)
                                    Text("máx $maxFreq MHz",
                                        fontSize = 11.sp, color = GreyText)
                                }
                                LinearProgressIndicator(
                                    progress = { freqPct.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = freqColor, trackColor = Color(0xFF1A1A1A))
                            }
                        }

                        // Advertencia de temperatura
                        if (cpuTemp >= 44f) {
                            Row(Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedBright.copy(alpha = 0.12f))
                                .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠", fontSize = 16.sp)
                                Text("Temperatura alta — el chip puede throttlear.\nPausa 2-3 min o reduce el brillo.",
                                    fontSize = 11.sp, color = RedBright.copy(alpha = 0.85f))
                            }
                        }

                        Text("FPS = rendimiento de la interfaz · la CPU a >75% garantiza frames estables en FF",
                            fontSize = 10.sp, color = GreyText.copy(alpha = 0.45f))
                    }
                }
            }

            // ── RAM + estado sistema ──────────────────────────────────────────
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
                        "Prueba siempre en sala de entrenamiento.",
                        fontSize = 11.sp, color = PurpleAcc.copy(alpha = 0.4f)
                    )
                }
            }

            // ── Optimizaciones (solo cuando activo) ───────────────────────────
            if (isActive) {

                // Temperatura
                OptCard("🌡 ANTI-CALENTAMIENTO — ACTIVO", OrangeAcc, Color(0xFF180900)) {
                    TempItem("NFC desactivado — chip NFC genera calor aunque no lo uses")
                    TempItem("Pantalla al 45 % — el panel LCD/OLED es la mayor fuente de calor")
                    TempItem("GPS OFF — módulo GPS calienta en segundo plano")
                    TempItem("WiFi: sin escaneos activos — antena WiFi fría")
                    TempItem("Bluetooth: modo escaneo OFF")
                    TempItem("AOD OFF — panel encendido 24/7 = calor constante")
                    TempItem("Rotación auto OFF — acelerómetro sin interrupciones")
                    TempItem("Sincronización OFF — radio de datos fría")
                    TempItem("10 servicios Samsung calientes → parados")
                    TempItem("RAM limpiada — menos apps = menos calor de CPU")
                }

                // GOS
                OptCard("◉ GAME OPTIMIZING SERVICE — PARADO", CyanAcc, Color(0xFF001820)) {
                    GOSItem("GOS parado → CPU/GPU al 100 % sin throttling de software")
                    GOSItem("Game Launcher parado → no pausa ni limita el juego")
                    GOSItem("Game Tools parado → sin overlay que robe frames")
                    GOSItem("Batería adaptativa OFF → sin límite de CPU por hábitos")
                    GOSItem("Ahorro automático OFF")
                    GOSItem("(Protección HARDWARE del kernel sigue activa — el chip no se daña)")
                }

                // AIM
                OptCard("◈ MIRA SIN SALTOS — AIM PROFESIONAL", PurpleAcc, Color(0xFF0E0018)) {
                    AimItem("Touch smoothing Samsung OFF → dedo sin interpolación")
                    AimItem("Ajuste auto de sensibilidad Samsung OFF")
                    AimItem("Rebotes táctiles → 0 ms (sin micro-saltos)")
                    AimItem("Debounce táctil → 0 ms (respuesta instantánea)")
                    AimItem("Frecuencia fija 60 Hz → sin jitter por cambio de Hz")
                    AimItem("Vision Booster OFF → GPU sin procesado extra de color")
                    AimItem("Vibración OFF → dedo más estable")
                    AimItem("Free Fire en máxima prioridad del scheduler")
                    AimItem("GPU: Vulkan optimizado, layers de debug OFF")
                }

                // A06
                OptCard("⚡ FALLOS SAMSUNG A06 — CORREGIDOS", YellowAcc, Color(0xFF140D00)) {
                    A06Item("Panel lateral → OFF (no se activa al tocar el borde)")
                    A06Item("Navegación → 3 botones (sin conflicto con controles FF)")
                    A06Item("Modo inmersivo forzado → barra de nav siempre oculta")
                    A06Item("Botón lateral doble toque → cámara desactivada")
                    A06Item("Asistente de voz → desactivado")
                    A06Item("Prevención toque accidental → OFF")
                    A06Item("WiFi watchdog → OFF (sin cortes de red de 1-2 s)")
                    A06Item("Doze del sistema aplazado durante la partida")
                }

                // General
                OptCard("✓ RENDIMIENTO GENERAL", BlueAcc, Color(0xFF000D1A)) {
                    OptItem("Animaciones → 0 (cero lag visual)")
                    OptItem("Apps de fondo congeladas")
                    OptItem("Solo 1 proceso extra permitido")
                    OptItem("Notificaciones emergentes desactivadas")
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
                    TipItem("Batería mínimo 50 % y no cargando si puedes")
                    TipItem("Si hay aviso de temperatura: pausa 2 min antes de continuar")
                    TipItem("Gráficos FF: Suave · Velocidad: Máxima · Sombras: OFF")
                }
            }

            Text("Solo ajustes del sistema Android · no toca archivos del juego",
                fontSize = 10.sp, color = GreyText.copy(alpha = 0.4f),
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Métrica del monitor ───────────────────────────────────────────────────────
@Composable
private fun MonitorMetric(value: String, label: String, hint: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = GreyText)
        Text(hint,  fontSize = 10.sp, color = color.copy(alpha = 0.7f))
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

// ── Items de tarjetas ─────────────────────────────────────────────────────────
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

@Composable private fun TempItem(text: String) =
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("❄", fontSize = 11.sp, color = CyanAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) }

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
        Text("⚡", fontSize = 11.sp, color = YellowAcc)
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
