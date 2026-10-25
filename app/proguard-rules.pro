# Tandem's R8 rules. R8 shrinks and optimizes; names are kept (-dontobfuscate) so stack traces in logcat stay
# readable and the baseline profile (src/main/baseline-prof.txt) matches classes by their real names.
-dontobfuscate

# QuietSemantics (ui/QuietSemantics.kt) reaches these Compose fields by reflection.
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat composeAccessibilityDelegate;
}
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat {
    java.util.List enabledServices;
}

# Shizuku: the provider is declared in the manifest, the binder API is AIDL, and Shell.run calls the private
# Shizuku.newProcess by reflection.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# kotlinx-serialization (the library ships its own rules too): the generated serializers of our @Serializable
# classes are looked up through their companions.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.zahakj.tandem.**$$serializer { *; }

# Enums are looked up by name in saved settings.
-keepclassmembers enum io.github.zahakj.tandem.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# media3 and Coil ship consumer rules; these only silence optional dependencies they reference.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
