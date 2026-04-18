package com.gamemodeai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt
import java.io.File

// ── Colores del panel gamer ───────────────────────────────────────────────────
private val BgDark     = Color(0xFF080808)
private val CardBg     = Color(0xFF0F0F0F)
private val GreenBright = Color(0xFF00FF88)
private val YellowAcc  = Color(0xFFFFCC00)
private val RedBright  = Color(0xFFFF3B3B)
private val OrangeAcc  = Color(0xFFFF8C00)
private val GreyText   = Color(0xFF666666)
private val TealAcc    = Color(0xFF00D4AA)
private val CyanAcc    = Color(0xFF00CCFF)
private val BlueAcc    = Color(0xFF4488FF)

// ── Estado de sistema encapsulado (evita recomposiciones masivas) ─────────────
private data class SystemStats(
    val cpuMhz: Int      = 0,
    val cpuTempC: Float  = 0f,
    val ramFreeMb: Int   = 0,
    val totalRamMb: Int  = 0,
    val batteryPct: Int  = 0,
    val batteryTempC: Float = 0f,
    val isCharging: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameModeAITheme {
                GameModeScreen()
            }
        }
    }
}

// ── Lecturas del sistema (sin Shizuku, solo lectura de archivos del kernel) ───

private fun readCpuFreqMhz(): Int = try {
    // Intentar múltiples rutas del kernel para frecuencia CPU
    val paths = listOf(
        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
        "/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq",
        "/sys/devices/system/cpu/cpufreq/all_time_in_state"
    )
    paths.firstNotNullOfOrNull { path ->
        File(path).takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
    }?.div(1000)?.toInt() ?: 0
} catch (_: Exception) { 0 }

private fun readMaxCpuFreqMhz(): Int = try {
    val paths = listOf(
        "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
        "/sys/devices/system/cpu/cpu4/cpufreq/cpuinfo_max_freq"
    )
    paths.firstNotNullOfOrNull { path ->
        File(path).takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
    }?.div(1000)?.toInt() ?: 1800
} catch (_: Exception) { 1800 }

private fun readCpuTempC(): Float = try {
    val paths = listOf(
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/thermal/thermal_zone1/temp",
        "/sys/class/thermal/thermal_zone2/temp",
        "/sys/devices/virtual/thermal/thermal_zone0/temp"
    )
    paths.firstNotNullOfOrNull { path ->
        File(path).takeIf { it.canRead() }?.readText()?.trim()?.toFloatOrNull()
    }?.let { temp ->
        if (temp > 1000f) temp / 1000f else temp // normalizar mili-Celsius
    }?.takeIf { it in 15f..90f } ?: 0f
} catch (_: Exception) { 0f }

private fun getAvailableRamMb(context: Context): Int = try {
    val mi = android.app.ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
        .getMemoryInfo(mi)
    (mi.availMem / 1_048_576L).toInt()
} catch (_: Exception) { 0 }

private fun getTotalRamMb(context: Context): Int = try {
    val mi = android.app.ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
        .getMemoryInfo(mi)
    (mi.totalMem / 1_048_576L).toInt()
} catch (_: Exception) { 3000 }

private fun getBatteryInfo(context: Context): Triple<Int, Float, Boolean> = try {
    val intent = context.registerReceiver(null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level   = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale   = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct     = if (scale > 0) (level * 100 / scale) else 0
    val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val tempC   = tempRaw / 10f
    val status  = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL
    Triple(pct, tempC, charging)
} catch (_: Exception) { Triple(0, 0f, false) }

// ── Pantalla principal ────────────────────────────────────────────────────────

@Composable
fun GameModeScreen() {
    val context = LocalContext.current

    // Estado de activación
    var isActive   by remember { mutableStateOf(Prefs.isActive(context)) }
    var isLoading  by remember { mutableStateOf(false) }
    var isPhase2   by remember { mutableStateOf(Prefs.isPhase2Active(context)) }
    var countdown  by remember { mutableStateOf("") }
    var sessionTime by remember { mutableStateOf("") }

    // Shizuku
    var shizukuStatus by remember {
        mutableStateOf(when {
            !ShizukuHelper.isShizukuAvailable() -> ShizukuStatus.UNAVAILABLE
            !ShizukuHelper.hasPermission()       -> ShizukuStatus.NO_PERMISSION
            else                                  -> ShizukuStatus.READY
        })
    }

    // Métricas del sistema
    var stats     by remember { mutableStateOf(SystemStats()) }
    val maxFreq   = remember { readMaxCpuFreqMhz() }
    val totalRam  = remember { getTotalRamMb(context) }

    // UpdateChecker
    var updateStatus by remember { mutableStateOf("idle") }
    var updateInfo   by remember { mutableStateOf<UpdateInfo?>(null) }
    var dlProgress   by remember { mutableStateOf(0) }
    val updateScope  = rememberCoroutineScope()

    // ── Monitor de Shizuku cada 5 segundos ───────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            shizukuStatus = when {
                !ShizukuHelper.isShizukuAvailable() -> ShizukuStatus.UNAVAILABLE
                !ShizukuHelper.hasPermission()       -> ShizukuStatus.NO_PERMISSION
                else                                  -> ShizukuStatus.READY
            }
        }
    }

    // ── Monitor de métricas del sistema cada 3 segundos ───────────────────────
    // Intervalo de 3s (no 1s) para reducir carga en CPU del A06
    LaunchedEffect(Unit) {
        while (true) {
            val (pct, battTemp, charging) = getBatteryInfo(context)
            stats = SystemStats(
                cpuMhz       = readCpuFreqMhz(),
                cpuTempC     = readCpuTempC(),
                ramFreeMb    = getAvailableRamMb(context),
                totalRamMb   = totalRam,
                batteryPct   = pct,
                batteryTempC = battTemp,
                isCharging   = charging
            )
            delay(3_000L)
        }
    }

    // ── Temporizador de sesión y cuenta regresiva a Fase 2 ───────────────────
    LaunchedEffect(isActive) {
        if (!isActive) {
            countdown   = ""
            sessionTime = ""
            return@LaunchedEffect
        }
        while (isActive) {
            delay(1_000L)
            isPhase2 = Prefs.isPhase2Active(context)
            val startMs = Prefs.getLongGameStartMs(context)
            if (startMs > 0L) {
                val elapsed = System.currentTimeMillis() - startMs
                val h  = (elapsed / 3_600_000L).toInt()
                val m  = ((elapsed % 3_600_000L) / 60_000L).toInt()
                val s  = ((elapsed % 60_000L) / 1_000L).toInt()
                sessionTime = if (h > 0) "%d:%02d:%02d".format(h, m, s)
                              else "%d:%02d".format(m, s)

                if (!isPhase2) {
                    val remaining = (20 * 60_000L) - elapsed
                    countdown = if (remaining > 0) {
                        val cm = (remaining / 60_000L).toInt()
                        val cs = ((remaining % 60_000L) / 1_000L).toInt()
                        "%d:%02d".format(cm, cs)
                    } else "0:00"
                }
            }
        }
    }

    // ── Derivados de estado ───────────────────────────────────────────────────
    val ramPct    = if (stats.totalRamMb > 0) stats.ramFreeMb.toFloat() / stats.totalRamMb else 0f
    val freqPct   = if (maxFreq > 0) stats.cpuMhz.toFloat() / maxFreq else 0f
    val ramColor  = when { ramPct > 0.4f -> GreenBright; ramPct > 0.2f -> YellowAcc; else -> RedBright }
    val tempColor = when { stats.cpuTempC <= 0f -> GreyText; stats.cpuTempC < 38f -> GreenBright; stats.cpuTempC < 44f -> YellowAcc; else -> RedBright }
    val tempLabel = when { stats.cpuTempC <= 0f -> "—"; stats.cpuTempC < 38f -> "Frío"; stats.cpuTempC < 44f -> "Tibio"; else -> "Caliente" }
    val freqColor = when { freqPct > 0.75f -> GreenBright; freqPct > 0.45f -> YellowAcc; else -> GreyText }

    Scaffold(
        containerColor = BgDark,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            // ── Header ───────────────────────────────────────────────────────
            HeaderSection()

            // ── Aviso de carga + juego ────────────────────────────────────────
            if (stats.isCharging && isActive) {
                WarningBanner(
                    icon = "⚠",
                    title = "CARGANDO MIENTRAS JUEGAS",
                    message = "El A06 se sobrecalienta. Desconecta el cargador para partidas largas.",
                    color = OrangeAcc,
                    bgColor = Color(0xFF1A0800)
                )
            }

            // ── Shizuku setup ─────────────────────────────────────────────────
            if (shizukuStatus != ShizukuStatus.READY) {
                ShizukuSetupCard(
                    status = shizukuStatus,
                    onRetry = {
                        shizukuStatus = when {
                            !ShizukuHelper.isShizukuAvailable() -> ShizukuStatus.UNAVAILABLE
                            !ShizukuHelper.hasPermission()       -> ShizukuStatus.NO_PERMISSION
                            else                                  -> ShizukuStatus.READY
                        }
                        if (shizukuStatus == ShizukuStatus.NO_PERMISSION)
                            ShizukuHelper.requestPermission()
                    }
                )
            }

            // ── Fase 2 activa ─────────────────────────────────────────────────
            if (isActive && isPhase2) {
                Phase2Banner()
            }

            // ── Cuenta regresiva a Fase 2 ─────────────────────────────────────
            if (isActive && !isPhase2 && countdown.isNotEmpty()) {
                CountdownBanner(countdown = countdown)
            }

            // ── Botón principal ───────────────────────────────────────────────
            MainToggleButton(
                isActive  = isActive,
                isLoading = isLoading,
                isPhase2  = isPhase2,
                countdown = countdown,
                onToggle  = {
                    if (isLoading) return@MainToggleButton
                    isLoading = true
                    updateScope.launch {
                        try {
                            if (!isActive) {
                                Prefs.setActive(context, true)
                                Prefs.startLongGame(context)
                                GameService.start(context)
                                delay(600L)
                                ShizukuHelper.enableGameMode()
                                isActive = true
                                isPhase2 = false
                            } else {
                                GameService.stop(context)
                                Prefs.setActive(context, false)
                                delay(400L)
                                ShizukuHelper.disableGameMode()
                                isActive  = false
                                isPhase2  = false
                                countdown = ""
                                sessionTime = ""
                            }
                        } catch (e: Exception) {
                            // Error gracioso: nunca crash
                            Prefs.setActive(context, isActive.not())
                            isActive = Prefs.isActive(context)
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )

            // ── Monitor en tiempo real (solo cuando está activo) ──────────────
            if (isActive) {
                MonitorCard(
                    stats     = stats,
                    maxFreq   = maxFreq,
                    freqPct   = freqPct,
                    freqColor = freqColor,
                    ramColor  = ramColor,
                    tempColor = tempColor,
                    tempLabel = tempLabel,
                    isPhase2  = isPhase2,
                    sessionTime = sessionTime
                )
            }

            // ── Cards de información ──────────────────────────────────────────
            OptimizationsCard()
            Phase2InfoCard()
            TipsCard()

            // ── Actualización ─────────────────────────────────────────────────
            UpdateCard(
                status      = updateStatus,
                info        = updateInfo,
                progress    = dlProgress,
                currentCode = BuildConfig.VERSION_CODE,
                onCheck = {
                    updateStatus = "checking"
                    updateScope.launch {
                        UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
                            .onSuccess { info ->
                                updateInfo   = info
                                updateStatus = if (info.isUpdateAvailable) "available" else "up_to_date"
                            }
                            .onFailure { updateStatus = "error" }
                    }
                },
                onDownload = {
                    updateInfo?.let { info ->
                        updateStatus = "downloading"
                        dlProgress   = 0
                        updateScope.launch {
                            val file = UpdateChecker.downloadApk(context, info.downloadUrl, info.tagName) { p ->
                                withContext(Dispatchers.Main) { dlProgress = p }
                            }
                            if (file != null) {
                                updateStatus = "done"
                                UpdateChecker.installApk(context, file)
                            } else {
                                updateStatus = "error"
                            }
                        }
                    }
                }
            )

            Text(
                "Solo ajustes del sistema Android · no modifica archivos del juego",
                fontSize = 10.sp, color = GreyText.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Componentes UI ────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "GameModeAI",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 1.sp
        )
        Text(
            "Galaxy A06  ·  Exynos 850  ·  Gaming",
            fontSize = 11.sp,
            color = GreyText
        )
        Text(
            "v${BuildConfig.VERSION_NAME}",
            fontSize = 10.sp,
            color = GreyText.copy(alpha = 0.6f),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF141414))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun WarningBanner(icon: String, title: String, message: String,
                          color: Color, bgColor: Color) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, fontSize = 16.sp)
            Column {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = color, letterSpacing = 0.5.sp)
                Text(message, fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f))
            }
        }
    }
}

enum class ShizukuStatus { UNAVAILABLE, NO_PERMISSION, READY }

@Composable
private fun ShizukuSetupCard(status: ShizukuStatus, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF160010)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(YellowAcc))
                Text(
                    if (status == ShizukuStatus.NO_PERMISSION)
                        "SHIZUKU SIN PERMISO" else "SHIZUKU NO ACTIVO",
                    fontSize = 10.sp, color = YellowAcc,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
            }
            Text(
                if (status == ShizukuStatus.NO_PERMISSION)
                    "Shizuku está instalado pero necesita permiso de esta app."
                else
                    "La app funciona sin Shizuku pero con funciones limitadas. Para activar todas las optimizaciones:",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f)
            )
            if (status == ShizukuStatus.UNAVAILABLE) {
                SetupStep("1", "Instala Shizuku desde Play Store")
                SetupStep("2", "Abre Shizuku → 'Iniciar mediante ADB inalámbrico'")
                SetupStep("3", "Sigue las instrucciones en pantalla de Shizuku")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1A00))
            ) {
                Text(
                    if (status == ShizukuStatus.NO_PERMISSION)
                        "Conceder permiso a Shizuku"
                    else
                        "Verificar estado de Shizuku",
                    fontSize = 13.sp, color = YellowAcc, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Phase2Banner() {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(
                listOf(Color(0xFF0D2B25), Color(0xFF0A3320))))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(TealAcc))
            Column {
                Text("FASE 2 ACTIVA — PARTIDA LARGA",
                    fontSize = 10.sp, color = TealAcc,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Brillo al 29% · mínimo fondo · temperatura controlada",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun CountdownBanner(countdown: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141400))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("FASE 2 TÉRMICA", fontSize = 10.sp,
                    color = YellowAcc.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Brillo reducido + limpieza al llegar a 0",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Text(countdown, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, color = YellowAcc)
        }
    }
}

@Composable
private fun MainToggleButton(
    isActive: Boolean, isLoading: Boolean,
    isPhase2: Boolean, countdown: String,
    onToggle: () -> Unit
) {
    Button(
        onClick = onToggle,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) Color(0xFF0D3B1E) else Color(0xFF141414),
            disabledContainerColor = Color(0xFF0A0A0A)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(
                    color = GreenBright,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
                Text("Aplicando optimizaciones...",
                    fontSize = 14.sp, color = GreenBright.copy(alpha = 0.8f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isActive) "● MODO JUEGO ACTIVO" else "○ MODO JUEGO INACTIVO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) GreenBright else GreyText
                )
                if (isActive) {
                    Text(
                        if (isPhase2) "Fase 2 · temp controlada · rendimiento máximo"
                        else "Optimizaciones activas · Fase 2 en $countdown",
                        fontSize = 11.sp, color = GreenBright.copy(alpha = 0.6f)
                    )
                } else {
                    Text("Toca para activar todas las optimizaciones",
                        fontSize = 11.sp, color = GreyText.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun MonitorCard(
    stats: SystemStats, maxFreq: Int,
    freqPct: Float, freqColor: Color,
    ramColor: Color, tempColor: Color,
    tempLabel: String, isPhase2: Boolean,
    sessionTime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1400)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("MONITOR EN VIVO", fontSize = 10.sp,
                    color = GreenBright.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionTime.isNotEmpty()) {
                        Text(sessionTime, fontSize = 11.sp, color = GreyText)
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape)
                        .background(if (isPhase2) TealAcc else GreenBright))
                }
            }

            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                val cpuHz = if (stats.cpuMhz > 0) "${stats.cpuMhz}" else "—"
                val cpuPct = if (stats.cpuMhz > 0 && maxFreq > 0)
                    "${(freqPct * 100).roundToInt()}%" else "—"
                MetricBlock(cpuHz, "CPU MHz", cpuPct, freqColor)

                val tempStr = if (stats.cpuTempC > 0f) "${stats.cpuTempC.roundToInt()}°C" else "—"
                MetricBlock(tempStr, "CPU Temp", tempLabel, tempColor)

                val ramPct = if (stats.totalRamMb > 0)
                    "${((stats.ramFreeMb.toFloat() / stats.totalRamMb) * 100).roundToInt()}%"
                else "—"
                MetricBlock("${stats.ramFreeMb}", "RAM MB libre", ramPct, ramColor)

                val batStr = if (stats.batteryPct > 0) "${stats.batteryPct}%" else "—"
                val batColor = when {
                    stats.isCharging -> BlueAcc
                    stats.batteryPct > 50 -> GreenBright
                    stats.batteryPct > 20 -> YellowAcc
                    else -> RedBright
                }
                MetricBlock(batStr, "Batería",
                    if (stats.isCharging) "Cargando" else "En uso", batColor)
            }

            // Temperatura batería si disponible
            if (stats.batteryTempC > 0f) {
                val btColor = when {
                    stats.batteryTempC < 35f -> GreenBright
                    stats.batteryTempC < 42f -> YellowAcc
                    else -> RedBright
                }
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center) {
                    Text("Temp. batería: ${stats.batteryTempC}°C",
                        fontSize = 11.sp, color = btColor)
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(value: String, label: String, hint: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = GreyText)
        Text(hint, fontSize = 9.sp, color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun OptimizationsCard() {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("OPTIMIZACIONES ACTIVAS", fontSize = 10.sp, color = GreenBright.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            OptItem("GOS · GameHome · GameTools desactivados")
            OptItem("Animaciones del sistema desactivadas (0ms)")
            OptItem("NFC · GPS · Sync · Escaneo WiFi desactivados")
            OptItem("Brillo manual optimizado · pantalla completa")
            OptItem("AIM: sin haptic · sin debounce táctil · sin predicción")
            OptItem("Free Fire: prioridad máxima del sistema")
            OptItem("Bixby · Digital Wellbeing · apps Samsung pausadas")
            OptItem("Red WiFi: power-save desactivado · anti-jitter")
            OptItem("GPU Vulkan optimizado para Exynos 850")
            OptItem("AOT compilation de Free Fire en background")
        }
    }
}

@Composable
private fun Phase2InfoCard() {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080F0C)),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("FASE 2 — PARTIDA LARGA (20 min)", fontSize = 10.sp,
                color = TealAcc.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Phase2Item("Brillo baja a 29 % (panel LCD = calor #1 del A06)")
            Phase2Item("Limpieza completa de todos los procesos en fondo")
            Phase2Item("Prioridad máxima de Free Fire reconfirmada")
            Phase2Item("Mantenimiento automático cada 5 minutos")
        }
    }
}

@Composable
private fun TipsCard() {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D00)),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ANTES DE CADA PARTIDA", fontSize = 10.sp,
                color = YellowAcc.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            TipItem("Activa esta app PRIMERO → luego abre Free Fire")
            TipItem("Cierra todas las apps del historial reciente")
            TipItem("Activa modo avión → desactívalo (limpia el ping)")
            TipItem("Batería mínimo 50 % · sin cargador si puedes")
            TipItem("Gráficos FF: Suave · Velocidad: Máxima · Sombras: OFF")
        }
    }
}

@Composable
private fun UpdateCard(
    status: String, info: UpdateInfo?, progress: Int,
    currentCode: Int, onCheck: () -> Unit, onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF08080F)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("ACTUALIZACIÓN", fontSize = 10.sp,
                    color = BlueAcc.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("build #$currentCode", fontSize = 10.sp, color = GreyText)
            }
            when (status) {
                "idle" -> OutlinedButton(
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BlueAcc.copy(alpha = 0.4f))
                ) { Text("Verificar actualización", fontSize = 12.sp, color = BlueAcc) }

                "checking" -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                        color = BlueAcc, trackColor = Color(0xFF1A2030))
                    Text("Verificando en GitHub...", fontSize = 12.sp, color = GreyText)
                }

                "up_to_date" -> {
                    Text("✓ Ya tienes la última versión", fontSize = 12.sp, color = GreenBright)
                    OutlinedButton(onClick = onCheck,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreyText.copy(alpha = 0.2f))
                    ) { Text("Volver a verificar", fontSize = 11.sp, color = GreyText) }
                }

                "available" -> info?.let { i ->
                    Text("⬆ Nueva versión: ${i.tagName}",
                        fontSize = 12.sp, color = YellowAcc,
                        fontWeight = FontWeight.SemiBold)
                    Button(onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAcc)
                    ) { Text("Descargar e instalar ${i.tagName}",
                        fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
                }

                "downloading" -> {
                    Text("Descargando... $progress%", fontSize = 12.sp,
                        color = BlueAcc, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = BlueAcc, trackColor = Color(0xFF1A2030)
                    )
                }

                "done" -> Text("✓ Descarga completa — sigue el instalador del sistema",
                    fontSize = 12.sp, color = GreenBright)

                "error" -> {
                    Text("✕ Error al verificar. Revisa tu conexión.",
                        fontSize = 12.sp, color = RedBright)
                    OutlinedButton(onClick = onCheck,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RedBright.copy(alpha = 0.3f))
                    ) { Text("Reintentar", fontSize = 11.sp, color = RedBright) }
                }
            }
        }
    }
}

// ── Micro-componentes ─────────────────────────────────────────────────────────

@Composable private fun Phase2Item(text: String) =
    Row(verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✦", fontSize = 11.sp, color = TealAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }

@Composable private fun OptItem(text: String) =
    Row(verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✓", fontSize = 12.sp, color = GreenBright)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }

@Composable private fun TipItem(text: String) =
    Row(verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("›", fontSize = 13.sp, color = YellowAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }

@Composable private fun SetupStep(number: String, text: String) =
    Row(verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape)
                .background(YellowAcc.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 11.sp, color = YellowAcc, fontWeight = FontWeight.Bold)
        }
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f))
    }

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
