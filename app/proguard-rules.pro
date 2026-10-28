# Retrofit / Gson
-keepattributes Signature, *Annotation*
-keep class com.uniban.campo.data.remote.dto.** { *; }
-keep class com.google.gson.** { *; }

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
