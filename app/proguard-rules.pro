# Remove common logging calls in release builds (reduces method count + a bit of size).
# NOTE: Keep this only if you are OK with logs being stripped in release.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Kotlin metadata is needed for reflection; safe to keep by default.
# (No explicit keep rules here to avoid blocking shrinking.)

# Keep common annotations that some AndroidX libraries may look up at runtime.
-keepattributes *Annotation*

# Avoid warnings for optional / unused dependencies when shrinking.
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
-dontwarn com.google.accompanist.**

# Keep your public API surface minimal (default R8 behavior) — no broad -keep rules.
# Components declared in the AndroidManifest (activities/services/receivers) are kept automatically.