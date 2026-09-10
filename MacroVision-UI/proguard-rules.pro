# Google Play Services Ads ProGuard Rules
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}
-keep class com.google.android.gms.internal.ads.** { *; }

# Keep JavaScript interfaces for WebViews used by AdMob
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowaccessmodification class com.fitter.** { *; }

# Ktor & Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**
