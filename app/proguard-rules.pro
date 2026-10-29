# Retrofit / Gson
-keepattributes Signature, *Annotation*
-keep class com.uniban.campo.data.remote.dto.** { *; }
-keep class com.google.gson.** { *; }

# LiteRT (TensorFlow Lite)
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**
