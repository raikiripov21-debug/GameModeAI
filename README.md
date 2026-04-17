# GameModeAI

Android app para optimizar el rendimiento en juegos usando Shizuku, enfocada en estabilidad térmica, respuesta táctil y sesiones largas en Free Fire.

## Funciones
- Activa/desactiva el modo juego con un botón
- Muestra RAM, CPU, temperatura y fluidez en tiempo real
- Servicio en segundo plano con mantenimiento automático cada 5 minutos
- Fase 2 térmica tras 20 minutos para partidas largas
- Perfil automático de aim estable sin controles manuales de sensibilidad
- Solicita permiso de notificaciones en Android 13+
- Aplica optimizaciones de animación, táctil, red, RAM y servicios Samsung via Shizuku
- Guarda el estado con SharedPreferences

## Requisitos
- Android 7.0+ (API 24)
- Shizuku instalado, activo y con permiso concedido a GameModeAI

## Compilar
El APK se genera automáticamente con GitHub Actions en cada push.
