# Keep Compose runtime
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# Keep data classes
-keep class com.locationtracker.LocationData { *; }

# Keep service and activity
-keep class com.locationtracker.MainActivity { *; }
-keep class com.locationtracker.LocationService { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimize
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove debug-only files
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlinx.coroutines.internal.**

# Strip unnecessary metadata files from APK
-dontnote **
-ignorewarnings
