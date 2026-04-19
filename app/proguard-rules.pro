# GameModeAI ProGuard Rules

# Shizuku - mantener todas las clases de la API
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Mantener IInterface de Android para Shizuku
-keep class android.os.IInterface { *; }
-keep class android.os.IBinder { *; }

# Evitar que R8 elimine anotaciones de Compose runtime
-keepclassmembers class androidx.compose.** { *; }

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Reglas genéricas de Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
