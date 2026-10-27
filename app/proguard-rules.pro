# Khotwa AI — R8 rules
-keep class com.khotwa.ai.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
# ML Kit GenAI / AICore
-keep class com.google.mlkit.genai.** { *; }
-dontwarn com.google.mlkit.genai.**
# Credentials / Google Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.gms.**
# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
