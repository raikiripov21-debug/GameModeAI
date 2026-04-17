package com.gamemodeai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameModeAITheme {
                GameModeScreen(context = this)
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
fun GameModeScreen(context: Context) {
    var isActive by remember { mutableStateOf(Prefs.isActive(context)) }
    var ramMb by remember { mutableStateOf(getAvailableRamMb(context)) }
    var shizukuStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        ramMb = getAvailableRamMb(context)
        shizukuStatus = when {
            !ShizukuHelper.isShizukuAvailable() -> "Shizuku no disponible"
            !ShizukuHelper.hasPermission() -> "Sin permiso Shizuku"
            else -> "Shizuku listo"
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = "GameModeAI",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) Color(0xFF1B5E20) else Color(0xFF424242)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ESTADO",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isActive) "ACTIVO" else "INACTIVO",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RAM Disponible",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$ramMb MB",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = shizukuStatus,
                        fontSize = 12.sp,
                        color = when {
                            shizukuStatus.contains("listo") -> Color(0xFF2E7D32)
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val newState = !isActive
                    if (newState) {
                        val success = ShizukuHelper.enableGameMode()
                        if (success) {
                            isActive = true
                            Prefs.setActive(context, true)
                            GameService.start(context)
                        }
                    } else {
                        ShizukuHelper.disableGameMode()
                        isActive = false
                        Prefs.setActive(context, false)
                        GameService.stop(context)
                    }
                    ramMb = getAvailableRamMb(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFFC62828) else Color(0xFF2E7D32)
                )
            ) {
                Text(
                    text = if (isActive) "DESACTIVAR MODO" else "ACTIVAR MODO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GameModeAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
