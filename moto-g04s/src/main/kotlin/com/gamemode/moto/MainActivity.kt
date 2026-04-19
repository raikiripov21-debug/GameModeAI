package com.gamemode.moto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
        setContent {
            GameModeMotoTheme {
                GameModeScreen(
                    onStartService = { startGameService() },
                    onStopService = { stopGameService() }
                )
            }
        }
    }

    private fun startGameService() {
        val intent = Intent(this, GameService::class.java)
        startForegroundService(intent)
    }

    private fun stopGameService() {
        val intent = Intent(this, GameService::class.java)
        stopService(intent)
    }
}

@Composable
fun GameModeMotoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00FF88),
            secondary = Color(0xFF00E5FF),
            background = Color(0xFF0A0F1E),
            surface = Color(0xFF111827)
        ),
        content = content
    )
}

@Composable
fun GameModeScreen(onStartService: () -> Unit, onStopService: () -> Unit) {
    var isActive by remember { mutableStateOf(false) }
    var fps by remember { mutableStateOf(60) }
    var temp by remember { mutableStateOf(30) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "GameMode AI",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00FF88)
        )
        Text(
            text = "Motorola Moto G04s — Unisoc T606",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                StatusRow("Estado", if (isActive) "ACTIVO" else "INACTIVO",
                    if (isActive) Color(0xFF00FF88) else Color(0xFF64748B))
                Spacer(modifier = Modifier.height(12.dp))
                StatusRow("FPS objetivo", "$fps fps", Color(0xFF00E5FF))
                Spacer(modifier = Modifier.height(12.dp))
                StatusRow("Temperatura", "${temp}°C", if (temp > 36) Color(0xFFFF4444) else Color(0xFF00FF88))
                Spacer(modifier = Modifier.height(12.dp))
                StatusRow("Dispositivo", "Unisoc T606 (8 núcleos)", Color(0xFFFFAA00))
                Spacer(modifier = Modifier.height(12.dp))
                StatusRow("App ID", BuildConfig.APPLICATION_ID, Color(0xFF8888AA))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Optimizaciones G04s", fontWeight = FontWeight.Bold, color = Color(0xFF00FF88), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "CPU Governor: performance (Unisoc T606)",
                    "GPU: IMG PowerVR GE8320 boost",
                    "RAM: 4GB — aggressive cache sweep",
                    "FPS Lock: 60Hz estable (LCD)",
                    "Thermal throttle: umbral 36°C",
                    "Battery: eficiencia en gama baja"
                ).forEach { opt ->
                    Text("• $opt", color = Color(0xFF94A3B8), fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                isActive = !isActive
                if (isActive) onStartService() else onStopService()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) Color(0xFFFF4444) else Color(0xFF00FF88)
            )
        ) {
            Text(
                text = if (isActive) "DETENER OPTIMIZACIÓN" else "INICIAR OPTIMIZACIÓN",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0A0F1E),
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF64748B), fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
