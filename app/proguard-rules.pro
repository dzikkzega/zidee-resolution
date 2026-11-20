# Room entities, DAOs & database
-keep class com.zidee.ress.data.local.entity.** { *; }
-keep class com.zidee.ress.data.local.ZideeDatabase { *; }
-keep @androidx.annotation.Keep class * { *; }

# FFmpegKit JNI & native callbacks
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-keepclassmembers class * {
    @com.arthenica.ffmpegkit.* <methods>;
}

# Koin reflection / DSL bindings & ViewModels
-keep class org.koin.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Kotlin Coroutines & Lifecycle StateFlow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Domain models used across serialization / reflection / Room mappers
-keep class com.zidee.ress.domain.model.** { *; }
