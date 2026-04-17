# GameModeAI — ProGuard rules

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Mantener clases principales de la app
-keep class com.gamemodeai.** { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
