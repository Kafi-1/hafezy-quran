# PdfiumAndroid ships native .so files; the JNI bridge resolves Kotlin/Java classes
# by name, so R8 must never rename or strip them. The package is io.legere
# (AndroidX-native fork); keeping the old `com.shockwave` package did nothing and
# let R8 break the JNI bridge in release builds.
-keep class io.legere.pdfiumandroid.** { *; }
-keepclassmembers class io.legere.pdfiumandroid.** { *; }
-dontwarn io.legere.pdfiumandroid.**

# Belt-and-braces: if any transitive artifact still references the removed
# Support Library class, don't fail the build over it (Jetifier should rewrite it).
-dontwarn android.support.v4.**

# Room / Hilt / Compose are handled by their own consumer rules, but keep
# entities & serializable models safe from aggressive R8 renaming.
-keep class com.hafeziquran.app.data.local.entity.** { *; }
-keepclassmembers class com.hafeziquran.app.data.local.entity.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Pdfium uses reflection for optional features.
-dontwarn io.legere.pdfiumandroid.**

# Whisper JNI: native method names are bound to the Kotlin class/package at
# link time, so R8 must neither rename the bridge nor strip its methods.
-keep class com.hafeziquran.app.data.whisper.WhisperBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
