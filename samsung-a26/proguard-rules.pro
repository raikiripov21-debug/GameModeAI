# GameMode AI A26 — ProGuard rules
-keep class com.gamemode.a26.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn kotlin.**
