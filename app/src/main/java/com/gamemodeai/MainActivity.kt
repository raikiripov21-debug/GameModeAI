package com.gamemodeai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

// ── Paleta ───────────────────────────────────────────────────────────────────
private val BgDark      = Color(0xFF0D0D0D)
private val CardDark    = Color(0xFF181818)
private val GreenBright = Color(0xFF00E676)
private val GreenDark   = Color(0xFF00C853)
private val RedBright   = Color(0xFFFF1744)
private val BlueAcc     = Color(0xFF42A5F5)
private val YellowAcc   = Color(0xFFFFD600)
private val PurpleAcc   = Color(0xFFCE93D8)
private val OrangeAcc   = Color(0xFFFF9800)
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

    // Sensibilidades guardadas
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

    Scaffold(containerColor = BgDark, modifier = Modifier.fillMaxSize()) { pad ->
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

            // ── Header ────────────────────────────────────────────────────────
            Text("GameModeAI", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Galaxy A06  ·  Free Fire", fontSize = 11.sp, color = GreyText)

            // ── Estado ────────────────────────────────────────────────────────
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
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MODO JUEGO", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    if (isLoading) {
                        CircularProgressIndicator(color = GreenBright,
                            modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text("Aplicando optimizaciones...", fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f))
                    } else {
                        Text(if (isActive) "● ACTIVO" else "○ INACTIVO",
                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            color = if (isActive) GreenBright else GreyText)
                        if (isActive)
                            Text("20 optimizaciones · Aim y A06 estabilizados",
                                fontSize = 12.sp, color = GreenBright.copy(alpha = 0.7f))
                    }
                }
            }

            // ── RAM ───────────────────────────────────────────────────────────
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
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = ramColor, trackColor = Color(0xFF2A2A2A))
                    if (isActive && ramBefore > 0) {
                        val freed = ramFree - ramBefore
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RAM ganada", fontSize = 12.sp, color = GreyText)
                            Text("${if (freed >= 0) "+" else ""}$freed MB", fontSize = 12.sp,
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

            // ── SLIDERS DE SENSIBILIDAD ───────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12001F)),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("SENSIBILIDAD DE MIRA", fontSize = 10.sp,
                            color = PurpleAcc.copy(alpha = 0.85f), fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp)
                        Text("Galaxy A06", fontSize = 10.sp, color = GreyText)
                    }

                    Text("Ajusta y usa estos valores en Free Fire",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))

                    Spacer(Modifier.height(6.dp))

                    SensSlider(
                        label    = "General",
                        hint     = "rec. 85–95",
                        value    = sensGeneral,
                        min      = 60, max = 100,
                        color    = PurpleAcc,
                        onChange = { sensGeneral = it; Prefs.setSensGeneral(context, it) }
                    )
                    SensSlider(
                        label    = "Punto rojo / Mira",
                        hint     = "rec. 90–100",
                        value    = sensRedDot,
                        min      = 70, max = 100,
                        color    = RedBright,
                        onChange = { sensRedDot = it; Prefs.setSensRedDot(context, it) }
                    )
                    SensSlider(
                        label    = "Vista 2x",
                        hint     = "rec. 65–75",
                        value    = sens2x,
                        min      = 40, max = 90,
                        color    = BlueAcc,
                        onChange = { sens2x = it; Prefs.setSens2x(context, it) }
                    )
                    SensSlider(
                        label    = "Vista 4x",
                        hint     = "rec. 45–55",
                        value    = sens4x,
                        min      = 20, max = 70,
                        color    = YellowAcc,
                        onChange = { sens4x = it; Prefs.setSens4x(context, it) }
                    )
                    SensSlider(
                        label    = "Francotirador",
                        hint     = "rec. 20–30",
                        value    = sensSniper,
                        min      = 5, max = 50,
                        color    = OrangeAcc,
                        onChange = { sensSniper = it; Prefs.setSensSniper(context, it) }
                    )

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Tip: si la mira salta mucho al levantar, baja el General 5 puntos.\n" +
                        "Si va lenta, súbelo 5. Guarda y prueba en sala de entrenamiento.",
                        fontSize = 11.sp, color = PurpleAcc.copy(alpha = 0.5f)
                    )
                }
            }

            // ── Optimizaciones activas ────────────────────────────────────────
            if (isActive) {

                OptCard("FALLOS SAMSUNG A06 — CORREGIDOS", OrangeAcc, Color(0xFF1A0D00)) {
                    A06Item("Panel lateral → desactivado (no se abre solo al borde)")
                    A06Item("Gestos navegación → 3 botones (sin conflicto con FF)")
                    A06Item("Modo inmersivo forzado → barra de nav oculta")
                    A06Item("Cartel 'desliza para mostrar navbar' → eliminado")
                    A06Item("Doble toque botón lateral → cámara desactivada")
                    A06Item("Asistente de voz al mantener botón → desactivado")
                    A06Item("Modo de una mano → desactivado")
                    A06Item("Game Launcher Samsung → desactivado")
                    A06Item("Prevención toque accidental → OFF")
                    A06Item("WiFi watchdog → OFF (sin cortes de 1-2 s)")
                    A06Item("Doze del sistema → aplazado durante la partida")
                    A06Item("CPU responsiveness Samsung mejorado")
                }

                OptCard("ESTABILIZACIÓN DE MIRA (AIM)", PurpleAcc, Color(0xFF12001F)) {
                    AimItem("Rebotes táctiles → 0 (sin micro-saltos)")
                    AimItem("Debounce táctil → 0 ms (respuesta instantánea)")
                    AimItem("Eventos táctiles parásitos bloqueados")
                    AimItem("Frecuencia pantalla fija 60 Hz → sin jitter")
                    AimItem("Vibración desactivada → dedo más estable")
                    AimItem("Zoom accesibilidad → OFF")
                    AimItem("Free Fire en máxima prioridad del scheduler")
                    AimItem("GPU sin capas de debug")
                }

                OptCard("RENDIMIENTO GENERAL", BlueAcc, Color(0xFF0D1B2A)) {
                    OptItem("Animaciones → 0 (cero lag visual)")
                    OptItem("CPU en modo rendimiento máximo")
                    OptItem("Apps en segundo plano congeladas")
                    OptItem("Solo 1 proceso extra permitido")
                    OptItem("Sincronización, GPS y notificaciones pausados")
                    OptItem("WiFi estable sin escaneos")
                    OptItem("RAM limpiada dos veces")
                    OptItem("Mantenimiento del sistema aplazado")
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
                Text(when {
                    isLoading -> "OPTIMIZANDO..."
                    isActive  -> "DESACTIVAR MODO"
                    else      -> "ACTIVAR MODO JUEGO"
                }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // ── Tips antes de jugar ───────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ANTES DE CADA PARTIDA", fontSize = 10.sp,
                        color = YellowAcc.copy(alpha = 0.8f), fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp)
                    TipItem("Activa esta app PRIMERO, luego abre Free Fire")
                    TipItem("Cierra todas las apps del historial reciente")
                    TipItem("Activa modo avión y desactívalo (ping más limpio)")
                    TipItem("Carga mínimo al 50 % de batería")
                    TipItem("No uses dos dedos al levantar la mira — causa salto")
                }
            }

            Text("Activa ANTES de abrir Free Fire · Galaxy A06",
                fontSize = 10.sp, color = GreyText.copy(alpha = 0.5f),
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ── Slider de sensibilidad ────────────────────────────────────────────────────

@Composable
private fun SensSlider(
    label: String,
    hint: String,
    value: Int,
    min: Int,
    max: Int,
    color: Color,
    onChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, fontSize = 10.sp, color = GreyText)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$value", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) - 1,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Color(0xFF2A2A2A)
            )
        )
    }
}

// ── Componentes reutilizables ─────────────────────────────────────────────────

@Composable
private fun OptCard(
    title: String,
    titleColor: Color,
    bg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontSize = 10.sp, color = titleColor.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            content()
        }
    }
}

@Composable
private fun A06Item(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚡", fontSize = 11.sp, color = OrangeAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun AimItem(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("◈", fontSize = 12.sp, color = PurpleAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun OptItem(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✓", fontSize = 12.sp, color = GreenBright)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun TipItem(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("›", fontSize = 13.sp, color = YellowAcc)
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
