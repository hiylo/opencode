# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# MNN JNI callback interface — native code resolves its name via FindClass,
# so it must not be obfuscated or renamed.
-keep class org.hiylo.opencode.ml.MnnLlm$StreamingCallback { *; }
-keepclassmembers class org.hiylo.opencode.ml.MnnLlm {
    native <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.hiylo.opencode.**$$serializer { *; }
-keepclassmembers class org.hiylo.opencode.** {
    *** Companion;
}
-keepclasseswithmembers class org.hiylo.opencode.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn com.typesafe.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
