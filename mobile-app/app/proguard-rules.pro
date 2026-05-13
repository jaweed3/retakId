# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**

# Google Play Services (Location, Auth)
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Data classes for serialization
-keep class com.unidagontor.retakid.data.** { *; }
-keep class com.unidagontor.retakid.ui.viewmodel.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# OSMDroid
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# Keep Retrofit/OkHttp if used
-dontwarn okhttp3.**
-dontwarn retrofit2.**