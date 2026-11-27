# Echo Wear: keep everything an extension APK might reach through the host class loader.
-dontobfuscate
-keep class dev.brahmkshatriya.echo.common.** { *; }
-keep class dev.brahmkshatriya.echo.extensions.** { *; }
-keep class dev.brahmkshatriya.echo.playback.** { *; }
-keep class dev.brahmkshatriya.echo.di.** { *; }
-keep class dev.brahmkshatriya.echo.utils.** { *; }
-keepclassmembers class * implements dev.brahmkshatriya.echo.common.clients.ExtensionClient {
    public <init>();
}
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.brahmkshatriya.echo.**$$serializer { *; }
-keepclassmembers class dev.brahmkshatriya.echo.** { *** Companion; }
-keepclasseswithmembers class dev.brahmkshatriya.echo.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Misc
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.StringConcatFactory
