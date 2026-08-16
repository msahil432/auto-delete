# ==============================================================================
# Multi Tool - ProGuard / R8 Security and Optimization Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Stack Trace & Line Number Preservation (for crash deobfuscation)
# ------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# 2. Strip Logging in Release Builds
# Prevents sensitive internal paths, package names, and metadata from leaking in logcat
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ------------------------------------------------------------------------------
# 3. Room Database & TypeConverters
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 4. Moshi & JSON Serialization
# ------------------------------------------------------------------------------
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class *JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# ------------------------------------------------------------------------------
# 5. Play Services & Location Geofencing
# ------------------------------------------------------------------------------
-keep class com.google.android.gms.location.** { *; }
-keep interface com.google.android.gms.location.** { *; }

# ------------------------------------------------------------------------------
# 6. MLKit Barcode Scanning & CameraX
# ------------------------------------------------------------------------------
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# ------------------------------------------------------------------------------
# 7. Kotlin Coroutines & DataStore
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }

# ------------------------------------------------------------------------------
# 8. WorkManager
# ------------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ------------------------------------------------------------------------------
# 9. Sentry Crash Reporting & Performance Tracing
# ------------------------------------------------------------------------------
-keepattributes LineNumberTable,SourceFile
-dontwarn org.slf4j.**
-dontwarn io.sentry.**

