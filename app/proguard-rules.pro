# kotlinx.serialization: keep generated serializers for the Jamendo DTOs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.vikash.mp3player.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.vikash.mp3player.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our own @Serializable classes also live outside data (playback queues).
-keepclassmembers class com.vikash.mp3player.** {
    *** Companion;
}
-keepclasseswithmembers class com.vikash.mp3player.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.vikash.mp3player.** {
    *** Companion;
    <fields>;
}

# Cast creates this by name, from the OPTIONS_PROVIDER_CLASS_NAME meta-data in the manifest.
-keep class com.vikash.mp3player.cast.CastOptionsProvider { <init>(); }

# Glance widget buttons store their callback class by name and create it later.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Readable stack traces in Play's crash reports (the mapping file is uploaded with the bundle).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# smbj (network folders): its event bus (mbassador) finds @Handler methods by reflection, and
# smbj itself builds some classes by name. Kerberos/GSS and desktop-only classes are never used.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class * { @net.engio.mbassy.listener.Handler *; }
-dontwarn com.hierynomus.**
-dontwarn net.engio.mbassy.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.**
-dontwarn sun.security.**
-dontwarn java.beans.**
