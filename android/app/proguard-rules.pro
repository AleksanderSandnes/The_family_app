# R8 rules for The Family App (release build; minify + shrinkResources).
#
# The hard constraint: Supabase / Ktor / kotlinx-serialization resolve
# serializers and class/field names reflectively. Every @Serializable model
# must keep its generated serializer and Companion, or decoding crashes at
# runtime with no compile-time signal. See Play Store Release Guide §A7.

# Readable crash reports (mapping.txt still deobfuscates fully).
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,EnclosingMethod

# ── kotlinx-serialization ────────────────────────────────────────────────
# Generated serializers + Companion serializer() lookups for our models.
-keep,includedescriptorclasses class com.sandnes.familyapp.**$$serializer { *; }
-keepclassmembers class com.sandnes.familyapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.sandnes.familyapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable classes keep their fields (decoded by name via descriptors).
-keepclassmembers @kotlinx.serialization.Serializable class com.sandnes.familyapp.** {
    <fields>;
}

# ── Ktor / OkHttp engine ─────────────────────────────────────────────────
# Optional TLS providers OkHttp probes for reflectively; absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Ktor's optional coroutines debug probes / management beans.
-dontwarn io.ktor.**
-dontwarn java.lang.management.**

# ── kotlin-reflect (pulled in for maps-compose) ──────────────────────────
-dontwarn kotlin.reflect.jvm.internal.**
-keep class kotlin.Metadata { *; }
