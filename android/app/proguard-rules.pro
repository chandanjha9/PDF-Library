# Referenced by build.gradle.kts. Only used if isMinifyEnabled is turned on for release.
# (Recommended: it would shrink material-icons-extended and cut APK size substantially.)

# Gson reflects over these models — keep field names.
-keep class com.example.pdflibrary.data.model.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface com.example.pdflibrary.data.network.BookApiService
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
