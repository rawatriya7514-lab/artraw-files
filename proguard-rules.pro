# ==============================================================================
# ARTRAW Files — ProGuard & R8 Obfuscation & Hardening Rules
# Target: Android 15 / 16 (API Level 35 / 36)
# ==============================================================================

# 1. Preserve Application & Android Components Declared in Manifest
-keep public class com.artraw.files.ui.MainActivity
-keep public class com.artraw.files.ui.BrowserActivity
-keep public class com.artraw.files.ui.CategoryActivity
-keep public class com.artraw.files.ui.AnalyzerActivity
-keep public class com.artraw.files.ui.TextPreviewActivity
-keep public class com.artraw.files.ui.SettingsActivity
-keep public class com.artraw.files.ui.OnboardingActivity
-keep public class com.artraw.files.preview.ArtrawFileProvider

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 2. Preserve Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 3. Preserve Custom Views and View Attributes (if inflated via XML)
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 4. Preserve R Class Constants for AAPT2 resource resolution
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 5. Anti-Reverse Engineering & Anti-Decompilation Protections
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
-dontwarn javax.annotation.**

# 6. Google Mobile Ads (AdMob) & Play Services Rules (Full Retention)
-dontwarn androidx.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn com.google.android.apps.common.proguard.**
-dontwarn com.google.android.gms.**
-dontwarn android.support.**
-dontwarn org.jspecify.nullness.**
-dontwarn sun.misc.Unsafe
-dontwarn libcore.io.Memory
-dontwarn **.R$*
-dontwarn android.security.NetworkSecurityPolicy
-dontwarn android.app.Notification

-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

-keep class * implements com.google.android.gms.ads.mediation.MediationAdapter { public *; }
-keep class * implements com.google.ads.mediation.MediationAdapter { public *; }
-keep class * implements com.google.android.gms.ads.mediation.customevent.CustomEvent { public *; }
-keep class * implements com.google.ads.mediation.customevent.CustomEvent { public *; }
-keep class * extends com.google.android.gms.ads.mediation.MediationAdNetworkAdapter { public *; }
-keep class * extends com.google.android.gms.ads.mediation.Adapter { public *; }
-keep class com.google.android.gms.ads.internal.client.LiteSdkInfo { public *; }
-keep class com.google.android.gms.ads.MobileAds { *; }
-keep class com.google.android.gms.ads.AdView { *; }
-keep class com.google.android.gms.ads.AdRequest { *; }
-keep class com.google.android.gms.ads.AdSize { *; }
-keep class com.google.android.gms.ads.AdListener { *; }
-keep class com.google.android.gms.ads.LoadAdError { *; }
-keep class com.google.android.gms.common.internal.safeparcel.SafeParcelable { public static final *** NULL; }
-keep class com.google.android.gms.common.internal.ReflectedParcelable
-keepnames class * implements com.google.android.gms.common.internal.ReflectedParcelable
-keepclassmembers class * implements android.os.Parcelable { public static final *** CREATOR; }

# 7. Aggressive Bytecode Obfuscation & Flat Repackaging
-repackageclasses 'com.artraw.files.core'
-allowaccessmodification
-overloadaggressively
-mergeinterfacesaggressively
