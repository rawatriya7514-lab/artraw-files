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

# 6. Optimization & Shrinking settings
-repackageclasses
-allowaccessmodification
-mergeinterfacesaggressively
