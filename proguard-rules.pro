# ==============================================================================
# ARTRAW Files — ProGuard & R8 Release Optimization Rules
# Target: Android 15 / 16 (API Level 35 / 36)
# ==============================================================================

# 1. Preserve Application & Android Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 2. Preserve ARTRAW Files Specific Components
-keep class com.artraw.files.ui.** { *; }
-keep class com.artraw.files.model.** { *; }
-keep class com.artraw.files.preview.ArtrawFileProvider { *; }
-keep class com.artraw.files.util.** { *; }
-keep class com.artraw.files.operations.** { *; }
-keep class com.artraw.files.repository.** { *; }
-keep class com.artraw.files.analysis.** { *; }

# 3. Preserve Custom Views and Attributes
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

# 5. Preserve Kotlin Metadata and Reflection Attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
-dontwarn javax.annotation.**

# 6. Google Mobile Ads (AdMob) & Play Services Rules
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

# AdMob and Google Play Services broad keep rules to prevent stripping
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

# 7. Optimization & Shrinking settings
-repackageclasses
-allowaccessmodification
-mergeinterfacesaggressively
