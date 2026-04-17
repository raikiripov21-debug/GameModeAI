package com.gamemodeai

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// ── Constantes del dispositivo ────────────────────────────────────────────────
const val DEVICE_NAME            = "Samsung Galaxy A06"
const val DEVICE_CHIP            = "Exynos 850"
const val DEVICE_DISPLAY         = "60 Hz LCD"
const val THERMAL_EMERGENCY_C    = 46f   // Umbral de emergencia térmica para A06
const val PHASE2_MINUTES_UI      = 20    // Minutos hasta Fase 2 (debe coincidir con GameService)

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

// ── Lecturas del sistema ──────────────────────────────────────────────────────
fun getAvailableRamMb(context: Context): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info)
    return info.availMem / (1024 * 1024)
}
fun getTotalRamMb(context: Context): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info)
    return info.totalMem / (1024 * 1024)
}
fun readCpuFreqMhz(): Int = try {
    (File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").readText().trim().toLong() / 1000).toInt()
} catch (_: Exception) { 0 }

fun readMaxCpuFreqMhz(): Int = try {
    (File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000).toInt()
} catch (_: Exception) { 0 }

fun readCpuTempC(): Float {
    // Exynos 850: zonas más fiables primero
    listOf(
        "/sys/class/thermal/thermal_zone5/temp",
        "/sys/class/thermal/thermal_zone4/temp",
        "/sys/class/thermal/thermal_zone3/temp",
        "/sys/class/thermal/thermal_zone2/temp",
        "/sys/class/thermal/thermal_zone1/temp",
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/power_supply/battery/temp"
    ).forEach { path ->
        try {
            val raw = File(path).readText().trim().toFloat()
            val t = if (raw > 1000f) raw / 1000f else raw
            if (t in 15f..85f) return t
        } catch (_: Exception) {}
    }
    return 0f
}

fun getBatteryLevel(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
}

fun isCharging(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return status == BatteryManager.BATTERY_STATUS_CHARGING ||
           status == BatteryManager.BATTERY_STATUS_FULL
}

// ── Paleta ────────────────────────────────────────────────────────────────────
private val BgDark      = Color(0xFF0A0A0A)
private val CardDark    = Color(0xFF141414)
private val CardDark2   = Color(0xFF1A1A1A)
private val GreenBright = Color(0xFF00E676)
private val GreenDark   = Color(0xFF00C853)
private val RedBright   = Color(0xFFFF1744)
private val BlueAcc     = Color(0xFF42A5F5)
private val YellowAcc   = Color(0xFFFFD600)
private val OrangeAcc   = Color(0xFFFF9800)
private val CyanAcc     = Color(0xFF00E5FF)
private val TealAcc     = Color(0xFF1DE9B6)
private val GreyText    = Color(0xFF757575)
private val GreyCard    = Color(0xFF2A2A2A)

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background    = BgDark,
            surface       = CardDark,
            primary       = GreenBright,
            onBackground  = Color.White,
            onSurface     = Color.White
        ),
        content = content
    )
}

@Composable
fun GameModeScreen(
    context: Context,
    onToggle: (Boolean, (Boolean) -> Unit) -> Unit
) {
    var isActive     by remember { mutableStateOf(Prefs.isActive(context)) }
    var isLoading    by remember { mutableStateOf(false) }
    var ramFree      by remember { mutableStateOf(getAvailableRamMb(context)) }
    var ramBefore    by remember { mutableStateOf(0L) }
    val totalRam     = remember { getTotalRamMb(context) }
    val maxFreq      = remember { readMaxCpuFreqMhz() }
    var shizukuState by remember { mutableStateOf("Verificando...") }

    var fps          by remember { mutableIntStateOf(0) }
    var cpuMhz       by remember { mutableIntStateOf(0) }
    var cpuTemp      by remember { mutableStateOf(0f) }
    var fpsCounter   by remember { mutableIntStateOf(0) }
    var lastFpsMs    by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var batteryPct   by remember { mutableIntStateOf(getBatteryLevel(context)) }
    var charging     by remember { mutableStateOf(isCharging(context)) }

    var isPhase2         by remember { mutableStateOf(false) }
    var countdown        by remember { mutableStateOf("") }
    var sessionTime      by remember { mutableStateOf("") }
    var thermalEmergency by remember { mutableStateOf(false) }
    var actionMessage    by remember { mutableStateOf("") }

    val thermalScope = rememberCoroutineScope()

    // Emergencia térmica automática
    LaunchedEffect(cpuTemp, isActive) {
        if (!isActive) { thermalEmergency = false; return@LaunchedEffect }
        if (cpuTemp >= THERMAL_EMERGENCY_C && !thermalEmergency) {
            thermalEmergency = true
            thermalScope.launch {
                ShizukuHelper.applyThermalEmergency()
            }
        } else if (cpuTemp in 1f..42f) {
            thermalEmergency = false
        }
    }

    LaunchedEffect(Unit) {
        shizukuState = when {
            !ShizukuHelper.isShizukuAvailable() -> "No disponible"
            !ShizukuHelper.hasPermission()       -> "Sin permiso"
            else                                  -> "Listo"
        }
        ramFree    = getAvailableRamMb(context)
        batteryPct = getBatteryLevel(context)
        charging   = isCharging(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            batteryPct = getBatteryLevel(context)
            charging   = isCharging(context)
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            fps = 0; cpuMhz = 0; cpuTemp = 0f
            isPhase2 = false; countdown = ""; sessionTime = ""
            return@LaunchedEffect
        }
        fpsCounter = 0
        lastFpsMs  = System.currentTimeMillis()
        while (isActive) {
            withFrameMillis { }
            fpsCounter++
            val now = System.currentTimeMillis()
            if (now - lastFpsMs >= 1000L) {
                fps        = fpsCounter
                fpsCounter = 0
                lastFpsMs  = now
                cpuMhz     = readCpuFreqMhz()
                cpuTemp    = readCpuTempC()
                ramFree    = getAvailableRamMb(context)

                isPhase2 = Prefs.isPhase2Active(context)
                val startMs = Prefs.getLongGameStartMs(context)
                if (startMs > 0L) {
                    val elapsedMs = now - startMs
                    val h  = (elapsedMs / 3_600_000L).toInt()
                    val mT = ((elapsedMs % 3_600_000L) / 60_000L).toInt()
                    val sT = ((elapsedMs % 60_000L) / 1000L).toInt()
                    sessionTime = if (h > 0) "%d:%02d:%02d".format(h, mT, sT)
                                  else "%d:%02d".format(mT, sT)
                    if (!isPhase2) {
                        val remainingMs = (PHASE2_MINUTES_UI * 60_000L) - elapsedMs
                        if (remainingMs > 0) {
                            val m = (remainingMs / 60_000L).toInt()
                            val s = ((remainingMs % 60_000L) / 1000L).toInt()
                            countdown = "%d:%02d".format(m, s)
                        } else countdown = "0:00"
                    }
                }
            }
        }
    }

    val ramPct    = if (totalRam > 0) ramFree.toFloat() / totalRam.toFloat() else 0f
    val ramColor  = when { ramPct > 0.4f -> GreenBright; ramPct > 0.2f -> YellowAcc; else -> RedBright }
    val tempColor = when {
        cpuTemp <= 0f  -> GreyText
        cpuTemp < 38f  -> GreenBright
        cpuTemp < 44f  -> YellowAcc
        else           -> if (thermalEmergency) RedBright else OrangeAcc
    }
    val tempLabel = when {
        cpuTemp <= 0f  -> "—"
        cpuTemp < 38f  -> "Frío"
        cpuTemp < 44f  -> "Tibio"
        else           -> if (thermalEmergency) "EMERG" else "Caliente"
    }
    val freqPct   = if (maxFreq > 0) cpuMhz.toFloat() / maxFreq.toFloat() else 0f
    val freqColor = when { freqPct > 0.75f -> GreenBright; freqPct > 0.45f -> YellowAcc; else -> RedBright }
    val fpsColor  = when { fps >= 58 -> GreenBright; fps >= 45 -> YellowAcc; fps > 0 -> RedBright; else -> GreyText }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "GameModeAI",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$DEVICE_NAME · $DEVICE_CHIP · $DEVICE_DISPLAY",
                        color = GreyText,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val battIcon = if (charging) "⚡" else "🔋"
                    Text(
                        "$battIcon $batteryPct%",
                        color = when {
                            charging       -> CyanAcc
                            batteryPct > 30 -> GreenBright
                            batteryPct > 15 -> YellowAcc
                            else           -> RedBright
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val shizukuColor = if (shizukuState == "Listo") GreenBright else RedBright
                    Text("Shizuku: $shizukuState", color = shizukuColor, fontSize = 11.sp)
                }
            }

            // ── Emergencia térmica ────────────────────────────────────────────
            if (thermalEmergency) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A0000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "EMERGENCIA TERMICA — ${cpuTemp.roundToInt()}°C · Enfriamiento aplicado",
                            color = RedBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Botón principal ───────────────────────────────────────────────
            val btnColor by animateColorAsState(
                if (isActive) GreenDark else Color(0xFF1E1E1E), label = "btn"
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = btnColor),
                shape = RoundedCornerShape(18.dp),
                onClick = {
                    if (!isLoading) {
                        isLoading = true
                        val newActive = !isActive
                        onToggle(newActive) { success ->
                            if (success) {
                                isActive = newActive
                                Prefs.setActive(context, newActive)
                                if (newActive) {
                                    ramBefore = ramFree
                                    GameService.start(context)
                                } else {
                                    GameService.stop(context)
                                }
                                actionMessage = if (newActive) "Modo juego activado" else "Modo juego desactivado"
                            } else {
                                actionMessage = "Error — verifica Shizuku"
                            }
                            isLoading = false
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = GreenBright,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (isActive) "DESACTIVAR" else "ACTIVAR",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            Text(
                                if (isActive) "Modo juego activo" else "Modo juego inactivo",
                                color = if (isActive) GreenBright else GreyText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ── Mensaje de acción ─────────────────────────────────────────────
            if (actionMessage.isNotEmpty()) {
                Text(
                    actionMessage,
                    color = if (actionMessage.startsWith("Error")) RedBright else TealAcc,
                    fontSize = 12.sp
                )
            }

            // ── Monitor en tiempo real ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    label  = "FPS UI",
                    value  = if (fps > 0) "$fps" else "—",
                    unit   = "fps",
                    color  = fpsColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label  = "CPU",
                    value  = if (cpuMhz > 0) "$cpuMhz" else "—",
                    unit   = "MHz",
                    color  = freqColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label  = "Temp",
                    value  = if (cpuTemp > 0f) "%.1f".format(cpuTemp) else "—",
                    unit   = "°C  $tempLabel",
                    color  = tempColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── RAM ───────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("RAM libre", color = GreyText, fontSize = 12.sp)
                        Text(
                            "$ramFree MB / $totalRam MB",
                            color = ramColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ramPct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = ramColor,
                        trackColor = CardDark2
                    )
                    if (isActive && ramBefore > 0L) {
                        val gained = ramFree - ramBefore
                        val sign   = if (gained >= 0) "+" else ""
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Desde activar: $sign$gained MB",
                            color = if (gained >= 0) GreenBright else YellowAcc,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── Fase 2 / Sesión ───────────────────────────────────────────────
            if (isActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPhase2) Color(0xFF0D2F1A) else CardDark
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isPhase2) "Fase 2 activa" else "Fase 2 térmica",
                                color = if (isPhase2) GreenBright else BlueAcc,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isPhase2 && countdown.isNotEmpty()) {
                                Text("en $countdown", color = YellowAcc, fontSize = 13.sp)
                            } else if (isPhase2) {
                                Text("Brillo reducido · Exynos 850", color = TealAcc, fontSize = 12.sp)
                            }
                        }
                        if (sessionTime.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Sesion: $sessionTime", color = GreyText, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Info del dispositivo ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dispositivo", color = GreyText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    DeviceRow("Chip",      DEVICE_CHIP)
                    DeviceRow("Pantalla",  DEVICE_DISPLAY)
                    DeviceRow("RAM total", "$totalRam MB")
                    DeviceRow("CPU max",   if (maxFreq > 0) "$maxFreq MHz" else "—")
                    DeviceRow("Emergencia térmica", "${THERMAL_EMERGENCY_C.roundToInt()}°C")
                    DeviceRow("Fase 2 en", "$PHASE2_MINUTES_UI min")
                }
            }

            // ── Instrucciones Shizuku ─────────────────────────────────────────
            if (shizukuState != "Listo") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A00)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Shizuku no está listo", color = YellowAcc, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("1. Instala Shizuku desde Play Store", color = Color.White, fontSize = 12.sp)
                        Text("2. Activa Shizuku via ADB o modo depuracion inalambrico", color = Color.White, fontSize = 12.sp)
                        Text("3. Abre Shizuku y concede permiso a GameModeAI", color = Color.White, fontSize = 12.sp)
                        Text("4. Vuelve aqui y pulsa Activar", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { ShizukuHelper.requestPermission() },
                            colors = ButtonDefaults.buttonColors(containerColor = YellowAcc)
                        ) {
                            Text("Solicitar permiso Shizuku", color = Color.Black, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = GreyText, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(unit, color = color.copy(alpha = 0.7f), fontSize = 9.sp)
        }
    }
}

@Composable
fun DeviceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = GreyText, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
