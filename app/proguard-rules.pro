# GameModeAI ProGuard / R8 Rules

# ── Shizuku API ────────────────────────────────────────────────────────────────
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# IInterface e IBinder necesarios para Shizuku
-keep class android.os.IInterface { *; }
-keep class android.os.IBinder { *; }

# ── Compose ────────────────────────────────────────────────────────────────────
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Coroutines ─────────────────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.android.** { *; }

# ── JSON (org.json nativo de Android) ──────────────────────────────────────────
-keep class org.json.** { *; }

# ── Atributos de depuración ────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keep public class * extends java.lang.Exception

# ── Optimizaciones agresivas para APK pequeño ─────────────────────────────────
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# ── Evitar advertencias en bibliotecas de Kotlin ──────────────────────────────
-dontwarn kotlin.**
-dontwarn kotlin.reflect.**
-dontwarn kotlinx.**
