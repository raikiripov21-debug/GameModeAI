# GameModeAI — Samsung Galaxy A06

App Android para optimizar el rendimiento en juegos usando Shizuku.  
Optimizada específicamente para el **Samsung Galaxy A06 (Exynos 850, 60 Hz LCD)**.

## Características

- Activa/desactiva el modo juego con un botón
- Muestra RAM, CPU, temperatura y FPS en tiempo real
- Servicio en segundo plano con mantenimiento automático cada 5 minutos
- **Fase 2 térmica** a los 20 minutos para partidas largas
- **Emergencia térmica automática** a los 46°C
- **BootReceiver**: reactiva el modo juego automáticamente tras reiniciar el teléfono
- Optimizaciones Samsung GOS, Bixby, pantalla 60Hz, touch, GPS, WiFi y más
- Inmersive mode completo para Free Fire y Free Fire MAX

## Diferencias vs versión A26

| Característica | A06 | A26 |
|---|---|---|
| Chip | Exynos 850 | Exynos 1380 |
| Pantalla | 60 Hz LCD | 90 Hz AMOLED |
| Fase 2 | 20 min | 25 min |
| Emergencia térmica | 46°C | 52°C |
| Background limit | 2 | 3 |
| Touch sensitivity | alta LCD | ultra AMOLED |

## Requisitos

- Android 7.0+ (API 24)
- Shizuku instalado, activo y con permiso concedido a GameModeAI

## Compilar

El APK se genera automáticamente con GitHub Actions en cada push.

Para compilar localmente:
```bash
./gradlew assembleRelease
```

## Permisos Shizuku (Android 11+)

1. Instala **Shizuku** desde Play Store
2. Activa la depuración inalámbrica en Opciones de desarrollador
3. Conecta Shizuku via ADB o depuración inalámbrica
4. Abre Shizuku y concede permiso a GameModeAI
5. Abre GameModeAI y pulsa **ACTIVAR**
