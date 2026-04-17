package com.gamemodeai

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    listOf("/sys/class/thermal/thermal_zone5/temp", "/sys/class/thermal/thermal_zone4/temp",
        "/sys/class/thermal/thermal_zone3/temp", "/sys/class/thermal/thermal_zone1/temp",
        "/sys/class/thermal/thermal_zone0/temp", "/sys/class/power_supply/battery/temp")
        .forEach { path ->
            try {
                val raw = File(path).readText().trim().toFloat()
                val t = if (raw > 1000f) raw / 1000f else raw
                if (t in 15f..80f) return t
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
private val TealAcc     = Color(0xFF1DE9B6)
private val GreyText    = Color(0xFF757575)

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
    var shizuku      by remember { mutableStateOf("Verificando...") }

    // Monitor en tiempo real
    var fps          by remember { mutableIntStateOf(0) }
    var cpuMhz       by remember { mutableIntStateOf(0) }
    var cpuTemp      by remember { mutableStateOf(0f) }
    var fpsCounter   by remember { mutableIntStateOf(0) }
    var lastFpsMs    by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Fase 2 — partida larga
    var isPhase2     by remember { mutableStateOf(false) }
    var countdown    by remember { mutableStateOf("") }   // "18:42" restantes

    var actionMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        shizuku = when {
            !ShizukuHelper.isShizukuAvailable() -> "No disponible"
            !ShizukuHelper.hasPermission()       -> "Sin permiso"
            else                                  -> "Listo"
        }
        ramFree = getAvailableRamMb(context)
    }

    // Monitor continuo: FPS + CPU + temperatura + fase2 cada segundo
    LaunchedEffect(isActive) {
        if (!isActive) {
            fps = 0; cpuMhz = 0; cpuTemp = 0f
            isPhase2 = false; countdown = ""
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

                // Calcular cuenta regresiva hasta Fase 2
                isPhase2 = Prefs.isPhase2Active(context)
                if (!isPhase2) {
                    val startMs  = Prefs.getLongGameStartMs(context)
                    if (startMs > 0L) {
                        val elapsedMs   = now - startMs
                        val remainingMs = (20 * 60 * 1000L) - elapsedMs
                        if (remainingMs > 0) {
                            val m = (remainingMs / 60_000L).toInt()
                            val s = ((remainingMs % 60_000L) / 1000L).toInt()
                            countdown = "%d:%02d".format(m, s)
                        } else {
                            countdown = "0:00"
                        }
                    }
                }
            }
        }
    }

    val ramPct    = if (totalRam > 0) ramFree.toFloat() / totalRam.toFloat() else 0f
    val ramColor  = when { ramPct > 0.4f -> GreenBright; ramPct > 0.2f -> YellowAcc; else -> RedBright }
    val tempColor = when { cpuTemp <= 0f -> GreyText; cpuTemp < 38f -> GreenBright; cpuTemp < 44f -> YellowAcc; else -> RedBright }
    val tempLabel = when { cpuTemp <= 0f -> "—"; cpuTemp < 38f -> "Frío"; cpuTemp < 44f -> "Tibio"; else -> "Caliente" }
    val freqPct   = if (maxFreq > 0) cpuMhz.toFloat() / maxFreq.toFloat() else 0f
    val freqColor = when { freqPct > 0.75f -> GreenBright; freqPct > 0.45f -> YellowAcc; else -> RedBright }
    val fpsColor  = when { fps >= 58 -> GreenBright; fps >= 45 -> YellowAcc; fps > 0 -> RedBright; else -> GreyText }

    Scaffold(containerColor = BgDark, modifier = Modifier.fillMaxSize()) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Header ────────────────────────────────────────────────────────
            Text("GameModeAI", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Galaxy A06  ·  Free Fire  ·  ajustes del sistema", fontSize = 11.sp, color = GreyText)

            // ── FASE 2 BANNER (cuando está activa) ────────────────────────────
            if (isActive && isPhase2) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(
                            listOf(Color(0xFF0D2B25), Color(0xFF0A3320))))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🌡", fontSize = 22.sp)
                        Column {
                            Text("FASE 2 ACTIVA — PARTIDA LARGA",
                                fontSize = 10.sp, color = TealAcc,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Brillo al 29 % · 0 procesos en fondo · temp controlada",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // ── CUENTA REGRESIVA hasta Fase 2 ────────────────────────────────
            if (isActive && !isPhase2 && countdown.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141400))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("FASE 2 TÉRMICA", fontSize = 10.sp,
                                color = YellowAcc.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Brillo extra bajo + limpieza total al llegar a 0",
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                        }
                        Text(countdown, fontSize = 26.sp,
                            fontWeight = FontWeight.Bold, color = YellowAcc)
                    }
                }
            }

            // ── Estado ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
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
                            Text(
                                if (isPhase2)
                                    "Fase 2 activa · temp controlada · mira pro"
                                else
                                    "GOS·NFC·GPS off · Mira sin saltos · Fase 2 en $countdown",
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
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(if (isPhase2) TealAcc else GreenBright))
                        }

                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            MonitorMetric(if (fps > 0) "$fps" else "—", "FPS app",
                                if (fps >= 58) "Fluido" else if (fps > 0) "Bajo" else "—", fpsColor)
                            MonitorMetric(if (cpuMhz > 0) "$cpuMhz" else "—", "CPU MHz",
                                if (cpuMhz > 0 && maxFreq > 0) "${(freqPct*100).roundToInt()}%" else "—", freqColor)
                            MonitorMetric(if (cpuTemp > 0f) "${cpuTemp.roundToInt()}°" else "—",
                                "Temperatura", tempLabel, tempColor)
                            MonitorMetric("$ramFree", "RAM MB",
                                "${(ramPct*100).roundToInt()}% libre", ramColor)
                        }

                        if (cpuMhz > 0 && maxFreq > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CPU $cpuMhz MHz", fontSize = 11.sp, color = GreyText)
                                    Text("máx $maxFreq MHz", fontSize = 11.sp, color = GreyText)
                                }
                                LinearProgressIndicator(
                                    progress = { freqPct.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = freqColor, trackColor = Color(0xFF1A1A1A))
                            }
                        }

                        if (cpuTemp >= 44f) {
                            Row(Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedBright.copy(alpha = 0.12f))
                                .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠", fontSize = 16.sp)
                                Text("Temperatura alta — pausa 2-3 min o reduce el brillo de FF",
                                    fontSize = 11.sp, color = RedBright.copy(alpha = 0.85f))
                            }
                        }
                        Text("FPS = fluidez de la interfaz · CPU alta = sin throttling = mira estable",
                            fontSize = 10.sp, color = GreyText.copy(alpha = 0.4f))
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
                    LinearProgressIndicator(progress = { ramPct.coerceIn(0f, 1f) },
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
                    if (actionMessage.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFF1E1E1E))
                        Text(actionMessage, fontSize = 11.sp,
                            color = if (shizuku == "Listo") GreenBright.copy(alpha = 0.75f) else YellowAcc.copy(alpha = 0.85f))
                    }
                }
            }

            // ── AIM AUTOMÁTICO ─────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0018)),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("AIM ESTABLE AUTOMÁTICO", fontSize = 10.sp,
                            color = PurpleAcc, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(if (isActive) "aplicado" else "listo", fontSize = 10.sp,
                            color = if (isActive) GreenBright else GreyText)
                    }
                    Text("Sin tocar la sensibilidad del juego: reduce micro-saltos del sistema, vibración, gestos y cambios de Hz.",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
                    HorizontalDivider(color = Color(0xFF1E1E1E))
                    AimItem("Touch sensitivity Samsung ON · bloqueo/debounce táctil al mínimo")
                    AimItem("Animaciones 0× · respuesta visual inmediata al deslizar")
                    AimItem("60 Hz fijo · evita jitter por cambio dinámico de refresco")
                    AimItem("Gestos, panel lateral, vibración y sonidos táctiles OFF")
                }
            }

            // ── Optimizaciones ────────────────────────────────────────────────
            if (isActive) {

                // Fase 2 detalle
                if (isPhase2) {
                    OptCard("🌡 FASE 2 — PARTIDA LARGA ACTIVA", TealAcc, Color(0xFF0A1F1A)) {
                        Phase2Item("Brillo bajado al 29 % (75/255) — máximo anti-calor")
                        Phase2Item("0 procesos en fondo — solo existe Free Fire")
                        Phase2Item("GOS + Bixby + Samsung sm → parados de nuevo")
                        Phase2Item("Tercera limpieza de RAM completa")
                        Phase2Item("WiFi·GPS·Sync·NFC confirmados OFF")
                        Phase2Item("Prioridad de proceso de FF confirmada al máximo")
                        Phase2Item("Temperatura controlada · mira estable en partidas de 30+ min")
                    }
                }

                // Anti-calor
                OptCard("❄ ANTI-CALENTAMIENTO", OrangeAcc, Color(0xFF180900)) {
                    TempItem("NFC OFF — chip NFC genera calor aunque no lo uses")
                    TempItem("Pantalla al 45 % (Fase 1) / 29 % (Fase 2)")
                    TempItem("GPS OFF · WiFi sin escaneos · BT escaneo OFF")
                    TempItem("AOD OFF · Acelerómetro OFF · Sync OFF")
                    TempItem("10 servicios Samsung calientes parados")
                    TempItem("RAM limpiada · menos apps = menos calor de CPU")
                }

                // GOS
                OptCard("◉ GAME OPTIMIZING SERVICE — PARADO", CyanAcc, Color(0xFF001820)) {
                    GOSItem("GOS parado → CPU/GPU al 100 % sin throttling de software")
                    GOSItem("Game Launcher + Game Tools parados")
                    GOSItem("Batería adaptativa OFF · ahorro automático OFF")
                    GOSItem("(Protección HARDWARE del kernel siempre activa)")
                }

                // AIM
                OptCard("◈ MIRA SIN SALTOS", PurpleAcc, Color(0xFF0E0018)) {
                    AimItem("Perfil automático de aim: sin sliders manuales ni cambios dentro del juego")
                    AimItem("Rebotes táctiles y debounce → mínimo posible")
                    AimItem("Puntero y respuesta táctil estabilizados por sistema")
                    AimItem("60 Hz fijo → sin jitter por cambio de Hz")
                    AimItem("Vision Booster OFF → GPU limpia")
                    AimItem("Vibración OFF → dedo más estable")
                    AimItem("Vulkan optimizado · layers debug OFF")
                }

                // A06
                OptCard("⚡ FALLOS SAMSUNG A06 — CORREGIDOS", YellowAcc, Color(0xFF140D00)) {
                    A06Item("Panel lateral OFF · Gestos → 3 botones")
                    A06Item("Modo inmersivo forzado en Free Fire")
                    A06Item("Botón lateral / asistente de voz → OFF")
                    A06Item("Prevención toque accidental → OFF")
                    A06Item("WiFi watchdog OFF · Doze aplazado")
                }

                // General
                OptCard("✓ RENDIMIENTO GENERAL", BlueAcc, Color(0xFF000D1A)) {
                    OptItem("Animaciones → 0 · Apps de fondo congeladas")
                    OptItem("Notificaciones emergentes OFF")
                    OptItem("WiFi estable sin escaneos")
                    OptItem("RAM limpiada 2× (Fase 1) + 1× (Fase 2)")
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
                            actionMessage = if (activate)
                                "Modo juego activo. El mantenimiento se reaplica cada 5 minutos durante partidas largas."
                            else
                                "Modo juego desactivado. Ajustes principales restaurados."
                            if (activate) { GameService.start(context); ramFree = getAvailableRamMb(context) }
                            else { GameService.stop(context); ramBefore = 0L; ramFree = getAvailableRamMb(context); isPhase2 = false; countdown = "" }
                            shizuku = "Listo"
                        } else {
                            shizuku = when {
                                !ShizukuHelper.isShizukuAvailable() -> "No disponible"
                                !ShizukuHelper.hasPermission() -> "Sin permiso"
                                else -> "Error"
                            }
                            actionMessage = when (shizuku) {
                                "No disponible" -> "Abre Shizuku, inicia el servicio y vuelve a intentarlo."
                                "Sin permiso" -> "Acepta el permiso de Shizuku y toca activar otra vez."
                                else -> "No se pudieron aplicar todos los ajustes. Revisa Shizuku y prueba de nuevo."
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isLoading -> Color(0xFF1E1E1E)
                        isPhase2  -> TealAcc.copy(alpha = 0.85f)
                        isActive  -> RedBright
                        else      -> GreenDark
                    },
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
                    TipItem("Si aparece aviso de temp: pausa 2 min antes de continuar")
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

// ── Componentes ───────────────────────────────────────────────────────────────
@Composable
private fun MonitorMetric(value: String, label: String, hint: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = GreyText)
        Text(hint,  fontSize = 10.sp, color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun OptCard(title: String, titleColor: Color, bg: Color,
                    content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 10.sp, color = titleColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable private fun Phase2Item(text: String) =
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✦", fontSize = 11.sp, color = TealAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) }

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
