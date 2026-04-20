package com.gamemode.moto

import android.content.Intent
import android.os.Build
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdaptiveEngine.restoreState(this)
        setContent { MaterialTheme { GameModeMotoScreen() } }
    }

    @Composable
    private fun GameModeMotoScreen() {
        val ctx = this
        var isActive by remember { mutableStateOf(Prefs.isActive(ctx)) }

        val status by flow {
            while (true) { emit(AdaptiveEngine.status()); delay(2000) }
        }.collectAsStateWithLifecycle(AdaptiveEngine.status())

        val aiState by flow {
            while (true) { emit(AdaptiveEngine.state); delay(2000) }
        }.collectAsStateWithLifecycle(AdaptiveEngine.state)

        Column(
            modifier = Modifier.fillMaxSize()
                .background(Color(0xFF0A0F1E))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            Text("GameModeAI", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Motorola Moto G04s", fontSize = 14.sp, color = Color(0xFF7788AA))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141E30))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Motor IA (Moto)", color = Color(0xFF7788AA), fontSize = 13.sp)
                        val stateColor = when (aiState) {
                            AdaptiveEngine.State.STABILIZED      -> Color(0xFF4CAF50)
                            AdaptiveEngine.State.ADAPTING        -> Color(0xFFFF9800)
                            AdaptiveEngine.State.THERMAL_CAUTION -> Color(0xFFF44336)
                            else                                 -> Color(0xFF2196F3)
                        }
                        Text(aiState.name, color = stateColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(status, color = Color.White, fontSize = 12.sp)
                    Text("Dispositivo: moto_g04s | Unisoc T606 | 4 GB",
                        color = Color(0xFF556677), fontSize = 11.sp)
                }
            }

            val btnColor = if (isActive) Color(0xFFF44336) else Color(0xFF1565C0)
            Button(
                onClick = {
                    isActive = !isActive
                    Prefs.setActive(ctx, isActive)
                    val svc = Intent(ctx, GameService::class.java)
                    if (isActive) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            ctx.startForegroundService(svc) else ctx.startService(svc)
                    } else ctx.stopService(svc)
                },
                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isActive) "DETENER" else "INICIAR OPTIMIZACION",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            val chipColor = if (isActive) Color(0xFF0D2137) else Color(0xFF141E30)
            Box(Modifier.background(chipColor, RoundedCornerShape(8.dp)).padding(12.dp, 6.dp)) {
                Text(if (isActive) "ACTIVO" else "INACTIVO",
                    color = if (isActive) Color(0xFF64B5F6) else Color(0xFF556677),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
