# PdfiumAndroid ships native .so files; keep the JNI bridge intact.
-keep class com.shockwave.** { *; }
-keepclassmembers class com.shockwave.** { *; }

# Room / Hilt / Compose are handled by their own consumer rules, but keep
# entities & serializable models safe from aggressive R8 renaming.
-keep class com.hafeziquran.app.data.local.entity.** { *; }
-keepclassmembers class com.hafeziquran.app.data.local.entity.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Google AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Pdfium uses reflection for optional features.
-dontwarn com.shockwave.**
