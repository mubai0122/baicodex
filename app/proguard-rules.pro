-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute S
-keep class b.ai.MainActivity { *; }
-keep class b.ai.App { *; }
-keep class b.ai.exec.ShizukuShell { *; }
-keep class b.ai.exec.TaskReceiver { *; }
-keep class rikka.shizuku.ShizukuProvider { *; }
-keep class androidx.core.content.FileProvider { *; }
-keep class com.vapro.vae.stringfog.** { *; }
-dontwarn com.vapro.vae.stringfog.**
-obfuscationdictionary obfuscation_dict.txt
-classobfuscationdictionary obfuscation_dict.txt
-packageobfuscationdictionary obfuscation_dict.txt
-repackageclasses 'b.ai'
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class b.ai.core.Js$Bridge { *; }
-keepclassmembers class **$Bridge {
    public java.lang.String call(java.lang.String, java.lang.String);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class **.R$* { *; }

-keepclassmembers class * {
    synthetic <methods>;
}
-keepclassmembers class * {
    *** $default$*(...);
}
-keepclassmembers class * {
    *** $composer$*(...);
}
-keepclassmembers class * {
    *** $changed$*(...);
}
-keep class androidx.compose.runtime.internal.ComposableLambdaImpl { *; }
-keep class androidx.compose.runtime.internal.ComposableLambdaKt { *; }

-dontwarn androidx.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn dev.rikka.shizuku.**
-dontwarn com.kyant.backdrop.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

-assumenosideeffects class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    private kotlin.coroutines.jvm.internal.DebugMetadata getDebugMetadataAnnotation() return null;
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}